package com.exordos.workspace

import android.util.Patterns
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.uio.MessageUIO

class WorkspaceViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
//    val repo = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")
    private val _currentCallMessage = MutableStateFlow<MessageUIO?>(null)
    val currentCallMessage: StateFlow<MessageUIO?> = _currentCallMessage

    fun setCurrentCallMessage(callMessage: MessageUIO?) {
        _currentCallMessage.value = callMessage
    }


}
