package com.exordos.workspace.modules.ownusersettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.ResetAvatarRequest
import com.exordos.workspace.data.remote.dto.UserResponseData

class OwnUserSettingsViewModel(
    val client: WorkspaceAPIClient,
    private val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val repo = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val user: StateFlow<UserResponseData?> = repo.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    suspend fun resetAvatar() {
        val response = client.performRequest(ResetAvatarRequest(user.value?.uuid ?: ""))
        when(response) {
            is ApiResult.Success -> {

            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun onImageUriChange(newUri: Uri?, context: Context) {
        if (newUri != null) {
            val response = client.uploadAvatarImage(context, newUri, user.value?.uuid ?: "")
            when(response) {
                is ApiResult.Success -> {

                }
                is ApiResult.Error -> {

                }
            }
        }
    }
}
