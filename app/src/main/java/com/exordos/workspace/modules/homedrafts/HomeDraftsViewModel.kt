package com.exordos.workspace.modules.homedrafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.DeleteDraftRequest
import com.exordos.workspace.data.remote.dto.Draft
import com.exordos.workspace.data.remote.dto.FolderResponseData
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.StreamBindingsRequest
import com.exordos.workspace.data.remote.dto.TopicsByIdsRequest
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.modules.chooseserver.QueryState

class HomeDraftsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val drafts: StateFlow<List<Draft>> = eventsRepository.draftsPool
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.draftsPool.value
        )

    val streams: StateFlow<List<Stream>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.streams.value
        )

    val topicsPool: StateFlow<List<TopicsResponseData>> = eventsRepository.topicsPool
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.topicsPool.value
        )

    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState


    init {
        viewModelScope.launch {
            loadTopicsIfNeeded()
        }
    }

    suspend fun loadTopicsIfNeeded() {
        val draftTopicUuids = drafts.value.map { it.topicUuid }
            .filter { draftUuid ->
                topicsPool.value.firstOrNull { it.uuid == draftUuid } == null
            }
        if (!draftTopicUuids.isEmpty()) {
            _topicsQueryState.value = QueryState.Loading
            val topicsResponse = client.performRequest(TopicsByIdsRequest(draftTopicUuids))
            when(topicsResponse) {
                is ApiResult.Success -> {
                    eventsRepository.appendItemsToTopicsPool(topicsResponse.value)
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
