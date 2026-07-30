package ru.genesiscorporation.workspace.beta.modules.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class UsersViewModel(
    val client: WorkspaceAPIClient
): ViewModel() {
    private val _users = MutableStateFlow<List<UserResponseData>>(emptyList())
    val users: StateFlow<List<UserResponseData>> = _users
    private val _state = MutableStateFlow<QueryState>(QueryState.Idle)
    val state: StateFlow<QueryState> = _state

    init {
        viewModelScope.launch {
            loadUsers()
        }
    }

    suspend fun loadUsers() {
        _state.value = QueryState.Loading
        val response = client.performRequest(UsersRequest())
        when(response) {
            is ApiResult.Success -> {
                _users.value = response.value
                _state.value = QueryState.Success
            }

            is ApiResult.Error -> {
                _state.value = QueryState.Error(
                    response.error.message ?: "Не удалось загрузить пользователей",
                )
            }
        }
    }

    fun retry() {
        if (_state.value is QueryState.Loading) return
        viewModelScope.launch { loadUsers() }
    }
}
