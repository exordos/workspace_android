package com.exordos.workspace.data.uio

import java.net.URLDecoder

data class MessageUIO(
    var uuid: String,
    val updatedAt: String,
    val createdAt: String,
    val streamUuid: String,
    var topicUuid: String,
    var userUuid: String,
    var authorUuid: String,
    var payload: MessagePayloadUIO,
    val isOwn: Boolean,
    var reactions: Map<String, Int>,
    var read: Boolean,
    var user: UserUIO? = null,
    val projectId: String? = null,
    val mentioned: Boolean = false,
    val originalEncryptedPayload: MessagePayloadUIO? = null,

) {
    /** Match the server's active-unread policy, including explicit topic overrides. */
    fun isActiveUnread(streamMode: String, topicMode: String): Boolean = !read && when (topicMode) {
        "mute" -> false
        "follow" -> true
        "unmute" -> mentioned
        else -> when (streamMode) {
            "all_messages" -> true
            "mentions_only" -> mentioned
            else -> false
        }
    }

    private fun parseQuotedMessages(): List<QuotedMessagePart> {
        return quoteBlockRegex.findAll(payload.content.trim())
            .map { match ->
                val selectedText = runCatching {
                    URLDecoder.decode(match.groupValues[3], "UTF-8")
                }.getOrNull()
                if (selectedText == null) {
                    // Keep malformed references literal, as MarkdownPayloadParser does.
                    QuotedMessagePart(uuid = null, text = match.value)
                } else {
                    QuotedMessagePart(
                        uuid = match.groupValues[2],
                        text = match.groupValues[4].trim().ifBlank { selectedText },
                    )
                }
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

    fun description(forwardedLabel: String = "Forwarded message"): String {
        if (payload.kind != "markdown") return when (payload.kind) {
            "e2ee" -> "Зашифрованное сообщение"
            "key_request" -> "Запрос ключа шифрования"
            "key_grant" -> "Ключ шифрования выдан"
            "key_reject" -> "Запрос ключа отклонён"
            "key_announced" -> "Ключ шифрования перевыпущен"
            else -> "Служебное сообщение"
        }
        val snapshotHeader = authoredQuoteHeaderRegex.find(payload.content.trim())
        if (snapshotHeader?.groupValues?.get(1)?.isNotBlank() == true) return forwardedLabel
        return asQuotedMessages().lastOrNull()?.text.orEmpty().ifBlank {
            if (containsQuotedMessages()) forwardedLabel else payload.content
        }
    }
}

data class QuotedMessagePart(
    val uuid: String?,
    val text: String,
)

private val quoteBlockRegex = Regex(
    """\[((?:\\.|[^\]\\])*)\]\(urn:quote:([0-9a-fA-F-]{36})(?:\?text=([^\s)&#]+))?\)\s*([\s\S]*?)(?=\n*\[(?:\\.|[^\]\\])*\]\(urn:quote:|\z)"""
)
private val authoredQuoteHeaderRegex = Regex(
    """^>[ \t]?\*\*((?:\\.|[^\r\n])+)\*\*:\r?(?:\n|$)"""
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

    /** A forwarded snapshot is readable independently of the original conversation. */
    data class SnapshotQuote(
        val displayName: String,
        val text: String,
    ) : MessageElement

    data class PlainText(
        val text: String,
    ) : MessageElement
}
