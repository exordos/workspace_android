package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

class AddChatToFolderRequest(
    folderUuid: String,
    streamUuid: String,
    chatType: String
): ApiRequest<AddChatToFolderRequestData, AddChatToFolderResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val isJson: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/folder_items/"
    override val data = AddChatToFolderRequestData(folderUuid, streamUuid, chatType)
}

@Serializable
data class AddChatToFolderRequestData(
    @SerialName("folder_uuid") val folderUuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("chat_type") val chatType: String
)
@Serializable
data class AddChatToFolderResponseData(
    val uuid: String
)
