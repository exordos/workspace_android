package ru.genesiscorporation.workspace.beta.modules.feed

import ru.genesiscorporation.workspace.beta.data.MessageProjectionEvent
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

enum class MessageTimelineKind(
    val starredOnly: Boolean,
    val pinnedOnly: Boolean,
    val mentionedOnly: Boolean,
    val persistent: Boolean,
    val title: String,
    val refreshDescription: String,
    val busyDescription: String,
    val emptyMessage: String,
    val refreshError: String,
    val olderError: String,
) {
    FEED(
        starredOnly = false,
        pinnedOnly = false,
        mentionedOnly = false,
        persistent = true,
        title = "Лента",
        refreshDescription = "Обновить ленту",
        busyDescription = "Обновление ленты",
        emptyMessage = "В ленте пока нет сообщений",
        refreshError = "Не удалось обновить ленту",
        olderError = "Не удалось загрузить предыдущие сообщения",
    ),
    STARRED(
        starredOnly = true,
        pinnedOnly = false,
        mentionedOnly = false,
        persistent = true,
        title = "Избранное",
        refreshDescription = "Обновить избранные сообщения",
        busyDescription = "Обновление избранных сообщений",
        emptyMessage = "В избранном пока нет сообщений",
        refreshError = "Не удалось обновить избранные сообщения",
        olderError = "Не удалось загрузить предыдущие избранные сообщения",
    ),
    PINNED(
        starredOnly = false,
        pinnedOnly = true,
        mentionedOnly = false,
        persistent = false,
        title = "Отмеченные сообщения",
        refreshDescription = "Обновить отмеченные сообщения",
        busyDescription = "Обновление отмеченных сообщений",
        emptyMessage = "Отмеченных сообщений пока нет",
        refreshError = "Не удалось обновить отмеченные сообщения",
        olderError = "Не удалось загрузить предыдущие отмеченные сообщения",
    ),
    MENTIONS(
        starredOnly = false,
        pinnedOnly = false,
        mentionedOnly = true,
        persistent = false,
        title = "Упоминания",
        refreshDescription = "Обновить упоминания",
        busyDescription = "Обновление упоминаний",
        emptyMessage = "Упоминаний пока нет",
        refreshError = "Не удалось обновить упоминания",
        olderError = "Не удалось загрузить предыдущие упоминания",
    ),
    STREAM(
        starredOnly = false,
        pinnedOnly = false,
        mentionedOnly = false,
        persistent = false,
        title = "Все темы",
        refreshDescription = "Обновить общий поток канала",
        busyDescription = "Обновление общего потока канала",
        emptyMessage = "В канале пока нет сообщений",
        refreshError = "Не удалось обновить общий поток канала",
        olderError = "Не удалось загрузить предыдущие сообщения канала",
    ),
}

data class FeedUiState(
    val ownerKey: String? = null,
    val messages: List<MessageResponse> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingOlder: Boolean = false,
    val hasLoaded: Boolean = false,
    val hasUsableSnapshot: Boolean = false,
    val nextPageMarker: String? = null,
    val error: String? = null,
    val olderError: String? = null,
) {
    val hasMore: Boolean
        get() = nextPageMarker != null
}

internal fun hasDisplayableFeedSnapshot(state: FeedUiState): Boolean =
    state.messages.isNotEmpty() || state.hasUsableSnapshot

internal data class FeedPageValidation(
    val messages: List<MessageResponse>,
    val nextPageMarker: String?,
    val error: String? = null,
)

internal data class FeedProjection(
    val messages: List<MessageResponse>,
    val nextPageMarker: String?,
)

internal data class SequencedMessageProjectionEvent(
    val sequence: Long,
    val event: MessageProjectionEvent,
)

internal fun isMessageProjectionSequenceGap(
    previousSequence: Long?,
    currentSequence: Long,
): Boolean =
    previousSequence != null &&
        currentSequence != previousSequence + 1L

/**
 * The Workspace feed endpoint returns descending pages. UI keeps one
 * chronological list so older pages can be prepended without reversing rows
 * inside an equal-timestamp group.
 */
