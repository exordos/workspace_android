package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class TokenRefreshRequest(
    val refreshToken: String,
    val scope: String? = null,
): ApiRequest<TokenRefreshRequestData, LoginResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = false
    override val shouldApplySuffix: Boolean = false
    override val url: String = "/api/core/v1/iam/clients/default/actions/get_token/invoke"
    override val data = TokenRefreshRequestData("refresh_token", refreshToken, scope)
}

@Serializable
data class TokenRefreshRequestData(
    val grant_type: String,
    val refresh_token: String,
    val scope: String? = null,
)

@Serializable
data class TokenRefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)
