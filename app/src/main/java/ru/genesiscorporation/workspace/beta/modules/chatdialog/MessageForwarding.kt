package ru.genesiscorporation.workspace.beta.modules.chatdialog

import ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class ForwardTargetKind {
    CHANNEL,
    DIRECT,
}

internal enum class ForwardDeliveryStatus {
    EDITING,
    UNCERTAIN,
    COMPLETED,
}

internal data class ForwardDialogState(
    val sourceMessage: MessageResponse,
    val targetKind: ForwardTargetKind = ForwardTargetKind.CHANNEL,
    val selectedStreamUuid: String? = null,
    val selectedTopicUuid: String? = null,
    val selectedUserUuid: String? = null,
    val currentUserUuid: String? = null,
    val catalogLoading: Boolean = false,
    val topicsLoading: Boolean = false,
    val submitting: Boolean = false,
    val verifying: Boolean = false,
    val deliveryStatus: ForwardDeliveryStatus = ForwardDeliveryStatus.EDITING,
    val error: String? = null,
    val canRetryUncertainSend: Boolean = false,
)

internal data class ForwardDestination(
    val streamUuid: String,
    val topicUuid: String,
)

internal data class ForwardDeliveryAttempt(
    val ownerKey: String,
    val destination: ForwardDestination,
    val content: String,
    val knownMatchingMessageUuids: Set<String>,
)

internal sealed interface ForwardPostDecision {
    data class Completed(val message: MessageResponse) : ForwardPostDecision
    data class Verify(val reason: String) : ForwardPostDecision
    data class Failed(val reason: String) : ForwardPostDecision
}

internal sealed interface ForwardQuoteResolution {
    data object Loading : ForwardQuoteResolution
    data class Ready(val message: MessageResponse) : ForwardQuoteResolution
    data object Unavailable : ForwardQuoteResolution
}

internal data class WorkspaceQuoteReference(
    val authorLabel: String,
    val messageUuid: String,
    val selectedText: String? = null,
)

internal sealed interface ForwardMarkdownSegment {
    data class Text(val markdown: String) : ForwardMarkdownSegment
    data class Quote(val reference: WorkspaceQuoteReference) : ForwardMarkdownSegment
}

internal fun canForwardMessage(message: MessageResponse): Boolean =
    !message.uuid.startsWith("local-") &&
        parseCanonicalMessageUuid(message.uuid) != null

internal fun buildWorkspaceForwardMarkdown(message: MessageResponse): String? {
    val canonicalUuid = parseCanonicalMessageUuid(message.uuid) ?: return null
    val authorLabel = message.user
        ?.displayableName()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: message.authorUuid.trim().takeIf(String::isNotEmpty)
        ?: return null
    return "[${escapeWorkspaceMarkdownInline(authorLabel)}]" +
        "(urn:quote:$canonicalUuid)"
}

internal fun escapeWorkspaceMarkdownInline(value: String): String = buildString {
    value.forEach { character ->
        if (character in WORKSPACE_MARKDOWN_INLINE_ESCAPES) append('\\')
        append(character)
    }
}

internal fun buildWorkspaceQuoteUrn(
    messageUuid: String,
    selectedText: String? = null,
): String? {
    val canonicalUuid = parseCanonicalMessageUuid(messageUuid) ?: return null
    if (selectedText.isNullOrEmpty()) return "urn:quote:$canonicalUuid"
    return "urn:quote:$canonicalUuid?text=${encodeUriComponent(selectedText)}"
}

internal fun parseWorkspaceQuoteUrn(value: String): Pair<String, String?>? {
    val match = WORKSPACE_QUOTE_URN.matchEntire(value.trim()) ?: return null
    val canonicalUuid = parseCanonicalMessageUuid(match.groupValues[1]) ?: return null
    val rawQuery = match.groupValues.getOrNull(2).orEmpty()
    if (rawQuery.isEmpty()) return canonicalUuid to null
    if (!rawQuery.startsWith("text=") || '&' in rawQuery) return null
    val rawText = rawQuery.removePrefix("text=")
    if (rawText.length > MAX_ENCODED_SELECTED_TEXT_CHARS) return null
    val decoded = decodeUriComponent(rawText) ?: return null
    if (decoded.length > MAX_SELECTED_TEXT_CHARS) return null
    return canonicalUuid to decoded.takeIf(String::isNotEmpty)
}

