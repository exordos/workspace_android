package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

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
