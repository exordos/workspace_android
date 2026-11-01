package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

class AddUsersToStreamRequest(
    val streamUuid: String,
    val members: List<String>
): ApiRequest<AddUsersToStreamRequestData, List<StreamBindingResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/${canonicalStreamId(streamUuid)}/actions/add_users/invoke"
    override val data = AddUsersToStreamRequestData(
        members
    )
}

@Serializable
data class AddUsersToStreamRequestData(
    @SerialName("member") val members: List<String>
)
