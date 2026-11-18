package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.StreamUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class StreamsRequest(): UIORequest<EmptyRequestData, List<Stream>, List<StreamUIO>, ApiError> {
    override fun toUio(dto: List<Stream>): List<StreamUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/"
    override val data = EmptyRequestData()
}

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class Stream(
    val uuid: String,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("active_unread_count") val activeUnreadCount: Int,
    @SerialName("passive_unread_count") val passiveUnreadCount: Int,
    @SerialName("updated_at") val updatedAt: String,
    val name: String,
    @SerialName("private") val isPrivate: Boolean,
    val color: Int,
    @SerialName("last_message_uuid") val lastMessageUuid: String? = null,
    @SerialName("default_topic_uuid") val defaultTopicUuid: String? = null,
    @SerialName("direct_user_uuid") val directUserUuid: String? = null,
    @SerialName("notification_mode") val notificationMode: String,
    @SerialName("is_archived") val isArchived: Boolean = false,
    val encryption: Boolean = false,
    @SerialName("current_encryption_key_uuid") val currentEncryptionKeyUuid: String? = null,
    @SerialName("owner") @JsonNames("owner_uuid") val ownerUuid: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("user_uuid") val userUuid: String? = null,
    val role: String? = null,
    val description: String = "",
    @SerialName("invite_only") val inviteOnly: Boolean = false,
    val announce: Boolean = false,
)
