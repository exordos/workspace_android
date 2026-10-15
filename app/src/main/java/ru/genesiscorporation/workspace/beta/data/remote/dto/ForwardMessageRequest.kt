package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

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
