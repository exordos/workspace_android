package ru.genesiscorporation.workspace.beta.modules.chatdialog

import ru.genesiscorporation.workspace.beta.data.PersistedAttachment
import ru.genesiscorporation.workspace.beta.data.PersistedComposerDraft
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedDraftConflict
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxEntry
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus
import ru.genesiscorporation.workspace.beta.data.PersistedReadBoundary
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalDraftUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.requireStrongDraftEntityTag
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

internal data class OutboxReconciliation(
    val localMessageUuid: String,
    val serverMessage: MessageResponse,
)

internal data class ConversationStateStoragePlan(
    val selectedState: PersistedConversationState,
    val baseState: PersistedConversationState?,
)

internal fun planConversationStateStorage(
    activeState: PersistedConversationState,
    existingBaseState: PersistedConversationState?,
): ConversationStateStoragePlan {
    val slot = activeState.draftStorageSlot
        ?: return ConversationStateStoragePlan(
            selectedState = activeState,
            baseState = null,
        )
    require(canonicalDraftUuid(slot) == slot)
    require(existingBaseState?.draftStorageSlot == null)
    val base = (existingBaseState ?: PersistedConversationState()).copy(
        route = existingBaseState?.route ?: activeState.route,
        draftStorageSlot = null,
        outbox = activeState.outbox,
        pendingReadBoundary = activeState.pendingReadBoundary,
    )
    return ConversationStateStoragePlan(
        selectedState = activeState.copy(
            outbox = emptyList(),
            pendingReadBoundary = null,
        ),
        baseState = base,
    )
}

internal fun PersistedConversationState.hasConversationWork(): Boolean =
    draftText.isNotEmpty() ||
        editingMessageUuid != null ||
        quotedMessageUuid != null ||
        attachments.isNotEmpty() ||
        suspendedDraft != null ||
        outbox.isNotEmpty() ||
        pendingReadBoundary != null ||
        serverDraft != null

internal fun sanitizePersistedConversationState(
    state: PersistedConversationState,
    expectedStreamUuid: String? = null,
    expectedTopicUuid: String? = null,
): PersistedConversationState =
    state.copy(
        draftStorageSlot = state.draftStorageSlot
            ?.let(::canonicalDraftUuid),
        route = state.route
            ?.let(::sanitizeConversationRoute)
            ?.takeIf { route ->
                (
                    expectedStreamUuid == null ||
                        route.streamUuid == expectedStreamUuid
                ) &&
                    (
                        expectedTopicUuid == null ||
                            route.topicUuid == expectedTopicUuid
                    )
            },
        draftText = state.draftText.take(PERSISTED_MESSAGE_CHARS),
        editingMessageUuid = state.editingMessageUuid
            ?.takeIf(::isReasonableIdentifier),
        quotedMessageUuid = state.quotedMessageUuid
            ?.takeIf(::isReasonableIdentifier),
        attachments = sanitizeAttachments(state.attachments),
        suspendedDraft = state.suspendedDraft?.let { draft ->
            PersistedComposerDraft(
                text = draft.text.take(PERSISTED_MESSAGE_CHARS),
                quotedMessageUuid = draft.quotedMessageUuid
                    ?.takeIf(::isReasonableIdentifier),
                attachments = sanitizeAttachments(draft.attachments),
            )
        },
        outbox = state.outbox
            .filter { entry ->
                    entry.localMessageUuid.startsWith("local-") &&
                    isReasonableIdentifier(entry.localMessageUuid) &&
                    isReasonableIdentifier(entry.streamUuid) &&
                    isReasonableIdentifier(entry.topicUuid) &&
                    (
                        expectedStreamUuid == null ||
                            entry.streamUuid == expectedStreamUuid
                    ) &&
                    (
                        expectedTopicUuid == null ||
                            entry.topicUuid == expectedTopicUuid
                    ) &&
                    entry.content.isNotBlank() &&
                    entry.content.length <= PERSISTED_MESSAGE_CHARS &&
                    parseOutboxInstant(entry.createdAt) != null &&
                    parseOutboxInstant(entry.lastAttemptAt) != null
            }
            .takeLast(PERSISTED_OUTBOX_ENTRIES)
            .map { entry ->
                entry.copy(
                    knownMatchingMessageUuids =
                        entry.knownMatchingMessageUuids
                            .filter(::isReasonableIdentifier)
                            .distinct()
                            .takeLast(PERSISTED_KNOWN_MESSAGE_UUIDS),
                    errorMessage = entry.errorMessage
                        ?.take(PERSISTED_ERROR_CHARS),
                )
            },
        pendingReadBoundary = state.pendingReadBoundary
            ?.let(::sanitizeReadBoundary),
        draftUpdatedAt = state.draftUpdatedAt
            ?.takeIf(::isValidDraftTimestamp),
        serverDraft = state.serverDraft?.let(::sanitizeServerDraftState),
        lastIncomingShareRequestId = state.lastIncomingShareRequestId
            ?.takeIf(::isReasonableIdentifier),
    )

