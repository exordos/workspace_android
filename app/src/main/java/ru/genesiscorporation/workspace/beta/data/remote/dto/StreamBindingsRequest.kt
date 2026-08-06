package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class StreamBindingsRequest(
    val streamUuid: String
): ApiRequest<StreamBindingsRequestData, List<StreamBindingResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_bindings/"
    override val data = StreamBindingsRequestData(streamUuid)
}

@Serializable
data class StreamBindingsRequestData(
    val stream_uuid: String
)

@Serializable
data class StreamBindingResponseData(
    val uuid: String,
    var role: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("user_uuid") var userUuid: String
)