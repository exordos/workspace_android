package ru.genesiscorporation.workspace.beta.modules.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatHeader

class UsersViewModel(
    val client: WorkspaceAPIClient
): ViewModel() {
    private val _users = MutableStateFlow<List<UsersResponseData>>(emptyList())
    val users: StateFlow<List<UsersResponseData>> = _users

    init {
        viewModelScope.launch {
            loadUsers()
        }
    }

    suspend fun loadUsers() {
        val response = client.performRequest(UsersRequest())
        when(response) {
            is ApiResult.Success -> {
                _users.value = response.value.members
            }

            is ApiResult.Error -> {

            }
        }
    }
}