package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class FolderChatsRequest(
    folderUuid: String
): ApiRequest<EmptyRequestData, List<FolderChatResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/workspace/v1/folders/${folderUuid}/items/"
    override val data = EmptyRequestData()
}

@Serializable
data class FolderChatResponseData(
    val uuid: String,
    @SerialName("chat_id") val chatId: Int,
    @SerialName("background_color_value") val backgroundColorValue: Long
)
