package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class PresenceRequest(
    val userUuid: String,
    val status: String,
    val emoji: String?,
    val text: String?
): ApiRequest<PresenceRequestData, PresenceResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/users/${userUuid}/actions/presence/invoke"
    override val data = PresenceRequestData(status, emoji, text)
}

@Serializable
data class PresenceRequestData(
    val status: String,
    val emoji: String?,
    val text: String?
)

@Serializable
class PresenceResponseData()
