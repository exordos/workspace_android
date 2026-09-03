package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

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