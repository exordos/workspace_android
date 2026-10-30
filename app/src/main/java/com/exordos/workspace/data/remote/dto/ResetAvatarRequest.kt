package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class ResetAvatarRequest(
    userUuid: String
): ApiRequest<EmptyRequestData, ResetAvatarResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/users/${userUuid}/actions/avatar_reset/invoke"
    override val data = EmptyRequestData()
}

@Serializable
class ResetAvatarResponseData
