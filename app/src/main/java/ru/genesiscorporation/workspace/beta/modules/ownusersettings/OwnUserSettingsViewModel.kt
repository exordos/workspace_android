package ru.genesiscorporation.workspace.beta.modules.ownusersettings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteFcmTokenRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ResetAvatarRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class OwnUserSettingsViewModel(
    val client: WorkspaceAPIClient,
    private val repo: EventsRepository
): ViewModel() {
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
