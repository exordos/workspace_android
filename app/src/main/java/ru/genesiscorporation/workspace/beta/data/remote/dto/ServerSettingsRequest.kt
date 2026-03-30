package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod


class ServerSettingsRequest(): ApiRequest<EmptyRequestData, ServerSettingsResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = false
    override val url: String = "/api/v1/server_settings"
    override val data = EmptyRequestData()
}

@Serializable
data class ServerSettingsResponseData(
    val email_auth_enabled: Boolean
)