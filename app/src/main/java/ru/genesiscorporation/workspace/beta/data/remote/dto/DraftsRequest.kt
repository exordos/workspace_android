package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class DraftsRequest(): ApiRequest<EmptyRequestData, List<Draft>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/drafts/"
    override val data = EmptyRequestData()
}

@Serializable
data class Draft(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    @SerialName("updated_at") val updatedAt: String,
    var payload: MessageResponsePayload,
    val revision: Int
)