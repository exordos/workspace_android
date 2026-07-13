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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
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
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DirectMessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EditMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Presense
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendDirectMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.io.encoding.Base64
import kotlin.text.toInt

class ChatDialogViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val chatTitle: String,
    var chatId: String,
    val topicName: String?,
    val topicUuid: String?,
    val isDirectMessages: Boolean,
    val repo: EventsRepository,
    val userId: Int?
): ViewModel() {

    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = repo.streamTopicMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    private var possibleMessage: MessageResponse? = null

    var user: UserResponseData? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _presense = MutableStateFlow<Presense?>(null)
    val presense: StateFlow<Presense?> = _presense


    var editingMessage: MessageResponse? = null
    private val _editingMessageBackupText = MutableStateFlow<String?>(null)
    val editingMessageBackupText: StateFlow<String?> = _editingMessageBackupText
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri

    var shouldScrollToBottom: Boolean = true

    fun onMessageChange(newText: String) {
        _messageText.value = newText
    }

    fun onImageUriChange(newUri: Uri?) {
        _imageUri.value = newUri
    }

//    fun nextMessageById(currentId: Int?): Message? {
//        val i = _messages.value.indexOfFirst { it.id == currentId }
//        return if (i >= 0) _messages.value.getOrNull(i + 1) else null
//    }
//
//    fun previousMessageById(currentId: Int): Message? {
//        val i = _messages.value.indexOfFirst { it.id == currentId }
//        return if (i >= 0) _messages.value.getOrNull(i - 1) else null
//    }

    fun onEditMessageClicked(message: MessageResponse) {
        if (message.uuid != "") {
            editingMessage = message
            _editingMessageBackupText.value = message.payload.content
            _messageText.value = message.payload.content
        }
    }

    fun onQuoteMessageClicked(message: MessageResponse) {
        editingMessage = null
        val users = repo.users.value
        val messageUser = users.firstOrNull { it.uuid == message.userUuid }
        _messageText.value = "@_**${messageUser?.username}**\n```quote\n${message.payload.content}\n```\n"
    }

    fun onScroll() {
        shouldScrollToBottom = false
    }

    fun clearEditingMessage() {
        _editingMessageBackupText.value = null
        editingMessage = null
        _messageText.value = ""
    }

    suspend fun onSendClicked(context: Context) {
        val text = _messageText.value
        if (text.isBlank()) return
        val messageId = editingMessage?.uuid
        if (messageId != null) {
            sendEditMessage(messageId, _messageText.value)
        } else {
            sendMessage(context)
        }
    }

    suspend fun sendMessage(context: Context) {
        val imageUri = _imageUri.value
        if (imageUri != null) {
            val response = client.uploadImage(context, imageUri, chatId)
            when(response) {
                is ApiResult.Success -> {
                    var messageText = ""
                    val text = _messageText.value
                    if (!text.isBlank()) {
                        messageText += "$text\r\n"
                    }
                    messageText += "[${response.value.name}](urn:image:${response.value.uuid})"
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
        var newMessage = MessageResponse(
            "",
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            chatId,
            topicUuid ?: "",
            "",
            MessageResponsePayload("markdown", messageText),
            true,
            emptyMap()
        )
        possibleMessage = newMessage
        _messageText.value = ""
        _imageUri.value = null
        if (isDirectMessages) {
            val sendMessageRequest = SendDirectMessageRequest(
                chatId,
                messageText
            )
            val response = client.performRequest(sendMessageRequest)
            when (response) {
                is ApiResult.Success -> {
                    newMessage.uuid = response.value.uuid
                    newMessage.topicUuid = response.value.topicUuid
                    possibleMessage = null
                }

                is ApiResult.Error -> {

                }
            }
        } else {
            val sendMessageRequest = SendMessageRequest(
                chatId,
                topicUuid,
                messageText
            )
            val response = client.performRequest(sendMessageRequest)
            when (response) {
                is ApiResult.Success -> {
                    newMessage.uuid = response.value.uuid
                    newMessage.topicUuid = response.value.topicUuid
                    possibleMessage = null
                }

                is ApiResult.Error -> {

                }
            }
        }
        _messageText.value = ""
    }

    suspend fun sendEditMessage(messageUuid: String, messageText: String) {
        val editMessageRequest = EditMessageRequest(messageUuid, messageText)
        val response = client.performRequest(editMessageRequest)
        when(response) {
            is ApiResult.Success -> {
                editingMessage = null
                _editingMessageBackupText.value = null
            }
            is ApiResult.Error -> {
                editingMessage?.let { it.payload.content = editingMessageBackupText.value ?: it.payload.content }
                editingMessage = null
                _editingMessageBackupText.value = null
            }
        }
        _messageText.value = ""
    }

    init {
        _isLoading.value = true
        viewModelScope.launch {
            val key = "${chatId}.${topicUuid}"
            if (streamTopicMessages.value[key]?.isEmpty() ?: true) {
                loadLatestMessages()
            }
        }
    }

    suspend fun loadLatestMessages() {
        val messagesRequest = MessagesRequest(chatId, topicUuid)
        val messagesResponse = client.performRequest(messagesRequest)
        when(messagesResponse) {
            is ApiResult.Success -> {
                repo.addStreamTopicMessages(chatId, topicUuid ?: "", messagesResponse.value)
                _isLoading.value = false
            }
            is ApiResult.Error -> {
                _isLoading.value = false
            }
        }
    }

    suspend fun onReactionTap(messageUuid: String, emoji: String) {
        val addReactionResponse = client.performRequest(AddMessageReactionRequest(messageUuid, emoji))
        when(addReactionResponse) {
            is ApiResult.Success -> {

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