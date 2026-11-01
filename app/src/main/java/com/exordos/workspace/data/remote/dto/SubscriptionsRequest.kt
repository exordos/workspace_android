package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class StreamsRequest(): ApiRequest<EmptyRequestData, List<Stream>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/"
    override val data = EmptyRequestData()
}

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class Stream(
    val uuid: String,
    @SerialName("unread_count") var unreadCount: Int,
    @SerialName("active_unread_count") var activeUnreadCount: Int,
    @SerialName("passive_unread_count") var passiveUnreadCount: Int,
    @SerialName("updated_at") var updatedAt: String,
    var name: String,
    @SerialName("private") val isPrivate: Boolean,
    val color: Int,
    @SerialName("last_message_uuid") var lastMessageUuid: String? = null,
    @SerialName("default_topic_uuid") var defaultTopicUuid: String? = null,
    @SerialName("direct_user_uuid") val directUserUuid: String? = null,
    @SerialName("notification_mode") var notificationMode: String,
    var directUser: UserResponseData? = null,
    var lastMessage: MessageResponse? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    val encryption: Boolean = false,
    @SerialName("current_encryption_key_uuid") var currentEncryptionKeyUuid: String? = null,
    @SerialName("owner") @JsonNames("owner_uuid") val ownerUuid: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("user_uuid") val userUuid: String? = null,
    val role: String? = null,
    val description: String = "",
    @SerialName("invite_only") val inviteOnly: Boolean = false,
    val announce: Boolean = false,
) {
    fun displayableName(participant: UserResponseData? = directUser): String =
        participant?.takeIf { it.uuid == directUserUuid }
            ?.displayableName()?.trim()?.takeIf { it.isNotEmpty() }
            ?: name
}
