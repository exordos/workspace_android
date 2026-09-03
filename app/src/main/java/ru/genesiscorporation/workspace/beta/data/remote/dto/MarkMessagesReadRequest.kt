package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class MarkMessagesReadRequest(
    val messageUuid: String
): ApiRequest<EmptyRequestData, MarkMessagesResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/${messageUuid}/actions/read/invoke"
    override val data = EmptyRequestData()
}

@Serializable
data class MarkMessagesReadUpToRequest(
    val messageUuid: String
): ApiRequest<EmptyRequestData, MarkMessagesResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/${messageUuid}/actions/read_up_to/invoke"
    override val data = EmptyRequestData()
}

@Serializable
data class MarkStreamMessagesReadRequest(
    val streamUuid: String
): ApiRequest<EmptyRequestData, MarkMessagesResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/${streamUuid}/actions/read/invoke"
    override val data = EmptyRequestData()
}

@Serializable
data class MarkTopicMessagesReadRequest(
    val topicUuid: String
): ApiRequest<EmptyRequestData, MarkMessagesResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/${topicUuid}/actions/read/invoke"
    override val data = EmptyRequestData()
}



@Serializable
class MarkMessagesResponseData()