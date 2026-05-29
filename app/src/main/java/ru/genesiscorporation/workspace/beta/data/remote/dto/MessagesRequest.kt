package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.MessageDto
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class MessagesRequest(
    val anchor: String,
    val num_before: String,
    val num_after: String,
    val narrow: String,
    val apply_markdown: String = "false"
): ApiRequest<MessagesRequestData, MessagesDtoResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/messages"
    override val data = MessagesRequestData(
        anchor, num_before, num_after, narrow, apply_markdown
    )
}

@Serializable
data class DirectMessagesRequest(
    val anchor: String,
    val num_before: String,
    var num_after: String,
    val narrow: String,
    val apply_markdown: String = "false"
): ApiRequest<MessagesRequestData, MessagesDtoResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/messages"
    override val data = MessagesRequestData(
        anchor, num_before, num_after, narrow, apply_markdown
    )
}

@Serializable
data class MessagesByIdsRequest(
    val messageIds: List<Int>
): ApiRequest<MessagesByIdsRequestData, MessagesDtoResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/messages"
    override val data = MessagesByIdsRequestData(
       "[${messageIds.joinToString(",")}]", "false"
    )
}


@Serializable
data class MessagesRequestData(
    val anchor: String,
    val num_before: String,
    var num_after: String,
    val narrow: String,
    val apply_markdown: String
)

@Serializable
data class MessagesByIdsRequestData(
    val message_ids: String,
    val apply_markdown: String
)

@Serializable
data class MessagesDtoResponse(
    val messages: List<MessageDto>
){
}
