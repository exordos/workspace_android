package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest

class ChooseServerViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel
): ViewModel() {
    private val _serverText = MutableStateFlow("")
    val serverText: StateFlow<String> = _serverText

    fun onServerChange(newText: String) {
        _serverText.value = newText
    }

    suspend fun getServerSettings() {
        val response = client.performRequest(ServerSettingsRequest())
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
            }
            is ApiResult.Error -> {

            }
        }
    }
}