package ru.genesiscorporation.workspace.beta.modules.chatdialog

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
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest

class ChatDialogViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val chatTitle: String,
    var chatId: String,
    val topic: String?,
    val isDirectMessages: Boolean
): ViewModel() {

    var items: List<Message> = emptyList()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText

    fun onMessageChange(newText: String) {
        _messageText.value = newText
    }

    suspend fun onSendClicked() {
        val text = _messageText.value
        if (text.isBlank()) return
        sendMessage(text)
    }

    suspend fun sendMessage(messageText: String) {
        val userId = userViewModel.repo.userIdFlow.first()
        var newMessage = Message(null,
            userViewModel.userData?.full_name ?: "",
            userId?.toInt() ?: 0,
            messageText,
            (System.currentTimeMillis() / 1000).toInt(),
            userViewModel.userData?.avatar_url ?: "",
            "",
            true
        )
        _messages.update { current -> current + newMessage }
        val sendMessageRequest = SendMessageRequest(
            type = if (isDirectMessages) "direct" else "stream",
            to = chatId,
            content = messageText,
            topic = if (isDirectMessages) null else topic
        )
        val response = client.performRequest(sendMessageRequest)
        when(response) {
            is ApiResult.Success -> {
                newMessage.id = response.value.id
            }
            is ApiResult.Error -> {

            }
        }
        _messageText.value = ""
    }

    init {
        viewModelScope.launch {
            if (isDirectMessages) {
                loadLatestDirectMessages()
            } else {
                loadLatestMessages()
            }
        }
    }

    suspend fun loadLatestMessages() {
        val narrow: String = "[{\"operand\": \"${chatTitle}\", \"operator\": \"channel\"},{\"operand\": \"${topic ?: ""}\", \"operator\": \"topic\"}]"
        val messagesRequest = MessagesRequest("newest", "100",  "0", narrow)
        val messagesResponse = client.performRequest(messagesRequest)
        val userId = userViewModel.repo.userIdFlow.first()
        when(messagesResponse) {
            is ApiResult.Success -> {
                val messages = messagesResponse.value.messages.map { Message.from(it, userId?.toInt() ?: 0) }
                _messages.value = messages
            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun loadLatestDirectMessages() {
        val narrow: String = "[{\"operand\": ${chatId}, \"operator\": \"dm\"}]"
        val messagesRequest = DirectMessagesRequest("newest", "100", "0", narrow)
        val messagesResponse = client.performRequest(messagesRequest)
        val userId = userViewModel.repo.userIdFlow.first()
        when(messagesResponse) {
            is ApiResult.Success -> {
                val messages = messagesResponse.value.messages.map { Message.from(it, userId?.toInt() ?: 0) }
                _messages.value = messages
            }
            is ApiResult.Error -> {

            }
        }
    }
}

@Serializable
data class Message(
    var id: Int?,
    val senderFullName: String,
    val senderId: Int,
    val content: String,
    val timestamp: Int,
    val avatarUrl: String,
    val subject: String,
    val isFromCurrentUser: Boolean
) {
    companion object {
        fun from(messageData: MessageData, currentUserId: Int) = Message(
            messageData.id,
            messageData.sender_full_name,
            messageData.sender_id,
            messageData.content,
            messageData.timestamp,
            messageData.avatar_url,
            messageData.subject,
            messageData.sender_id == currentUserId
        )

        fun from(messageData: DirectMessageData, currentUserId: Int) = Message(
            messageData.id,
            messageData.sender_full_name,
            messageData.sender_id,
            messageData.content,
            messageData.timestamp,
            messageData.avatar_url,
            messageData.subject,
            messageData.sender_id == currentUserId
        )
    }
}