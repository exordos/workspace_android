package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddTopicRequest(
    val name: String,
    val streamUuid: String
): ApiRequest<AddTopicRequestData, TopicsResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/"
    override val data = AddTopicRequestData(
        name, streamUuid
    )
}

@Serializable
data class AddTopicRequestData(
    val name: String,
    @SerialName("stream_uuid") val streamUuid: String
)