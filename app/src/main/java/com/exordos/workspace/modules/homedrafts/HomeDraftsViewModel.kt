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
import com.exordos.workspace.data.uio.DraftUIO
import com.exordos.workspace.data.uio.FolderUIO
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.remote.dto.StreamBindingsRequest
import com.exordos.workspace.data.remote.dto.TopicsByIdsRequest
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.modules.chooseserver.QueryState

class HomeDraftsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val drafts: StateFlow<List<DraftUIO>> = eventsRepository.drafts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val streams: StateFlow<List<StreamUIO>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val topicsPool: StateFlow<List<TopicUIO>> = eventsRepository.topics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState

    suspend fun deleteCurrentTopicDraft(draft: DraftUIO) {
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