internal fun validateFeedPage(
    messages: List<MessageResponse>,
    nextMarkerHeader: String?,
    previousMarker: String? = null,
    requireStarred: Boolean = false,
    requirePinned: Boolean = false,
    requireMentioned: Boolean = false,
    requiredStreamUuid: String? = null,
): FeedPageValidation {
    val canonicalRequiredStreamUuid = requiredStreamUuid?.let(::canonicalFeedUuid)
    if (requiredStreamUuid != null && canonicalRequiredStreamUuid == null) {
        return malformedFeedPage()
    }
    val normalizedMessages = messages.map { message ->
        normalizeFeedMessage(message) ?: return malformedFeedPage()
    }
    if (requireStarred && normalizedMessages.any { !it.starred }) {
        return malformedFeedPage()
    }
    if (requirePinned && normalizedMessages.any { !it.pinned }) {
        return malformedFeedPage()
    }
    if (requireMentioned && normalizedMessages.any { !it.mentioned }) {
        return malformedFeedPage()
    }
    if (
        canonicalRequiredStreamUuid != null &&
        normalizedMessages.any { it.streamUuid != canonicalRequiredStreamUuid }
    ) {
        return malformedFeedPage()
    }
    if (
        normalizedMessages
            .map(MessageResponse::uuid)
            .distinct()
            .size != normalizedMessages.size
    ) {
        return malformedFeedPage()
    }

    val rawMarker = nextMarkerHeader?.trim().orEmpty()
    val nextMarker = if (rawMarker.isEmpty()) {
        null
    } else {
        val marker = canonicalFeedUuid(rawMarker) ?: return FeedPageValidation(
            messages = sortFeedMessages(normalizedMessages),
            nextPageMarker = null,
            error = MALFORMED_FEED_PAGE_ERROR,
        )
        if (
            marker == previousMarker ||
            normalizedMessages.lastOrNull()?.uuid != marker
        ) {
            return FeedPageValidation(
                messages = sortFeedMessages(normalizedMessages),
                nextPageMarker = null,
                error = MALFORMED_FEED_PAGE_ERROR,
            )
        }
        marker
    }
    return FeedPageValidation(
        messages = sortFeedMessages(normalizedMessages),
        nextPageMarker = nextMarker,
    )
}

internal fun mergeOlderFeedMessages(
    current: List<MessageResponse>,
    older: List<MessageResponse>,
): List<MessageResponse> {
    val currentUuids = current.mapTo(mutableSetOf(), MessageResponse::uuid)
    return sortFeedMessages(
        current + older.filterNot { it.uuid in currentUuids },
    )
}

internal fun applyFeedProjectionEvents(
    messages: List<MessageResponse>,
    nextPageMarker: String?,
    events: List<SequencedMessageProjectionEvent>,
    requireStarred: Boolean,
    requirePinned: Boolean = false,
    requireMentioned: Boolean = false,
    requiredStreamUuid: String? = null,
    maximumMessages: Int = MAX_FEED_CACHE_MESSAGES,
): FeedProjection {
    require(maximumMessages > 0)
    val canonicalRequiredStreamUuid = requiredStreamUuid?.let(::canonicalFeedUuid)
    require(requiredStreamUuid == null || canonicalRequiredStreamUuid != null)
    return events.fold(
        FeedProjection(
            messages = sortFeedMessages(messages),
            nextPageMarker = nextPageMarker,
        ),
    ) { projection, sequencedEvent ->
        applyFeedProjectionEvent(
            projection = projection,
            event = sequencedEvent.event,
            requireStarred = requireStarred,
            requirePinned = requirePinned,
            requireMentioned = requireMentioned,
            requiredStreamUuid = canonicalRequiredStreamUuid,
            maximumMessages = maximumMessages,
        )
    }
}

