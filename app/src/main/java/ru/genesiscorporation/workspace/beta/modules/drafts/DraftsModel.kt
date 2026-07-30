package ru.genesiscorporation.workspace.beta.modules.drafts

import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedDraft
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalDraftUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateDraftResponse
import ru.genesiscorporation.workspace.beta.modules.chatdialog.normalizeRemoteDraftText
import ru.genesiscorporation.workspace.beta.modules.chatdialog.applyDraftConflict
import ru.genesiscorporation.workspace.beta.modules.chatdialog.applyDraftSaveSuccess
import java.time.Instant
import java.time.OffsetDateTime

data class DraftListItem(
    val key: String,
    val draftUuid: String?,
    val storageSlot: String?,
    val streamUuid: String,
    val topicUuid: String,
    val content: String,
    val updatedAt: String?,
    val entityTag: String?,
    val serverRevision: Int?,
    val status: PersistedDraftSyncStatus,
    val server: ValidatedDraft? = null,
    val localState: PersistedConversationState? = null,
)

data class DraftPageValidation(
    val drafts: List<ValidatedDraft>,
    val nextPageMarker: String?,
    val error: String? = null,
)

internal fun validateDraftPage(
    responses: List<DraftResponse>,
    nextPageMarkerHeader: String?,
    expectedProjectId: String,
    expectedUserUuid: String,
    previousPageMarker: String? = null,
): DraftPageValidation {
    val drafts = responses.map { response ->
        try {
            validateDraftResponse(
                response = response,
                expectedProjectId = expectedProjectId,
                expectedUserUuid = expectedUserUuid,
            )
        } catch (exception: IllegalArgumentException) {
            return malformedDraftPage()
        }
    }
    if (drafts.map { it.response.uuid }.distinct().size != drafts.size) {
        return malformedDraftPage()
    }
    val expectedOrder = drafts.sortedWith(
        compareByDescending<ValidatedDraft> {
            parseDraftListInstant(it.response.updatedAt)
        }.thenByDescending { it.response.uuid },
    )
    if (drafts.map { it.response.uuid } != expectedOrder.map { it.response.uuid }) {
        return malformedDraftPage()
    }
    val rawMarker = nextPageMarkerHeader?.trim().orEmpty()
    val nextMarker = if (rawMarker.isEmpty()) {
        null
    } else {
        val canonical = runCatching { canonicalDraftUuid(rawMarker) }
            .getOrNull()
            ?: return malformedDraftPage()
        if (
            canonical == previousPageMarker ||
            drafts.lastOrNull()?.response?.uuid != canonical
        ) {
            return malformedDraftPage()
        }
        canonical
    }
    return DraftPageValidation(
        drafts = drafts,
        nextPageMarker = nextMarker,
    )
}

internal fun mergeDraftItems(
    serverDrafts: List<ValidatedDraft>,
    localStates: List<PersistedConversationState>,
): List<DraftListItem> {
    val serverByUuid = serverDrafts.associateBy { it.response.uuid }
    val localByServerUuid = mutableMapOf<String, PersistedConversationState>()
    val result = mutableListOf<DraftListItem>()
    localStates.forEach { local ->
        val route = local.route ?: return@forEach
        val localContent = local.draftText.trim()
        val sync = local.serverDraft
        if (sync != null) {
            if (localByServerUuid.put(sync.draftUuid, local) != null) {
                return@forEach
            }
            val server = serverByUuid[sync.draftUuid]
            val serverAbsentAndUnchanged =
                server == null &&
                    sync.status == PersistedDraftSyncStatus.SAVED &&
                    normalizeRemoteDraftText(localContent) ==
                        normalizeRemoteDraftText(sync.syncedContent.orEmpty())
            if (serverAbsentAndUnchanged) return@forEach
            val content = localContent
                .ifBlank {
                    sync.conflict?.serverContent
                        ?: sync.syncedContent
                        ?: server?.response?.payload?.content
                        ?: ""
                }
            if (content.isBlank() && sync.status != PersistedDraftSyncStatus.FAILED) {
                return@forEach
            }
            result += DraftListItem(
                key = "server:${sync.draftUuid}",
                draftUuid = sync.draftUuid,
                storageSlot = local.draftStorageSlot,
                streamUuid = route.streamUuid,
                topicUuid = route.topicUuid,
                content = content,
                updatedAt = local.draftUpdatedAt
                    ?: sync.serverUpdatedAt
                    ?: server?.response?.updatedAt,
                entityTag = sync.entityTag
                    ?: server?.entityTag,
                serverRevision = sync.serverRevision
                    ?: server?.response?.revision,
                status = sync.status,
                server = server,
                localState = local,
            )
        } else if (localContent.isNotBlank()) {
            result += DraftListItem(
                key = buildString {
                    append("local:${route.streamUuid}:${route.topicUuid}:")
                    append(local.draftStorageSlot ?: "base")
                },
                draftUuid = null,
                storageSlot = local.draftStorageSlot,
                streamUuid = route.streamUuid,
                topicUuid = route.topicUuid,
                content = localContent,
                updatedAt = local.draftUpdatedAt,
                entityTag = null,
                serverRevision = null,
                status = PersistedDraftSyncStatus.LOCAL,
                localState = local,
            )
        }
    }
    serverDrafts.forEach { server ->
        if (server.response.uuid in localByServerUuid) return@forEach
        result += DraftListItem(
            key = "server:${server.response.uuid}",
            draftUuid = server.response.uuid,
            storageSlot = server.response.uuid,
            streamUuid = server.response.streamUuid,
            topicUuid = server.response.topicUuid,
            content = server.response.payload.content,
            updatedAt = server.response.updatedAt,
            entityTag = server.entityTag,
            serverRevision = server.response.revision,
            status = PersistedDraftSyncStatus.SAVED,
            server = server,
        )
    }
    return result
        .distinctBy(DraftListItem::key)
        .sortedWith(
            compareByDescending<DraftListItem> {
                parseDraftListInstant(it.updatedAt)
            }.thenByDescending(DraftListItem::key),
        )
}

