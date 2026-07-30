package ru.genesiscorporation.workspace.beta.modules.drafts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.deleteRemovedOwnedIncomingAttachments
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.CreateDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DEFAULT_DRAFT_PAGE_SIZE
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedDraft
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalDraftUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseDraftConflictBody
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateDraftResponse
import ru.genesiscorporation.workspace.beta.modules.chatdialog.acceptServerDraftVersion
import ru.genesiscorporation.workspace.beta.modules.chatdialog.applyDraftConflict
import ru.genesiscorporation.workspace.beta.modules.chatdialog.applyDraftSaveSuccess
import ru.genesiscorporation.workspace.beta.modules.chatdialog.beginDraftSync
import ru.genesiscorporation.workspace.beta.modules.chatdialog.deleteConflictedServerDraft
import ru.genesiscorporation.workspace.beta.modules.chatdialog.draftCreatePayload
import ru.genesiscorporation.workspace.beta.modules.chatdialog.keepLocalDraftVersion
import ru.genesiscorporation.workspace.beta.modules.chatdialog.markDraftSaving
import ru.genesiscorporation.workspace.beta.modules.chatdialog.markDraftSyncFailed
import ru.genesiscorporation.workspace.beta.modules.chatdialog.sanitizePersistedConversationState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class DraftsUiState(
    val ownerKey: String? = null,
    val items: List<DraftListItem> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
    val busyKeys: Set<String> = emptySet(),
)

