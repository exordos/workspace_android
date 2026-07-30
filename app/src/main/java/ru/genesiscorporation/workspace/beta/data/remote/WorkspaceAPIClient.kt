package ru.genesiscorporation.workspace.beta.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.append
import io.ktor.http.contentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ActiveCredentialSnapshot
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadFileResponseData
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.TokenRefreshRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.login.accessTokenMatchesAccount
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.nio.channels.UnresolvedAddressException
import java.util.UUID

@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal val explicitNullsRequestJson = Json {
    explicitNulls = true
}

class WorkspaceAPIClient(
    val client: HttpClient,
    val userViewModel: UserViewModel,
    val sessionCookieStore: SessionCookieStore
): APIClient {
    private val refreshMutex = Mutex()
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified RequestData : Any, reified Response : Any, reified ResponseError : Any> performRequest(
        request: ApiRequest<RequestData, Response, ResponseError>
    ): ApiResult<Response, ApiError> {
        val session = userViewModel.repo.activeCredentialSnapshot()
        val baseUrl = session.baseUrl
        if (!request.isAbsoluteUrl && baseUrl.isNullOrBlank()) {
            return ApiResult.Error(
                ApiError(
                    "Workspace server is not selected",
                    "MISSING_BASE_URL",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val accessToken = session.accessToken.orEmpty()
        val isOidc = accessToken.contains("__Host-sessionid=")
        val urlSuffix = if (request.shouldApplySuffix) {
            if (isOidc) "/json" else "/api/v1"
        } else {
            ""
        }
        val urlString = if (request.isAbsoluteUrl) request.url else "${baseUrl}${urlSuffix}${request.url}"
        return try {
            val requestBuilder = workspaceRequestBuilder(request, urlString, accessToken)
            val httpResponse: HttpResponse = client.request(requestBuilder)

            if (httpResponse.status.isSuccess()) {
                if (
                    request.requiresApiKey &&
                    session.ownerKey != null &&
                    !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
                ) {
                    return ApiResult.Error(accountChangedError())
                }
                val json = Json { ignoreUnknownKeys = true }

                val responseString = httpResponse.readTextWithLimit()
                if (Response::class == String::class) {
                    val finalResponseString = if (request.shouldReturnUrl) httpResponse.call.request.url.toString() else if (request.hasSessionCookie) sessionCookieStore.getFullSessionCookie() ?: "" else responseString
                    ApiResult.Success(
                        finalResponseString as Response,
                        httpResponse.apiResponseMetadata(),
                    )
                } else {
                    val response = json.decodeFromString<Response>(responseString)
                    ApiResult.Success(
                        response,
                        httpResponse.apiResponseMetadata(),
                    )
                }
            } else if (
                shouldAttemptTokenRefresh(
                    statusCode = httpResponse.status.value,
                    requiresApiKey = request.requiresApiKey,
                )
            ) {
                val refreshedToken = when (
                    val refreshResult = refreshToken(session, accessToken)
                ) {
                    is ApiResult.Success -> refreshResult.value
                    is ApiResult.Error -> return ApiResult.Error(refreshResult.error)
                }
                val retryRequestBuilder = workspaceRequestBuilder(
                    request,
                    urlString,
                    refreshedToken,
                )
                val httpResponseAfterRefresh: HttpResponse = client.request(retryRequestBuilder)
                if (httpResponseAfterRefresh.status.isSuccess()) {
                    if (
                        session.ownerKey != null &&
                        !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
                    ) {
                        return ApiResult.Error(accountChangedError())
                    }
                    val json = Json { ignoreUnknownKeys = true }

                    val responseString = httpResponseAfterRefresh.readTextWithLimit()
                    if (Response::class == String::class) {
                        val finalResponseString = if (request.shouldReturnUrl) httpResponseAfterRefresh.call.request.url.toString() else if (request.hasSessionCookie) sessionCookieStore.getFullSessionCookie() ?: "" else responseString
                        ApiResult.Success(
                            finalResponseString as Response,
                            httpResponseAfterRefresh.apiResponseMetadata(),
                        )
                    } else {
                        val response = json.decodeFromString<Response>(responseString)
                        ApiResult.Success(
                            response,
                            httpResponseAfterRefresh.apiResponseMetadata(),
                        )
                    }
                } else if (httpResponseAfterRefresh.status.value == 401) {
                    session.ownerKey?.let {
                        userViewModel.removeActiveAccountIfOwnerAndWait(it)
                    }
                    val error = ApiError(
                        "Authentication expired",
                        "401",
                        ApiErrorKind.UNAUTHORIZED,
                        httpStatus = 401,
                    )

                    ApiResult.Error(error)
                } else {
                    val responseString = httpResponseAfterRefresh.readTextWithLimit()
                    ApiResult.Error(
                        httpApiError(
                            httpResponseAfterRefresh.status.value,
                            responseString,
                            httpResponseAfterRefresh.headers,
                        ),
                    )
                }
            }  else {
                val responseString = httpResponse.readTextWithLimit()
                ApiResult.Error(
                    httpApiError(
                        httpResponse.status.value,
                        responseString,
                        httpResponse.headers,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: ResponseBodyTooLargeException) {
            ApiResult.Error(responseBodyTooLargeError())
        } catch (timeout: HttpRequestTimeoutException) {
            ApiResult.Error(ApiError("Request timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: ConnectTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: SocketTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (serialization: SerializationException) {
            ApiResult.Error(
                ApiError(
                    "Invalid server response",
                    "MALFORMED_RESPONSE",
                    ApiErrorKind.MALFORMED_RESPONSE,
                ),
            )
        } catch (network: UnresolvedAddressException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (network: IOException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (exception: Exception) {
            val error = ApiError("Request failed", "REQUEST_FAILED", ApiErrorKind.UNKNOWN)

            ApiResult.Error(error)
        }
    }

    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        streamUuid: String,
    ): ApiResult<UploadFileResponseData, ApiError> {
        val mime = try {
            context.contentResolver.getType(uri).orEmpty()
        } catch (security: SecurityException) {
            return ApiResult.Error(
                ApiError(
                    "The selected file is no longer accessible",
                    "FILE_PERMISSION_DENIED",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val normalizedMime = mime
            .substringBefore(';')
            .trim()
            .lowercase()
            .takeIf { it.matches(Regex("""[a-z0-9.+-]+/[a-z0-9.+-]+""")) }
            ?: "application/octet-stream"
        if (normalizedMime.length > 127) {
            return ApiResult.Error(
                ApiError(
                    "Selected file type is invalid",
                    "UNSUPPORTED_FILE_TYPE",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val fileName = sanitizeUploadFileName(
            runCatching { resolveDisplayName(context, uri) }.getOrNull(),
        )
        val bytes = try {
            readUriBytes(context, uri)
        } catch (tooLarge: UploadTooLargeException) {
            return ApiResult.Error(
                ApiError(
                    "File is larger than 25 MiB",
                    "FILE_TOO_LARGE",
                    ApiErrorKind.VALIDATION,
                ),
            )
        } catch (empty: UploadEmptyException) {
            return ApiResult.Error(
                ApiError(
                    "File is empty",
                    "FILE_EMPTY",
                    ApiErrorKind.VALIDATION,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: IOException) {
            return ApiResult.Error(
                ApiError(
                    "Could not read the selected file",
                    "FILE_READ_FAILED",
                    ApiErrorKind.VALIDATION,
                ),
            )
        } catch (security: SecurityException) {
            return ApiResult.Error(
                ApiError(
                    "The selected file is no longer accessible",
                    "FILE_PERMISSION_DENIED",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val session = userViewModel.repo.activeCredentialSnapshot()
        val baseUrl = session.baseUrl
        if (baseUrl.isNullOrBlank()) {
            return ApiResult.Error(
                ApiError(
                    "Workspace server is not selected",
                    "MISSING_BASE_URL",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val accessToken = session.accessToken.orEmpty()
        return try {
            val firstAttempt = uploadFileWithToken(
                baseUrl = baseUrl,
                accessToken = accessToken,
                streamUuid = streamUuid,
                bytes = bytes,
                mime = normalizedMime,
                fileName = fileName,
            )
            if (
                firstAttempt is ApiResult.Error &&
                firstAttempt.error.kind == ApiErrorKind.UNAUTHORIZED
            ) {
                val refreshedToken = when (
                    val refresh = refreshToken(session, accessToken)
                ) {
                    is ApiResult.Success -> refresh.value
                    is ApiResult.Error -> return ApiResult.Error(refresh.error)
                }
                val retry = uploadFileWithToken(
                    baseUrl = baseUrl,
                    accessToken = refreshedToken,
                    streamUuid = streamUuid,
                    bytes = bytes,
                    mime = normalizedMime,
                    fileName = fileName,
                )
                if (
                    retry is ApiResult.Error &&
                    retry.error.kind == ApiErrorKind.UNAUTHORIZED
                ) {
                    session.ownerKey?.let {
                        userViewModel.removeActiveAccountIfOwnerAndWait(it)
                    }
                }
                if (
                    retry is ApiResult.Success &&
                    session.ownerKey != null &&
                    !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
                ) {
                    ApiResult.Error(accountChangedError())
                } else {
                    retry
                }
            } else {
                if (
                    firstAttempt is ApiResult.Success &&
                    session.ownerKey != null &&
                    !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
                ) {
                    ApiResult.Error(accountChangedError())
                } else {
                    firstAttempt
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: ResponseBodyTooLargeException) {
            ApiResult.Error(responseBodyTooLargeError())
        } catch (timeout: HttpRequestTimeoutException) {
            ApiResult.Error(ApiError("Upload timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: ConnectTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: SocketTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (serialization: SerializationException) {
            ApiResult.Error(
                ApiError(
                    "Invalid upload response",
                    "MALFORMED_RESPONSE",
                    ApiErrorKind.MALFORMED_RESPONSE,
                ),
            )
        } catch (network: UnresolvedAddressException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (network: IOException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (exception: Exception) {
            ApiResult.Error(ApiError("Upload failed", "UPLOAD_FAILED", ApiErrorKind.UNKNOWN))
        }
    }

    suspend fun uploadOwnAvatar(
        context: Context,
        uri: Uri,
        userUuid: String,
    ): ApiResult<UserResponseData, ApiError> {
        if (runCatching { UUID.fromString(userUuid) }.isFailure) {
            return ApiResult.Error(
                ApiError(
                    "Current user identity is invalid",
                    "INVALID_USER_UUID",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val declaredMime = try {
            context.contentResolver.getType(uri)
        } catch (security: SecurityException) {
            return ApiResult.Error(
                ApiError(
                    "The selected image is no longer accessible",
                    "FILE_PERMISSION_DENIED",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val bytes = try {
            readUriBytes(context, uri)
        } catch (tooLarge: UploadTooLargeException) {
            return ApiResult.Error(
                ApiError(
                    "Image is larger than 25 MiB",
                    "AVATAR_TOO_LARGE",
                    ApiErrorKind.VALIDATION,
                ),
            )
        } catch (empty: UploadEmptyException) {
            return ApiResult.Error(
                ApiError(
                    "Image is empty",
                    "AVATAR_EMPTY",
                    ApiErrorKind.VALIDATION,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: IOException) {
            return ApiResult.Error(
                ApiError(
                    "Could not read the selected image",
                    "FILE_READ_FAILED",
                    ApiErrorKind.VALIDATION,
                ),
            )
        } catch (security: SecurityException) {
            return ApiResult.Error(
                ApiError(
                    "The selected image is no longer accessible",
                    "FILE_PERMISSION_DENIED",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val detectedMime = detectAvatarMime(bytes)
            ?: return ApiResult.Error(
                ApiError(
                    "Select a PNG, JPEG, GIF, or WebP image",
                    "AVATAR_INVALID",
                    ApiErrorKind.VALIDATION,
                ),
            )
        val normalizedDeclaredMime = normalizeAvatarMime(declaredMime)
        if (
            normalizedDeclaredMime != null &&
            normalizedDeclaredMime != detectedMime
        ) {
            return ApiResult.Error(
                ApiError(
                    "The image content does not match its file type",
                    "AVATAR_TYPE_MISMATCH",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val fileName = sanitizeUploadFileName(
            runCatching { resolveDisplayName(context, uri) }.getOrNull(),
        )
        val session = userViewModel.repo.activeCredentialSnapshot()
        val baseUrl = session.baseUrl
        if (baseUrl.isNullOrBlank()) {
            return ApiResult.Error(
                ApiError(
                    "Workspace server is not selected",
                    "MISSING_BASE_URL",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val accessToken = session.accessToken.orEmpty()
        return try {
            val firstAttempt = uploadAvatarWithToken(
                baseUrl = baseUrl,
                accessToken = accessToken,
                userUuid = userUuid,
                bytes = bytes,
                mime = detectedMime,
                fileName = fileName,
            )
            val result = if (
                firstAttempt is ApiResult.Error &&
                firstAttempt.error.kind == ApiErrorKind.UNAUTHORIZED
            ) {
                val refreshedToken = when (
                    val refresh = refreshToken(session, accessToken)
                ) {
                    is ApiResult.Success -> refresh.value
                    is ApiResult.Error -> return ApiResult.Error(refresh.error)
                }
                val retry = uploadAvatarWithToken(
                    baseUrl = baseUrl,
                    accessToken = refreshedToken,
                    userUuid = userUuid,
                    bytes = bytes,
                    mime = detectedMime,
                    fileName = fileName,
                )
                if (
                    retry is ApiResult.Error &&
                    retry.error.kind == ApiErrorKind.UNAUTHORIZED
                ) {
                    session.ownerKey?.let {
                        userViewModel.removeActiveAccountIfOwnerAndWait(it)
                    }
                }
                retry
            } else {
                firstAttempt
            }
            if (
                result is ApiResult.Success &&
                session.ownerKey != null &&
                !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
            ) {
                ApiResult.Error(accountChangedError())
            } else {
                result
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: ResponseBodyTooLargeException) {
            ApiResult.Error(responseBodyTooLargeError())
        } catch (timeout: HttpRequestTimeoutException) {
            ApiResult.Error(ApiError("Upload timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: ConnectTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: SocketTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (serialization: SerializationException) {
            ApiResult.Error(
                ApiError(
                    "Invalid avatar response",
                    "MALFORMED_RESPONSE",
                    ApiErrorKind.MALFORMED_RESPONSE,
                ),
            )
        } catch (network: UnresolvedAddressException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (network: IOException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (exception: Exception) {
            ApiResult.Error(ApiError("Upload failed", "UPLOAD_FAILED", ApiErrorKind.UNKNOWN))
        }
    }

    private suspend fun uploadFileWithToken(
        baseUrl: String,
        accessToken: String,
        streamUuid: String,
        bytes: ByteArray,
        mime: String,
        fileName: String,
    ): ApiResult<UploadFileResponseData, ApiError> {
        val httpResponse: HttpResponse =
            client.post("$baseUrl/api/workspace/v1/messenger/files/") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"$fileName\""
                                )
                            }
                        )
                        append(key = "stream_uuid", value = streamUuid)
                    }
                )
            )
            header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")
            header("Authorization", "Bearer $accessToken")
        }
        val responseString = httpResponse.readTextWithLimit()
        if (httpResponse.status.isSuccess()) {
            val response = ERROR_RESPONSE_JSON
                .decodeFromString<UploadFileResponseData>(responseString)
            if (
                runCatching { UUID.fromString(response.uuid) }.isFailure ||
                response.name.isBlank()
            ) {
                return ApiResult.Error(
                    ApiError(
                        "Invalid upload response",
                        "MALFORMED_RESPONSE",
                        ApiErrorKind.MALFORMED_RESPONSE,
                    ),
                )
            }
            return ApiResult.Success(response)
        }
        return ApiResult.Error(httpApiError(httpResponse.status.value, responseString))
    }

    private suspend fun uploadAvatarWithToken(
        baseUrl: String,
        accessToken: String,
        userUuid: String,
        bytes: ByteArray,
        mime: String,
        fileName: String,
    ): ApiResult<UserResponseData, ApiError> {
        val httpResponse = client.post(
            "$baseUrl/api/workspace/v1/messenger/users/$userUuid/" +
                "actions/avatar_upload/invoke",
        ) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"$fileName\"",
                                )
                            },
                        )
                    },
                ),
            )
            header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")
            header("Authorization", "Bearer $accessToken")
        }
        val responseString = httpResponse.readTextWithLimit()
        if (!httpResponse.status.isSuccess()) {
            return ApiResult.Error(
                httpApiError(httpResponse.status.value, responseString),
            )
        }
        val response = ERROR_RESPONSE_JSON
            .decodeFromString<UserResponseData>(responseString)
        if (
            runCatching { UUID.fromString(response.uuid) }.getOrNull() !=
            UUID.fromString(userUuid)
        ) {
            return ApiResult.Error(
                ApiError(
                    "Avatar response belongs to another user",
                    "MALFORMED_RESPONSE",
                    ApiErrorKind.MALFORMED_RESPONSE,
                ),
            )
        }
        return ApiResult.Success(response)
    }

    suspend fun downloadFile(fileUuid: String): ApiResult<ByteArray, ApiError> {
        val canonicalUuid = runCatching { UUID.fromString(fileUuid).toString() }.getOrNull()
            ?: return ApiResult.Error(
                ApiError(
                    "Attachment identifier is invalid",
                    "INVALID_FILE_UUID",
                    ApiErrorKind.VALIDATION,
                ),
            )
        val session = userViewModel.repo.activeCredentialSnapshot()
        val baseUrl = session.baseUrl
        if (baseUrl.isNullOrBlank()) {
            return ApiResult.Error(
                ApiError(
                    "Workspace server is not selected",
                    "MISSING_BASE_URL",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val accessToken = session.accessToken.orEmpty()
        return try {
            val firstAttempt = downloadFileWithToken(baseUrl, accessToken, canonicalUuid)
            if (
                firstAttempt is ApiResult.Error &&
                firstAttempt.error.kind == ApiErrorKind.UNAUTHORIZED
            ) {
                val refreshedToken = when (
                    val refresh = refreshToken(session, accessToken)
                ) {
                    is ApiResult.Success -> refresh.value
                    is ApiResult.Error -> return ApiResult.Error(refresh.error)
                }
                val retry = downloadFileWithToken(baseUrl, refreshedToken, canonicalUuid)
                if (
                    retry is ApiResult.Error &&
                    retry.error.kind == ApiErrorKind.UNAUTHORIZED
                ) {
                    session.ownerKey?.let {
                        userViewModel.removeActiveAccountIfOwnerAndWait(it)
                    }
                }
                if (
                    retry is ApiResult.Success &&
                    session.ownerKey != null &&
                    !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
                ) {
                    ApiResult.Error(accountChangedError())
                } else {
                    retry
                }
            } else {
                if (
                    firstAttempt is ApiResult.Success &&
                    session.ownerKey != null &&
                    !userViewModel.repo.isActiveCredentialOwner(session.ownerKey)
                ) {
                    ApiResult.Error(accountChangedError())
                } else {
                    firstAttempt
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: ResponseBodyTooLargeException) {
            ApiResult.Error(responseBodyTooLargeError())
        } catch (timeout: HttpRequestTimeoutException) {
            ApiResult.Error(ApiError("Download timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: ConnectTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (timeout: SocketTimeoutException) {
            ApiResult.Error(ApiError("Connection timed out", "TIMEOUT", ApiErrorKind.TIMEOUT))
        } catch (network: UnresolvedAddressException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (network: IOException) {
            ApiResult.Error(ApiError("Network unavailable", "NETWORK", ApiErrorKind.NETWORK))
        } catch (exception: Exception) {
            ApiResult.Error(ApiError("Download failed", "DOWNLOAD_FAILED", ApiErrorKind.UNKNOWN))
        }
    }

    private suspend fun downloadFileWithToken(
        baseUrl: String,
        accessToken: String,
        fileUuid: String,
    ): ApiResult<ByteArray, ApiError> {
        val response = client.get(
            "$baseUrl/api/workspace/v1/messenger/files/$fileUuid/actions/download",
        ) {
            header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")
            header("Authorization", "Bearer $accessToken")
        }
        if (!response.status.isSuccess()) {
            return ApiResult.Error(
                httpApiError(response.status.value, response.readTextWithLimit()),
            )
        }
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_FILE_DOWNLOAD_BYTES) {
            return ApiResult.Error(
                ApiError(
                    "File is too large to open on this device",
                    "FILE_TOO_LARGE",
                    ApiErrorKind.VALIDATION,
                ),
            )
        }
        val bytes = readBytesWithLimit(
            response.bodyAsChannel(),
            MAX_FILE_DOWNLOAD_BYTES,
        ) ?: return ApiResult.Error(
            ApiError(
                "File is too large to open on this device",
                "FILE_TOO_LARGE",
                ApiErrorKind.VALIDATION,
            ),
        )
        return ApiResult.Success(bytes)
    }

    suspend fun refreshToken(
        failedSession: ActiveCredentialSnapshot,
        failedAccessToken: String?,
    ): ApiResult<String, ApiError> = refreshMutex.withLock {
        val expectedOwnerKey = failedSession.ownerKey
            ?: return@withLock ApiResult.Error(accountChangedError())
        val currentSession = userViewModel.repo.activeCredentialSnapshot()
        if (currentSession.ownerKey != expectedOwnerKey) {
            return@withLock ApiResult.Error(accountChangedError())
        }
        val currentAccessToken = currentSession.accessToken
        if (
            !currentAccessToken.isNullOrBlank() &&
            currentAccessToken != failedAccessToken
        ) {
            return@withLock ApiResult.Success(currentAccessToken)
        }

        val storedRefreshToken = currentSession.refreshToken
        if (storedRefreshToken.isNullOrBlank()) {
            userViewModel.removeActiveAccountIfOwnerAndWait(expectedOwnerKey)
            return@withLock ApiResult.Error(
                ApiError(
                    "Authentication expired",
                    "401",
                    ApiErrorKind.UNAUTHORIZED,
                ),
            )
        }

        val baseUrl = currentSession.baseUrl
            ?: return@withLock ApiResult.Error(accountChangedError())
        when (
            val refreshResponse = performTokenRefresh(baseUrl, storedRefreshToken)
        ) {
                is ApiResult.Success -> {
                    val userResponse = refreshResponse.value
                    val expectedUserId = currentSession.userId
                    val expectedProjectId = currentSession.projectId
                    if (
                        expectedUserId.isNullOrBlank() ||
                        expectedProjectId.isNullOrBlank() ||
                        !accessTokenMatchesAccount(
                            accessToken = userResponse.accessToken,
                            expectedUserId = expectedUserId,
                            expectedProjectId = expectedProjectId,
                        )
                    ) {
                        return@withLock ApiResult.Error(
                            ApiError(
                                errorMessage = "Token owner does not match the active account",
                                code = "TOKEN_IDENTITY_MISMATCH",
                                kind = ApiErrorKind.MALFORMED_RESPONSE,
                            ),
                        )
                    }
                    val saved = userViewModel.repo.saveRefreshedTokensIfActive(
                        expectedOwnerKey = expectedOwnerKey,
                        accessToken = userResponse.accessToken,
                        refreshToken = userResponse.refreshToken,
                    )
                    if (!saved) {
                        return@withLock ApiResult.Error(accountChangedError())
                    }
                    ApiResult.Success(userResponse.accessToken)
                }
                is ApiResult.Error -> {
                    if (
                        refreshResponse.error.kind == ApiErrorKind.UNAUTHORIZED ||
                        refreshResponse.error.kind == ApiErrorKind.FORBIDDEN
                    ) {
                        userViewModel.removeActiveAccountIfOwnerAndWait(
                            expectedOwnerKey,
                        )
                    }
                    ApiResult.Error(refreshResponse.error)
                }
            }
    }

    private suspend fun performTokenRefresh(
        baseUrl: String,
        refreshToken: String,
    ): ApiResult<LoginResponse, ApiError> {
        val request = TokenRefreshRequest(refreshToken)
        val response = client.request(
            workspaceRequestBuilder(
                request = request,
                urlString = "$baseUrl${request.url}",
                accessToken = "",
            ),
        )
        val responseString = response.readTextWithLimit()
        return if (response.status.isSuccess()) {
            ApiResult.Success(
                ERROR_RESPONSE_JSON.decodeFromString<LoginResponse>(responseString),
            )
        } else {
            ApiResult.Error(httpApiError(response.status.value, responseString))
        }
    }

    suspend fun readUriBytes(
        context: Context,
        uri: Uri,
        maxBytes: Int = MAX_FILE_UPLOAD_BYTES,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Selected image could not be opened")
            input.use {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw UploadTooLargeException()
                    output.write(buffer, 0, count)
                }
                output.toByteArray().also {
                    if (it.isEmpty()) throw UploadEmptyException()
                }
            }
        }

    private fun resolveDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private companion object {
        const val MAX_FILE_UPLOAD_BYTES = 25 * 1024 * 1024
        const val MAX_FILE_DOWNLOAD_BYTES = 50 * 1024 * 1024
    }
}

private class UploadTooLargeException : IOException()
private class UploadEmptyException : IOException()

internal fun sanitizeUploadFileName(value: String?): String {
    val sanitized = value
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.replace(Regex("""["\r\n]"""), "_")
        ?.trim()
        ?.take(160)
        .orEmpty()
    return sanitized.ifBlank { "image" }
}

internal fun normalizeAvatarMime(value: String?): String? {
    val normalized = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)
        ?: return null
    return when (normalized) {
        "image/jpg" -> "image/jpeg"
        "image/gif", "image/jpeg", "image/png", "image/webp" -> normalized
        else -> "unsupported"
    }
}

internal fun detectAvatarMime(bytes: ByteArray): String? = when {
    bytes.startsWith(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61)) ||
        bytes.startsWith(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) ->
        "image/gif"
    bytes.startsWith(
        byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
        ),
    ) -> "image/jpeg"
    bytes.startsWith(
        byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        ),
    ) -> "image/png"
    bytes.size >= 12 &&
        bytes.startsWith(byteArrayOf(0x52, 0x49, 0x46, 0x46)) &&
        bytes.copyOfRange(8, 12).contentEquals(
            byteArrayOf(0x57, 0x45, 0x42, 0x50),
        ) -> "image/webp"
    else -> null
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size &&
        prefix.indices.all { index -> this[index] == prefix[index] }

@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal inline fun <
    reified RequestData : Any,
    reified Response : Any,
    reified ResponseError : Any,
> workspaceRequestBuilder(
    request: ApiRequest<RequestData, Response, ResponseError>,
    urlString: String,
    accessToken: String,
): HttpRequestBuilder = HttpRequestBuilder().apply {
    url(urlString)
    method = when (request.method) {
        HTTPMethod.GET -> HttpMethod.Get
        HTTPMethod.POST -> HttpMethod.Post
        HTTPMethod.PATCH -> HttpMethod.Patch
        HTTPMethod.DELETE -> HttpMethod.Delete
        HTTPMethod.PUT -> HttpMethod.Put
    }
    header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")

    if (RequestData::class != EmptyRequestData::class) {
        val bodyDict = Properties.encodeToStringMap(request.data)
        if (request.method == HTTPMethod.GET) {
            url {
                for ((key, value) in bodyDict) {
                    val paramName = key.replace(Regex("\\.\\d+$"), "")
                    parameters.append(paramName, value)
                }
            }
        } else if (request.isJson) {
            contentType(ContentType.Application.Json)
            if (request.encodeExplicitNulls) {
                setBody(
                    TextContent(
                        text = explicitNullsRequestJson.encodeToString(request.data),
                        contentType = ContentType.Application.Json,
                    ),
                )
            } else {
                setBody(request.data)
            }
        } else {
            setBody(
                FormDataContent(
                    Parameters.build {
                        for ((key, value) in bodyDict) {
                            append(key, value)
                        }
                    },
                ),
            )
        }
    }
    if (request.requiresApiKey) {
        header("Authorization", "Bearer $accessToken")
    }
    request.additionalHeaders.forEach { (key, value) ->
        header(key, value)
    }
}

internal suspend fun readBytesWithLimit(
    channel: ByteReadChannel,
    maxBytes: Int,
): ByteArray? {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0
    while (true) {
        val count = channel.readAvailable(buffer, 0, buffer.size)
        if (count < 0) break
        if (count == 0) continue
        totalBytes += count
        if (totalBytes > maxBytes) {
            channel.cancel(IllegalStateException("Response body exceeds the client limit"))
            return null
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

@PublishedApi
internal suspend fun HttpResponse.readTextWithLimit(
    maxBytes: Int = MAX_API_RESPONSE_BYTES,
): String {
    val contentLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > maxBytes) {
        throw ResponseBodyTooLargeException()
    }
    val bytes = readBytesWithLimit(bodyAsChannel(), maxBytes)
        ?: throw ResponseBodyTooLargeException()
    return bytes.toString(Charsets.UTF_8)
}

@PublishedApi
internal fun HttpResponse.apiResponseMetadata(): ApiResponseMetadata =
    apiResponseMetadata(headers)

internal fun apiResponseMetadata(headers: Headers): ApiResponseMetadata =
    ApiResponseMetadata(
        nextPageMarker = headers["X-Pagination-Marker"],
        entityTag = parseStrongRevisionEntityTag(headers[HttpHeaders.ETag]),
    )

fun apiErrorKind(statusCode: Int): ApiErrorKind = when (statusCode) {
    400, 422 -> ApiErrorKind.VALIDATION
    401 -> ApiErrorKind.UNAUTHORIZED
    403 -> ApiErrorKind.FORBIDDEN
    404 -> ApiErrorKind.NOT_FOUND
    409, 412 -> ApiErrorKind.CONFLICT
    429 -> ApiErrorKind.RATE_LIMITED
    in 500..599 -> ApiErrorKind.SERVER
    else -> ApiErrorKind.UNKNOWN
}

@PublishedApi
internal fun shouldAttemptTokenRefresh(
    statusCode: Int,
    requiresApiKey: Boolean,
): Boolean = statusCode == 401 && requiresApiKey

@PublishedApi
internal fun accountChangedError(): ApiError =
    ApiError(
        errorMessage = "The active account changed while the request was running",
        code = "ACCOUNT_CHANGED",
        kind = ApiErrorKind.CONFLICT,
    )

@PublishedApi
internal fun responseBodyTooLargeError(): ApiError =
    ApiError(
        errorMessage = "Workspace returned an unexpectedly large response",
        code = "RESPONSE_TOO_LARGE",
        kind = ApiErrorKind.MALFORMED_RESPONSE,
    )

@PublishedApi
internal fun httpApiError(
    statusCode: Int,
    responseBody: String,
    headers: Headers = Headers.Empty,
): ApiError {
    val parsed = runCatching {
        ERROR_RESPONSE_JSON.decodeFromString<ResponseStatusError>(responseBody)
    }.getOrNull()
    val parsedMessage = listOfNotNull(
        parsed?.msg,
        parsed?.message,
        parsed?.detail,
        parsed?.description,
        parsed?.errorDescription,
    ).firstNotNullOfOrNull { candidate ->
        candidate
            .replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
            .trim()
            .takeIf(String::isNotBlank)
            ?.take(512)
    }
    val message = parsedMessage ?: when (statusCode) {
        400, 422 -> "Request validation failed"
        401 -> "Authentication required"
        403 -> "Action is not permitted"
        404 -> "Requested item was not found"
        409, 412 -> "The item changed on another client"
        429 -> "Too many requests. Try again later"
        in 500..599 -> "Workspace service is temporarily unavailable"
        else -> "Request failed"
    }
    return ApiError(
        errorMessage = message,
        code = parsed?.code?.takeIf(String::isNotBlank)
            ?: parsed?.error?.takeIf {
                it.length <= 128 &&
                    it.matches(Regex("""[A-Za-z0-9_.:+-]+"""))
            }
            ?: statusCode.toString(),
        kind = apiErrorKind(statusCode),
        httpStatus = statusCode,
        conflictBody = responseBody
            .takeIf { statusCode == 412 && it.length <= MAX_CONFLICT_BODY_CHARS },
        entityTag = parseStrongRevisionEntityTag(headers[HttpHeaders.ETag]),
    )
}

internal fun parseStrongRevisionEntityTag(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.matches(Regex("""\"[1-9][0-9]*\"""")) }

private val ERROR_RESPONSE_JSON = Json { ignoreUnknownKeys = true }

@PublishedApi
internal const val MAX_API_RESPONSE_BYTES = 16 * 1024 * 1024
private const val MAX_CONFLICT_BODY_CHARS = 64 * 1024

@PublishedApi
internal class ResponseBodyTooLargeException : Exception()

@Serializable
data class ResponseResult (
    val result: String
)

@Serializable
data class ResponseStatusError (
    val msg: String? = null,
    val code: String? = null,
    val message: String? = null,
    val detail: String? = null,
    val description: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val error: String? = null,
)

data class ApiResponseMetadata(
    val nextPageMarker: String? = null,
    val entityTag: String? = null,
)

sealed class ApiResult<out R, out E> {
    data class Success<R>(
        val value: R,
        val metadata: ApiResponseMetadata = ApiResponseMetadata(),
    ) : ApiResult<R, Nothing>()
    data class Error<E>(val error: E) : ApiResult<Nothing, E>()
}
