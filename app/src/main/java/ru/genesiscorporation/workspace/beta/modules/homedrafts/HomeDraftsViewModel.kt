package ru.genesiscorporation.workspace.beta.modules.homedrafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.readTopicCache
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Draft
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class HomeDraftsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val drafts: StateFlow<List<Draft>> = eventsRepository.draftsPool
    val streams: StateFlow<List<Stream>> = eventsRepository.streams
    val topicsPool: StateFlow<List<TopicsResponseData>> = eventsRepository.topicsPool

    private val topicQueryGeneration = java.util.concurrent.atomic.AtomicLong()
    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState


    init {
        viewModelScope.launch {
            // Same-owner token refresh must finish its bounded read instead of restarting the observer.
            val owners = eventsRepository.verifiedOwnerAuthentication.map { it?.userUuid }.distinctUntilChanged()
            combine(drafts, owners) { _, ownerUuid -> ownerUuid }
                .collectLatest { ownerUuid ->
                    if (ownerUuid == null) {
                        topicQueryGeneration.incrementAndGet()
                        _topicsQueryState.value = QueryState.Idle
                    } else {
                        loadTopicsIfNeeded()
                    }
                }
        }
    }

    suspend fun loadTopicsIfNeeded() {
        val generation = topicQueryGeneration.incrementAndGet()
        val draftTopicUuids = drafts.value.map { it.topicUuid }
            .filter { draftUuid ->
                topicsPool.value.firstOrNull { it.uuid == draftUuid } == null
            }
        if (!draftTopicUuids.isEmpty()) {
            _topicsQueryState.value = QueryState.Loading
            val scoped = client.readTopicCache(eventsRepository, TopicsByIdsRequest(draftTopicUuids))
            if (generation != topicQueryGeneration.get()) return
            if (scoped.stale) { _topicsQueryState.value = QueryState.Idle; return }
            when(val topicsResponse = scoped.result) {
                is ApiResult.Success -> {
                    if (eventsRepository.appendItemsToTopicsPool(topicsResponse.value, scoped.scope))
                        _topicsQueryState.value = QueryState.Success
                }
                is ApiResult.Error -> {
                    _topicsQueryState.value = QueryState.Error("")
                }
            }
        } else {
            _topicsQueryState.value = QueryState.Success
        }
    }

    suspend fun deleteCurrentTopicDraft(draft: Draft) {
        val response = client.performRequest(DeleteDraftRequest(draft.uuid, draft.revision))
        when (response) {
            is ApiResult.Success -> {
                eventsRepository.removeDraft(draft)
            }

            is ApiResult.Error -> {

            }
        }
    }
}