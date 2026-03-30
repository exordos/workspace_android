package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DirectMessageData
import ru.genesiscorporation.workspace.beta.data.remote.dto.DirectMessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Recipient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Subscription
import ru.genesiscorporation.workspace.beta.data.remote.dto.SubscriptionsRequest

class ChatViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel
): ViewModel() {

    var items: List<ChatHeader> = emptyList()
    private val _subscriptions = MutableStateFlow<List<ChatHeader>>(emptyList())
    val subscriptions: StateFlow<List<ChatHeader>> = _subscriptions

    init {
        viewModelScope.launch {
            loadUserInfo()
        }
    }

    suspend fun loadUserInfo() {
        val response = client.performRequest(OwnUserRequest())
        when(response) {
            is ApiResult.Success -> {
                userViewModel.userData = response.value
                loadSubscribedChannels()
            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun loadSubscribedChannels() {
        val response = client.performRequest(SubscriptionsRequest())
        when(response) {
            is ApiResult.Success -> {
                val subscriptionsResponse = response.value
                val messagesRequest = DirectMessagesRequest(
                    "newest",
                    "100",
                    "0",
                    "[{\"operand\": \"dm\", \"operator\": \"is\"}]"
                )
                val messagesResponse = client.performRequest(messagesRequest)
                when(messagesResponse) {
                    is ApiResult.Success -> {
                        val email = userViewModel.repo.emailFlow.first()
                        val userId = userViewModel.repo.userIdFlow.first()
                        val uniqueRecipientsWithoutUser =
                            messagesResponse.value.messages.flatMap { it.display_recipient }
                                .distinctBy { it.id }
                                .filterNot { it.id == (userId?.toInt() ?: 0) }
                        val dmChatHeaders = uniqueRecipientsWithoutUser.map { user ->
                            val latestMessage = messagesResponse.value.messages
                                .filter { it.display_recipient.contains(user) }
                                .maxByOrNull { it.timestamp }
                            ChatHeader.from(user, latestMessage, userId ?: "" )
                        }
                        _subscriptions.update { current -> current + dmChatHeaders}
                        val channelChatHeaders = subscriptionsResponse.subscriptions.map { ChatHeader.from(it) }
                        _subscriptions.update { current -> current + channelChatHeaders }
                    }


                    is ApiResult.Error -> {

                    }
                }
//                if (userResponse?.subscriptions != null) {
//                    items = userResponse.subscriptions
//                    _subscriptions.value = userResponse.subscriptions
//                }
            }
            is ApiResult.Error -> {

            }
        }
    }
}

@Serializable
data class ChatHeader(
    val title: String,
    val gravatar: String?,
    val narrow: String,
    val streamId: String,
    val lastMessage: DirectMessageData?,
    val isDirectMessages: Boolean
) {
    companion object {
        fun from(subscription: Subscription) = ChatHeader(
            subscription.name,
            null,
            "[{\"operand\": \"${subscription.name}\", \"operator\": \"channel\"}]",
            subscription.stream_id.toString(),
            null,
            isDirectMessages = false
        )

        fun from(recipient: Recipient, lastMessage: DirectMessageData?, currentUserId: String) = ChatHeader(
            recipient.full_name,
            null,
            "[{\"operand\": [${recipient.id}, ${currentUserId}], \"operator\": \"dm\"}]",
            "[${recipient.id}, ${currentUserId}]",
            lastMessage,
            isDirectMessages = true
        )
    }
}
