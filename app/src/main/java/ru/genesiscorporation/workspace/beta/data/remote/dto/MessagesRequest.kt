package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod
import ru.genesiscorporation.workspace.beta.modules.chatdialog.QuotedMessage

@Serializable
data class MessagesRequest(
    val streamId: String,
    val topicId: String?
): ApiRequest<MessagesRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MessagesRequestData(
        streamId, topicId
    )
}

@Serializable
data class MessagesByIdsRequest(
    val messageIds: List<String>
): ApiRequest<MessagesByIdsRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MessagesByIdsRequestData(
       messageIds
    )
}

@Serializable
class MentionedMessagesRequest: ApiRequest<MentionedMessagesRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MentionedMessagesRequestData()
}


@Serializable
data class MessagesRequestData(
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String?
)

@Serializable
data class MessagesByIdsRequestData(
    val uuid: List<String>
) {
}
@Serializable
data class MentionedMessagesRequestData(
    val mentioned: Boolean = true
)

@Serializable
data class MessageResponse(
    var uuid: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") var topicUuid: String,
    @SerialName("user_uuid") var userUuid: String,
    @SerialName("author_uuid") var authorUuid: String,
    var payload: MessageResponsePayload,
    @SerialName("is_own") val isOwn: Boolean,
    var reactions: Map<String, Int>,
    var read: Boolean,
    var user: UserResponseData? = null

) {
    private fun parseQuotedMessages(): List<QuotedMessagePart> {
        return quoteBlockRegex.findAll(payload.content.trim())
            .map { match ->
                QuotedMessagePart(
                    uuid = match.groupValues[2],
                    text = match.groupValues[3].trim(),
                )
            }
            .toList()
    }
    private fun containsQuotedMessages(): Boolean {
        return quoteBlockRegex.containsMatchIn(payload.content)
    }

    fun asQuotedMessages(): List<QuotedMessagePart> {
        if (containsQuotedMessages()) {
            return parseQuotedMessages()
        } else {
            return listOf(QuotedMessagePart(null, payload.content))
        }
    }

    fun description(): String {
        return asQuotedMessages().last().text
    }
}

@Serializable
data class MessageResponsePayload(
    val kind: String,
    var content: String
)

data class QuotedMessagePart(
    val uuid: String?,
    val text: String,
)

private val quoteBlockRegex = Regex(
    """\[([^\]]*)\]\(urn:quote:([0-9a-fA-F-]{36})\)\s*\n+([\s\S]*?)(?=\n*\[(?:[^\]]*)\]\(urn:quote:|\z)"""
)

sealed interface MessageElement {

    data class Image(
        val fileName: String,
        val uuid: String,
    ) : MessageElement

    data class File(
        val fileName: String,
        val uuid: String,
    ) : MessageElement

    data class Quote(
        val displayName: String,
        val uuid: String,
        val text: String,
    ) : MessageElement

    data class PlainText(
        val text: String,
    ) : MessageElement
}