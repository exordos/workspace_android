package ru.genesiscorporation.workspace.beta.modules.homementions

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
import ru.genesiscorporation.workspace.beta.data.remote.dto.MentionedMessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import kotlin.collections.map

class HomeMentionsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepository: EventsRepository
): ViewModel() {
    private val _messagesQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val messagesQueryState: StateFlow<QueryState> = _messagesQueryState

    val users: StateFlow<List<UserResponseData>> = eventsRepository.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.users.value
        )

    val streams: StateFlow<List<Stream>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.streams.value
        )

    private val _messages = MutableStateFlow<List<MessageResponse>>(emptyList())
    val messages: StateFlow<List<MessageResponse>> = _messages

    init {
        viewModelScope.launch {
            loadMentionedMessages()
        }
    }

    suspend fun loadMentionedMessages() {
        _messagesQueryState.value = QueryState.Loading
        val messagesResponse = client.performRequest(MentionedMessagesRequest())
        when(messagesResponse) {
            is ApiResult.Success -> {
                val messages = messagesResponse.value
                val messagesWithUser = messages.map { message ->
                    message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
                    message
                }
                _messages.value = messagesWithUser
                _messagesQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _messagesQueryState.value = QueryState.Error("")
            }
        }
    }
}