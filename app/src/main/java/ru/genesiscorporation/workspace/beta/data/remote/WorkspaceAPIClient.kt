package ru.genesiscorporation.workspace.beta.data.remote

import android.content.Context
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.append
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
import kotlin.math.log

class WorkspaceAPIClient(
    val client: HttpClient,
    val userViewModel: UserViewModel,
    val sessionCookieStore: SessionCookieStore
): APIClient {
    var baseApiKey: String? = null
    var baseEmail: String? = null
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified RequestData : Any, reified Response : Any, reified ResponseError : Any> performRequest(
        request: ApiRequest<RequestData, Response, ResponseError>
    ): ApiResult<Response, ApiError> {
        val baseUrl = userViewModel.repo.baseUrlFlow.first()
        val apiKey = if (baseApiKey != null) baseApiKey ?: "" else userViewModel.repo.apiKeyFlow.first() ?: ""
        val isOidc = apiKey.contains("__Host-sessionid=")
        val urlSuffix = if (request.shouldApplySuffix) {
            if (isOidc) "/json" else "/api/v1"
        } else {
            ""
        }
        val url = if (request.isAbsoluteUrl) request.url else "${baseUrl}${urlSuffix}${request.url}"
        return try {
            val httpResponse: HttpResponse = client.request(url) {
                method = when (request.method) {
                    HTTPMethod.GET -> HttpMethod.Get
                    HTTPMethod.POST -> HttpMethod.Post
                    HTTPMethod.PATCH -> HttpMethod.Patch
                    HTTPMethod.DELETE -> HttpMethod.Delete
                }
                header("User-Agent", "Workspace/android/0.8")

                if (RequestData::class != EmptyRequestData::class) {
                    val bodyDict = Properties.encodeToStringMap(request.data)

                    when (request.method) {
                        HTTPMethod.GET -> {
                            url {
                                for ((key, value) in bodyDict) {
                                    parameters.append(key, value)
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
                            setBody(FormDataContent(Parameters.build {
                                for ((key, value) in bodyDict) {
                                    append(key, value)
                                }
                            }))
                        }

                        HTTPMethod.DELETE -> {
                            setBody(FormDataContent(Parameters.build {
                                for ((key, value) in bodyDict) {
                                    append(key, value)
                                }
                            }))
                        }
                    }
                }
                if (request.requiresApiKey == true) {
                    if (isOidc) {
                        header("cookie", apiKey)
                        val csrfToken = apiKey.substringAfter(';')
                            .takeIf { it.startsWith(" __Host-csrftoken=") }
                            ?.substringAfter('=')
                            ?.takeIf { it.isNotBlank() }
                        if (csrfToken != null) {
                            header("X-CSRFToken", csrfToken)
                        }
                    } else {
                        val email = if (baseEmail != null) baseEmail else userViewModel.repo.emailFlow.first()
                        val authHeader = Base64.encode("$email:$apiKey".encodeToByteArray())
                        header("Authorization", "Basic $authHeader")
                    }
                }
                if (request.hasSessionCookie && sessionCookieStore.getSessionId() != null) {
                    header("cookie", "__Host-sessionid=${sessionCookieStore.getSessionId()}")
                }
            }

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
            } else {
                val json = Json { ignoreUnknownKeys = true }
                val responseString: String = httpResponse.body()
                val response = json.decodeFromString<ResponseStatusError>(responseString)
                val error = ApiError(response.msg, response.code)

                ApiResult.Error(error)
            }
        } catch (t: Throwable) {
            val error = ApiError("Request failed", "REQUEST_FAILED")

            ApiResult.Error(error)
        }
    }

    suspend fun uploadImage(context: Context, uri: Uri): ApiResult<UploadFileResponseData, ApiError> {
        val bytes = readUriBytes(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = "image.jpg"
        val baseUrl = userViewModel.repo.baseUrlFlow.first()
        val httpResponse: HttpResponse = client.post("$baseUrl/api/v1/user_uploads") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "body", // must match what your API expects
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
                )
            )
            header("User-Agent", "Workspace/android/0.8")
            val apiKey = userViewModel.repo.apiKeyFlow.first()
            val email = userViewModel.repo.emailFlow.first()
            val authHeader = Base64.encode("$email:$apiKey".encodeToByteArray())
            header("Authorization", "Basic $authHeader")
        }
        if (httpResponse.status.isSuccess()) {
            val json = Json { ignoreUnknownKeys = true }

            val responseString: String = httpResponse.body()
            val response = json.decodeFromString<UploadFileResponseData>(responseString)
            return ApiResult.Success(response)
        } else {
            val error = ApiError("Request failed", "REQUEST_FAILED")

            return ApiResult.Error(error)
        }
    }

    suspend fun readUriBytes(context: Context, uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not open $uri")
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