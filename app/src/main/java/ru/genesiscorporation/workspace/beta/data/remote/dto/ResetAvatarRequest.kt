package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class ResetAvatarRequest(
    userUuid: String
): ApiRequest<EmptyRequestData, ResetAvatarResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val url: String = "/api/workspace/v1/users/${userUuid}/actions/avatar_reset/invoke"
    override val data = EmptyRequestData()
}

@Serializable
class ResetAvatarResponseData