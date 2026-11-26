package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.TopicUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class TopicsRequest(
    val streamUuids: List<String>? = null
): UIORequest<TopicsRequestData, List<TopicsResponseData>, List<TopicUIO>, ApiError> {
    override fun toUio(dto: List<TopicsResponseData>): List<TopicUIO> = dto.map { it.toUio() }
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/"
    override val data = TopicsRequestData(streamUuids)
}

@Serializable
data class TopicsRequestData(
    val stream_uuid: List<String>?
)
@Serializable
data class TopicsByIdsRequest(
    val topicIds: List<String>
): UIORequest<TopicsByIdsRequestData, List<TopicsResponseData>, List<TopicUIO>, ApiError> {
    override fun toUio(dto: List<TopicsResponseData>): List<TopicUIO> = dto.map { it.toUio() }
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
    val name: String,
    val color: Int,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("active_unread_count") val activeUnreadCount: Int,
    @SerialName("is_done") val isDone: Boolean,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("last_message_uuid") val lastMessageUuid: String? = null,
    @SerialName("notification_mode") val notificationMode: String,
    val summary: String? = null
)