private fun sanitizeReadBoundary(
    boundary: PersistedReadBoundary,
): PersistedReadBoundary? {
    val messageUuid = parseCanonicalMessageUuid(boundary.messageUuid)
        ?: return null
    val createdAt = parseOutboxInstant(boundary.createdAt)
        ?: return null
    return PersistedReadBoundary(
        messageUuid = messageUuid,
        createdAt = createdAt.toString(),
    )
}

private fun sanitizeServerDraftState(
    state: PersistedServerDraftState,
): PersistedServerDraftState? = runCatching {
    val draftUuid = canonicalDraftUuid(state.draftUuid)
    val entityTag = state.entityTag?.let(::requireStrongDraftEntityTag)
    val revision = state.serverRevision?.also {
        require(it >= 1)
        require(entityTag == "\"$it\"")
    }
    require((entityTag == null) == (revision == null))
    val syncedContent = state.syncedContent
        ?.trim()
        ?.take(PERSISTED_MESSAGE_CHARS)
        ?.takeIf(String::isNotBlank)
    val pendingCreateContent = state.pendingCreateContent
        ?.trim()
        ?.take(PERSISTED_MESSAGE_CHARS)
        ?.takeIf(String::isNotBlank)
    if (entityTag == null) require(pendingCreateContent != null)
    val serverUpdatedAt = state.serverUpdatedAt
        ?.takeIf(::isValidDraftTimestamp)
    val conflict = state.conflict?.let(::sanitizeDraftConflict)
    val recoveredStatus = when (state.status) {
        PersistedDraftSyncStatus.SAVING,
        PersistedDraftSyncStatus.DELETING,
        -> PersistedDraftSyncStatus.FAILED

        else -> state.status
    }
    require(
        (recoveredStatus == PersistedDraftSyncStatus.CONFLICT) ==
            (conflict != null),
    )
    PersistedServerDraftState(
        draftUuid = draftUuid,
        entityTag = entityTag,
        serverRevision = revision,
        syncedContent = syncedContent,
        pendingCreateContent = pendingCreateContent,
        serverUpdatedAt = serverUpdatedAt,
        status = recoveredStatus,
        conflict = conflict,
        deleteRequested = state.deleteRequested,
        errorMessage = (
            state.errorMessage ?: if (
                state.status == PersistedDraftSyncStatus.SAVING ||
                state.status == PersistedDraftSyncStatus.DELETING
            ) {
                "Приложение было перезапущено до подтверждения синхронизации"
            } else {
                null
            }
        )
            ?.replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
            ?.trim()
            ?.take(PERSISTED_ERROR_CHARS)
            ?.takeIf(String::isNotBlank),
    )
}.getOrNull()

private fun sanitizeDraftConflict(
    conflict: PersistedDraftConflict,
): PersistedDraftConflict? = runCatching {
    require(conflict.serverRevision >= 1)
    val entityTag = requireStrongDraftEntityTag(conflict.serverEntityTag)
    require(entityTag == "\"${conflict.serverRevision}\"")
    val content = conflict.serverContent
        .trim()
        .take(PERSISTED_MESSAGE_CHARS)
        .takeIf(String::isNotBlank)
        ?: return null
    require(isValidDraftTimestamp(conflict.serverUpdatedAt))
    PersistedDraftConflict(
        serverContent = content,
        serverEntityTag = entityTag,
        serverRevision = conflict.serverRevision,
        serverUpdatedAt = conflict.serverUpdatedAt,
    )
}.getOrNull()

private fun isValidDraftTimestamp(value: String): Boolean =
    parseOutboxInstant(value) != null

private fun sanitizeConversationRoute(
    route: PersistedConversationRoute,
): PersistedConversationRoute? {
    val streamUuid = route.streamUuid.takeIf(::isReasonableIdentifier)
        ?: return null
    val topicUuid = route.topicUuid.takeIf(::isReasonableIdentifier)
        ?: return null
    val chatTitle = route.chatTitle
        .trim()
        .take(PERSISTED_ROUTE_NAME_CHARS)
        .takeIf(String::isNotBlank)
        ?: return null
    val topicName = route.topicName
        ?.trim()
        ?.take(PERSISTED_ROUTE_NAME_CHARS)
        ?.takeIf(String::isNotBlank)
    if (!route.isDirectMessages && topicName == null) return null
    return PersistedConversationRoute(
        streamUuid = streamUuid,
        topicUuid = topicUuid,
        chatTitle = chatTitle,
        topicName = topicName,
        isDirectMessages = route.isDirectMessages,
    )
}

