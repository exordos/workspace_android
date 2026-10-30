package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

class CreateDraftRequest(
    draftUuid: String,
    streamUuid: String,
    topicUuid: String,
    content: String
): ApiRequest<CreateDraftRequestData, Draft, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/drafts/"
    override val data = CreateDraftRequestData(draftUuid, streamUuid, topicUuid,
        MessageResponsePayload("markdown", content)
    )
}

@Serializable
data class CreateDraftRequestData(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid")  val topicUuid: String,
    @Serializable(with = MessageBodyPayloadSerializer::class) val payload: MessageResponsePayload
)
