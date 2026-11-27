package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.FolderUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class FoldersRequest(): UIORequest<EmptyRequestData, List<FolderResponseData>, List<FolderUIO>, ApiError> {
    override fun toUio(dto: List<FolderResponseData>): List<FolderUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/folders/"
    override val data = EmptyRequestData()
}

@Serializable
data class FolderResponseData(
    val uuid: String,
    val title: String,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("system_type") val systemType: String,
    @SerialName("created_at") val creationDate: String,
    @SerialName("folder_items") val items: List<FolderItem> = emptyList()

)

@Serializable
data class FolderItem(
    val uuid: String,
    @SerialName("folder_uuid") val folder: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("chat_type") val chatType: String,
    @SerialName("unread_count") val unreadCount: Int,
)
