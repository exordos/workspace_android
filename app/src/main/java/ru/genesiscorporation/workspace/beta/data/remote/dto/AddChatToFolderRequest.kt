package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddChatToFolderRequest(
    folderUuid: String,
    streamUuid: String,
    chatType: String,
    orderIndex: Int? = null,
): ApiRequest<AddChatToFolderRequestData, String, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val isJson: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/folder_items/"
    override val data = AddChatToFolderRequestData(
        folderUuid = folderUuid,
        streamUuid = streamUuid,
        chatType = chatType,
        orderIndex = orderIndex,
    )
}

@Serializable
data class AddChatToFolderRequestData(
    @SerialName("folder_uuid") val folderUuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("chat_type") val chatType: String,
    @SerialName("order_index") val orderIndex: Int? = null,
)
