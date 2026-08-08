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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EditMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RemoveMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendDirectMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import java.time.LocalDateTime
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
    val topicUuid: String,
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

    val streamBindings: StateFlow<Map<String, List<StreamBindingResponseData>>> = repo.streamBindings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val users: StateFlow<List<UserResponseData>> = repo.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private var possibleMessage: MessageResponse? = null

    var user: UserResponseData? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    var editingMessage: MessageResponse? = null
    private val _editingMessageBackupText = MutableStateFlow<String?>(null)
    val editingMessageBackupText: StateFlow<String?> = _editingMessageBackupText

    private val _quotedMessage = MutableStateFlow<MessageResponse?>(null)
    val quotedMessage: StateFlow<MessageResponse?> = _quotedMessage

    val mentions = MentionTextFieldState()

    fun onUserSelected(name: String, urn: String) {
        if (!mentions.insertMentionFromAtQuery(name, urn)) {
            mentions.insertMention(name, urn)
        }
    }

//    private val _messageText = MutableStateFlow("")
//    val messageText: StateFlow<String> = _messageText

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri

    var shouldScrollToBottom: Boolean = true

    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun onImageUriChange(newUri: Uri?) {
        _imageUri.value = newUri
    }

    fun nextMessageByUuid(currentUuid: String): MessageResponse? {
        val streamTopicKey = "${chatId}.${topicUuid}"
        val messages = streamTopicMessages.value[streamTopicKey]?.sortedBy { LocalDateTime.parse(it.createdAt, messageFormatter) }
        if (messages != null) {
            val i =
                messages.indexOfFirst { it.uuid == currentUuid }
            return if (i >= 0) messages.getOrNull(i + 1) else null
        } else {
            return null
        }
    }

    fun previousMessageByUuid(currentUuid: String): MessageResponse? {
        val streamTopicKey = "${chatId}.${topicUuid}"
        val messages = streamTopicMessages.value[streamTopicKey]?.sortedBy { LocalDateTime.parse(it.createdAt, messageFormatter) }
        if (messages != null) {
            val i =
                messages.indexOfFirst { it.uuid == currentUuid }
            return if (i >= 0) messages.getOrNull(i - 1) else null
        } else {
            return null
        }
    }

    fun getUser(userUuid: String): UserResponseData? {
        return repo.users.value.firstOrNull { it.uuid == userUuid }
    }

    fun onEditMessageClicked(message: MessageResponse) {
        _quotedMessage.value = null
        if (message.uuid != "") {
            editingMessage = message
            _editingMessageBackupText.value = message.payload.content
            mentions.insertText(message.payload.content)
        }
    }

    fun onQuoteMessageClicked(message: MessageResponse) {
        editingMessage = null
        _editingMessageBackupText.value = null
        _quotedMessage.value = message
    }

    fun onScroll() {
        shouldScrollToBottom = false
    }

    fun clearEditingMessage() {
        _editingMessageBackupText.value = null
        editingMessage = null
        mentions.insertText("")
    }

    fun hasMyReaction(reaction: String, messageUuid: String): Boolean {
        return  !repo.userReactions.value.none { it.emojiName == reaction && it.messageUuid == messageUuid }
    }

    suspend fun onSendClicked(context: Context) {
        val text = mentions.text
        val messageId = editingMessage?.uuid
        if (messageId != null && !text.isBlank()) {
            sendEditMessage(messageId, mentions.text)
        } else {
            sendMessage(context)
        }
    }

    suspend fun sendMessage(context: Context) {
        val imageUri = _imageUri.value
        var messageText = ""
        val currentlyQuotedMessage = _quotedMessage.value
        if (currentlyQuotedMessage != null) {
            messageText += "[${currentlyQuotedMessage.user?.displayableName() ?: ""}](urn:user:${currentlyQuotedMessage.authorUuid}) [said](urn:message:${currentlyQuotedMessage.uuid})\n```quote\n${currentlyQuotedMessage.payload.content}\n```\n"
        }
        if (imageUri != null) {
            val response = client.uploadStreamImage(context, imageUri, chatId)
            when(response) {
                is ApiResult.Success -> {
                    val text = mentions.text
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
            val text = mentions.text
            messageText += text
            if (text.isBlank()) return
            sendTextMessage(messageText)
        }
    }

    suspend fun sendTextMessage(messageText: String) {
        val userId = repo.currentUser.value?.uuid
        var newMessage = MessageResponse(
            "",
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            chatId,
            topicUuid,
            userId ?: "",
            userId ?: "",
            MessageResponsePayload("markdown", messageText),
            true,
            emptyMap()
        )
        newMessage.user = repo.currentUser.value
        repo.updateMessagesPool(listOf(newMessage))
        repo.addMessageToStreamTopic(newMessage)
        possibleMessage = newMessage
        mentions.insertText("")
        _imageUri.value = null
        editingMessage = null
        _editingMessageBackupText.value = null
        _quotedMessage.value = null
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
                repo.updateMessage(newMessage)
                possibleMessage = null
            }

            is ApiResult.Error -> {

            }
        }
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
        mentions.insertText("")
    }

    init {
        _isLoading.value = true
        viewModelScope.launch {
            val key = "${chatId}.${topicUuid}"
            if (streamTopicMessages.value[key]?.isEmpty() ?: true) {
                loadLatestMessages()
            }
            if (streamBindings.value[chatId]?.isEmpty() ?: true) {
                loadStreamBindings()
            }
        }
    }

    suspend fun loadLatestMessages() {
        val messagesRequest = MessagesRequest(chatId, topicUuid)
        val messagesResponse = client.performRequest(messagesRequest)
        when(messagesResponse) {
            is ApiResult.Success -> {
                val lastMessage = messagesResponse.value.sortedBy { LocalDateTime.parse(it.createdAt, messageFormatter) }.lastOrNull()
                if (lastMessage != null) {
                    markMessagesReadUpTo(lastMessage.uuid)
                }
                repo.addStreamTopicMessages(chatId, topicUuid ?: "", messagesResponse.value)
                _isLoading.value = false
            }
            is ApiResult.Error -> {
                _isLoading.value = false
            }
        }
    }

    suspend fun loadStreamBindings() {
        val streamBindingsResponse = client.performRequest(StreamBindingsRequest(chatId))
        when(streamBindingsResponse) {
            is ApiResult.Success -> {
                repo.addStreamBindings(chatId, streamBindingsResponse.value)
            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun markMessagesReadUpTo(messageUuid: String) {
        val markMessagesReadResponse = client.performRequest(MarkMessagesReadRequest(messageUuid))
        when(markMessagesReadResponse) {
            is ApiResult.Success -> {

            }

            is ApiResult.Error -> {

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

    suspend fun onMessageReactionTap(messageUuid: String, emoji: String) {
        val reaction = repo.userReactions.value.firstOrNull { it.emojiName == emoji && it.messageUuid == messageUuid }
        if (reaction != null) {
            val removeReactionResponse =
                client.performRequest(RemoveMessageReactionRequest(reaction.uuid))
            when (removeReactionResponse) {
                is ApiResult.Success -> {

                }

                is ApiResult.Error -> {

                }
            }
        } else {
            val addReactionResponse =
                client.performRequest(AddMessageReactionRequest(messageUuid, emoji))
            when (addReactionResponse) {
                is ApiResult.Success -> {

                }

                is ApiResult.Error -> {

                }
            }
        }
    }
}
