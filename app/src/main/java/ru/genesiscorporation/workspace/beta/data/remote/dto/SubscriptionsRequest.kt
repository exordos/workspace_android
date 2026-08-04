package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class StreamsRequest(): ApiRequest<EmptyRequestData, List<Stream>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/"
    override val data = EmptyRequestData()
}

@Serializable
data class Stream(
    val uuid: String,
    @SerialName("unread_count") var unreadCount: Int,
    @SerialName("active_unread_count")
    var activeUnreadCount: Int? = null,
    @SerialName("passive_unread_count")
    var passiveUnreadCount: Int? = null,
    @SerialName("updated_at") var updatedAt: String,
    var name: String,
    var description: String = "",
    @SerialName("private") val isPrivate: Boolean,
    val color: Int? = null,
    val owner: String? = null,
    @SerialName("user_uuid") val userUuid: String? = null,
    val role: String = "member",
    @SerialName("notification_mode") var notificationMode: String = "all_messages",
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("invite_only") val inviteOnly: Boolean = false,
    val announce: Boolean = false,
    @SerialName("source_name") val sourceName: String = "native",
    @SerialName("last_message_uuid") var lastMessageUuid: String? = null,
    @SerialName("default_topic_uuid") var defaultTopicUuid: String? = null,
    @SerialName("direct_user_uuid") val directUserUuid: String? = null,
    var avatar: String? = null,
    var lastMessage: MessageResponse? = null,
    val provider: ProviderReference? = null,
)

@Serializable
data class ProviderReference(
    val kind: String,
    @SerialName("account_uuid") val accountUuid: String? = null,
    @SerialName("external_id") val externalId: String? = null,
)
