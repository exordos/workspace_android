package com.exordos.workspace.modules.chatuserinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.exordos.workspace.R
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.AddStreamRequest
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.uio.MessagePayloadUIO
import com.exordos.workspace.data.remote.dto.SendMessageRequest
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import kotlin.collections.first

class ChatUserInfoViewModel(
    val userName: String,
    val userUuid: String,
    val avatarUrl: String,
    val email: String,
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val repo = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val user: StateFlow<UserUIO> = repo.users
        .map { list -> list.first { it.uuid == userUuid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.users.value.first { it.uuid == userUuid }
        )
    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    var createdStream: StreamUIO? = null

    var shouldSendMessage = false

    suspend fun createPrivateStream(user: UserUIO) {
        _createQueryState.value = QueryState.Loading
        val response = client.performRequest(AddStreamRequest("Direct", "Private workspace", user.uuid))
        when(response) {
            is ApiResult.Success -> {
                val newStream = response.value
                createdStream = newStream
                _createQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _createQueryState.value = QueryState.Error(response.error.message ?: "Error")
            }
        }
    }

    suspend fun sendTextMessage(messageText: String, stream: StreamUIO) {

        val sendMessageRequest = SendMessageRequest(
            stream.uuid,
            stream.defaultTopicUuid ?: "",
            messageText
        )
        val response = client.performRequest(sendMessageRequest)
        when (response) {
            is ApiResult.Success -> {

            }

            is ApiResult.Error -> {

            }
        }
    }
}
