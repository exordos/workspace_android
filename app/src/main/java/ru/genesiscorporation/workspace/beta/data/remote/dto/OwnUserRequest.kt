package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class OwnUserRequest(): ApiRequest<EmptyRequestData, UserResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/users/me"
    override val data = EmptyRequestData()
}

@Serializable
data class UserResponse(
    val avatar_url: String,
    val email: String,
    val full_name: String,
    val user_id: Int,
    val delivery_email: String
)