package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.MessageDto
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class MessagesRequest(
    val streamId: String,
    val topicId: String?
): ApiRequest<MessagesRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/messages/"
    override val data = MessagesRequestData(
        streamId, topicId
    )
}

@Serializable
data class DirectMessagesRequest(
    val streamId: String,
    val topicId: String?
): ApiRequest<MessagesRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/messages/"
    override val data = MessagesRequestData(
        streamId, topicId
    )
}

@Serializable
data class MessagesByIdsRequest(
    val messageIds: List<String>
): ApiRequest<MessagesByIdsRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/messages/"
    override val data = MessagesByIdsRequestData(
       messageIds//"${messageIds.joinToString("&uuid=")}"
    )
}


@Serializable
data class MessagesRequestData(
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String?
)

@Serializable
data class MessagesByIdsRequestData(
    val uuid: List<String>
)

@Serializable
data class MessagesDtoResponse(
    val messages: List<MessageDto>
){
}
@Serializable
data class MessageResponse(
    var uuid: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") var topicUuid: String,
    @SerialName("user_uuid") var userUuid: String,
    var payload: MessageResponsePayload,
    @SerialName("is_own") val isOwn: Boolean,
    var reactions: Map<String, Int>,
    var user: UserResponseData? = null
)

@Serializable
data class MessageResponsePayload(
    val kind: String,
    var content: String
)
