package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class DeleteFcmTokenRequest(
    val token: String
): ApiRequest<EmptyRequestData, DeleteFcmTokenResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.DELETE
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/push_devices/${
        token}"
    override val data = EmptyRequestData()
}

@Serializable
data class DeleteFcmTokenRequestData(
    val token: String
)

@Serializable
data class DeleteFcmTokenResponse(
    val msg: String
)