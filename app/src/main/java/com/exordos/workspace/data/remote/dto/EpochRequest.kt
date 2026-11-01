package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class EpochRequest(): ApiRequest<EmptyRequestData, EpochResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/epoch/"
    override val data = EmptyRequestData()
}

@Serializable
data class EpochResponseData(
    @SerialName("epoch_version") val epochVersion: Int,
    @SerialName("epoch_generation") val epochGeneration: String
)
