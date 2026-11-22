package com.exordos.workspace.modules.homementions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.MentionedMessagesRequest
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.remote.dto.TopicsByIdsRequest
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import kotlin.collections.map

class HomeMentionsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    private val _messagesQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val messagesQueryState: StateFlow<QueryState> = _messagesQueryState

    val users: StateFlow<List<UserUIO>> = eventsRepository.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.users.value
        )

    val streams: StateFlow<List<StreamUIO>> = eventsRepository.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = eventsRepository.streams.value
        )

    private val _messages = MutableStateFlow<List<MessageUIO>>(emptyList())
    val messages: StateFlow<List<MessageUIO>> = _messages

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
