package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.StreamBindingUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

class AddUsersToStreamRequest(
    val streamUuid: String,
    val members: List<String>
): UIORequest<AddUsersToStreamRequestData, List<StreamBindingResponseData>, List<StreamBindingUIO>, ApiError> {
    override fun toUio(dto: List<StreamBindingResponseData>): List<StreamBindingUIO> = dto.map { it.toUio() }
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
