package com.exordos.workspace.modules.homeinbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.HomeFlow
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.remote.dto.TopicsRequest
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

internal val StreamUIO.hasInboxUnread: Boolean
    get() = activeUnreadCount > 0

internal val TopicUIO.hasInboxUnread: Boolean
    get() = notificationMode != "mute" && activeUnreadCount > 0

internal fun firstInboxUnread(messages: List<MessageUIO>, stream: StreamUIO, topic: TopicUIO): MessageUIO? =
    messages.asSequence().filter {
        it.streamUuid == stream.uuid && it.topicUuid == topic.uuid &&
            it.isActiveUnread(stream.notificationMode, topic.notificationMode)
    }.minWithOrNull(compareBy<MessageUIO> { WorkspaceDateTime.parse(it.createdAt) }.thenBy { it.uuid })

class HomeInboundsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")
    val streams: StateFlow<List<StreamUIO>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val topics: StateFlow<List<TopicUIO>> = eventsRepository.topics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState


    suspend fun unreadDestination(stream: StreamUIO, topic: TopicUIO): HomeFlow.ChatDialog? {
        val messages = when (val response = eventsRepository.refreshConversation(stream.uuid, topic.uuid)) {
            is ApiResult.Success -> response.value.filter { it.streamUuid == stream.uuid }
            is ApiResult.Error -> return null
        }
        val first = firstInboxUnread(messages, stream, topic)
        return HomeFlow.ChatDialog(stream.name, stream.uuid, topic.name,
            topic.uuid, stream.isPrivate, null, first?.uuid)
    }
}
