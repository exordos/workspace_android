package com.exordos.workspace.modules.createdirectstream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.AddStreamRequest
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.creationbase.streamActionAccount

class CreateDirectStreamViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val users: StateFlow<List<UserResponseData>> = combine(eventsRepository.users, eventsRepository.currentUser) { users, current ->
        users.filter { it.uuid != current?.uuid }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    var createdStream: Stream? = null

    fun submitPrivateStream(user: UserResponseData) { viewModelScope.launch { createPrivateStream(user) } }

    suspend fun createPrivateStream(user: UserResponseData) {
        if (_createQueryState.value is QueryState.Loading || _createQueryState.value is QueryState.Success ||
            user.uuid == eventsRepository.currentUser.value?.uuid) return
        val account = streamActionAccount(client, eventsRepository) ?: run {
            _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
            return
        }
        _createQueryState.value = QueryState.Loading
        val response = client.performRequest(AddStreamRequest("Direct", "Private workspace", user.uuid), account.server.id, account.server)
        if (!account.isCurrent()) {
            _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
            return
        }
        when(response) {
            is ApiResult.Success -> {
                val newStream = response.value
                createdStream = newStream
                _createQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _createQueryState.value = QueryState.Error(response.error.errorMessage)
            }
        }
    }
}
