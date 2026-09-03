package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class TopicsRequest(
    val streamUuids: List<String>
): ApiRequest<TopicsRequestData, List<TopicsResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/"
    override val data = TopicsRequestData(streamUuids)
}

@Serializable
data class TopicsRequestData(
    val stream_uuid: List<String>
)
@Serializable
data class TopicsByIdsRequest(
    val topicIds: List<String>
): ApiRequest<TopicsByIdsRequestData, List<TopicsResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/"
    override val data = TopicsByIdsRequestData(
        topicIds
    )
}

@Serializable
data class TopicsByIdsRequestData(
    val uuid: List<String>
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
    @SerialName("notification_mode") var notificationMode: String,
    var lastMessage: MessageResponse? = null,
    var summary: String? = null
)