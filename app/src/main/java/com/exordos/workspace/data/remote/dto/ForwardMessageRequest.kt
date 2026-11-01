package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

/** A stable request UUID lets a lost acknowledgement be checked without another POST. */
internal class ForwardMessageRequest(
    messageUuid: String,
    streamUuid: String,
    topicUuid: String,
    content: String,
) : ApiRequest<MessageCreateData, SendMessageResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url = "/api/workspace/v1/messenger/messages/"
    override val data = MessageCreateData(
        uuid = requireNotNull(parseCanonicalUuid(messageUuid)),
        streamUuid = requireNotNull(parseCanonicalUuid(streamUuid)),
        topicUuid = requireNotNull(parseCanonicalUuid(topicUuid)),
        payload = MessageResponsePayload("markdown", content),
    )
}

@Serializable
data class MessageCreateData(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    val payload: MessageResponsePayload,
)