private fun applyFeedProjectionEvent(
    projection: FeedProjection,
    event: MessageProjectionEvent,
    requireStarred: Boolean,
    requirePinned: Boolean,
    requireMentioned: Boolean,
    requiredStreamUuid: String?,
    maximumMessages: Int,
): FeedProjection {
    val currentByUuid = projection.messages
        .associateByTo(linkedMapOf(), MessageResponse::uuid)
    var nextMarker = projection.nextPageMarker
    when (event) {
        is MessageProjectionEvent.Upsert -> {
            val incoming = normalizeFeedMessage(event.message)
                ?: return projection
            val existing = currentByUuid[incoming.uuid]
            if (
                existing != null &&
                feedMessageInstant(incoming.updatedAt) <
                feedMessageInstant(existing.updatedAt)
            ) {
                return projection
            }
            if (
                (requireStarred && !incoming.starred) ||
                (requirePinned && !incoming.pinned) ||
                (requireMentioned && !incoming.mentioned) ||
                (requiredStreamUuid != null && incoming.streamUuid != requiredStreamUuid)
            ) {
                currentByUuid.remove(incoming.uuid)
                if (nextMarker == incoming.uuid) {
                    nextMarker = sortFeedMessages(
                        currentByUuid.values.toList(),
                    )
                        .firstOrNull()
                        ?.uuid
                }
            } else {
                currentByUuid[incoming.uuid] = incoming.copy(
                    read = incoming.read || existing?.read == true,
                )
            }
        }

        is MessageProjectionEvent.Read -> {
            val messageUuids = event.messageUuids
                .mapNotNull(::canonicalFeedUuid)
                .toSet()
            messageUuids.forEach { uuid ->
                currentByUuid[uuid]?.let { message ->
                    currentByUuid[uuid] = message.copy(read = true)
                }
            }
        }

        is MessageProjectionEvent.Deleted -> {
            val uuid = canonicalFeedUuid(event.messageUuid)
                ?: return projection
            currentByUuid.remove(uuid)
            if (nextMarker == uuid) {
                nextMarker = sortFeedMessages(
                    currentByUuid.values.toList(),
                )
                    .firstOrNull()
                    ?.uuid
            }
        }
    }
    val sorted = sortFeedMessages(currentByUuid.values.toList())
    val retained = sorted.takeLast(maximumMessages)
    if (retained.size < sorted.size) {
        nextMarker = retained.firstOrNull()?.uuid
    } else if (nextMarker != null) {
        nextMarker = retained.firstOrNull()?.uuid
    }
    return FeedProjection(
        messages = retained,
        nextPageMarker = nextMarker,
    )
}

internal fun feedMessageSummary(
    markdown: String,
    maxLength: Int = 160,
): String {
    require(maxLength > 0)
    val normalized = markdown
        .replace(IMAGE_MARKDOWN, "Изображение")
        .replace(ATTACHMENT_MARKDOWN, "Вложение")
        .replace(FORWARD_QUOTE_URN, "Пересланное сообщение")
        .replace(MARKDOWN_LINK) { match -> match.groupValues[1] }
        .replace(MARKDOWN_DECORATION, "")
        .replace(WHITESPACE, " ")
        .trim()
        .ifBlank { "Сообщение без текста" }
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        "${normalized.take(maxLength - 1).trimEnd()}…"
    }
}

private fun malformedFeedPage() = FeedPageValidation(
    messages = emptyList(),
    nextPageMarker = null,
    error = MALFORMED_FEED_PAGE_ERROR,
)

internal fun sortFeedMessages(
    messages: List<MessageResponse>,
): List<MessageResponse> = messages.sortedWith(
    compareBy<MessageResponse>(
        { feedMessageInstant(it.createdAt) },
        MessageResponse::uuid,
    ),
)

private fun feedMessageInstant(value: String): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrDefault(Instant.EPOCH)

internal fun canonicalFeedUuid(value: String): String? {
    val trimmed = value.trim()
    val canonical = runCatching { UUID.fromString(trimmed).toString() }.getOrNull()
        ?: return null
    return canonical.takeIf { it.equals(trimmed, ignoreCase = true) }
}

internal fun normalizeFeedMessage(message: MessageResponse): MessageResponse? {
    val messageUuid = canonicalFeedUuid(message.uuid) ?: return null
    val streamUuid = canonicalFeedUuid(message.streamUuid) ?: return null
    val topicUuid = canonicalFeedUuid(message.topicUuid) ?: return null
    val userUuid = canonicalFeedUuid(message.userUuid) ?: return null
    val authorUuid = canonicalFeedUuid(message.authorUuid) ?: return null
    runCatching { OffsetDateTime.parse(message.createdAt).toInstant() }
        .getOrNull()
        ?: return null
    runCatching { OffsetDateTime.parse(message.updatedAt).toInstant() }
        .getOrNull()
        ?: return null
    return message.copy(
        uuid = messageUuid,
        streamUuid = streamUuid,
        topicUuid = topicUuid,
        userUuid = userUuid,
        authorUuid = authorUuid,
    )
}

private const val MALFORMED_FEED_PAGE_ERROR =
    "Сервер вернул некорректную страницу ленты"
internal const val MAX_FEED_CACHE_MESSAGES = 500
private val IMAGE_MARKDOWN = Regex("""!\[[^\]]*]\([^)]+\)""")
private val ATTACHMENT_MARKDOWN =
    Regex("""\[[^\]]+]\(urn:(?:image|file):[^)]+\)""")
private val FORWARD_QUOTE_URN =
    Regex("""urn:workspace:message:[0-9a-fA-F-]{36}""")
private val MARKDOWN_LINK = Regex("""\[([^\]]+)]\([^)]+\)""")
private val MARKDOWN_DECORATION = Regex("""[`*_>#~]""")
private val WHITESPACE = Regex("""\s+""")
