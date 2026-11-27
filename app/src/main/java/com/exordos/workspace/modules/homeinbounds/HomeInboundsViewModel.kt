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
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.data.uio.toLastMessageUIO
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
    internal val users: StateFlow<List<UserUIO>> = eventsRepository.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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


    init {
        viewModelScope.launch {
            eventsRepository.streams.map { streams -> streams.filter { it.hasInboxUnread } }
                .distinctUntilChanged().conflate().collect(::loadTopics)
        }
    }

    fun retryLoadTopics() {
        viewModelScope.launch { loadTopics(streams.value.filter { it.hasInboxUnread }) }
    }

    private suspend fun loadTopics(unreadStreams: List<StreamUIO>) {
        if (unreadStreams.isEmpty()) {
            _topicsQueryState.value = QueryState.Success
            return
        }
        if (_topicsQueryState.value != QueryState.Success) _topicsQueryState.value = QueryState.Loading
        val serverId = eventsRepository.serverId
        val server = client.requireUserViewModel().getServer(serverId)
            ?.takeIf(eventsRepository::matchesServerConfiguration) ?: return
        val streamUuids = unreadStreams.map { it.uuid }
        when (val response = client.performRequest(TopicsRequest(streamUuids), serverId, server)) {
            is ApiResult.Success -> {
                val topics = response.value.filter { it.streamUuid in streamUuids }
                val messageIds = topics.mapNotNull { it.lastMessageUuid }.distinct()
                for (batch in messageIds.chunked(100)) {
                    val messages = client.performRequest(MessagesByIdsRequest(batch), serverId, server)
                    if (messages is ApiResult.Success) {
                        eventsRepository.addLastMessages(messages.value.filter { it.uuid in batch }.map { it.toLastMessageUIO() })
                    }
                }
                eventsRepository.replaceTopicsForStreams(streamUuids, topics)
                _topicsQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> _topicsQueryState.value = QueryState.Error("")
        }
    }

    suspend fun unreadDestination(stream: StreamUIO, topic: TopicUIO): HomeFlow.ChatDialog? {
        val messages = when (val response = eventsRepository.loadConversationPage(stream.uuid, topic.uuid)) {
            is ApiResult.Success -> response.value
            is ApiResult.Error -> return null
        }
        val first = firstInboxUnread(messages, stream, topic)
        return HomeFlow.ChatDialog(stream.name, stream.uuid, topic.name,
            topic.uuid, stream.isPrivate, null, first?.uuid)
    }
}
