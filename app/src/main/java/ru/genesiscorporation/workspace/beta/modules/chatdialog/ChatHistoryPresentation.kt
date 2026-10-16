package ru.genesiscorporation.workspace.beta.modules.chatdialog

import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatSessionState
import java.time.Instant

internal data class OwnGenerationRequestKey(
    val streamUuid: String,
    val generationUuid: String,
    val viewerUuid: String,
    val deviceId: String,
)

/** One derived snapshot for the list and every service row; never stored in a repository. */
internal data class ChatHistoryPresentation(
    val orderedMessages: List<MessageResponse>?,
    val firstMessageUuid: String?,
    val showInitialKeyRequest: Boolean,
    val latestOwnRequestUuids: Map<OwnGenerationRequestKey, String>,
    val ownGenerationRequests: Set<OwnGenerationRequestKey>,
    val viewerUuid: String?,
    val deviceId: String?,
) {
    fun requestKey(streamUuid: String, generationUuid: String?): OwnGenerationRequestKey? =
        if (generationUuid.isNullOrBlank() || viewerUuid.isNullOrBlank() || deviceId.isNullOrBlank()) null
        else OwnGenerationRequestKey(streamUuid, generationUuid, viewerUuid, deviceId)
}

internal fun deriveChatHistoryPresentation(
    visibleMessages: List<MessageResponse>?,
    messagePool: List<MessageResponse>,
    topicHistories: Map<String, List<MessageResponse>>,
    streamUuid: String,
    encrypted: Boolean,
    viewerUuid: String?,
    encryption: EncryptedChatSessionState,
): ChatHistoryPresentation {
    val ordered = visibleMessages?.distinctBy { it.uuid }
        ?.map { it to Instant.parse(it.createdAt) }
        ?.sortedWith(compareBy<Pair<MessageResponse, Instant>> { it.second }.thenBy { it.first.uuid })
        ?.map { it.first }
    val latest = mutableMapOf<OwnGenerationRequestKey, Pair<Instant, String>>()
    val ownRequests = mutableSetOf<OwnGenerationRequestKey>()
    val deviceId = encryption.deviceId
    if (encrypted && !viewerUuid.isNullOrBlank() && !deviceId.isNullOrBlank()) {
        (messagePool.asSequence() + topicHistories.values.asSequence().flatten() + visibleMessages.orEmpty().asSequence())
            .filter { it.payload.kind == "key_request" && it.authorUuid == viewerUuid && it.payload.deviceId == deviceId }
            .forEach { message ->
                val generation = message.payload.keyUuid?.takeIf { it.isNotBlank() } ?: return@forEach
                val key = OwnGenerationRequestKey(message.streamUuid, generation, viewerUuid, deviceId)
                ownRequests += key
                val instant = runCatching { Instant.parse(message.createdAt) }.getOrNull() ?: return@forEach
                val previous = latest[key]
                if (previous == null || instant > previous.first || (instant == previous.first && message.uuid > previous.second)) {
                    latest[key] = instant to message.uuid
                }
            }
        encryption.requests.forEach { request ->
            val message = request.message
            if (message.authorId == viewerUuid && message.payload.deviceId == deviceId) {
                ownRequests += OwnGenerationRequestKey(message.streamId, message.payload.keyUuid, viewerUuid, deviceId)
            }
        }
    }
    val currentRequest = encryption.currentKeyUuid?.let { generation ->
        if (viewerUuid.isNullOrBlank() || deviceId.isNullOrBlank()) null
        else OwnGenerationRequestKey(streamUuid, generation, viewerUuid, deviceId)
    }
    val visibleCurrentRequest = ordered.orEmpty().any { it.uuid == latest[currentRequest]?.second }
    val visibleCurrentAnnouncement = ordered.orEmpty().any {
        it.payload.kind == "key_announced" && it.payload.keyUuid == encryption.currentKeyUuid
    }
    return ChatHistoryPresentation(
        orderedMessages = ordered,
        firstMessageUuid = ordered?.firstOrNull()?.uuid,
        // A request in another topic cannot provide an action/status row in this conversation.
        showInitialKeyRequest = encrypted && currentRequest != null && !visibleCurrentRequest &&
            (!visibleCurrentAnnouncement || currentRequest in ownRequests),
        latestOwnRequestUuids = latest.mapValues { it.value.second },
        ownGenerationRequests = ownRequests,
        viewerUuid = viewerUuid,
        deviceId = deviceId,
    )
}