internal fun serverDraftState(
    server: ValidatedDraft,
): PersistedServerDraftState =
    PersistedServerDraftState(
        draftUuid = server.response.uuid,
        entityTag = server.entityTag,
        serverRevision = server.response.revision,
        syncedContent = server.response.payload.content,
        serverUpdatedAt = server.response.updatedAt,
        status = PersistedDraftSyncStatus.SAVED,
    )

internal fun reconcilePendingDraftCreate(
    local: PersistedConversationState,
    server: ValidatedDraft,
): PersistedConversationState {
    val route = local.route ?: return local
    val sync = local.serverDraft ?: return local
    if (
        sync.entityTag != null ||
        sync.draftUuid != server.response.uuid ||
        route.streamUuid != server.response.streamUuid ||
        route.topicUuid != server.response.topicUuid
    ) {
        return local
    }
    val sentContent = sync.pendingCreateContent
        ?: sync.syncedContent
        ?: local.draftText
    val nextSync = if (
        normalizeRemoteDraftText(sentContent) ==
        normalizeRemoteDraftText(server.response.payload.content)
    ) {
        applyDraftSaveSuccess(
            state = sync,
            server = server,
            sentContent = sentContent,
            currentLocalContent = local.draftText,
        )
    } else {
        applyDraftConflict(
            state = sync,
            server = server,
            currentLocalContent = local.draftText,
        )
    }
    return local.copy(
        serverDraft = if (
            sync.deleteRequested &&
            nextSync.status == PersistedDraftSyncStatus.DELETING
        ) {
            nextSync.copy(
                status = PersistedDraftSyncStatus.FAILED,
                errorMessage =
                    "Черновик создан на сервере; повторите удаление",
            )
        } else {
            nextSync
        },
    )
}

internal fun parseDraftListInstant(value: String?): Instant =
    value
        ?.let { timestamp ->
            runCatching { OffsetDateTime.parse(timestamp).toInstant() }
                .recoverCatching { Instant.parse(timestamp) }
                .getOrNull()
        }
        ?: Instant.EPOCH

internal fun draftContextLabel(
    streamName: String?,
    topicName: String?,
    isDirectMessages: Boolean,
): String {
    val normalizedStream = streamName
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val normalizedTopic = topicName
        ?.trim()
        ?.takeIf(String::isNotBlank)
    return when {
        isDirectMessages -> normalizedStream ?: "Личный чат"
        normalizedStream == null && normalizedTopic == null -> "Чат"
        normalizedStream == null -> normalizedTopic.orEmpty()
        normalizedTopic == null -> "#$normalizedStream"
        else -> "#$normalizedStream · $normalizedTopic"
    }
}

private fun malformedDraftPage() = DraftPageValidation(
    drafts = emptyList(),
    nextPageMarker = null,
    error = "Сервер вернул некорректную страницу черновиков",
)
