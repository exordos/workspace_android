package ru.genesiscorporation.workspace.beta.modules.homedrafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.EventsRepository
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
    val eventsRepository: EventsRepository
): ViewModel() {
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