package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class FolderChatsRequest(
    folderUuid: String
): ApiRequest<EmptyRequestData, List<FolderChatResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val url: String = "/workspace/v1/folders/${folderUuid}/items/"
    override val data = EmptyRequestData()
}

@Serializable
data class FolderChatResponseData(
    val uuid: String,
    @SerialName("chat_id") val chatId: Int,
    @SerialName("background_color_value") val backgroundColorValue: Long
)