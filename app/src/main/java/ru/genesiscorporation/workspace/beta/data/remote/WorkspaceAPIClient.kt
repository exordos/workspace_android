package ru.genesiscorporation.workspace.beta.data.remote

import androidx.recyclerview.widget.RecyclerView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.append
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import okhttp3.Response
import ru.genesiscorporation.workspace.beta.UserViewModel
//import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.SubscriptionsResponse
import kotlin.io.encoding.Base64
import kotlin.math.log

class WorkspaceAPIClient(
    val client: HttpClient,
    val userViewModel: UserViewModel
): APIClient {

    override suspend fun subscriptions(): SubscriptionsResponse? {
        val apiKey = userViewModel.repo.apiKeyFlow.first()
        val email = userViewModel.repo.emailFlow.first()
        val baseUrl = userViewModel.repo.baseUrlFlow.first()
        val authHeader = Base64.encode("$email:$apiKey".encodeToByteArray())
        val response = try {
            client.get(
                urlString = "${baseUrl}/api/v1/users/me/subscriptions"
            ) {
                header("Authorization", "Basic $authHeader")
                header("User-Agent", "Workspace/android/0.0.1")
            }
        } catch (e: Exception) {
            return null
        }
        val json = Json { ignoreUnknownKeys = true }
        val responseString: String = response.body()
        val subscriptionsResponse = json.decodeFromString<SubscriptionsResponse>(responseString)
        return subscriptionsResponse
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified RequestData : Any, reified Response : Any, reified ResponseError : Any> performRequest(
        request: ApiRequest<RequestData, Response, ResponseError>
    ): ApiResult<Response, ApiError> {
        val baseUrl = userViewModel.repo.baseUrlFlow.first()
        val url = "${baseUrl}${request.url}"
        return try {
            val httpResponse: HttpResponse = client.request(url) {
                method = when (request.method) {
                    HTTPMethod.GET -> HttpMethod.Get
                    HTTPMethod.POST -> HttpMethod.Post
                }
                header("User-Agent", "Workspace/android/0.0.1")

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
                            setBody(FormDataContent(Parameters.build {
                                for ((key, value) in bodyDict) {
                                    append(key, value)
                                }
                            }))
                        }
                    }
                }
                if (request.requiresApiKey == true) {
                    val apiKey = userViewModel.repo.apiKeyFlow.first()
                    val email = userViewModel.repo.emailFlow.first()
                    val authHeader = Base64.encode("$email:$apiKey".encodeToByteArray())
                    header("Authorization", "Basic $authHeader")
                }
            }

            if (httpResponse.status.isSuccess()) {
                val json = Json { ignoreUnknownKeys = true }

                val responseString: String = httpResponse.body()
                if (Response::class == String::class) {
                    ApiResult.Success(responseString as Response)
                } else {
                    val response = json.decodeFromString<Response>(responseString)
                    ApiResult.Success(response)
                }
            } else {
                val error = ApiError("Request failed")

                ApiResult.Error(error)
            }
        } catch (t: Throwable) {
            // If you want, you can map unexpected exceptions to a generic error type instead.
            throw t
        }
    }
}

sealed class ApiResult<out R, out E> {
    data class Success<R>(val value: R) : ApiResult<R, Nothing>()
    data class Error<E>(val error: E) : ApiResult<Nothing, E>()
}