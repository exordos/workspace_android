package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class UpdateStreamNotificationModeRequest(
    val streamUuid: String,
    val notificationMode: String
): ApiRequest<UpdateStreamNotificationModeRequestData, UpdateStreamNotificationModeResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/${streamUuid}/actions/notifications/invoke"
    override val data = UpdateStreamNotificationModeRequestData(
        notificationMode
    )
}


@Serializable
data class UpdateStreamNotificationModeRequestData(
    @SerialName("notification_mode") val notificationMode: String
)

@Serializable
class UpdateStreamNotificationModeResponseData()