internal fun parseForwardMarkdown(markdown: String): List<ForwardMarkdownSegment> {
    if (!markdown.contains("urn:quote:", ignoreCase = true)) {
        return listOf(ForwardMarkdownSegment.Text(markdown))
    }
    val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
    val result = mutableListOf<ForwardMarkdownSegment>()
    val text = StringBuilder()
    var fenceCharacter: Char? = null
    var fenceLength = 0

    fun flushText() {
        val value = text.toString().trim('\n')
        if (value.isNotEmpty()) result += ForwardMarkdownSegment.Text(value)
        text.clear()
    }

    normalized.split('\n').forEach { line ->
        val trimmed = line.trimStart()
        val fence = FORWARD_MARKDOWN_FENCE.find(trimmed)
        if (fence != null) {
            val marker = fence.value
            val markerCharacter = marker.first()
            if (fenceCharacter == null) {
                fenceCharacter = markerCharacter
                fenceLength = marker.length
            } else if (
                markerCharacter == fenceCharacter &&
                marker.length >= fenceLength
            ) {
                fenceCharacter = null
                fenceLength = 0
            }
        }
        val quote = if (fenceCharacter == null && fence == null) {
            parseStandaloneWorkspaceQuote(line)
        } else {
            null
        }
        if (quote == null) {
            if (text.isNotEmpty()) text.append('\n')
            text.append(line)
        } else {
            flushText()
            result += ForwardMarkdownSegment.Quote(quote)
        }
    }
    flushText()
    return result.ifEmpty { listOf(ForwardMarkdownSegment.Text(markdown)) }
}

private fun parseStandaloneWorkspaceQuote(line: String): WorkspaceQuoteReference? {
    val match = FORWARD_QUOTE_MARKDOWN.matchEntire(line) ?: return null
    val parsedUrn = parseWorkspaceQuoteUrn(match.groupValues[2]) ?: return null
    val label = unescapeWorkspaceMarkdownInline(match.groupValues[1])
        .trim()
        .take(MAX_FORWARD_AUTHOR_LABEL_CHARS)
        .takeIf(String::isNotEmpty)
        ?: return null
    return WorkspaceQuoteReference(
        authorLabel = label,
        messageUuid = parsedUrn.first,
        selectedText = parsedUrn.second,
    )
}

internal fun forwardableStreams(streams: List<Stream>): List<Stream> =
    streams
        .asSequence()
        .filterNot(Stream::isArchived)
        .filterNot { it.isPrivate && !it.directUserUuid.isNullOrBlank() }
        .distinctBy(Stream::uuid)
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
        .toList()

internal fun forwardTopics(
    streamUuid: String,
    topics: List<TopicsResponseData>,
): List<TopicsResponseData> =
    topics
        .asSequence()
        .filter { it.streamUuid == streamUuid }
        .distinctBy(TopicsResponseData::uuid)
        .sortedWith(
            compareByDescending<TopicsResponseData> { it.isDefault }
                .thenBy { it.name.lowercase(Locale.getDefault()) },
        )
        .toList()

internal fun List<TopicsResponseData>.preferredForwardTopicUuid(
    selectedStreamUuid: String,
    currentStreamUuid: String,
    currentTopicUuid: String,
): String? =
    takeIf { selectedStreamUuid == currentStreamUuid }
        ?.firstOrNull { it.uuid == currentTopicUuid }
        ?.uuid
        ?: firstOrNull()?.uuid

internal fun forwardTopicLabel(
    topic: TopicsResponseData,
    topics: List<TopicsResponseData>,
): String {
    val duplicateName = topics.count {
        it.name.equals(topic.name, ignoreCase = true)
    } > 1
    return buildString {
        append(topic.name)
        if (topic.isDefault) append(" · основной")
        if (duplicateName) append(" · ${topic.uuid.take(8)}")
    }
}

internal fun forwardUsers(
    users: List<UserResponseData>,
    currentUserUuid: String?,
): List<UserResponseData> =
    users
        .asSequence()
        .filterNot { it.uuid == currentUserUuid }
        .distinctBy(UserResponseData::uuid)
        .sortedBy { it.displayableName().lowercase(Locale.getDefault()) }
        .toList()

internal fun existingDirectForwardDestination(
    userUuid: String,
    streams: List<Stream>,
    topicsByStream: Map<String, List<TopicsResponseData>>,
): ForwardDestination? {
    val stream = streams.firstOrNull {
        it.isPrivate &&
            !it.isArchived &&
            it.directUserUuid == userUuid
    } ?: return null
    val defaultTopicUuid = stream.defaultTopicUuid?.takeIf(String::isNotBlank)
        ?: topicsByStream[stream.uuid]
            .orEmpty()
            .singleOrNull(TopicsResponseData::isDefault)
            ?.uuid
        ?: return null
    return ForwardDestination(stream.uuid, defaultTopicUuid)
}

