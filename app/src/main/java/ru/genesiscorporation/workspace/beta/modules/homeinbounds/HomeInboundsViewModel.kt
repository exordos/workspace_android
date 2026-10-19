package ru.genesiscorporation.workspace.beta.modules.homeinbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.HomeFlow
import ru.genesiscorporation.workspace.beta.data.readTopicSnapshot
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import java.time.Instant

internal val Stream.hasInboxUnread: Boolean
    get() = activeUnreadCount > 0

internal val TopicsResponseData.hasInboxUnread: Boolean
    get() = notificationMode != "mute" && activeUnreadCount > 0

internal fun firstInboxUnread(messages: List<MessageResponse>, stream: Stream, topic: TopicsResponseData): MessageResponse? =
    messages.asSequence().filter {
        it.streamUuid == stream.uuid && it.topicUuid == topic.uuid &&
            it.isActiveUnread(stream.notificationMode, topic.notificationMode)
    }.minWithOrNull(compareBy<MessageResponse> { Instant.parse(it.createdAt) }.thenBy { it.uuid })

class HomeInboundsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")
    val streams = eventsRepository.streams
    val streamTopics = eventsRepository.streamTopics

    private val topicQueryGeneration = java.util.concurrent.atomic.AtomicLong()
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
        val generation = topicQueryGeneration.incrementAndGet()
        if (_topicsQueryState.value != QueryState.Success) _topicsQueryState.value = QueryState.Loading
        if (streams.isEmpty()) {
            _topicsQueryState.value = QueryState.Success
            return
        }
        val scoped = client.readTopicSnapshot(eventsRepository, TopicsRequest(streams.map { it.uuid }))
        if (generation != topicQueryGeneration.get()) return
        if (scoped.stale) { _topicsQueryState.value = QueryState.Idle; return }
        when (val response = scoped.result) {
            is ApiResult.Success -> {
                val topics = streams.associate { stream -> stream.uuid to response.value.topics.filter { it.streamUuid == stream.uuid } }
                if (eventsRepository.publishTopicSnapshot(scoped.scope, topics, response.value.messages))
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