class DraftsViewModel(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val conversationStateStore: ConversationStateStore,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(DraftsUiState())
    val state: StateFlow<DraftsUiState> = _state
    private val refreshMutex = Mutex()
    private val mutationMutex = Mutex()
    private var pendingCleanupWarning: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!refreshMutex.tryLock()) return@launch
            try {
                refreshInternal()
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    suspend fun prepareOpen(
        item: DraftListItem,
        route: ChatFlow.ChatDialog,
    ): String? {
        val credentials = userViewModel.repo.activeCredentialSnapshot()
        val ownerKey = credentials.ownerKey?.takeIf(String::isNotBlank)
            ?: return "Не удалось определить активную учётную запись"
        if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
            return "Учётная запись изменилась до открытия черновика"
        }
        return mutationMutex.withLock {
            try {
                val existing = conversationStateStore.read(
                    ownerKey,
                    item.streamUuid,
                    item.topicUuid,
                    item.storageSlot,
                )?.let {
                    sanitizePersistedConversationState(
                        it,
                        item.streamUuid,
                        item.topicUuid,
                    )
                }
                val server = item.server
                val existingSync = existing?.serverDraft
                if (
                    existingSync != null &&
                    item.draftUuid != null &&
                    existingSync.draftUuid != item.draftUuid
                ) {
                    return@withLock "В этом чате найден другой локальный черновик"
                }
                val next = (existing ?: PersistedConversationState()).copy(
                    route = route.persistedRoute(),
                    draftStorageSlot = item.storageSlot,
                    draftText = when {
                        existing?.draftText?.isNotBlank() == true ->
                            existing.draftText
                        server != null -> server.response.payload.content
                        else -> item.content
                    },
                    draftUpdatedAt = existing?.draftUpdatedAt
                        ?: item.updatedAt
                        ?: nowTimestamp(),
                    serverDraft = existingSync
                        ?: server?.let(::serverDraftState),
                )
                conversationStateStore.write(
                    ownerKey,
                    item.streamUuid,
                    item.topicUuid,
                    next,
                    item.storageSlot,
                )
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    "Учётная запись изменилась во время открытия черновика"
                } else {
                    null
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                "Не удалось сохранить черновик перед открытием"
            }
        }
    }

    fun deleteDraft(item: DraftListItem) {
        mutate(item) { owner, project, user ->
            deleteDraftInternal(item, owner, project, user)
        }
    }

    fun acceptServerVersion(item: DraftListItem) {
        mutate(item) { owner, _, _ ->
            val current = readState(owner, item) ?: item.localState
                ?: return@mutate
            val resolution = current.serverDraft
                ?.let(::acceptServerDraftVersion)
                ?: return@mutate
            writeDraftState(
                owner,
                item,
                current.copy(
                    draftText = resolution.first,
                    draftUpdatedAt = nowTimestamp(),
                    serverDraft = resolution.second,
                ),
            )
        }
    }

    fun keepLocalVersion(item: DraftListItem) {
        mutate(item) { owner, project, user ->
            val current = readState(owner, item) ?: item.localState
                ?: return@mutate
            val nextSync = current.serverDraft
                ?.let(::keepLocalDraftVersion)
                ?: return@mutate
            val next = current.copy(serverDraft = nextSync)
            writeDraftState(owner, item, next)
            saveLocalDraftInternal(item, owner, project, user, next)
        }
    }

    fun retryDraft(item: DraftListItem) {
        mutate(item) { owner, project, user ->
            val current = readState(owner, item) ?: item.localState
                ?: return@mutate
            if (current.serverDraft?.deleteRequested == true) {
                deleteDraftInternal(item, owner, project, user)
            } else {
                saveLocalDraftInternal(item, owner, project, user, current)
            }
        }
    }

    private fun mutate(
        item: DraftListItem,
        action: suspend (String, String, String) -> Unit,
    ) {
        if (item.key in _state.value.busyKeys) return
        viewModelScope.launch {
            mutationMutex.withLock {
                refreshMutex.withLock refreshLock@ {
                    _state.value = _state.value.copy(
                        busyKeys = _state.value.busyKeys + item.key,
                        error = null,
                    )
                    try {
                        val credentials =
                            userViewModel.repo.activeCredentialSnapshot()
                        val owner =
                            credentials.ownerKey?.takeIf(String::isNotBlank)
                        val project =
                            credentials.projectId?.takeIf(String::isNotBlank)
                        val user =
                            credentials.userId?.takeIf(String::isNotBlank)
                        if (owner == null || project == null || user == null) {
                            _state.value = _state.value.copy(
                                error =
                                    "Не удалось определить активную учётную запись",
                            )
                            return@refreshLock
                        }
                        action(owner, project, user)
                        if (userViewModel.repo.isActiveCredentialOwner(owner)) {
                            refreshInternal()
                        } else {
                            failForAccountChange()
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        _state.value = _state.value.copy(
                            error = "Не удалось изменить черновик",
                        )
                    } finally {
                        _state.value = _state.value.copy(
                            busyKeys = _state.value.busyKeys - item.key,
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshInternal() {
        val credentials = userViewModel.repo.activeCredentialSnapshot()
        val ownerKey = credentials.ownerKey?.takeIf(String::isNotBlank)
        val projectId = credentials.projectId?.takeIf(String::isNotBlank)
        val userUuid = credentials.userId?.takeIf(String::isNotBlank)
        if (ownerKey == null || projectId == null || userUuid == null) {
            _state.value = DraftsUiState(
                hasLoaded = true,
                error = "Не удалось определить активную учётную запись",
            )
            return
        }
        val prior = _state.value.takeIf { it.ownerKey == ownerKey }
        _state.value = (prior ?: DraftsUiState(ownerKey = ownerKey)).copy(
            initialLoading = prior?.items.isNullOrEmpty(),
            refreshing = !prior?.items.isNullOrEmpty(),
            error = null,
        )
        try {
            val loadedLocalStates = conversationStateStore.list(ownerKey).map {
                sanitizePersistedConversationState(it)
            }
            val localDraftUuids = loadedLocalStates
                .mapNotNull { it.serverDraft?.draftUuid }
            if (localDraftUuids.distinct().size != localDraftUuids.size) {
                retainOrFail(
                    ownerKey,
                    "Локальное хранилище содержит повторяющиеся черновики",
                )
                return
            }
            val serverDrafts = mutableListOf<ValidatedDraft>()
            val seenDraftUuids = mutableSetOf<String>()
            val seenMarkers = mutableSetOf<String>()
            var marker: String? = null
            var pages = 0
            do {
                if (pages >= MAX_DRAFT_PAGES) {
                    retainOrFail(
                        ownerKey,
                        "Список черновиков слишком велик для безопасной загрузки",
                    )
                    return
                }
                val response = client.performRequest(
                    DraftsRequest(
                        pageLimit = DEFAULT_DRAFT_PAGE_SIZE,
                        pageMarker = marker,
                    ),
                )
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    failForAccountChange()
                    return
                }
                when (response) {
                    is ApiResult.Success -> {
                        val page = validateDraftPage(
                            responses = response.value,
                            nextPageMarkerHeader =
                                response.metadata.nextPageMarker,
                            expectedProjectId = projectId,
                            expectedUserUuid = userUuid,
                            previousPageMarker = marker,
                        )
                        if (
                            page.error != null ||
                            page.drafts.any {
                                !seenDraftUuids.add(it.response.uuid)
                            }
                        ) {
                            retainOrFail(
                                ownerKey,
                                page.error
                                    ?: "Сервер вернул повторяющиеся черновики",
                            )
                            return
                        }
                        serverDrafts += page.drafts
                        marker = page.nextPageMarker
                        if (marker != null && !seenMarkers.add(marker)) {
                            retainOrFail(
                                ownerKey,
                                "Сервер зациклил страницы черновиков",
                            )
                            return
                        }
                    }

                    is ApiResult.Error -> {
                        retainOrFail(
                            ownerKey,
                            draftError(
                                "Не удалось обновить черновики",
                                response.error,
                            ),
                        )
                        return
                    }
                }
                pages += 1
            } while (marker != null)
            val serverDraftsByUuid = serverDrafts.associateBy {
                it.response.uuid
            }
            val scopeMismatch = loadedLocalStates.any { local ->
                val route = local.route
                val sync = local.serverDraft
                val server = sync?.draftUuid?.let(serverDraftsByUuid::get)
                route != null &&
                    server != null &&
                    (
                        route.streamUuid != server.response.streamUuid ||
                            route.topicUuid != server.response.topicUuid
                        )
            }
            if (scopeMismatch) {
                retainOrFail(
                    ownerKey,
                    "Сервер вернул черновик с несовпадающим маршрутом",
                )
                return
            }
            val recoveredLocalStates = reconcilePendingDraftCreates(
                ownerKey = ownerKey,
                localStates = loadedLocalStates,
                serverDraftsByUuid = serverDraftsByUuid,
            )
            val localStates = reconcileExternallyDeletedDrafts(
                ownerKey = ownerKey,
                localStates = recoveredLocalStates,
                serverDraftUuids = seenDraftUuids,
            )
            _state.value = DraftsUiState(
                ownerKey = ownerKey,
                items = mergeDraftItems(serverDrafts, localStates),
                hasLoaded = true,
                busyKeys = _state.value.busyKeys,
                error = pendingCleanupWarning.also {
                    pendingCleanupWarning = null
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            retainOrFail(
                ownerKey,
                "Не удалось безопасно прочитать локальные черновики",
            )
        }
    }

    private suspend fun deleteDraftInternal(
        item: DraftListItem,
        ownerKey: String,
        projectId: String,
        userUuid: String,
    ) {
        val current = readState(ownerKey, item) ?: item.localState
            ?: PersistedConversationState(
                route = fallbackRoute(item),
                draftStorageSlot = item.storageSlot,
                draftText = item.content,
                draftUpdatedAt = item.updatedAt,
            )
        val draftUuid = item.draftUuid
        val entityTag = current.serverDraft?.entityTag ?: item.entityTag
        if (draftUuid == null || entityTag == null) {
            writeDraftState(ownerKey, item, clearDraftFields(current))
            return
        }
        val sync = current.serverDraft
            ?: item.server?.let(::serverDraftState)
            ?: PersistedServerDraftState(
                draftUuid = canonicalDraftUuid(draftUuid),
                entityTag = entityTag,
                serverRevision = item.serverRevision,
                syncedContent = item.content,
                serverUpdatedAt = item.updatedAt,
                status = PersistedDraftSyncStatus.SAVED,
            )
        val deleting = (
            if (sync.status == PersistedDraftSyncStatus.CONFLICT) {
                deleteConflictedServerDraft(sync)
            } else {
                sync.copy(
                    status = PersistedDraftSyncStatus.DELETING,
                    deleteRequested = true,
                    conflict = null,
                    errorMessage = null,
                )
            }
        ) ?: return
        val tombstone = current.copy(
            route = current.route ?: fallbackRoute(item),
            draftText = "",
            attachments = emptyList(),
            quotedMessageUuid = null,
            editingMessageUuid = null,
            suspendedDraft = null,
            serverDraft = deleting,
        )
        writeDraftState(ownerKey, item, tombstone)
        when (
            val response = client.performRequest(
                DeleteDraftRequest(
                    draftUuid = deleting.draftUuid,
                    entityTag = deleting.entityTag ?: entityTag,
                ),
            )
        ) {
            is ApiResult.Success -> {
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) return
                writeDraftState(ownerKey, item, clearDraftFields(tombstone))
            }

            is ApiResult.Error -> {
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) return
                if (response.error.httpStatus == 404) {
                    writeDraftState(ownerKey, item, clearDraftFields(tombstone))
                    return
                }
                val conflict = parseDraftConflictBody(
                    response.error.conflictBody,
                    response.error.entityTag,
                    deleting.draftUuid,
                    projectId,
                    userUuid,
                    item.streamUuid,
                    item.topicUuid,
                )
                val nextSync = if (
                    response.error.httpStatus == 412 &&
                    conflict != null
                ) {
                    applyDraftConflict(deleting, conflict, "")
                } else {
                    markDraftSyncFailed(deleting, response.error)
                }
                writeDraftState(
                    ownerKey,
                    item,
                    tombstone.copy(serverDraft = nextSync),
                )
            }
        }
    }

    private suspend fun saveLocalDraftInternal(
        item: DraftListItem,
        ownerKey: String,
        projectId: String,
        userUuid: String,
        initialState: PersistedConversationState,
        allowMissingRecreate: Boolean = true,
    ) {
        var sync = initialState.serverDraft ?: return
        val localContent = initialState.draftText
        if (localContent.isBlank()) return
        val sentContent: String
        val request = if (sync.entityTag == null) {
            sentContent = draftCreatePayload(sync, localContent)
            CreateDraftRequest(
                sync.draftUuid,
                item.streamUuid,
                item.topicUuid,
                sentContent,
            )
        } else {
            sentContent = localContent
            UpdateDraftRequest(
                sync.draftUuid,
                sentContent,
                sync.entityTag,
            )
        }
        sync = markDraftSaving(sync)
        writeDraftState(
            ownerKey,
            item,
            initialState.copy(serverDraft = sync),
        )
        @Suppress("UNCHECKED_CAST")
        val response = when (request) {
            is CreateDraftRequest -> client.performRequest(request)
            is UpdateDraftRequest -> client.performRequest(request)
            else -> return
        }
        if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) return
        when (response) {
            is ApiResult.Success -> {
                val server = try {
                    validateDraftResponse(
                        response.value,
                        expectedDraftUuid = sync.draftUuid,
                        expectedProjectId = projectId,
                        expectedUserUuid = userUuid,
                        expectedStreamUuid = item.streamUuid,
                        expectedTopicUuid = item.topicUuid,
                        responseEntityTag = response.metadata.entityTag,
                    )
                } catch (exception: IllegalArgumentException) {
                    val malformed = ApiError(
                        "Сервер вернул некорректный черновик",
                        "MALFORMED_RESPONSE",
                        ApiErrorKind.MALFORMED_RESPONSE,
                    )
                    writeDraftState(
                        ownerKey,
                        item,
                        initialState.copy(
                            serverDraft = markDraftSyncFailed(sync, malformed),
                        ),
                    )
                    return
                }
                val latest = readState(ownerKey, item) ?: initialState
                val nextSync = applyDraftSaveSuccess(
                    sync,
                    server,
                    sentContent,
                    latest.draftText,
                )
                writeDraftState(
                    ownerKey,
                    item,
                    latest.copy(serverDraft = nextSync),
                )
            }

            is ApiResult.Error -> {
                if (
                    response.error.httpStatus == 404 &&
                    sync.entityTag != null
                ) {
                    val latest = readState(ownerKey, item) ?: initialState
                    val recreated = beginDraftSync(
                        existing = null,
                        localContent = latest.draftText,
                    ) ?: return
                    val recreatedState = latest.copy(serverDraft = recreated)
                    writeDraftState(ownerKey, item, recreatedState)
                    if (allowMissingRecreate) {
                        saveLocalDraftInternal(
                            item,
                            ownerKey,
                            projectId,
                            userUuid,
                            recreatedState,
                            allowMissingRecreate = false,
                        )
                    }
                    return
                }
                val conflict = parseDraftConflictBody(
                    response.error.conflictBody,
                    response.error.entityTag,
                    sync.draftUuid,
                    projectId,
                    userUuid,
                    item.streamUuid,
                    item.topicUuid,
                )
                val nextSync = if (
                    response.error.httpStatus == 412 &&
                    conflict != null
                ) {
                    applyDraftConflict(sync, conflict, localContent)
                } else {
                    markDraftSyncFailed(sync, response.error)
                }
                val latest = readState(ownerKey, item) ?: initialState
                writeDraftState(
                    ownerKey,
                    item,
                    latest.copy(serverDraft = nextSync),
                )
            }
        }
    }

    private suspend fun reconcileExternallyDeletedDrafts(
        ownerKey: String,
        localStates: List<PersistedConversationState>,
        serverDraftUuids: Set<String>,
    ): List<PersistedConversationState> {
        val reconciled = mutableListOf<PersistedConversationState>()
        for (local in localStates) {
            val route = local.route
            val sync = local.serverDraft
            val shouldClear =
                route != null &&
                    sync != null &&
                    sync.draftUuid !in serverDraftUuids &&
                    sync.status == PersistedDraftSyncStatus.SAVED &&
                    local.draftText.trim() ==
                        sync.syncedContent.orEmpty().trim()
            if (!shouldClear) {
                reconciled += local
                continue
            }
            val cleared = clearDraftFields(local)
            if (cleared.hasRetainedConversationWork()) {
                conversationStateStore.write(
                    ownerKey,
                    route.streamUuid,
                    route.topicUuid,
                    cleared,
                    local.draftStorageSlot,
                )
                reconciled += cleared
            } else {
                conversationStateStore.remove(
                    ownerKey,
                    route.streamUuid,
                    route.topicUuid,
                    local.draftStorageSlot,
                )
            }
            if (
                !deleteRemovedOwnedIncomingAttachments(
                    context = appContext,
                    previous = local,
                    current = cleared.takeIf {
                        it.hasRetainedConversationWork()
                    },
                )
            ) {
                pendingCleanupWarning =
                    "Черновик обновлён, но временную копию вложения " +
                        "не удалось очистить"
            }
        }
        return reconciled
    }

    private suspend fun reconcilePendingDraftCreates(
        ownerKey: String,
        localStates: List<PersistedConversationState>,
        serverDraftsByUuid: Map<String, ValidatedDraft>,
    ): List<PersistedConversationState> {
        val reconciled = mutableListOf<PersistedConversationState>()
        for (local in localStates) {
            val route = local.route
            val server = local.serverDraft
                ?.draftUuid
                ?.let(serverDraftsByUuid::get)
            val next = if (server == null) {
                local
            } else {
                reconcilePendingDraftCreate(local, server)
            }
            if (next != local && route != null) {
                conversationStateStore.write(
                    ownerKey,
                    route.streamUuid,
                    route.topicUuid,
                    next,
                    local.draftStorageSlot,
                )
            }
            reconciled += next
        }
        return reconciled
    }

    private suspend fun readState(
        ownerKey: String,
        item: DraftListItem,
    ): PersistedConversationState? =
        conversationStateStore.read(
            ownerKey,
            item.streamUuid,
            item.topicUuid,
            item.storageSlot,
        )?.let {
            sanitizePersistedConversationState(
                it,
                item.streamUuid,
                item.topicUuid,
            )
        }

    private suspend fun writeDraftState(
        ownerKey: String,
        item: DraftListItem,
        state: PersistedConversationState,
    ) {
        val previous = conversationStateStore.read(
            ownerKey,
            item.streamUuid,
            item.topicUuid,
            item.storageSlot,
        )
        if (state.hasRetainedConversationWork()) {
            conversationStateStore.write(
                ownerKey,
                item.streamUuid,
                item.topicUuid,
                state,
                item.storageSlot,
            )
        } else {
            conversationStateStore.remove(
                ownerKey,
                item.streamUuid,
                item.topicUuid,
                item.storageSlot,
            )
        }
        if (
            !deleteRemovedOwnedIncomingAttachments(
                context = appContext,
                previous = previous,
                current = state.takeIf {
                    it.hasRetainedConversationWork()
                },
            )
        ) {
            pendingCleanupWarning =
                "Черновик обновлён, но временную копию вложения " +
                    "не удалось очистить"
        }
    }

    private fun retainOrFail(ownerKey: String, error: String) {
        val prior = _state.value.takeIf { it.ownerKey == ownerKey }
        _state.value = if (prior?.items?.isNotEmpty() == true) {
            prior.copy(
                initialLoading = false,
                refreshing = false,
                hasLoaded = true,
                error = error,
            )
        } else {
            DraftsUiState(
                ownerKey = ownerKey,
                hasLoaded = true,
                error = error,
                busyKeys = _state.value.busyKeys,
            )
        }
    }

    private fun failForAccountChange() {
        _state.value = DraftsUiState(
            hasLoaded = true,
            error = "Учётная запись изменилась во время загрузки черновиков",
        )
    }
}

private fun ChatFlow.ChatDialog.persistedRoute() =
    PersistedConversationRoute(
        streamUuid = chatId,
        topicUuid = topicUuid,
        chatTitle = title.trim().take(512),
        topicName = topicName?.trim()?.take(512),
        isDirectMessages = isDirectMessages,
    )

private fun fallbackRoute(item: DraftListItem) =
    PersistedConversationRoute(
        streamUuid = item.streamUuid,
        topicUuid = item.topicUuid,
        chatTitle = "Чат",
        topicName = "Топик",
        isDirectMessages = false,
    )

private fun clearDraftFields(
    state: PersistedConversationState,
): PersistedConversationState =
    state.copy(
        draftText = "",
        editingMessageUuid = null,
        quotedMessageUuid = null,
        attachments = emptyList(),
        suspendedDraft = null,
        draftUpdatedAt = null,
        serverDraft = null,
    )

private fun PersistedConversationState.hasRetainedConversationWork(): Boolean =
    draftText.isNotEmpty() ||
        editingMessageUuid != null ||
        quotedMessageUuid != null ||
        attachments.isNotEmpty() ||
        suspendedDraft != null ||
        outbox.isNotEmpty() ||
        serverDraft != null

private fun nowTimestamp(): String =
    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

private fun draftError(summary: String, error: ApiError): String {
    val safe = error.message
        ?.replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(160)
    return if (safe == null) summary else "$summary: $safe"
}

private const val MAX_DRAFT_PAGES = 100
