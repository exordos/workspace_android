package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod


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
    @SerialName("organization_uuid") val organizationUuid: String? = null,
    @SerialName("push_notifications_enabled") val pushNotificationsEnabled: Boolean = false,
    val email_auth_enabled: Boolean,
    @SerialName("realm_name") val realmName: String,
    @SerialName("realm_url") val realmUrl: String,
    @SerialName("realm_icon") val realmIcon: String,
    @SerialName("meet_url") val meetUrl: String
)
