package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
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
    override val url: String = "${baseUrl}/api/workspace/v1/messenger/server_settings/"
    override val data = EmptyRequestData()
}

@Serializable
data class ServerSettingsResponseData(
    val email_auth_enabled: Boolean,
    @SerialName("realm_name") val realmName: String,
    @SerialName("meet_url") val meetUrl: String,
    @SerialName("realm_url") val realmUrl: String? = null,
    @SerialName("realm_icon") val realmIcon: String? = null,
)
