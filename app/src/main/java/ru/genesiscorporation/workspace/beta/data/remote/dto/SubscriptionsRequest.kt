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
    @SerialName("updated_at") var updatedAt: String,
    var name: String,
    @SerialName("private") val isPrivate: Boolean,
    val color: Int,
    @SerialName("last_message_uuid") var lastMessageUuid: String? = null,
    @SerialName("default_topic_uuid") var defaultTopicUuid: String? = null,
    @SerialName("direct_user_uuid") val directUserUuid: String? = null,
    var directUser: UserResponseData? = null,
    var lastMessage: MessageResponse? = null
)