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
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.append
import io.ktor.http.content.PartData
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import okhttp3.Response
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadFileResponseData
import kotlin.io.encoding.Base64
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TokenRefreshRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadAvatarResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class WorkspaceAPIClient(
    val client: HttpClient,
    val userViewModel: UserViewModel,
    val sessionCookieStore: SessionCookieStore
): APIClient {
    var baseAccessToken: String? = null
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified RequestData : Any, reified Response : Any, reified ResponseError : Any> performRequest(
        request: ApiRequest<RequestData, Response, ResponseError>
    ): ApiResult<Response, ApiError> {
        val baseUrl = userViewModel.repo.baseUrlFlow.first()
        val accessToken = if (baseAccessToken != null) baseAccessToken ?: "" else userViewModel.baseUrl.value ?: ""
        val isOidc = accessToken.contains("__Host-sessionid=")
        val urlSuffix = if (request.shouldApplySuffix) {
            if (isOidc) "/json" else "/api/v1"
        } else {
            ""
        }
        val urlString = if (request.isAbsoluteUrl) request.url else "${baseUrl}${urlSuffix}${request.url}"
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

                if (RequestData::class != EmptyRequestData::class) {
                    val bodyDict = Properties.encodeToStringMap(request.data)

                    when (request.method) {
                        HTTPMethod.GET -> {
                            url {
                                for ((key, value) in bodyDict) {
                                    val paramName = key.replace(Regex("\\.\\d+$"), "")
                                    parameters.append(paramName, value)
                                }
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
                    val finalResponseString = if (request.shouldReturnUrl) httpResponse.call.request.url.toString() else if (request.hasSessionCookie) sessionCookieStore.getFullSessionCookie() ?: "" else responseString
                    ApiResult.Success(finalResponseString as Response)
                } else {
                    val response = json.decodeFromString<Response>(responseString)
                    ApiResult.Success(response)
                }
            } else if (httpResponse.status.value == 401 && request !is LoginRequest && request !is TokenRefreshRequest) {
                refreshToken()
                requestBuilder.headers.set("Authorization", "Bearer ${baseAccessToken ?: ""}")
                val httpResponseAfterRefresh: HttpResponse = client.request(requestBuilder)
                if (httpResponseAfterRefresh.status.isSuccess()) {
                    val json = Json { ignoreUnknownKeys = true }

                    val responseString: String = httpResponseAfterRefresh.body()
                    if (Response::class == String::class) {
                        val finalResponseString = if (request.shouldReturnUrl) httpResponseAfterRefresh.call.request.url.toString() else if (request.hasSessionCookie) sessionCookieStore.getFullSessionCookie() ?: "" else responseString
                        ApiResult.Success(finalResponseString as Response)
                    } else {
                        val response = json.decodeFromString<Response>(responseString)
                        ApiResult.Success(response)
                    }
                } else if (httpResponseAfterRefresh.status.value == 401) {
                    userViewModel.clearAll()
                    val error = ApiError("Request failed", "REQUEST_FAILED")

                    ApiResult.Error(error)
                } else {
                    val json = Json { ignoreUnknownKeys = true }
                    val responseString: String = httpResponseAfterRefresh.body()
                    val response = json.decodeFromString<ResponseStatusError>(responseString)
                    val error = ApiError(response.msg, response.code)

                    ApiResult.Error(error)
                }
            }  else {
                val json = Json { ignoreUnknownKeys = true }
                val responseString: String = httpResponse.body()
                val error = ApiError(responseString, "${httpResponse.status.value}")

                ApiResult.Error(error)
            }
        } catch (t: Throwable) {
            val error = ApiError("Request failed", "REQUEST_FAILED")

            ApiResult.Error(error)
        }
    }

    suspend fun uploadStreamImage(context: Context, uri: Uri, streamUuid: String): ApiResult<UploadFileResponseData, ApiError> {
        val path = "/api/workspace/v1/messenger/files/"
        val bytes = readUriBytes(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = "image.jpg"
        val parts = formData {
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
        return uploadFile(path, parts)
    }

    suspend fun uploadAvatarImage(context: Context, uri: Uri, userUuid: String): ApiResult<UploadAvatarResponseData, ApiError> {
        val path = "/api/workspace/v1/users/${userUuid}/actions/avatar_upload/invoke"
        val bytes = readUriBytes(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = "image.jpg"
        val parts = formData {
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
        }
        return uploadFile(path, parts)
    }

    suspend inline fun <reified T> uploadFile(path: String, parts: List<PartData>): ApiResult<T, ApiError> {
        val baseUrl = userViewModel.repo.baseUrlFlow.first()
        val accessToken = if (baseAccessToken != null) baseAccessToken ?: "" else userViewModel.repo.accessTokenFlow.first() ?: ""
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

    suspend fun refreshToken() {
        val refreshToken = userViewModel.repo.refreshTokenFlow.first()
        if (refreshToken != null) {
            val refreshResponse = performRequest(TokenRefreshRequest(refreshToken))
            when(refreshResponse) {
                is ApiResult.Success -> {
                    val userResponse = refreshResponse.value
                    userViewModel.setAccessToken(userResponse.accessToken)
                    userViewModel.setRefreshToken(userResponse.refreshToken)
                    baseAccessToken = userResponse.accessToken
                }
                is ApiResult.Error -> {
                    userViewModel.clearAll()
                }
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
        val accessToken = if (baseAccessToken != null) baseAccessToken ?: "" else userViewModel.baseUrl.value ?: ""
        authHeadersList += AuthHeader("Authorization", "Bearer $accessToken")

        return  authHeadersList
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