package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class DeleteChatFromFolderRequest(
    folderUuid: String,
    folderChatUuid: String
): ApiRequest<EmptyRequestData, DeleteChatFromFolderResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.DELETE
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val isJson: Boolean = true
    override val url: String = "/workspace/v1/folders/${folderUuid}/items/${folderChatUuid}"
    override val data = EmptyRequestData()
}
@Serializable
data class DeleteChatFromFolderResponseData(
    val uuid: String
)