package ru.genesiscorporation.workspace.beta.modules.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.MessageProjectionEvent
import ru.genesiscorporation.workspace.beta.data.WorkspaceTimelineKind
import ru.genesiscorporation.workspace.beta.data.WorkspaceTimelineSnapshot
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DEFAULT_MESSAGE_PAGE_SIZE
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageSortDirection
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest

class FeedViewModel(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val eventsRepository: EventsRepository,
    private val kind: MessageTimelineKind = MessageTimelineKind.FEED,
    streamUuid: String? = null,
) : ViewModel() {
    private val requiredStreamUuid = streamUuid?.let(::canonicalFeedUuid)
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state
    private val requestMutex = Mutex()
    private val realtimeJournal = ArrayDeque<SequencedMessageProjectionEvent>()
    private var realtimeSequence = 0L
    private var droppedThroughSequence = 0L
    private var realtimeGapThroughSequence = 0L
    private var lastRepositoryEventSequence: Long? = null
    private var cacheWriteJob: Job? = null

    init {
        require(kind == MessageTimelineKind.STREAM || streamUuid == null) {
            "A stream filter is only valid for a stream timeline"
        }
        require(kind != MessageTimelineKind.STREAM || requiredStreamUuid != null) {
            "A stream timeline requires a canonical stream UUID"
        }
        viewModelScope.launch {
            eventsRepository.messageProjectionEvents.collect { ownedEvent ->
                applyRealtimeEvent(
                    eventOwnerKey = ownedEvent.ownerKey,
                    repositorySequence = ownedEvent.sequence,
                    event = ownedEvent.event,
                )
            }
        }
        viewModelScope.launch {
            requestMutex.lock()
            try {
                restoreAndRefresh()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                refreshInternal()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    fun loadOlder() {
        viewModelScope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                loadOlderInternal()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    private suspend fun restoreAndRefresh() {
        val ownerKey = activeOwnerKey()
        if (ownerKey != null && kind.persistent) {
            resetRealtimeJournal()
            try {
                val cached = userViewModel.workspaceSnapshotStore
                    .readTimeline(ownerKey, workspaceTimelineKind())
                if (cached != null) {
                    userViewModel.repo.withActiveCredentialOwner(ownerKey) {
                        _state.value = FeedUiState(
                            ownerKey = ownerKey,
                            messages = cached.messages,
                            hasLoaded = true,
                            hasUsableSnapshot = true,
                            nextPageMarker = cached.nextPageMarker,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // A missing, obsolete, or damaged cache never blocks REST.
            }
        }
        refreshInternal()
    }

    private suspend fun refreshInternal() {
        val ownerKey = activeOwnerKey() ?: run {
            _state.value = FeedUiState(
                hasLoaded = true,
                error = "Не удалось определить активную учётную запись",
            )
            return
        }
        if (_state.value.ownerKey != ownerKey) {
            resetRealtimeJournal()
        }
        val existing = _state.value.takeIf { it.ownerKey == ownerKey }
        val requestSequence = realtimeSequence
        _state.value = (existing ?: FeedUiState(ownerKey = ownerKey)).copy(
            initialLoading =
                existing?.hasUsableSnapshot != true &&
                    existing?.messages.isNullOrEmpty(),
            refreshing =
                existing?.hasUsableSnapshot == true ||
                    !existing?.messages.isNullOrEmpty(),
            loadingOlder = false,
            error = null,
            olderError = null,
        )

        try {
            when (
                val response = client.performRequest(
                    MessagesRequest(
                        streamId = requiredStreamUuid,
                        pageLimit = FEED_PAGE_SIZE,
                        sortDirection = MessageSortDirection.DESCENDING,
                        starred = true.takeIf { kind.starredOnly },
                        pinned = true.takeIf { kind.pinnedOnly },
                        mentioned = true.takeIf { kind.mentionedOnly },
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (!ownerIsCurrent(ownerKey)) {
                        failForAccountChange()
                        return
                    }
                    val page = validateFeedPage(
                        messages = response.value,
                        nextMarkerHeader = response.metadata.nextPageMarker,
                        requireStarred = kind.starredOnly,
                        requirePinned = kind.pinnedOnly,
                        requireMentioned = kind.mentionedOnly,
                        requiredStreamUuid = requiredStreamUuid,
                    )
                    val prior = _state.value
                    if (page.error != null) {
                        _state.value = if (
                            hasDisplayableFeedSnapshot(prior)
                        ) {
                            prior.copy(
                                initialLoading = false,
                                refreshing = false,
                                loadingOlder = false,
                                hasLoaded = true,
                                error = page.error,
                            )
                        } else {
                            FeedUiState(
                                ownerKey = ownerKey,
                                hasLoaded = true,
                                error = page.error,
                            )
                        }
                        return
                    }
                    val reconciled = replayRealtimeEvents(
                        requestSequence = requestSequence,
                        messages = page.messages,
                        nextPageMarker = page.nextPageMarker,
                    )
                    if (reconciled == null) {
                        retainCurrentAfterRealtimeOverflow(
                            error = REALTIME_OVERFLOW_REFRESH_ERROR,
                        )
                        return
                    }
                    _state.value = FeedUiState(
                        ownerKey = ownerKey,
                        messages = reconciled.messages,
                        hasLoaded = true,
                        hasUsableSnapshot = true,
                        nextPageMarker = reconciled.nextPageMarker,
                    )
                    persistTimeline(ownerKey)
                }

                is ApiResult.Error -> {
                    if (!ownerIsCurrent(ownerKey)) {
                        failForAccountChange()
                        return
                    }
                    _state.value = _state.value.copy(
                        initialLoading = false,
                        refreshing = false,
                        hasLoaded = true,
                        error = feedError(
                            kind.refreshError,
                            response.error.message,
                        ),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (!ownerIsCurrent(ownerKey)) {
                failForAccountChange()
            } else {
                _state.value = _state.value.copy(
                    initialLoading = false,
                    refreshing = false,
                    hasLoaded = true,
                    error = kind.refreshError,
                )
            }
        }
    }

    private suspend fun loadOlderInternal() {
        val current = _state.value
        val ownerKey = current.ownerKey ?: return
        val marker = current.nextPageMarker ?: return
        if (!ownerIsCurrent(ownerKey)) {
            failForAccountChange()
            return
        }
        val requestSequence = realtimeSequence
        _state.value = current.copy(
            loadingOlder = true,
            olderError = null,
        )
        try {
            when (
                val response = client.performRequest(
                    MessagesRequest(
                        streamId = requiredStreamUuid,
                        pageLimit = FEED_PAGE_SIZE,
                        pageMarker = marker,
                        sortDirection = MessageSortDirection.DESCENDING,
                        starred = true.takeIf { kind.starredOnly },
                        pinned = true.takeIf { kind.pinnedOnly },
                        mentioned = true.takeIf { kind.mentionedOnly },
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (
                        !ownerIsCurrent(ownerKey) ||
                        _state.value.ownerKey != ownerKey
                    ) {
                        failForAccountChange()
                        return
                    }
                    val page = validateFeedPage(
                        messages = response.value,
                        nextMarkerHeader = response.metadata.nextPageMarker,
                        previousMarker = marker,
                        requireStarred = kind.starredOnly,
                        requirePinned = kind.pinnedOnly,
                        requireMentioned = kind.mentionedOnly,
                        requiredStreamUuid = requiredStreamUuid,
                    )
                    if (page.error != null) {
                        _state.value = _state.value.copy(
                            loadingOlder = false,
                            olderError = page.error,
                        )
                        return
                    }
                    val reconciled = replayRealtimeEvents(
                        requestSequence = requestSequence,
                        messages = mergeOlderFeedMessages(
                            current = current.messages,
                            older = page.messages,
                        ),
                        nextPageMarker = page.nextPageMarker,
                    )
                    if (reconciled == null) {
                        retainCurrentAfterRealtimeOverflow(
                            olderError = REALTIME_OVERFLOW_OLDER_ERROR,
                        )
                        return
                    }
                    _state.value = _state.value.copy(
                        messages = reconciled.messages,
                        loadingOlder = false,
                        nextPageMarker = reconciled.nextPageMarker,
                        olderError = null,
                    )
                    persistTimeline(ownerKey)
                }

                is ApiResult.Error -> {
                    if (!ownerIsCurrent(ownerKey)) {
                        failForAccountChange()
                        return
                    }
                    _state.value = _state.value.copy(
                        loadingOlder = false,
                        olderError = feedError(
                            kind.olderError,
                            response.error.message,
                        ),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (!ownerIsCurrent(ownerKey)) {
                failForAccountChange()
            } else {
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    olderError = kind.olderError,
                )
            }
        }
    }

    private suspend fun applyRealtimeEvent(
        eventOwnerKey: String,
        repositorySequence: Long,
        event: MessageProjectionEvent,
    ) {
        val current = _state.value
        val ownerKey = current.ownerKey ?: return
        if (eventOwnerKey != ownerKey) return
        if (!ownerIsCurrent(ownerKey)) {
            failForAccountChange()
            return
        }
        val repositoryGap = isMessageProjectionSequenceGap(
            previousSequence = lastRepositoryEventSequence,
            currentSequence = repositorySequence,
        )
        lastRepositoryEventSequence = repositorySequence
        realtimeSequence += 1
        val sequenced = SequencedMessageProjectionEvent(
            sequence = realtimeSequence,
            event = event,
        )
        if (realtimeJournal.size == MAX_REALTIME_JOURNAL_EVENTS) {
            droppedThroughSequence = realtimeJournal.removeFirst().sequence
        }
        realtimeJournal.addLast(sequenced)
        if (repositoryGap) {
            realtimeGapThroughSequence = realtimeSequence
        }
        val projection = applyFeedProjectionEvents(
            messages = current.messages,
            nextPageMarker = current.nextPageMarker,
            events = listOf(sequenced),
            requireStarred = kind.starredOnly,
            requirePinned = kind.pinnedOnly,
            requireMentioned = kind.mentionedOnly,
            requiredStreamUuid = requiredStreamUuid,
        )
        _state.value = current.copy(
            messages = projection.messages,
            nextPageMarker = projection.nextPageMarker,
            error = if (repositoryGap) {
                REALTIME_OVERFLOW_REFRESH_ERROR
            } else {
                current.error
            },
        )
        scheduleTimelineWrite(ownerKey)
        if (repositoryGap && !requestMutex.isLocked) {
            refresh()
        }
    }

    private fun replayRealtimeEvents(
        requestSequence: Long,
        messages: List<MessageResponse>,
        nextPageMarker: String?,
    ): FeedProjection? {
        if (
            requestSequence < droppedThroughSequence ||
            requestSequence < realtimeGapThroughSequence
        ) {
            return null
        }
        return applyFeedProjectionEvents(
            messages = messages,
            nextPageMarker = nextPageMarker,
            events = realtimeJournal.filter { it.sequence > requestSequence },
            requireStarred = kind.starredOnly,
            requirePinned = kind.pinnedOnly,
            requireMentioned = kind.mentionedOnly,
            requiredStreamUuid = requiredStreamUuid,
        )
    }

    private fun retainCurrentAfterRealtimeOverflow(
        error: String? = null,
        olderError: String? = null,
    ) {
        _state.value = _state.value.copy(
            initialLoading = false,
            refreshing = false,
            loadingOlder = false,
            hasLoaded = true,
            error = error,
            olderError = olderError,
        )
    }

    private fun scheduleTimelineWrite(ownerKey: String) {
        if (!kind.persistent) return
        cacheWriteJob?.cancel()
        cacheWriteJob = viewModelScope.launch {
            delay(REALTIME_CACHE_WRITE_DEBOUNCE_MILLIS)
            persistTimeline(ownerKey)
        }
    }

    private suspend fun persistTimeline(ownerKey: String) {
        if (!kind.persistent) return
        try {
            userViewModel.repo.withActiveCredentialOwner(ownerKey) {
                val current = _state.value
                if (current.ownerKey != ownerKey || !current.hasLoaded) {
                    return@withActiveCredentialOwner
                }
                userViewModel.workspaceSnapshotStore.writeTimeline(
                    ownerKey = ownerKey,
                    kind = workspaceTimelineKind(),
                    snapshot = WorkspaceTimelineSnapshot(
                        messages = current.messages,
                        nextPageMarker = current.nextPageMarker,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Cache persistence is best effort and cannot invalidate live data.
        }
    }

    private fun workspaceTimelineKind(): WorkspaceTimelineKind = when (kind) {
        MessageTimelineKind.FEED -> WorkspaceTimelineKind.FEED
        MessageTimelineKind.STARRED -> WorkspaceTimelineKind.STARRED
        MessageTimelineKind.PINNED,
        MessageTimelineKind.MENTIONS,
        MessageTimelineKind.STREAM -> error("Stream timelines are not persisted")
    }

    private fun resetRealtimeJournal() {
        realtimeJournal.clear()
        droppedThroughSequence = realtimeSequence
        realtimeGapThroughSequence = realtimeSequence
        lastRepositoryEventSequence = null
    }

    private suspend fun activeOwnerKey(): String? =
        userViewModel.repo.activeCredentialSnapshot()
            .ownerKey
            ?.takeIf(String::isNotBlank)

    private suspend fun ownerIsCurrent(ownerKey: String): Boolean =
        userViewModel.repo.isActiveCredentialOwner(ownerKey)

    private fun failForAccountChange() {
        cacheWriteJob?.cancel()
        resetRealtimeJournal()
        _state.value = FeedUiState(
            hasLoaded = true,
            error = "Учётная запись изменилась во время загрузки",
        )
    }
}

private fun feedError(summary: String, detail: String?): String {
    val safeDetail = detail
        ?.replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(160)
    return if (safeDetail == null) summary else "$summary: $safeDetail"
}

private const val FEED_PAGE_SIZE = DEFAULT_MESSAGE_PAGE_SIZE
private const val MAX_REALTIME_JOURNAL_EVENTS = 256
private const val REALTIME_CACHE_WRITE_DEBOUNCE_MILLIS = 500L
private const val REALTIME_OVERFLOW_REFRESH_ERROR =
    "Лента изменилась слишком быстро; повторите обновление"
private const val REALTIME_OVERFLOW_OLDER_ERROR =
    "Лента изменилась слишком быстро; повторите загрузку"
