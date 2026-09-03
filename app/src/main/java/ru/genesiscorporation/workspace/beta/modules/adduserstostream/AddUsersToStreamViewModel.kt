package ru.genesiscorporation.workspace.beta.modules.adduserstostream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddUsersToStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class AddUsersToStreamViewModel(
    val streamUuid: String,
    val client: WorkspaceAPIClient,
    val eventsRepository: EventsRepository
): ViewModel() {
    val streamBindingUuids: StateFlow<List<String>> = eventsRepository.streamBindings
        .mapNotNull {
                map -> map[streamUuid]
        }
        .map {
                list -> list.map { it.userUuid }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )


    val users: StateFlow<List<UserResponseData>> = eventsRepository.users
        .map { list ->
            list.filter { !streamBindingUuids.value.contains(it.uuid) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedUserUuids = MutableStateFlow<List<String>>(emptyList())

    val selectedUserUuids: StateFlow<List<String>> = _selectedUserUuids.asStateFlow()


    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    fun didTapOnUser(user: UserResponseData) {
        if (_selectedUserUuids.value.contains(user.uuid)) {
            _selectedUserUuids.update { it - user.uuid }
        } else {
            _selectedUserUuids.update { it + user.uuid }
        }
    }

    suspend fun addUsersToStream() {
        _createQueryState.value = QueryState.Loading

        val addUsersResponse = client.performRequest(
            AddUsersToStreamRequest(
                streamUuid,
                _selectedUserUuids.value
            )
        )
        when(addUsersResponse) {
            is ApiResult.Success -> {
                _createQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _createQueryState.value = QueryState.Error(addUsersResponse.error.message ?: "Error")
            }
        }
    }
}
