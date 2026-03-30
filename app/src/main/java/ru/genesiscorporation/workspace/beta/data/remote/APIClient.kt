package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.SubscriptionsResponse

interface APIClient {

    suspend fun subscriptions(): SubscriptionsResponse?

}

@Serializable
data class ApiError(val errorMessage: String) : Throwable()

interface ApiRequest<RequestData, Response, ResponseError> {
    val url: String
    val method: HTTPMethod
    val requiresApiKey: Boolean
        get() = true
    val data: RequestData
}

@Serializable
data class EmptyRequestData(
    val emptyString: String = ""
)

enum class HTTPMethod(val value: String) {
    GET("GET"),
    POST("POST")
}