internal fun forwardFailureStatus(error: ApiError): PersistedOutboxStatus =
    classifyOutboxFailure(error)

internal fun decideForwardPostResult(
    attempt: ForwardDeliveryAttempt,
    result: ApiResult<MessageResponse, ApiError>,
): ForwardPostDecision =
    when (result) {
        is ApiResult.Success -> {
            if (isExpectedForwardConfirmation(attempt, result.value)) {
                ForwardPostDecision.Completed(result.value)
            } else {
                ForwardPostDecision.Verify(
                    "Сервер вернул неожиданное подтверждение; " +
                        "проверяю целевой чат",
                )
            }
        }

        is ApiResult.Error -> {
            if (
                forwardFailureStatus(result.error) ==
                PersistedOutboxStatus.UNCERTAIN
            ) {
                ForwardPostDecision.Verify(
                    "Результат отправки не подтверждён; " +
                        "проверяю целевой чат",
                )
            } else {
                ForwardPostDecision.Failed(
                    result.error.message ?: "Не удалось переслать сообщение",
                )
            }
        }
    }

internal fun unexpectedForwardFailureNeedsVerification(
    sendStarted: Boolean,
    attempt: ForwardDeliveryAttempt?,
): Boolean = sendStarted && attempt != null

internal fun isExpectedForwardConfirmation(
    attempt: ForwardDeliveryAttempt,
    message: MessageResponse,
): Boolean =
    message.isOwn &&
        parseCanonicalMessageUuid(message.uuid) != null &&
        message.streamUuid == attempt.destination.streamUuid &&
        message.topicUuid == attempt.destination.topicUuid &&
        message.payload.content == attempt.content

internal fun uniqueForwardVerificationMatch(
    attempt: ForwardDeliveryAttempt,
    messages: List<MessageResponse>,
): MessageResponse? =
    messages
        .filter { message ->
            isExpectedForwardConfirmation(attempt, message) &&
                message.uuid !in attempt.knownMatchingMessageUuids
        }
        .singleOrNull()

private fun unescapeWorkspaceMarkdownInline(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        val escaped = value.getOrNull(index + 1)
        if (
            character == '\\' &&
            escaped != null &&
            escaped in WORKSPACE_MARKDOWN_INLINE_ESCAPES
        ) {
            append(escaped)
            index += 2
        } else {
            append(character)
            index += 1
        }
    }
}

private fun encodeUriComponent(value: String): String = buildString {
    value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val character = unsigned.toChar()
        if (
            character in 'a'..'z' ||
            character in 'A'..'Z' ||
            character in '0'..'9' ||
            character == '-' ||
            character == '_' ||
            character == '.' ||
            character == '~'
        ) {
            append(character)
        } else {
            append('%')
            append(HEX[unsigned ushr 4])
            append(HEX[unsigned and 0x0f])
        }
    }
}

private fun decodeUriComponent(value: String): String? = runCatching {
    val bytes = ByteArrayOutputStream(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%') {
            val high = value.getOrNull(index + 1)?.digitToIntOrNull(16)
                ?: error("Malformed percent escape")
            val low = value.getOrNull(index + 2)?.digitToIntOrNull(16)
                ?: error("Malformed percent escape")
            bytes.write((high shl 4) or low)
            index += 3
        } else {
            val codePoint = value.codePointAt(index)
            String(Character.toChars(codePoint))
                .toByteArray(StandardCharsets.UTF_8)
                .let(bytes::write)
            index += Character.charCount(codePoint)
        }
    }
    StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes.toByteArray()))
        .toString()
}.getOrNull()

private val WORKSPACE_MARKDOWN_INLINE_ESCAPES =
    setOf('\\', '`', '*', '_', '{', '}', '(', ')', '[', ']', '#', '+', '.', '!', '|', '>', '~', '-')
private val WORKSPACE_QUOTE_URN = Regex(
    """^urn:quote:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:\?(.*))?$""",
)
private val FORWARD_QUOTE_MARKDOWN = Regex(
    """^\s*\[((?:\\.|[^\]])+)]\((urn:quote:[^\s)]+)\)\s*$""",
    RegexOption.IGNORE_CASE,
)
private val FORWARD_MARKDOWN_FENCE = Regex("""^(`{3,}|~{3,})""")
private const val MAX_FORWARD_AUTHOR_LABEL_CHARS = 512
private const val MAX_SELECTED_TEXT_CHARS = 40_000
private const val MAX_ENCODED_SELECTED_TEXT_CHARS = MAX_SELECTED_TEXT_CHARS * 3
private const val HEX = "0123456789ABCDEF"
