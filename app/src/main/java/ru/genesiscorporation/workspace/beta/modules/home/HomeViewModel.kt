package ru.genesiscorporation.workspace.beta.modules.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class HomeViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepository: EventsRepository
): ViewModel() {

    val streamsQueryState: StateFlow<QueryState> = eventsRepository.streamsQueryState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueryState.Idle
        )

    val streams: StateFlow<List<Stream>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.streams.value
        )

    init {
        viewModelScope.launch {
            loadServerSettings()
        }
    }

    suspend fun loadServerSettings() {
        eventsRepository.loadServerSettings()
    }
}