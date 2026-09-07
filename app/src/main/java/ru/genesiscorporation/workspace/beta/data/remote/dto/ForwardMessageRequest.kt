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
) : ApiRequest<ForwardMessageRequestData, SendMessageResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url = "/api/workspace/v1/messenger/messages/"
    override val data = ForwardMessageRequestData(
        uuid = requireNotNull(parseCanonicalMessageUuid(messageUuid)),
        streamUuid = requireNotNull(parseCanonicalMessageUuid(streamUuid)),
        topicUuid = requireNotNull(parseCanonicalMessageUuid(topicUuid)),
        payload = MessageResponsePayload("markdown", content),
    )
}

@Serializable
internal data class ForwardMessageRequestData(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    val payload: MessageResponsePayload,
)
