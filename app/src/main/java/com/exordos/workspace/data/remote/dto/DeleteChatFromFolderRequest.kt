package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class DeleteChatFromFolderRequest(
    folderChatUuid: String
): ApiRequest<EmptyRequestData, DeleteChatFromFolderResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.DELETE
    override val requiresApiKey: Boolean = true
    override val isJson: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/folder_items/${folderChatUuid}"
    override val data = EmptyRequestData()
}
@Serializable
data class DeleteChatFromFolderResponseData(
    val uuid: String
)
