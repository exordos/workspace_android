package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddChatToFolderRequest(
    folderUuid: String,
    chatId: Int,
    chatType: String
): ApiRequest<AddChatToFolderRequestData, AddChatToFolderResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val isJson: Boolean = true
    override val url: String = "/workspace/v1/folders/${folderUuid}/items/"
    override val data = AddChatToFolderRequestData(chatId, chatType)
}

@Serializable
data class AddChatToFolderRequestData(
    @SerialName("chat_id") val chatId: Int,
    @SerialName("chat_type") val chatType: String
)
@Serializable
data class AddChatToFolderResponseData(
    val uuid: String
)