package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class TopicsRequest(
    val streamId: String
): ApiRequest<EmptyRequestData, TopicsResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/users/me/${streamId}/topics"
    override val data = EmptyRequestData("")
}

@Serializable
data class TopicsResponse(
    val topics: List<TopicsResponseData>
)

@Serializable
data class TopicsResponseData(
    val max_id: Int,
    val name: String
)