package com.exordos.workspace.data.uio

import androidx.room3.ColumnTypeConverter
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.net.URLDecoder

@Entity
data class MessageUIO(
    @PrimaryKey var uuid: String,
    val updatedAt: String,
    val createdAt: String,
    val streamUuid: String,
    var topicUuid: String,
    var userUuid: String,
    var authorUuid: String,
    @Embedded(prefix = "payload_")
    var payload: MessagePayloadUIO,
    val isOwn: Boolean,
    var reactions: Map<String, Int>,
    var read: Boolean,
    val projectId: String? = null,
    val mentioned: Boolean = false,
    @Embedded(prefix = "orig_payload_")
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

@Entity
data class LastMessageUIO(
    @PrimaryKey var uuid: String,
    val updatedAt: String,
    val createdAt: String,
    val streamUuid: String,
    var topicUuid: String,
    var userUuid: String,
    var authorUuid: String,
    @Embedded(prefix = "payload_")
    var payload: MessagePayloadUIO,
    val isOwn: Boolean,
    var read: Boolean,
    val projectId: String? = null,
    val mentioned: Boolean = false,
    @Embedded(prefix = "orig_payload_")
    val originalEncryptedPayload: MessagePayloadUIO? = null,
) {
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

object MapConverters {
    @ColumnTypeConverter
    fun reactionsToJson(value: Map<String, Int>): String =
        Json.encodeToString(value)
    @ColumnTypeConverter
    fun reactionsFromJson(value: String): Map<String, Int> =
        Json.decodeFromString(value)
}

fun MessageUIO.toLastMessageUIO() = LastMessageUIO(
    uuid = uuid,
    updatedAt = updatedAt,
    createdAt = createdAt,
    streamUuid = streamUuid,
    topicUuid = topicUuid,
    userUuid = userUuid,
    authorUuid = authorUuid,
    payload = payload,
    isOwn = isOwn,
    read = read,
    projectId = projectId,
    mentioned = mentioned,
    originalEncryptedPayload = originalEncryptedPayload,
)

fun LastMessageUIO.toMessageUIO() = MessageUIO(
    uuid = uuid,
    updatedAt = updatedAt,
    createdAt = createdAt,
    streamUuid = streamUuid,
    topicUuid = topicUuid,
    userUuid = userUuid,
    authorUuid = authorUuid,
    payload = payload,
    isOwn = isOwn,
    read = read,
    projectId = projectId,
    mentioned = mentioned,
    originalEncryptedPayload = originalEncryptedPayload,
    reactions = emptyMap()
)
@Dao
interface LastMessageDao {
    @Query("SELECT * FROM LastMessageUIO")
    fun observeLastMessages(): Flow<List<LastMessageUIO>>

    @Query("SELECT COUNT(*) FROM LastMessageUIO")
    fun countObjects(): Int
    @Query("SELECT * FROM LastMessageUIO WHERE streamUuid LIKE :streamUuid AND topicUuid LIKE :topicUuid LIMIT 1")
    fun getLastMessage(streamUuid: String, topicUuid: String): LastMessageUIO?
    @Query("SELECT * FROM LastMessageUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getLastMessage(uuid: String): LastMessageUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lastMessage: List<LastMessageUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lastMessage: LastMessageUIO)
    @Delete
    suspend fun delete(lastMessage: LastMessageUIO)
    @Query("DELETE FROM LastMessageUIO")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM MessageUIO")
    fun observeMessages(): Flow<List<MessageUIO>>

    @Query("SELECT COUNT(*) FROM MessageUIO")
    fun countObjects(): Int
    @Query("SELECT * FROM MessageUIO WHERE streamUuid LIKE :streamUuid AND topicUuid LIKE :topicUuid")
    fun observeMessages(streamUuid: String, topicUuid: String): Flow<List<MessageUIO>>
    @Query("SELECT * FROM MessageUIO WHERE uuid LIKE :uuid LIMIT 1")
    fun getMessage(uuid: String): MessageUIO?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(messages: List<MessageUIO>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageUIO)
    @Delete
    suspend fun delete(message: MessageUIO)
    @Query("DELETE FROM MessageUIO")
    suspend fun deleteAll()
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
