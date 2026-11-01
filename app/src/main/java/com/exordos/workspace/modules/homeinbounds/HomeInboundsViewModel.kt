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
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.TopicsRequest
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.modules.chooseserver.QueryState

internal val Stream.hasInboxUnread: Boolean
    get() = activeUnreadCount > 0

internal val TopicsResponseData.hasInboxUnread: Boolean
    get() = notificationMode != "mute" && activeUnreadCount > 0

internal fun firstInboxUnread(messages: List<MessageResponse>, stream: Stream, topic: TopicsResponseData): MessageResponse? =
    messages.asSequence().filter {
        it.streamUuid == stream.uuid && it.topicUuid == topic.uuid &&
            it.isActiveUnread(stream.notificationMode, topic.notificationMode)
    }.minWithOrNull(compareBy<MessageResponse> { WorkspaceDateTime.parse(it.createdAt) }.thenBy { it.uuid })

class HomeInboundsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")
    val streams = eventsRepository.streams
    val streamTopics = eventsRepository.streamTopics

    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState

    init {
        viewModelScope.launch {
            streams.map { all -> all.filter { it.hasInboxUnread } }.distinctUntilChanged().conflate().collect {
                loadTopics(it)
            }
        }
    }

    fun loadTopicsIfNeeded() {
        viewModelScope.launch { loadTopics(streams.value.filter { it.hasInboxUnread }) }
    }

    suspend fun loadTopics(streams: List<Stream>) {
        if (_topicsQueryState.value != QueryState.Success) _topicsQueryState.value = QueryState.Loading
        if (streams.isEmpty()) {
            _topicsQueryState.value = QueryState.Success
            return
        }
        when (val response = client.performRequest(TopicsRequest(streams.map { it.uuid }))) {
            is ApiResult.Success -> {
                val messageIds = response.value.mapNotNull { it.lastMessageUuid }.distinct()
                if (messageIds.isNotEmpty()) {
                    when (val messages = client.performRequest(MessagesByIdsRequest(messageIds))) {
                        is ApiResult.Success -> eventsRepository.updateMessagesPool(messages.value)
                        is ApiResult.Error -> Unit
                    }
                }
                streams.forEach { stream ->
                    val cachedTopics = streamTopics.value[stream.uuid].orEmpty().associateBy { it.uuid }
                    val topics = response.value.filter { it.streamUuid == stream.uuid }.map { topic ->
                        topic.copy(lastMessage = eventsRepository.poolMessage(topic.lastMessageUuid)
                            ?: cachedTopics[topic.uuid]?.lastMessage?.takeIf { it.uuid == topic.lastMessageUuid })
                    }
                    eventsRepository.addStreamTopics(stream.uuid, topics)
                }
                _topicsQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> _topicsQueryState.value = QueryState.Error("")
        }
    }

    suspend fun unreadDestination(stream: Stream, topic: TopicsResponseData): HomeFlow.ChatDialog? {
        val messages = when (val response = eventsRepository.refreshConversation(stream.uuid, topic.uuid)) {
            is ApiResult.Success -> response.value.filter { it.streamUuid == stream.uuid }
            is ApiResult.Error -> return null
        }
        val first = firstInboxUnread(messages, stream, topic)
        return HomeFlow.ChatDialog(stream.displayableName(), stream.uuid, topic.name,
            topic.uuid, stream.isPrivate, null, first?.uuid)
    }
}
