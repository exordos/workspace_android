package ru.genesiscorporation.workspace.beta.data.remote

import android.content.Context
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.append
import io.ktor.http.content.PartData
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import okhttp3.Response
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadFileResponseData
import kotlin.io.encoding.Base64
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.TokenPair
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TokenRefreshRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadAvatarResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import java.io.File
import java.util.UUID

class WorkspaceAPIClient(
    val client: HttpClient
): APIClient {
    private val refreshMutex = Mutex()

    fun getCurrentServerId(): String {
        return userViewModel?.selectedServerId?.value ?: ""
    }
    var userViewModel: UserViewModel? = null

    fun attachUserViewModel(userViewModel: UserViewModel) {
        this.userViewModel = userViewModel
    }

    fun requireUserViewModel(): UserViewModel =
        userViewModel ?: error("UserViewModel not attached. Call attachUserState() first.")

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified RequestData : Any, reified Response : Any, reified ResponseError : Any> performRequest(
        request: ApiRequest<RequestData, Response, ResponseError>,
        serverId: String? = null,
        serverConfig: ServerConfig? = null,
    ): ApiResult<Response, ApiError> {
        if (serverConfig != null && serverId != null && serverConfig.id != serverId) {
            return ApiResult.Error(ApiError("Server configuration changed", "SERVER_CHANGED"))
        }
        var activeServerConfig = serverConfig
            ?: if (serverId != null) {
                requireUserViewModel().getServer(serverId)
            } else {
                requireUserViewModel().selectedServer.value
            }
        if (request.isAbsoluteUrl) {
            activeServerConfig = ServerConfig(UUID.randomUUID().toString(), request.url, "", "", false)
        }
        activeServerConfig ?: return ApiResult.Error(ApiError("Internal error", "INTERNAL_ERROR"))

        val activeServerId = serverId ?: activeServerConfig.id
        val baseUrl = activeServerConfig.baseUrl
        val accessToken = requireUserViewModel().getTokens(activeServerId)?.accessToken

        val urlString = if (request.isAbsoluteUrl) request.url else "${baseUrl}${request.url}"
        return try {
            val requestBuilder: HttpRequestBuilder = HttpRequestBuilder().apply {
                url(urlString)
                method = when (request.method) {
                    HTTPMethod.GET -> HttpMethod.Get
                    HTTPMethod.POST -> HttpMethod.Post
                    HTTPMethod.PATCH -> HttpMethod.Patch
                    HTTPMethod.DELETE -> HttpMethod.Delete
                    HTTPMethod.PUT -> HttpMethod.Put
                }
                header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")

                if (request.multipartParts != null) {
                    setBody(MultiPartFormDataContent(request.multipartParts.orEmpty()))
                } else if (RequestData::class != EmptyRequestData::class) {
                    val bodyDict = Properties.encodeToStringMap(request.data)

                    when (request.method) {
                        HTTPMethod.GET -> {
                            url {
                                appendGetQueryParameters(bodyDict)
                            }
                        }

                        HTTPMethod.POST -> {
                            if (request.isJson) {
                                contentType(ContentType.Application.Json)
                                setBody(
                                    request.data
                                )
                            } else {
                                setBody(FormDataContent(Parameters.build {
                                    for ((key, value) in bodyDict) {
                                        append(key, value)
                                    }
                                }))
                            }
                        }

                        HTTPMethod.PATCH -> {
                            if (request.isJson) {
                                contentType(ContentType.Application.Json)
                                setBody(
                                    request.data
                                )
                            } else {
                                setBody(FormDataContent(Parameters.build {
                                    for ((key, value) in bodyDict) {
                                        append(key, value)
                                    }
                                }))
                            }
                        }

                        HTTPMethod.DELETE -> {
                            if (request.isJson) {
                                contentType(ContentType.Application.Json)
                                setBody(
                                    request.data
                                )
                            } else {
                                setBody(FormDataContent(Parameters.build {
                                    for ((key, value) in bodyDict) {
                                        append(key, value)
                                    }
                                }))
                            }
                        }

                        HTTPMethod.PUT -> {
                            if (request.isJson) {
                                contentType(ContentType.Application.Json)
                                setBody(
                                    request.data
                                )
                            } else {
                                setBody(FormDataContent(Parameters.build {
                                    for ((key, value) in bodyDict) {
                                        append(key, value)
                                    }
                                }))
                            }
                        }
                    }
                }
                if (request.requiresApiKey) {
                    header("Authorization", "Bearer $accessToken")
                }
                if (!request.additionalHeaders.isEmpty()) {
                    request.additionalHeaders.forEach { additionalHeader ->
                        header(additionalHeader.key, additionalHeader.value)
                    }
                }
            }
            val httpResponse: HttpResponse = client.request(requestBuilder)

            if (httpResponse.status.isSuccess()) {
                val json = Json { ignoreUnknownKeys = true }

                val responseString: String = httpResponse.body()
                if (Response::class == String::class) {
                    val finalResponseString = if (request.shouldReturnUrl) httpResponse.call.request.url.toString()  else responseString
                    ApiResult.Success(finalResponseString as Response)
                } else {
                    val response = json.decodeFromString<Response>(responseString)
                    ApiResult.Success(response)
                }
            } else if (
                httpResponse.status.value == 401 &&
                request.requiresApiKey &&
                request !is LoginRequest &&
                request !is TokenRefreshRequest
            ) {
                val refreshedTokens = when (
                    val refresh = refreshSession(activeServerId, accessToken.orEmpty())
                ) {
                    is ApiResult.Success -> refresh.value
                    is ApiResult.Error -> return ApiResult.Error(refresh.error)
                }
                requestBuilder.headers.set(
                    "Authorization",
                    "Bearer ${refreshedTokens.accessToken}",
                )
                val httpResponseAfterRefresh: HttpResponse = client.request(requestBuilder)
                if (httpResponseAfterRefresh.status.isSuccess()) {
                    val json = Json { ignoreUnknownKeys = true }

                    val responseString: String = httpResponseAfterRefresh.body()
                    if (Response::class == String::class) {
                        val finalResponseString = if (request.shouldReturnUrl) httpResponseAfterRefresh.call.request.url.toString()  else responseString
                        ApiResult.Success(finalResponseString as Response)
                    } else {
                        val response = json.decodeFromString<Response>(responseString)
                        ApiResult.Success(response)
                    }
                } else {
                    val responseString: String = httpResponseAfterRefresh.body()
                    val error = if (httpResponseAfterRefresh.status.value == 401) {
                        requireUserViewModel().repo.setNeedsToReloginIfTokensCurrent(
                            serverId = activeServerId,
                            expectedTokens = refreshedTokens,
                        )
                        ApiError("Request remained unauthorized after token refresh", "401")
                    } else {
                        responseError(
                            responseBody = responseString,
                            statusCode = httpResponseAfterRefresh.status.value,
                        )
                    }

                    ApiResult.Error(error)
                }
            }  else {
                val json = Json { ignoreUnknownKeys = true }
                val responseString: String = httpResponse.body()
                val error = ApiError(responseString, "${httpResponse.status.value}")

                ApiResult.Error(error)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            val error = ApiError("Request failed", "REQUEST_FAILED")

            ApiResult.Error(error)
        }
    }

    suspend fun uploadStreamFile(fileName: String, context: Context, uri: Uri, streamUuid: String): ApiResult<UploadFileResponseData, ApiError> {
        val path = "/api/workspace/v1/messenger/files/"
        val bytes = readUriBytes(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val parts = workspaceFileUploadParts(fileName, mime, bytes, streamUuid)
        return uploadFile(path, parts)
    }

    suspend fun uploadAvatarImage(context: Context, uri: Uri, userUuid: String): ApiResult<UploadAvatarResponseData, ApiError> {
        val path = "/api/workspace/v1/users/${userUuid}/actions/avatar_upload/invoke"
        val bytes = readUriBytes(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = "image.jpg"
        val parts = workspaceFileUploadParts(fileName, mime, bytes)
        return uploadFile(path, parts)
    }

    suspend inline fun <reified T> uploadFile(
        path: String, parts: List<PartData>
        ): ApiResult<T, ApiError> {

        val activeServerConfig = requireUserViewModel().selectedServer.value
        activeServerConfig ?: return ApiResult.Error(ApiError("Internal error", "INTERNAL_ERROR"))

        val baseUrl = activeServerConfig.baseUrl
        val accessToken = requireUserViewModel()
            .getTokens(activeServerConfig.id)
            ?.accessToken
            .orEmpty()
        val httpResponse: HttpResponse = client.post("${baseUrl}${path}") {
            setBody(
                MultiPartFormDataContent(
                    parts
                )
            )
            header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")
            header("Authorization", "Bearer $accessToken")
        }
        if (httpResponse.status.isSuccess()) {
            val json = Json { ignoreUnknownKeys = true }

            val responseString: String = httpResponse.body()
            val response = json.decodeFromString<T>(responseString)
            return ApiResult.Success(response)
        } else {
            val error = ApiError("Request failed", "REQUEST_FAILED")

            return ApiResult.Error(error)
        }
    }

    suspend fun downloadFile(
        path: String, destination: File
    ): ApiResult<File, ApiError> {

        val activeServerConfig = requireUserViewModel().selectedServer.value
        activeServerConfig ?: return ApiResult.Error(ApiError("Internal error", "INTERNAL_ERROR"))

        val baseUrl = activeServerConfig.baseUrl
        val accessToken = requireUserViewModel()
            .getTokens(activeServerConfig.id)
            ?.accessToken
            .orEmpty()
        val httpResponse: HttpResponse = client.get("${baseUrl}${path}") {
            header("User-Agent", "Workspace/android/${BuildConfig.VERSION_NAME}")
            header("Authorization", "Bearer $accessToken")
        }
        if (httpResponse.status.isSuccess()) {
            val channel: ByteReadChannel = httpResponse.body()
            destination.parentFile?.mkdirs()
            destination.writeBytes(channel.readRemaining().readBytes())
            return ApiResult.Success(destination)
        } else {
            val error = ApiError("Request failed", "REQUEST_FAILED")
            return ApiResult.Error(error)
        }
    }

    suspend fun refreshToken(serverId: String? = null): ApiResult<String, ApiError> {
        val targetServerId = serverId
            ?: requireUserViewModel().selectedServerId.value
            ?: return ApiResult.Error(authenticationChangedError())
        val failedAccessToken = requireUserViewModel()
            .getTokens(targetServerId)
            ?.accessToken
            .orEmpty()
        return when (val refresh = refreshSession(targetServerId, failedAccessToken)) {
            is ApiResult.Success -> ApiResult.Success(refresh.value.accessToken)
            is ApiResult.Error -> refresh
        }
    }

    @PublishedApi
    internal suspend fun refreshSession(
        serverId: String,
        failedAccessToken: String,
    ): ApiResult<TokenPair, ApiError> = refreshMutex.withLock {
        val currentTokens = requireUserViewModel().getTokens(serverId)
        val currentAccessToken = currentTokens?.accessToken
        if (
            !currentAccessToken.isNullOrBlank() &&
            currentAccessToken != failedAccessToken
        ) {
            return@withLock ApiResult.Success(requireNotNull(currentTokens))
        }

        val storedRefreshToken = currentTokens?.refreshToken
        if (storedRefreshToken.isNullOrBlank()) {
            requireUserViewModel().repo.setNeedsToReloginIfRefreshTokenCurrent(
                serverId = serverId,
                expectedRefreshToken = null,
            )
            return@withLock ApiResult.Error(authenticationExpiredError())
        }

        when (
            val refreshResponse = performRequest(
                TokenRefreshRequest(storedRefreshToken),
                serverId,
            )
        ) {
            is ApiResult.Success -> {
                val userResponse = refreshResponse.value
                val refreshedTokens = TokenPair(
                    accessToken = userResponse.accessToken,
                    refreshToken = userResponse.refreshToken,
                )
                val saved = requireUserViewModel().repo.saveRefreshedTokensIfCurrent(
                    serverId = serverId,
                    expectedRefreshToken = storedRefreshToken,
                    tokens = refreshedTokens,
                )
                if (!saved) {
                    return@withLock ApiResult.Error(authenticationChangedError())
                }
                ApiResult.Success(refreshedTokens)
            }
            is ApiResult.Error -> {
                if (shouldRequireReloginAfterRefresh(refreshResponse.error)) {
                    requireUserViewModel().repo.setNeedsToReloginIfRefreshTokenCurrent(
                        serverId = serverId,
                        expectedRefreshToken = storedRefreshToken,
                    )
                }
                ApiResult.Error(refreshResponse.error)
            }
        }
    }

    suspend fun readUriBytes(context: Context, uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not open $uri")
        }

    fun authHeaders(): List<AuthHeader> {
        val authHeadersList: MutableList<AuthHeader> = mutableListOf()
        val user = requireUserViewModel()
        val accessToken = user.selectedServerId.value
            ?.let(user::getTokens)
            ?.accessToken
            .orEmpty()
        authHeadersList += AuthHeader("Authorization", "Bearer $accessToken")

        return  authHeadersList
    }
}

internal fun shouldRequireReloginAfterRefresh(error: ApiError): Boolean {
    if (error.httpStatusCode == 401) return true
    val normalized = "${error.code} ${error.errorMessage}"
        .lowercase()
        .filter(Char::isLetterOrDigit)
    return normalized.contains("invalidgrant") ||
        normalized.contains("invalidrefreshtoken")
}

@PublishedApi
internal fun responseError(responseBody: String, statusCode: Int): ApiError {
    val structured = runCatching {
        responseErrorJson.decodeFromString<ResponseStatusError>(responseBody)
    }.getOrNull()
    return if (structured != null) {
        ApiError(structured.msg, structured.code, statusCode)
    } else {
        ApiError(responseBody, statusCode.toString(), statusCode)
    }
}

private val responseErrorJson = Json { ignoreUnknownKeys = true }

private fun authenticationExpiredError(): ApiError =
    ApiError("Authentication expired. Sign in again", "401")

private fun authenticationChangedError(): ApiError =
    ApiError("Authentication changed while refreshing", "AUTHENTICATION_CHANGED")

internal fun workspaceFileUploadParts(
    fileName: String,
    mime: String,
    bytes: ByteArray,
    streamUuid: String? = null,
): List<PartData> = buildList {
    val uploadFileName = fileName.ifBlank { "attachment" }
    val disposition = ContentDisposition("form-data")
        .withParameter(ContentDisposition.Parameters.Name, "file")
        .withParameter(ContentDisposition.Parameters.FileName, uploadFileName)
    add(
        PartData.FileItem(
            provider = { ByteReadChannel(bytes) },
            dispose = {},
            partHeaders = Headers.build {
                append(HttpHeaders.ContentDisposition, disposition.toString())
                append(HttpHeaders.ContentType, mime)
                append(HttpHeaders.ContentLength, bytes.size.toString())
            },
        )
    )
    if (streamUuid != null) {
        addAll(formData { append("stream_uuid", streamUuid) })
    }
}

@PublishedApi
internal fun URLBuilder.appendGetQueryParameters(body: Map<String, String>) {
    for ((key, value) in body) {
        val parameterName = key.replace(Regex("\\.\\d+$"), "")
        parameters.append(parameterName, value)
    }
}

@Serializable
data class ResponseResult (
    val result: String
)

@Serializable
data class ResponseStatusError (
    val msg: String,
    val code: String
)

sealed class ApiResult<out R, out E> {
    data class Success<R>(val value: R) : ApiResult<R, Nothing>()
    data class Error<E>(val error: E) : ApiResult<Nothing, E>()
}

data class AuthHeader(
    val title: String,
    val value: String
)
