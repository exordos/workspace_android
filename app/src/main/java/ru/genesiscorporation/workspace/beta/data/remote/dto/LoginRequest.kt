package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
): ApiRequest<LoginRequestData, LoginResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = false
    override val url: String = "/api/v1/fetch_api_key"
    override val data = LoginRequestData(
        username, password
    )
}

@Serializable
data class LoginRequestData(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val api_key: String,
    val user_id: Int,
    val email: String
)