package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
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
): ApiRequest<MessagesRequestData, MessagesResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/messages"
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
): ApiRequest<MessagesRequestData, DirectMessagesResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/messages"
    override val data = MessagesRequestData(
        anchor, num_before, num_after, narrow, apply_markdown
    )
}


@Serializable
data class MessagesRequestData(
    val anchor: String,
    val num_before: String,
    var num_after: String,
    val narrow: String,
    val apply_markdown: String
) {
}@Serializable
data class MessagesResponse(
    val messages: List<MessageData>
) {
}

@Serializable
data class MessageData(
    val id: Int,
    val sender_full_name: String,
    val sender_id: Int,
    val content: String,
    val timestamp: Int,
    val avatar_url: String,
    val subject: String,
    val display_recipient: String
)

@Serializable
data class DirectMessagesResponse(
    val messages: List<DirectMessageData>
)
@Serializable
data class DirectMessageData(
    val id: Int,
    val sender_full_name: String,
    val sender_id: Int,
    val content: String,
    val timestamp: Int,
    val avatar_url: String,
    val subject: String,
    val display_recipient: List<Recipient>
)
@Serializable
data class Recipient(
    val id: Int,
    val full_name: String,
    val email: String
)