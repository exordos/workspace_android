package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod


class ServerSettingsRequest(
    val baseUrl: String
): ApiRequest<EmptyRequestData, ServerSettingsResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = false
    override val isAbsoluteUrl: Boolean = true
    override val url: String = "${baseUrl}/api/v1/server_settings"
    override val data = EmptyRequestData()
}

@Serializable
data class ServerSettingsResponseData(
    val email_auth_enabled: Boolean,
    val external_authentication_methods: List<ExternalAuthenticationMethod>
)
@Serializable
data class ExternalAuthenticationMethod(
    val name: String,
    val display_name: String,
    val login_url: String
)