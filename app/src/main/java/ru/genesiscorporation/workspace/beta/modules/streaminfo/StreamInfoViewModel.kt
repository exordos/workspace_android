package ru.genesiscorporation.workspace.beta.modules.streaminfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateStreamNotificationModeRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateTopicNotificationModeRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class StreamInfoViewModel(
    val streamUuid: String,
    val topicUuid: String,
    val client: WorkspaceAPIClient,
    val repo: EventsRepository
): ViewModel() {

    val stream: StateFlow<Stream> = repo.streams
        .map { list -> list.first { it.uuid == streamUuid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.streams.value.first { it.uuid == streamUuid }
        )

    val topic: StateFlow<TopicsResponseData?> = repo.streamTopics
        .map { map -> map[streamUuid] }
        .map { list -> list?.first { it.uuid == topicUuid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.streamTopics.value[streamUuid]?.first { it.uuid == topicUuid }
        )

    val streamBindings: StateFlow<Map<String, List<StreamBindingResponseData>>> = repo.streamBindings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val users: StateFlow<List<UserResponseData>> = repo.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    suspend fun setStreamNotificationMode(notificationMode: String) {
        val response = client.performRequest(
            UpdateStreamNotificationModeRequest(
                streamUuid,
                notificationMode
            )
        )
        when(response) {
            is ApiResult.Success -> {

            }
            is ApiResult.Error -> {

            }
        }
    }
}