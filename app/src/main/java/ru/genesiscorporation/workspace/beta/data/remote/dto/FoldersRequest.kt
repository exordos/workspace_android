package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class FoldersRequest(): ApiRequest<EmptyRequestData, List<FolderResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val url: String = "/workspace/v1/folders/"
    override val data = EmptyRequestData()
}

@Serializable
data class FolderResponseData(
    val uuid: String,
    val title: String,
    @SerialName("system_type") val systemType: String,
    @SerialName("created_at") val creationDate: String,
    val items: List<FolderItem> = emptyList()

)

@Serializable
data class FolderItem(
    val uuid: String,
    @SerialName("chat_id") val chatId: Int,
    @SerialName("chat_type") val chatType: String
)