package ru.genesiscorporation.workspace.beta.modules.createstream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddUsersToStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class CreateStreamViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepository: EventsRepository
): ViewModel() {
    val users: StateFlow<List<UserResponseData>> = eventsRepository.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    var createdStream: Stream? = null

    private val _streamName = MutableStateFlow("")
    val streamName: StateFlow<String> = _streamName

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
    fun onStreamNameChange(newText: String) {
        _streamName.value = newText
    }

    suspend fun createStream() {
        _createQueryState.value = QueryState.Loading
        val response = client.performRequest(AddStreamRequest(_streamName.value, "", null))
        when(response) {
            is ApiResult.Success -> {
                val addUsersResponse = client.performRequest(
                    AddUsersToStreamRequest(
                        response.value.uuid,
                        _selectedUserUuids.value
                    )
                )
                when(addUsersResponse) {
                    is ApiResult.Success -> {
                        val newStream = response.value
                        createdStream = newStream
                        _createQueryState.value = QueryState.Success
                    }
                    is ApiResult.Error -> {
                        _createQueryState.value = QueryState.Error(addUsersResponse.error.message ?: "Error")
                    }
                }
            }
            is ApiResult.Error -> {
                _createQueryState.value = QueryState.Error(response.error.message ?: "Error")
            }
        }
    }
}