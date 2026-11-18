package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.MessageUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class MessagesRequest(
    val streamId: String,
    val topicId: String?
): UIORequest<MessagesRequestData, List<MessageResponse>, List<MessageUIO>, ApiError> {
    override fun toUio(dto: List<MessageResponse>): List<MessageUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MessagesRequestData(
        streamId, topicId
    )
}

@Serializable
data class MessagesByIdsRequest(
    val messageIds: List<String>
): UIORequest<MessagesByIdsRequestData, List<MessageResponse>, List<MessageUIO>, ApiError> {
    override fun toUio(dto: List<MessageResponse>): List<MessageUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MessagesByIdsRequestData(
       messageIds
    )
}

@Serializable
class MentionedMessagesRequest: UIORequest<MentionedMessagesRequestData, List<MessageResponse>, List<MessageUIO>, ApiError> {
    override fun toUio(dto: List<MessageResponse>): List<MessageUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MentionedMessagesRequestData()
}


@Serializable
data class MessagesRequestData(
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String?
)

@Serializable
data class MessagesByIdsRequestData(
    val uuid: List<String>
) {
}
@Serializable
data class MentionedMessagesRequestData(
    val mentioned: Boolean = true
)

@Serializable
data class MessageResponse(
    val uuid: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("author_uuid") val authorUuid: String,
    val payload: MessageResponsePayload,
    @SerialName("is_own") val isOwn: Boolean,
    val reactions: Map<String, Int>,
    val read: Boolean,
    @SerialName("project_id") val projectId: String? = null,
    val mentioned: Boolean = false,

)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MessageResponsePayload(
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val content: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("key_uuid") val keyUuid: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("device_id") val deviceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("device_name") val deviceName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("public_key") val publicKey: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("request_message_uuid") val requestMessageUuid: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("sender_device_id") val senderDeviceId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("recipient_device_id") val recipientDeviceId: String? = null,
)
