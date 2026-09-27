package ru.genesiscorporation.workspace.beta.modules.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class HomeViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
//    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    private val currentServerId: Flow<String?> = userViewModel.selectedServerId

    val eventsRepo: StateFlow<EventsRepository?> = combine(
        currentServerId,
        userViewModel.servers,
    ) { id, servers ->
        id?.let { eventsRepositoryStore.getOrCreateForId(it, servers) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streamsQueryState: StateFlow<QueryState> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streamsQueryState ?: flowOf(QueryState.Idle)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueryState.Idle,
        )
    val streams: StateFlow<List<Stream>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streams ?: flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
//        viewModelScope.launch {
//            loadServerSettings()
//        }
    }

    suspend fun loadServerSettings() {
        eventsRepo.value?.loadServerSettings()
    }
}