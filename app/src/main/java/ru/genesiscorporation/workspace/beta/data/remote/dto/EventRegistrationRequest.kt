package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class EventRegistrationRequest(
    val fetchEventTypes: String,
    val narrow: String?
): ApiRequest<EventRegistrationRequestData, EventRegistrationResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/register"
    override val data = EventRegistrationRequestData(
        narrow
    )
}

@Serializable
data class EventRegistrationRequestData(
//    val fetch_event_types: String,
//    val event_types: String,
    val narrow: String?
)

@Serializable
data class EventRegistrationResponse(
    @SerialName("queue_id") val queueId: String,
    val presences: Map<String, Presense>,
    @SerialName("user_status") val userStatus: Map<String, UserStatus>,
    @SerialName("recent_private_conversations") val recentPrivateConversations: List<RecentPrivateConversation>,
    val subscriptions: List<Subscription>,
    @SerialName("unread_msgs") val unreadMessages: UnreadMessages,
    @SerialName("custom_profile_fields") val customProfileFields: List<CustomProfileField>? = emptyList()
)
@Serializable
data class Presense(
    val aggregated: PresenseAggregated
)

@Serializable
data class PresenseAggregated(
    val status: String,
    val timestamp: Long
)

@Serializable
data class UserStatus(
    @SerialName("status_text") val statusText: String? = null,
    @SerialName("emoji_name") val emojiName: String? = null,
    @SerialName("emoji_code") val emojiCode: String? = null,
    @SerialName("reaction_type") val reactionType: String? = null
)

@Serializable
data class RecentPrivateConversation (
    @SerialName("max_message_id") val maxMessageId: Int,
    @SerialName("user_ids") val userIds: List<Int>
)
@Serializable
data class UnreadMessages(
    var pms: List<UnreadPrivateMessage>,
    var streams: List<UnreadStreamMessage>
)
@Serializable
data class UnreadPrivateMessage(
    @SerialName("other_user_id") val otherUserId: Int,
    @SerialName("unread_message_ids") var unreadMessageIds: List<Int>
)
@Serializable
data class UnreadStreamMessage(
    @SerialName("stream_id") val streamId: Int,
    val topic: String,
    @SerialName("unread_message_ids") var unreadMessageIds: List<Int>
)

@Serializable
data class CustomProfileField(
    val id: Int,
    val name: String,
    val order: Int,
    @SerialName("display_in_profile_summary") var shouldDisplayInProfileSummary: Boolean = false
)