private fun sanitizeAttachments(
    attachments: List<PersistedAttachment>,
): List<PersistedAttachment> =
    attachments
        .asSequence()
        .filter { attachment ->
            attachment.uri.isNotBlank() &&
                attachment.uri.length <= PERSISTED_URI_CHARS &&
                attachment.fileName.isNotBlank() &&
                attachment.fileName.length <= PERSISTED_FILE_NAME_CHARS &&
                attachment.contentType.isNotBlank() &&
                attachment.contentType.length <= PERSISTED_CONTENT_TYPE_CHARS &&
                (
                    attachment.sizeBytes == null ||
                        attachment.sizeBytes in 1..PERSISTED_ATTACHMENT_BYTES
                )
        }
        .distinctBy(PersistedAttachment::uri)
        .take(PERSISTED_ATTACHMENTS)
        .toList()

private fun isReasonableIdentifier(value: String): Boolean =
    value.isNotBlank() && value.length <= PERSISTED_IDENTIFIER_CHARS

internal fun isExpectedSendConfirmation(
    entry: PersistedOutboxEntry,
    message: MessageResponse,
): Boolean =
    message.uuid.isNotBlank() &&
        message.isOwn &&
        message.streamUuid == entry.streamUuid &&
        message.topicUuid == entry.topicUuid &&
        message.payload.content == entry.content

internal fun classifyOutboxFailure(error: ApiError): PersistedOutboxStatus =
    if (error.code == "ACCOUNT_CHANGED") {
        // The server may have accepted the mutation before the client rejected
        // its response for belonging to the previous account generation.
        PersistedOutboxStatus.UNCERTAIN
    } else {
        when (error.kind) {
            ApiErrorKind.NETWORK,
            ApiErrorKind.TIMEOUT,
            ApiErrorKind.MALFORMED_RESPONSE,
            ApiErrorKind.SERVER,
            ApiErrorKind.UNKNOWN,
            -> PersistedOutboxStatus.UNCERTAIN

            ApiErrorKind.VALIDATION,
            ApiErrorKind.UNAUTHORIZED,
            ApiErrorKind.FORBIDDEN,
            ApiErrorKind.NOT_FOUND,
            ApiErrorKind.CONFLICT,
            ApiErrorKind.RATE_LIMITED,
            -> PersistedOutboxStatus.FAILED
        }
    }

internal fun interruptedOutboxEntry(
    entry: PersistedOutboxEntry,
): PersistedOutboxEntry =
    if (entry.status == PersistedOutboxStatus.SENDING) {
        entry.copy(
            status = PersistedOutboxStatus.UNCERTAIN,
            errorMessage =
                "Приложение было перезапущено до подтверждения отправки",
        )
    } else {
        entry
    }

internal fun reconcileUncertainOutbox(
    outbox: List<PersistedOutboxEntry>,
    serverMessages: List<MessageResponse>,
): List<OutboxReconciliation> {
    val singleCandidates = outbox.mapNotNull { entry ->
        if (entry.status != PersistedOutboxStatus.UNCERTAIN) {
            return@mapNotNull null
        }
        val attemptedAt =
            parseOutboxInstant(entry.lastAttemptAt) ?: return@mapNotNull null
        val earliest = attemptedAt
        val latest = attemptedAt.plus(RECONCILIATION_WINDOW)
        val candidates = serverMessages.filter { message ->
            val serverCreatedAt = parseOutboxInstant(message.createdAt)
            message.isOwn &&
                message.streamUuid == entry.streamUuid &&
                message.topicUuid == entry.topicUuid &&
                message.payload.content == entry.content &&
                message.uuid !in entry.knownMatchingMessageUuids &&
                serverCreatedAt != null &&
                !serverCreatedAt.isBefore(earliest) &&
                !serverCreatedAt.isAfter(latest)
        }
        candidates.singleOrNull()?.let { serverMessage -> entry to serverMessage }
    }
    val serverUseCount = singleCandidates
        .groupingBy { (_, serverMessage) -> serverMessage.uuid }
        .eachCount()
    return singleCandidates.mapNotNull { (entry, serverMessage) ->
        if (serverUseCount[serverMessage.uuid] != 1) return@mapNotNull null
        OutboxReconciliation(
            localMessageUuid = entry.localMessageUuid,
            serverMessage = serverMessage,
        )
    }
}

private fun parseOutboxInstant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrNull()

private val RECONCILIATION_WINDOW = Duration.ofMinutes(10)
private const val PERSISTED_MESSAGE_CHARS = 40_000
private const val PERSISTED_OUTBOX_ENTRIES = 100
private const val PERSISTED_KNOWN_MESSAGE_UUIDS = 50
private const val PERSISTED_ATTACHMENTS = 10
private const val PERSISTED_ATTACHMENT_BYTES = 25L * 1024L * 1024L
private const val PERSISTED_IDENTIFIER_CHARS = 128
private const val PERSISTED_URI_CHARS = 4_096
private const val PERSISTED_FILE_NAME_CHARS = 255
private const val PERSISTED_CONTENT_TYPE_CHARS = 127
private const val PERSISTED_ERROR_CHARS = 1_000
private const val PERSISTED_ROUTE_NAME_CHARS = 512
