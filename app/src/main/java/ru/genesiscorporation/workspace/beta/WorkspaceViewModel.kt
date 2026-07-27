package ru.genesiscorporation.workspace.beta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager
import ru.genesiscorporation.workspace.beta.data.push.PushTokenUpdates
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse

class WorkspaceViewModel(
    val client: WorkspaceAPIClient,
    val repo: EventsRepository,
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager,
): ViewModel() {
    private val _currentCallMessage = MutableStateFlow<MessageResponse?>(null)
    val currentCallMessage: StateFlow<MessageResponse?> = _currentCallMessage

    init {
        viewModelScope.launch {
            client.userViewModel.accessToken.collectLatest { accessToken ->
                if (accessToken != null) {
                    repo.start()
                } else {
                    repo.resetRealtimeCursor()
                }
            }
        }
        viewModelScope.launch {
            client.userViewModel.accessToken.collectLatest { accessToken ->
                if (accessToken != null) {
                    pushDeviceRegistrationManager.registerCurrentTokenWithRetry()
                }
            }
        }
        viewModelScope.launch {
            PushTokenUpdates.tokens.collectLatest { token ->
                if (client.userViewModel.accessToken.value != null) {
                    pushDeviceRegistrationManager.registerTokenWithRetry(token)
                }
            }
        }
    }

    fun setCurrentCallMessage(callMessage: MessageResponse?) {
        _currentCallMessage.value = callMessage
    }
}
