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
    override val url: String = "/api/workspace/v1/messenger/folders/"
    override val data = EmptyRequestData()
}

@Serializable
data class FolderResponseData(
    val uuid: String,
    var title: String,
    @SerialName("unread_count") var unreadCount: Int,
    @SerialName("system_type") val systemType: String? = null,
    @SerialName("created_at") val creationDate: String,
    @SerialName("background_color_value")
    val backgroundColorValue: Long? = null,
    @SerialName("folder_items") var items: List<FolderItem> = emptyList()

)

@Serializable
data class FolderItem(
    val uuid: String,
    @SerialName("folder_uuid") val folderUuid: String? = null,
    val folder: String? = null,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("chat_type") val chatType: String,
    @SerialName("unread_count") var unreadCount: Int,
    @SerialName("active_unread_count")
    var activeUnreadCount: Int? = null,
    @SerialName("passive_unread_count")
    var passiveUnreadCount: Int? = null,
    @SerialName("order_index") val orderIndex: Int? = null,
    @SerialName("pinned_at") val pinnedAt: String? = null,
)
