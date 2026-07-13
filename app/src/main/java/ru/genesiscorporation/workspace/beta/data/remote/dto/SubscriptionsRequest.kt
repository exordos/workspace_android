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
    override val url: String = "/api/messenger/v1/streams/"
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
    var avatar: String? = null,
    var lastMessage: MessageResponse? = null
)