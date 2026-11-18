package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.UserUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class UsersRequest(): UIORequest<EmptyRequestData, List<UserResponseData>, List<UserUIO>, ApiError> {
    override fun toUio(dto: List<UserResponseData>): List<UserUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/users/"
    override val data = EmptyRequestData()
}

@Serializable
data class UserResponseData(
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val username: String,
    val uuid: String,
    @SerialName("status_emoji") val statusEmoji: String? = null,
    @SerialName("status_text") val statusText: String? = null,
    val status: String,
    val avatar: String
)
