package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

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
        EditMessageRequestPayload("markdown", content)
    )
}

@Serializable
data class CreateDraftRequestData(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid")  val topicUuid: String,
    val payload: EditMessageRequestPayload
)
