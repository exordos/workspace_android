package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class UpdateTopicNotificationModeRequest(
    val topicUuid: String,
    val notificationMode: String
): ApiRequest<UpdateTopicNotificationModeRequestData, UpdateTopicNotificationModeResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/${topicUuid}/actions/notifications/invoke"
    override val data = UpdateTopicNotificationModeRequestData(
        notificationMode
    )
}


@Serializable
data class UpdateTopicNotificationModeRequestData(
    @SerialName("notification_mode") val notificationMode: String
)

@Serializable
class UpdateTopicNotificationModeResponseData()
