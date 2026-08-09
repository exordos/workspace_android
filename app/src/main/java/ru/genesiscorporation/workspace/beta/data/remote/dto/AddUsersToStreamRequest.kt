package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddUsersToStreamRequest(
    val streamUuid: String,
    val members: List<String>
): ApiRequest<AddUsersToStreamRequestData, AddUsersToStreamResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/${streamUuid}/actions/add_users/invoke"
    override val data = AddUsersToStreamRequestData(
        members
    )
}

@Serializable
data class AddUsersToStreamRequestData(
    val members: List<String>
)
@Serializable
class AddUsersToStreamResponseData(
)