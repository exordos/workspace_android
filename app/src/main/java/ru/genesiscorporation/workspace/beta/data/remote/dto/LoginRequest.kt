package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val otp: String
): ApiRequest<LoginRequestData, LoginResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = false
    override val shouldApplySuffix: Boolean = false
    override val url: String = "/api/core/v1/iam/clients/default/actions/get_token/invoke"
    override val data = LoginRequestData(
        username,
        password,
        "login+password",
        "openid email profile",
        "3600",
        "2592000",
    )

    override val additionalHeaders: Map<String, String> =
        if (!otp.isEmpty()) {
            mapOf("X-OTP" to otp)
        } else {
            emptyMap()
        }
}

@Serializable
data class LoginRequestData(
    val login: String,
    val password: String,
    val grant_type: String,
    val scope: String,
    val ttl: String,
    val refresh_ttl: String
)

fun workspaceProjectScope(projectId: String): String =
    "openid email profile project:${projectId.trim()}"

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
)
