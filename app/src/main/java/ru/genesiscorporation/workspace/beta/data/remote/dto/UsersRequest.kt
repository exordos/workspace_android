package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class UsersRequest(): ApiRequest<UsersRequestData, UsersResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/users"
    override val data = UsersRequestData(true, false)
}

@Serializable
data class UsersRequestData(
    @SerialName("include_custom_profile_fields") val includeCustomProfileFields: Boolean,
    @SerialName("client_gravatar") val clientGravatar: Boolean
)

@Serializable
data class UsersResponse(
    val members: List<UsersResponseData>
)

@Serializable
data class UsersResponseData(
    @SerialName("avatar_url") val avatarUrl: String?,
    val email: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("user_id") val userId: Int,
    @SerialName("profile_data") val profileData: Map<String, UserResonseProfileData>? = emptyMap()
)

@Serializable
data class UserResonseProfileData(
    val value: String
)