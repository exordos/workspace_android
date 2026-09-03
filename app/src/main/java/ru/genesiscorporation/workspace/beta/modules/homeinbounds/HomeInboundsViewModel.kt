package ru.genesiscorporation.workspace.beta.modules.homeinbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class HomeInboundsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepository: EventsRepository
): ViewModel() {

    val streams: StateFlow<List<Stream>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.streams.value
        )

    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = eventsRepository.streamTopics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.streamTopics.value
        )

    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState

    init {
        loadTopicsIfNeeded()
    }

    fun loadTopicsIfNeeded() {
        val unreadStreams = streams.value.filter { it.unreadCount > 0 }
        val streamWithLoadedTopicsUuids = streamTopics.value.keys
        val unreadStreamsToLoadTopics = unreadStreams.filter { !streamWithLoadedTopicsUuids.contains(it.uuid) }
        if (unreadStreamsToLoadTopics.count() > 0) {
            viewModelScope.launch {
                loadTopics(unreadStreamsToLoadTopics)
            }
        } else {
            _topicsQueryState.value = QueryState.Success
        }
    }

    suspend fun loadTopics(streams: List<Stream>) {
        _topicsQueryState.value = QueryState.Loading
        val streamUuids = streams.map { it.uuid }
        val response = client.performRequest(TopicsRequest(streamUuids))
        when(response) {
            is ApiResult.Success -> {
                for (streamUuid in streamUuids) {
                    val topics = response.value.filter { it.streamUuid == streamUuid }
                    val messageIds = topics.mapNotNull { it.lastMessageUuid }
                    if (!messageIds.isEmpty()) {
                        val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
                        when (messagesResponse) {
                            is ApiResult.Success -> {
                                eventsRepository.updateMessagesPool(messagesResponse.value)
                                val topicsWithMessages = topics.map { topic ->
                                    var updatedTopic = topic
                                    updatedTopic.lastMessage = poolMessage(topic.lastMessageUuid)
                                    updatedTopic
                                }
                                eventsRepository.addStreamTopics(streamUuid, topicsWithMessages)
                                _topicsQueryState.value = QueryState.Success
                            }

                            is ApiResult.Error -> {
                                eventsRepository.addStreamTopics(streamUuid, topics)
                                _topicsQueryState.value = QueryState.Success
                            }
                        }
                    } else {
                        eventsRepository.addStreamTopics(streamUuid, topics)
                        _topicsQueryState.value = QueryState.Success
                    }
                }
            }
            is ApiResult.Error -> {
                _topicsQueryState.value = QueryState.Error("")
            }
        }
    }

    fun poolMessage(uuid: String?): MessageResponse? {
        return eventsRepository.messagesPool.value.firstOrNull { it.uuid == uuid }
    }
}