package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class ToggleTopicDoneRequest(
    val topicUuid: String
): ApiRequest<EmptyRequestData, ToggleTopicDoneRequestResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/${topicUuid}/actions/toggle_done/invoke"
    override val data = EmptyRequestData()
}

@Serializable
class ToggleTopicDoneRequestResponseData()