package com.exordos.workspace.modules.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.UsersRequest
import com.exordos.workspace.data.remote.dto.UserResponseData

class UsersViewModel(
    val client: WorkspaceAPIClient
): ViewModel() {
    private val _users = MutableStateFlow<List<UserResponseData>>(emptyList())
    val users: StateFlow<List<UserResponseData>> = _users

    init {
        viewModelScope.launch {
            loadUsers()
        }
    }

    suspend fun loadUsers() {
        val response = client.performRequest(UsersRequest())
        when(response) {
            is ApiResult.Success -> {
                _users.value = response.value
            }

            is ApiResult.Error -> {

            }
        }
    }
}
