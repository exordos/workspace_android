package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.request.ImageRequest
import io.ktor.client.request.header
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.DisplayRecipient
import ru.genesiscorporation.workspace.beta.MessageDto
import ru.genesiscorporation.workspace.beta.RecipientUser
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.FlatPresense
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DirectMessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EditMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Presense
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersResponseData
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.io.encoding.Base64
import kotlin.text.toInt

class ChatDialogViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val chatTitle: String,
    var chatId: String,
    val topic: String?,
    val isDirectMessages: Boolean,
    private val repo: EventsRepository,
    val userId: Int?
): ViewModel() {

    var items: List<Message> = emptyList()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    fun updateMessage(updated: Message) {
        _messages.update { list ->
            list.map { if (it.id == updated.id)
                it.copy(content = updated?.content ?: it.content)
            else
                it
            }
        }
    }

    private var possibleMessage: Message? = null

    var user: UsersResponseData? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _presense = MutableStateFlow<Presense?>(null)
    val presense: StateFlow<Presense?> = _presense


    private var editingMessage: Message? = null
    private var editingMessageBackupText: String? = null
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri

    fun onMessageChange(newText: String) {
        _messageText.value = newText
    }

    fun onImageUriChange(newUri: Uri?) {
        _imageUri.value = newUri
    }

    fun nextMessageById(currentId: Int?): Message? {
        val i = _messages.value.indexOfFirst { it.id == currentId }
        return if (i >= 0) _messages.value.getOrNull(i + 1) else null
    }

    fun previousMessageById(currentId: Int): Message? {
        val i = _messages.value.indexOfFirst { it.id == currentId }
        return if (i >= 0) _messages.value.getOrNull(i - 1) else null
    }

    fun onEditMessageClicked(message: Message) {
        if (message.id != null) {
            editingMessage = message
            _messageText.value = message.content
        }
    }

    fun onQuoteMessageClicked(message: Message) {
        editingMessage = null
        _messageText.value = "@_**${message.senderFullName}**\n```quote\n${message.content}\n```\n"
    }

    suspend fun onSendClicked(context: Context) {
        val messageId = editingMessage?.id
        if (messageId != null) {
            sendEditMessage(messageId, _messageText.value)
        } else {
            sendMessage(context)
        }
    }

    suspend fun sendMessage(context: Context) {
        val imageUri = _imageUri.value
        if (imageUri != null) {
            val response = client.uploadImage(context, imageUri)
            when(response) {
                is ApiResult.Success -> {
                    var messageText = ""
                    val text = _messageText.value
                    if (!text.isBlank()) {
                        messageText += "$text\r\n"
                    }
                    messageText += "[${response.value.filename}](${response.value.url})"
                    sendTextMessage(messageText)
                }
                is ApiResult.Error -> {

                }
            }
        } else {
            val text = _messageText.value
            if (text.isBlank()) return
            sendTextMessage(text)
        }
    }

    suspend fun sendTextMessage(messageText: String) {
        val userId = userViewModel.repo.userIdFlow.first()
        var newMessage = Message(null,
            userViewModel.userData?.full_name ?: "",
            userId?.toInt() ?: 0,
            messageText,
            (System.currentTimeMillis() / 1000),
            userViewModel.userData?.avatar_url ?: "",
            "",
            true,
            emptyList()
        )
        possibleMessage = newMessage
        _messages.update { current -> current + newMessage }
        _messageText.value = ""
        _imageUri.value = null
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
                possibleMessage = null
            }
            is ApiResult.Error -> {

            }
        }
        _messageText.value = ""
    }

    suspend fun sendEditMessage(messageId: Int, messageText: String) {
        val editMessageRequest = EditMessageRequest(messageId, messageText)
        val response = client.performRequest(editMessageRequest)
        when(response) {
            is ApiResult.Success -> {
                var message = editingMessage?.copy()
                if (message != null) {
                    var newMessage = Message(
                        message.id,
                        message.senderFullName,
                        message.senderId,
                        messageText,
                        message.timestamp,
                        message.avatarUrl,
                        message.subject,
                        message.isFromCurrentUser,
                        emptyList()
                    )
                    updateMessage(newMessage)
                }
                editingMessage = null
                editingMessageBackupText = null
            }
            is ApiResult.Error -> {
                editingMessage?.let { it.content = editingMessageBackupText ?: it.content }
                editingMessage = null
                editingMessageBackupText = null
            }
        }
        _messageText.value = ""
    }

    init {
        _isLoading.value = true
        if (isDirectMessages) {
            user = repo.users.value.firstOrNull { it.userId == userId }
            _presense.value = repo.presences.value[user?.email]
        }
        viewModelScope.launch {
            if (isDirectMessages) {
                loadLatestDirectMessages()
            } else {
                loadLatestMessages()
            }
            repo.messages.collect { updated ->
                processNewMessages(updated)
            }
            repo.presences.collect { updated ->
                if (isDirectMessages) {
                    if (updated[user?.email] != null) {
                        _presense.update { updated[user?.email] }
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.newPresences.collect { updated ->
                processNewPresenses(updated)
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
                _isLoading.value = false
                processUnreadMessages(messages)

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
                _isLoading.value = false
                processUnreadMessages(messages)
            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun processUnreadMessages(messages: List<Message>) {
        val unreadMessageIds = messages.filter { !it.flags.contains("read") }.mapNotNull { it.id }
        if (!unreadMessageIds.isEmpty()) {
            val markMessagesReadResponse = client.performRequest(MarkMessagesReadRequest(unreadMessageIds))
            when(markMessagesReadResponse) {
                is ApiResult.Success -> {
                    if (isDirectMessages && userId != null) {
                        repo.didReadDirectMessages(unreadMessageIds, userId)
                    } else if (topic != null && userId != null) {
                        repo.didReadChannelMessages(unreadMessageIds, userId, topic)
                    }
                }

                is ApiResult.Error -> {

                }
            }
        }
    }

    suspend fun processNewMessages(messages: List<MessageDto>) {
        if (isDirectMessages) {
            processNewDirectMessages(messages)
        } else {
            processNewChannelMessages(messages)
        }
    }

    fun processNewPresenses(presenses: List<FlatPresense>) {
        if (isDirectMessages) {
            val currentUserPresense = presenses.firstOrNull { it.email == user?.email }
            if (currentUserPresense != null) {
                _presense.update { currentUserPresense?.presense }
            }
        }
    }

    suspend fun processNewDirectMessages(messages: List<MessageDto>) {
        val userId = userViewModel.repo.userIdFlow.first()
        val filteredMessageDtos = messages.filter {
            when (val dr = it.displayRecipient) {
                is DisplayRecipient.Users -> isFromCurrentChat(dr.value)
                is DisplayRecipient.StreamName -> false
            }
        }
        val filteredMessages = filteredMessageDtos.map { Message.from(it, userId?.toInt() ?: 0) }
        val newMessages = filteredMessages.filter { message -> !_messages.value.any { it.id == message.id } }.filter { possibleMessage?.content != it.content }
        _messages.update { current -> current + newMessages }
    }

    suspend fun isFromCurrentChat(recipients: List<RecipientUser>): Boolean {
        val userId = userViewModel.repo.userIdFlow.first()
        if (userId != null) {
            val filteredRecipients = recipients.filter { it.id != userId.toInt() }
            val firstRecipient = filteredRecipients.first()
            val possibleStreamId = "[${firstRecipient.id}, ${userId}]"
            return  possibleStreamId == chatId
        } else {
            return  false
        }
    }

    suspend fun processNewChannelMessages(messages: List<MessageDto>) {
        val userId = userViewModel.repo.userIdFlow.first()
        val filteredMessageDtos = messages.filter {
            when (val dr = it.displayRecipient) {
                is DisplayRecipient.Users -> false
                is DisplayRecipient.StreamName ->
                    dr.value == chatTitle && it.subject == topic
            }
        }
        val filteredMessages = filteredMessageDtos.map { Message.from(it, userId?.toInt() ?: 0) }
        val newMessages = filteredMessages.filter { message -> !_messages.value.any { it.id == message.id} }.filter { possibleMessage?.content != it.content }
        _messages.update { current -> current + newMessages }
    }
}

@Serializable
data class Message(
    var id: Int?,
    val senderFullName: String,
    val senderId: Int,
    var content: String,
    val timestamp: Long,
    val avatarUrl: String,
    val subject: String,
    val isFromCurrentUser: Boolean,
    val flags: List<String>
) {
    companion object {
        fun from(messageDto: MessageDto, currentUserId: Int) = Message(
            messageDto.id,
            messageDto.senderFullName,
            messageDto.senderId,
            messageDto.content,
            messageDto.timestamp,
            messageDto.avatarUrl ?: "",
            messageDto.subject,
            messageDto.senderId == currentUserId,
            messageDto.flags
        )
    }
}