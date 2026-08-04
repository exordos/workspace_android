package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteFcmTokenRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ProfileViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val repo: EventsRepository
): ViewModel() {
    val user: StateFlow<UserResponseData?> = repo.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
    fun currentPushToken(): String? {
        return repo.pushId
    }
    fun logout() {
        val token = currentPushToken()
        if (token != null) {
            viewModelScope.launch {
                deleteToken("workspace:android:$token")
            }
        }
        userViewModel.clearAll()
    }

    suspend fun deleteToken(token: String) {
        client.performRequest(DeleteFcmTokenRequest(token))
    }
}
