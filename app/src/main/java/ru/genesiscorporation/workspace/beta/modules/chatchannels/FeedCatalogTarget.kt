package ru.genesiscorporation.workspace.beta.modules.chatchannels

import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import java.util.UUID

internal data class FeedCatalogSelection<T>(
    val value: T? = null,
    val conflicting: Boolean = false,
)

/**
 * Resolves only the canonical stream requested by a Feed row. The catalog may
 * contain stale or foreign rows, but case-variant duplicates must never select
 * an arbitrary destination.
 */
internal fun selectFeedStream(
    streamUuid: String,
    candidates: List<Stream>,
): FeedCatalogSelection<Stream> {
    val target = canonicalUuid(streamUuid)
        ?: return FeedCatalogSelection(conflicting = true)
    val matches = candidates.mapNotNull { stream ->
        if (canonicalUuid(stream.uuid) == target) {
            stream.copy(uuid = target)
        } else {
            null
        }
    }
    return when (matches.size) {
        0 -> FeedCatalogSelection()
        1 -> FeedCatalogSelection(value = matches.single())
        else -> FeedCatalogSelection(conflicting = true)
    }
}

/**
 * Resolves a topic by the stream/topic UUID pair. A same-topic UUID from
 * another stream is deliberately ignored rather than guessed.
 */
internal fun selectFeedTopic(
    streamUuid: String,
    topicUuid: String,
    candidates: List<TopicsResponseData>,
): FeedCatalogSelection<TopicsResponseData> {
    val targetStream = canonicalUuid(streamUuid)
        ?: return FeedCatalogSelection(conflicting = true)
    val targetTopic = canonicalUuid(topicUuid)
        ?: return FeedCatalogSelection(conflicting = true)
    val matches = candidates.mapNotNull { topic ->
        if (
            canonicalUuid(topic.streamUuid) == targetStream &&
            canonicalUuid(topic.uuid) == targetTopic
        ) {
            topic.copy(
                uuid = targetTopic,
                streamUuid = targetStream,
            )
        } else {
            null
        }
    }
    return when (matches.size) {
        0 -> FeedCatalogSelection()
        1 -> FeedCatalogSelection(value = matches.single())
        else -> FeedCatalogSelection(conflicting = true)
    }
}

private fun canonicalUuid(value: String): String? {
    val trimmed = value.trim()
    return runCatching { UUID.fromString(trimmed).toString() }
        .getOrNull()
        ?.takeIf { it.equals(trimmed, ignoreCase = true) }
}
