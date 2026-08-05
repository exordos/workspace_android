package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

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