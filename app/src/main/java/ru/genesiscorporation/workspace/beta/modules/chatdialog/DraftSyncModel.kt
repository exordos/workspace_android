package ru.genesiscorporation.workspace.beta.modules.chatdialog

import ru.genesiscorporation.workspace.beta.data.PersistedDraftConflict
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedDraft
import ru.genesiscorporation.workspace.beta.data.remote.dto.normalizedDraftContent
import java.util.UUID

internal fun beginDraftSync(
    existing: PersistedServerDraftState?,
    localContent: String,
    newUuid: () -> String = { UUID.randomUUID().toString() },
): PersistedServerDraftState? {
    val normalized = localContent.trim()
    if (normalized.isEmpty()) {
        return existing?.copy(
            status = PersistedDraftSyncStatus.DELETING,
            deleteRequested = true,
            conflict = null,
            errorMessage = null,
        )
    }
    normalizedDraftContent(normalized)
    if (existing == null) {
        return PersistedServerDraftState(
            draftUuid = newUuid(),
            pendingCreateContent = normalized,
            status = PersistedDraftSyncStatus.LOCAL,
        )
    }
    if (existing.status == PersistedDraftSyncStatus.CONFLICT) {
        return existing
    }
    val isSynced =
        existing.entityTag != null &&
            normalizeRemoteDraftText(existing.syncedContent.orEmpty()) ==
                normalizeRemoteDraftText(normalized)
    return existing.copy(
        status = if (isSynced) {
            PersistedDraftSyncStatus.SAVED
        } else {
            PersistedDraftSyncStatus.LOCAL
        },
        deleteRequested = false,
        errorMessage = null,
    )
}

internal fun draftCreatePayload(
    state: PersistedServerDraftState,
    localContent: String,
): String =
    normalizedDraftContent(
        state.pendingCreateContent ?: localContent,
    )

internal fun markDraftSaving(
    state: PersistedServerDraftState,
): PersistedServerDraftState =
    state.copy(
        status = if (state.deleteRequested) {
            PersistedDraftSyncStatus.DELETING
        } else {
            PersistedDraftSyncStatus.SAVING
        },
        errorMessage = null,
    )

internal fun applyDraftSaveSuccess(
    state: PersistedServerDraftState,
    server: ValidatedDraft,
    sentContent: String,
    currentLocalContent: String,
): PersistedServerDraftState {
    require(server.response.uuid == state.draftUuid) {
        "Draft response UUID changed"
    }
    val sent = normalizeRemoteDraftText(sentContent)
    val current = normalizeRemoteDraftText(currentLocalContent)
    return state.copy(
        entityTag = server.entityTag,
        serverRevision = server.response.revision,
        syncedContent = sent,
        pendingCreateContent = null,
        serverUpdatedAt = server.response.updatedAt,
        status = when {
            state.deleteRequested -> PersistedDraftSyncStatus.DELETING
            current == sent -> PersistedDraftSyncStatus.SAVED
            else -> PersistedDraftSyncStatus.LOCAL
        },
        conflict = null,
        errorMessage = null,
    )
}

internal fun applyDraftConflict(
    state: PersistedServerDraftState,
    server: ValidatedDraft,
    currentLocalContent: String,
): PersistedServerDraftState {
    require(server.response.uuid == state.draftUuid) {
        "Draft conflict UUID changed"
    }
    val serverContent = normalizeRemoteDraftText(
        server.response.payload.content,
    )
    val localContent = normalizeRemoteDraftText(currentLocalContent)
    if (!state.deleteRequested && localContent == serverContent) {
        return state.copy(
            entityTag = server.entityTag,
            serverRevision = server.response.revision,
            syncedContent = serverContent,
            pendingCreateContent = null,
            serverUpdatedAt = server.response.updatedAt,
            status = PersistedDraftSyncStatus.SAVED,
            conflict = null,
            errorMessage = null,
        )
    }
    return state.copy(
        entityTag = server.entityTag,
        serverRevision = server.response.revision,
        serverUpdatedAt = server.response.updatedAt,
        status = PersistedDraftSyncStatus.CONFLICT,
        conflict = PersistedDraftConflict(
            serverContent = serverContent,
            serverEntityTag = server.entityTag,
            serverRevision = server.response.revision,
            serverUpdatedAt = server.response.updatedAt,
        ),
        errorMessage = null,
    )
}

internal fun canRetryMatchingDraftDeleteConflict(
    state: PersistedServerDraftState,
    server: ValidatedDraft,
): Boolean =
    state.deleteRequested &&
        normalizeRemoteDraftText(state.syncedContent.orEmpty()) ==
            normalizeRemoteDraftText(server.response.payload.content)

internal fun acceptServerDraftVersion(
    state: PersistedServerDraftState,
): Pair<String, PersistedServerDraftState>? {
    val conflict = state.conflict ?: return null
    return conflict.serverContent to state.copy(
        entityTag = conflict.serverEntityTag,
        serverRevision = conflict.serverRevision,
        syncedContent = conflict.serverContent,
        pendingCreateContent = null,
        serverUpdatedAt = conflict.serverUpdatedAt,
        status = PersistedDraftSyncStatus.SAVED,
        conflict = null,
        deleteRequested = false,
        errorMessage = null,
    )
}

internal fun keepLocalDraftVersion(
    state: PersistedServerDraftState,
): PersistedServerDraftState? {
    val conflict = state.conflict ?: return null
    return state.copy(
        entityTag = conflict.serverEntityTag,
        serverRevision = conflict.serverRevision,
        syncedContent = conflict.serverContent,
        pendingCreateContent = null,
        serverUpdatedAt = conflict.serverUpdatedAt,
        status = PersistedDraftSyncStatus.LOCAL,
        conflict = null,
        deleteRequested = false,
        errorMessage = null,
    )
}

internal fun deleteConflictedServerDraft(
    state: PersistedServerDraftState,
): PersistedServerDraftState? {
    val conflict = state.conflict ?: return null
    return state.copy(
        entityTag = conflict.serverEntityTag,
        serverRevision = conflict.serverRevision,
        syncedContent = conflict.serverContent,
        pendingCreateContent = null,
        serverUpdatedAt = conflict.serverUpdatedAt,
        status = PersistedDraftSyncStatus.DELETING,
        conflict = null,
        deleteRequested = true,
        errorMessage = null,
    )
}

internal fun markDraftSyncFailed(
    state: PersistedServerDraftState,
    error: ApiError,
): PersistedServerDraftState =
    state.copy(
        status = PersistedDraftSyncStatus.FAILED,
        errorMessage = safeDraftSyncError(error.message),
    )

internal fun isRetryableDraftError(error: ApiError): Boolean =
    error.httpStatus == 408 ||
        error.kind in setOf(
            ApiErrorKind.NETWORK,
            ApiErrorKind.TIMEOUT,
            ApiErrorKind.MALFORMED_RESPONSE,
            ApiErrorKind.RATE_LIMITED,
            ApiErrorKind.SERVER,
            ApiErrorKind.UNKNOWN,
        )

internal fun normalizeRemoteDraftText(value: String): String = value.trim()

private fun safeDraftSyncError(value: String?): String =
    value
        ?.replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(300)
        ?: "Не удалось синхронизировать черновик"
