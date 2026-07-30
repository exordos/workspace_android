package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class UpdateFolderRequest(
    folderUuid: String,
    title: String,
    backgroundColorValue: Long? = null,
) : ApiRequest<UpdateFolderRequestData, FolderResponseData, ApiError> {
    override val method = HTTPMethod.PUT
    override val url = "/api/workspace/v1/messenger/folders/$folderUuid"
    override val data = UpdateFolderRequestData(title, backgroundColorValue)
}

@Serializable
data class UpdateFolderRequestData(
    val title: String,
    @SerialName("background_color_value")
    val backgroundColorValue: Long? = null,
)

class DeleteFolderRequest(
    folderUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url = "/api/workspace/v1/messenger/folders/$folderUuid"
    override val data = EmptyRequestData()
}

class PinFolderItemRequest(
    folderItemUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/folder_items/$folderItemUuid/actions/pin/invoke"
    override val data = EmptyRequestData()
}

class UnpinFolderItemRequest(
    folderItemUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/folder_items/$folderItemUuid/actions/unpin/invoke"
    override val data = EmptyRequestData()
}

class CreateTopicRequest(
    name: String,
    streamUuid: String,
) : ApiRequest<CreateTopicRequestData, TopicsResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val url = "/api/workspace/v1/messenger/stream_topics/"
    override val data = CreateTopicRequestData(name, streamUuid)
}

@Serializable
data class CreateTopicRequestData(
    val name: String,
    @SerialName("stream_uuid") val streamUuid: String,
)

class RenameTopicRequest(
    topicUuid: String,
    name: String,
) : ApiRequest<RenameTopicRequestData, TopicsResponseData, ApiError> {
    override val method = HTTPMethod.PUT
    override val url = "/api/workspace/v1/messenger/stream_topics/$topicUuid"
    override val data = RenameTopicRequestData(name)
}

@Serializable
data class RenameTopicRequestData(
    val name: String,
)

class DeleteTopicRequest(
    topicUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url = "/api/workspace/v1/messenger/stream_topics/$topicUuid"
    override val data = EmptyRequestData()
}

class ToggleTopicDoneRequest(
    topicUuid: String,
) : ApiRequest<EmptyRequestData, TopicsResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/stream_topics/$topicUuid/actions/toggle_done/invoke"
    override val data = EmptyRequestData()
}

class TopicNotificationsRequest(
    topicUuid: String,
    notificationMode: String,
) : ApiRequest<TopicNotificationsRequestData, TopicsResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/stream_topics/$topicUuid/actions/notifications/invoke"
    override val data = TopicNotificationsRequestData(notificationMode)
}

@Serializable
data class TopicNotificationsRequestData(
    @SerialName("notification_mode") val notificationMode: String,
)

class MarkTopicReadRequest(
    topicUuid: String,
) : ApiRequest<EmptyRequestData, TopicsResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/stream_topics/$topicUuid/actions/read/invoke"
    override val data = EmptyRequestData()
}

class MarkStreamReadRequest(
    streamUuid: String,
) : ApiRequest<EmptyRequestData, Stream, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/streams/$streamUuid/actions/read/invoke"
    override val data = EmptyRequestData()
}

class AddStreamMembersRequest(
    streamUuid: String,
    memberUserUuids: List<String>,
) : ApiRequest<AddStreamMembersRequestData, List<StreamBindingResponseData>, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/streams/$streamUuid/actions/add_users/invoke"
    override val data = AddStreamMembersRequestData(memberUserUuids)
}

@Serializable
data class AddStreamMembersRequestData(
    val member: List<String>,
)

class DeleteStreamBindingRequest(
    bindingUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url =
        "/api/workspace/v1/messenger/stream_bindings/$bindingUuid"
    override val data = EmptyRequestData()
}
