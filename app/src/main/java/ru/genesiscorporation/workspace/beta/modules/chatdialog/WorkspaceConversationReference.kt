package ru.genesiscorporation.workspace.beta.modules.chatdialog

import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import java.util.UUID

internal sealed interface WorkspaceConversationReference {
    data class StreamReference(
        val streamUuid: String,
    ) : WorkspaceConversationReference

    data class TopicReference(
        val topicUuid: String,
        val streamUuid: String? = null,
    ) : WorkspaceConversationReference
}

internal sealed interface OpenWorkspaceConversationEvent {
    data class TopicList(
        val route: ChatFlow.ChatTopic,
    ) : OpenWorkspaceConversationEvent

    data class Dialog(
        val route: ChatFlow.ChatDialog,
    ) : OpenWorkspaceConversationEvent
}

internal fun parseWorkspaceConversationReferenceUrn(
    value: String,
): WorkspaceConversationReference? {
    val parts = value.trim().split(':')
    if (parts.size !in 3..4 || !parts[0].equals("urn", ignoreCase = true)) {
        return null
    }
    return when {
        parts.size == 3 && parts[1].equals("stream", ignoreCase = true) ->
            canonicalWorkspaceReferenceUuid(parts[2])?.let {
                WorkspaceConversationReference.StreamReference(it)
            }

        parts.size == 3 && parts[1].equals("topic", ignoreCase = true) ->
            canonicalWorkspaceReferenceUuid(parts[2])?.let {
                WorkspaceConversationReference.TopicReference(topicUuid = it)
            }

        parts.size == 4 && parts[1].equals("topic", ignoreCase = true) -> {
            val streamUuid = canonicalWorkspaceReferenceUuid(parts[2])
                ?: return null
            val topicUuid = canonicalWorkspaceReferenceUuid(parts[3])
                ?: return null
            WorkspaceConversationReference.TopicReference(
                streamUuid = streamUuid,
                topicUuid = topicUuid,
            )
        }

        else -> null
    }
}

internal fun selectWorkspaceReferenceStream(
    streamUuid: String,
    streams: Collection<Stream>,
): Stream? = streams
    .filter { it.uuid == streamUuid }
    .singleOrNull()
    ?.takeUnless(Stream::isArchived)

internal fun selectWorkspaceReferenceTopic(
    reference: WorkspaceConversationReference.TopicReference,
    topics: Collection<TopicsResponseData>,
): TopicsResponseData? = topics
    .filter { topic ->
        topic.uuid == reference.topicUuid &&
            (
                reference.streamUuid == null ||
                    topic.streamUuid == reference.streamUuid
            )
    }
    .singleOrNull()

internal fun selectWorkspaceReferenceDefaultTopic(
    stream: Stream,
    topics: Collection<TopicsResponseData>,
): TopicsResponseData? {
    val scopedTopics = topics.filter { it.streamUuid == stream.uuid }
    val preferredUuid = stream.defaultTopicUuid
        ?.let(::canonicalWorkspaceReferenceUuid)
    if (preferredUuid != null) {
        val preferredMatches =
            scopedTopics.filter { it.uuid == preferredUuid }
        if (preferredMatches.isNotEmpty()) {
            return preferredMatches.singleOrNull()
        }
    }
    return scopedTopics
        .filter(TopicsResponseData::isDefault)
        .singleOrNull()
}

internal fun buildOpenWorkspaceConversationEvent(
    stream: Stream,
    topic: TopicsResponseData?,
): OpenWorkspaceConversationEvent? {
    if (topic == null) {
        return if (stream.isDirectProviderChat()) {
            null
        } else {
            OpenWorkspaceConversationEvent.TopicList(
                ChatFlow.ChatTopic(
                    channelName = stream.name,
                    channelId = stream.uuid,
                ),
            )
        }
    }
    if (topic.streamUuid != stream.uuid) return null
    val isDirect = stream.isDirectProviderChat()
    return OpenWorkspaceConversationEvent.Dialog(
        ChatFlow.ChatDialog(
            title = stream.name,
            chatId = stream.uuid,
            topicName = topic.name.takeUnless { isDirect },
            topicUuid = topic.uuid,
            isDirectMessages = isDirect,
            userId = null,
        ),
    )
}

private fun canonicalWorkspaceReferenceUuid(value: String): String? {
    val canonical = runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?: return null
    return canonical.takeIf { it == value.lowercase() }
}
