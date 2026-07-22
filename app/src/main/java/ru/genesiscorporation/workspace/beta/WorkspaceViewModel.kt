package ru.genesiscorporation.workspace.beta

import android.util.Patterns
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendFcmTokenRequest
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse

class WorkspaceViewModel(
    val client: WorkspaceAPIClient,
    val repo: EventsRepository
): ViewModel() {
    private val _currentCallMessage = MutableStateFlow<MessageResponse?>(null)
    val currentCallMessage: StateFlow<MessageResponse?> = _currentCallMessage

    fun setCurrentCallMessage(callMessage: MessageResponse?) {
        _currentCallMessage.value = callMessage
    }

    suspend fun sendToken(token: String) {
        client.performRequest(SendFcmTokenRequest(token))
    }
}