package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod


@Serializable
data class SendMessageRequest(
    val streamUuid: String,
    val topicUuid: String?,
    val content: String
): ApiRequest<SendMessageRequestData, SendMessageResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/messages/"
    override val data = SendMessageRequestData(
        streamUuid,
        topicUuid,
        MessageResponsePayload("markdown", content)
    )
}

@Serializable
data class SendDirectMessageRequest(
    val streamUuid: String,
    val content: String
): ApiRequest<SendDirectMessageRequestData, SendMessageResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/messages/"
    override val data = SendDirectMessageRequestData(
        streamUuid,
        MessageResponsePayload("markdown", content)
    )
}

@Serializable
data class SendDirectMessageRequestData(
    @SerialName("stream_uuid") val streamUuid: String,
    val payload: MessageResponsePayload
)


@Serializable
data class SendMessageRequestData(
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String?,
    val payload: MessageResponsePayload
)
@Serializable
data class SendMessageResponse(
    val uuid: String,
    @SerialName("topic_uuid") val topicUuid: String
)