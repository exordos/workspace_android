package ru.genesiscorporation.workspace.beta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventRegistrationRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest

class WorkspaceViewModel(
    val client: WorkspaceAPIClient
): ViewModel() {
    private var pollingJob: Job? = null

    private var messagesQueueId: String = ""

    suspend fun registerForEvents() {
        val response = client.performRequest(EventRegistrationRequest("[\"messages\"]", null))
        when(response) {
            is ApiResult.Success -> {
                messagesQueueId = response.value.queue_id
            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun startLongPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val messagesResponse = client.performRequest(EventRequest(messagesQueueId, "-1"))
                    when(messagesResponse) {
                        is ApiResult.Success -> {
                            print(messagesResponse.value)
                        }
                        is ApiResult.Error -> {

                        }
                    }
                    delay(10000)
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
    fun stopLongPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    override fun onCleared() {
        stopLongPolling()
    }
}