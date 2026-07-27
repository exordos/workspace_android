package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class StreamBindingsRequest :
    ApiRequest<EmptyRequestData, List<StreamBindingResponseData>, ApiError> {
    override val method = HTTPMethod.GET
    override val url = "/api/workspace/v1/messenger/stream_bindings/"
    override val data = EmptyRequestData()
}

@Serializable
data class StreamBindingResponseData(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("who_uuid") val whoUuid: String,
    val role: String = "member",
    @SerialName("notification_mode") val notificationMode: String = "all_messages",
)

class StreamNotificationsRequest(
    streamUuid: String,
    notificationMode: String,
) : ApiRequest<StreamNotificationsRequestData, Stream, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/streams/$streamUuid/actions/notifications/invoke"
    override val data = StreamNotificationsRequestData(notificationMode)
}

@Serializable
data class StreamNotificationsRequestData(
    @SerialName("notification_mode") val notificationMode: String,
)
