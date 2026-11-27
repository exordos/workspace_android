package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.StreamBindingUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class StreamBindingsRequest(
    val streamUuid: String? = null
): UIORequest<StreamBindingsRequestData, List<StreamBindingResponseData>, List<StreamBindingUIO>, ApiError> {
    override fun toUio(dto: List<StreamBindingResponseData>): List<StreamBindingUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_bindings/"
    override val data = StreamBindingsRequestData(streamUuid)
}

@Serializable
data class StreamBindingsRequestData(
    val stream_uuid: String?
)

@Serializable
data class StreamBindingResponseData(
    val uuid: String,
    val role: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("user_uuid") val userUuid: String
)
