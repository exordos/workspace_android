package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class FoldersRequest(): ApiRequest<EmptyRequestData, List<FolderResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/folders/"
    override val data = EmptyRequestData()
}

@Serializable
data class FolderResponseData(
    val uuid: String,
    var title: String,
    @SerialName("unread_count") var unreadCount: Int,
    @SerialName("system_type") val systemType: String,
    @SerialName("created_at") val creationDate: String,
    @SerialName("folder_items") var items: List<FolderItem> = emptyList()

)

@Serializable
data class FolderItem(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("chat_type") val chatType: String,
    @SerialName("unread_count") var unreadCount: Int,
)
