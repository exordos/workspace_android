package ru.genesiscorporation.workspace.beta.modules.inbox

import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat

enum class InboxGroupKind {
    DIRECT,
    CHANNEL,
}

sealed interface InboxDestination {
    data class Topic(
        val streamUuid: String,
        val topicUuid: String,
    ) : InboxDestination

    data class Stream(
        val streamUuid: String,
    ) : InboxDestination
}

data class InboxRow(
    val id: String,
    val title: String,
    val unreadCount: Int,
    val updatedAt: String,
    val destination: InboxDestination,
)

data class InboxGroup(
    val streamUuid: String,
    val streamTitle: String,
    val kind: InboxGroupKind,
    val unreadCount: Int,
    val rows: List<InboxRow>,
)

data class InboxSyncState(
    val ownerKey: String? = null,
    val refreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val hasUsableSnapshot: Boolean = false,
    val error: String? = null,
)

internal fun hasDisplayableInboxSnapshot(
    groups: List<InboxGroup>,
    state: InboxSyncState,
): Boolean = groups.isNotEmpty() || state.hasUsableSnapshot

internal enum class InboxCatalogApplyDecision {
    APPLY,
    RETRY,
    FAIL_BUSY,
}

/**
 * Mirrors the maintained desktop Inbox projection:
 * - archived and fully read streams are absent;
 * - unread topics are rows;
 * - a stream-level unread without unread topic data gets one fallback row;
 * - direct conversations and channels remain separate groups.
 *
 * Input order is intentionally preserved because the desktop selector owns
 * catalog ordering.
 */
internal fun buildInboxGroups(
    streams: List<Stream>,
    topicsByStream: Map<String, List<TopicsResponseData>>,
    users: List<UserResponseData>,
): List<InboxGroup> {
    val usersByUuid = users.associateBy(UserResponseData::uuid)
    return streams.mapNotNull { stream ->
        if (stream.isArchived) return@mapNotNull null

        val topics = topicsByStream[stream.uuid].orEmpty()
        val unreadTopics = topics.filter { it.unreadCount > 0 }
        if (stream.unreadCount <= 0 && unreadTopics.isEmpty()) {
            return@mapNotNull null
        }

        val direct = stream.isDirectProviderChat()
        val plainStreamTitle = if (direct) {
            stream.directUserUuid
                ?.let(usersByUuid::get)
                ?.displayableName()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: stream.name.trim().ifEmpty { "Личный чат" }
        } else {
            stream.name.trim().ifEmpty { "Канал" }
        }
        val decoratedStreamTitle =
            if (direct) plainStreamTitle else "#$plainStreamTitle"
        val rows = if (unreadTopics.isNotEmpty()) {
            unreadTopics.map { topic ->
                val topicTitle = topic.name.trim().ifEmpty { "Все сообщения" }
                InboxRow(
                    id = topic.uuid,
                    title = "$decoratedStreamTitle · $topicTitle",
                    unreadCount = topic.unreadCount.coerceAtLeast(0),
                    updatedAt = topic.updatedAt,
                    destination = InboxDestination.Topic(
                        streamUuid = stream.uuid,
                        topicUuid = topic.uuid,
                    ),
                )
            }
        } else {
            listOf(
                InboxRow(
                    id = stream.uuid,
                    title = decoratedStreamTitle,
                    unreadCount = stream.unreadCount.coerceAtLeast(0),
                    updatedAt = stream.updatedAt,
                    destination = InboxDestination.Stream(stream.uuid),
                ),
            )
        }

        InboxGroup(
            streamUuid = stream.uuid,
            streamTitle = decoratedStreamTitle,
            kind = if (direct) InboxGroupKind.DIRECT else InboxGroupKind.CHANNEL,
            unreadCount = stream.unreadCount.coerceAtLeast(0),
            rows = rows,
        )
    }
}

internal fun inboxUnreadCount(groups: List<InboxGroup>): Int =
    groups.fold(0L) { total, group ->
        val rowCount = group.rows.sumOf { it.unreadCount.coerceAtLeast(0).toLong() }
        total + maxOf(group.unreadCount.coerceAtLeast(0).toLong(), rowCount)
    }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun validateInboxCatalog(
    streams: List<Stream>,
    topics: List<TopicsResponseData>,
): String? {
    val streamUuids = streams.map(Stream::uuid)
    if (streamUuids.any(String::isBlank)) return "stream UUID is blank"
    if (streamUuids.toSet().size != streamUuids.size) {
        return "stream UUIDs are not unique"
    }

    val topicUuids = topics.map(TopicsResponseData::uuid)
    if (topicUuids.any(String::isBlank)) return "topic UUID is blank"
    if (topicUuids.toSet().size != topicUuids.size) {
        return "topic UUIDs are not unique"
    }
    val knownStreams = streamUuids.toSet()
    if (topics.any { it.streamUuid.isBlank() || it.streamUuid !in knownStreams }) {
        return "topic references an unknown stream"
    }
    return null
}

internal fun decideInboxCatalogApply(
    catalogChangedDuringRequest: Boolean,
    attempt: Int,
    maxAttempts: Int,
): InboxCatalogApplyDecision {
    require(attempt >= 0)
    require(maxAttempts > 0)
    require(attempt < maxAttempts)
    return when {
        !catalogChangedDuringRequest -> InboxCatalogApplyDecision.APPLY
        attempt + 1 < maxAttempts -> InboxCatalogApplyDecision.RETRY
        else -> InboxCatalogApplyDecision.FAIL_BUSY
    }
}
