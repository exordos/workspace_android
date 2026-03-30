package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod


@Serializable
data class SendMessageRequest(
    val type: String,
    val to: String,
    val content: String,
    val topic: String?
): ApiRequest<SendMessageRequestData, SendMessageResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/messages"
    override val data = SendMessageRequestData(
        type,
        to,
        content,
        topic
    )
}

@Serializable
data class SendMessageRequestData(
    val type: String,
    val to: String,
    val content: String,
    val topic: String?
)
@Serializable
data class SendMessageResponse(
    val id: Int
)