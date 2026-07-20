package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class TopicsRequest(
    val streamUuid: String
): ApiRequest<TopicsRequestData, List<TopicsResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/"
    override val data = TopicsRequestData(streamUuid)
}

@Serializable
data class TopicsRequestData(
    val stream_uuid: String
)

@Serializable
data class TopicsResponseData(
    val uuid: String,
    var name: String,
    val color: Int,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("updated_at") var updatedAt: String,
    @SerialName("unread_count") var unreadCount: Int,
    @SerialName("is_done") var isDone: Boolean,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("last_message_uuid") var lastMessageUuid: String? = null,
    var lastMessage: MessageResponse? = null
)