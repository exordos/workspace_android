package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Draft
import ru.genesiscorporation.workspace.beta.data.remote.dto.EditMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadUpToRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RemoveMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.io.encoding.Base64
import kotlin.text.toInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatDialogViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val chatTitle: String,
    var chatId: String,
    val topicName: String?,
    val topicUuid: String,
    val isDirectMessages: Boolean,
    val repo: EventsRepository,
    val userId: Int?,
    val storage: AttachmentStorage
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
    val stream: StateFlow<Stream> = repo.streams
        .map { list -> list.first { it.uuid == chatId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.streams.value.first { it.uuid == chatId }
        )

    val directUser: StateFlow<UserResponseData?> = repo.users
        .map { list -> list.firstOrNull() { it.uuid == stream.value.directUserUuid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val users: StateFlow<List<UserResponseData>> = repo.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val drafts: StateFlow<List<Draft>> = repo.draftsPool
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.draftsPool.value
        )

    private var possibleMessage: MessageResponse? = null

    var user: UserResponseData? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    var editingMessage: MessageResponse? = null
    private val _editingMessageBackupText = MutableStateFlow<String?>(null)
    val editingMessageBackupText: StateFlow<String?> = _editingMessageBackupText

    private val _quotedMessages = MutableStateFlow<List<QuotedMessage>>(emptyList())
    val quotedMessages: StateFlow<List<QuotedMessage>> = _quotedMessages
    private val _currentQuotedMessage = MutableStateFlow<QuotedMessage?>(null)
    val currentQuotedMessage: StateFlow<QuotedMessage?> = _currentQuotedMessage

    val mentions = MentionTextFieldState()

    fun onUserSelected(name: String, urn: String) {
        if (!mentions.insertMentionFromAtQuery(name, urn)) {
            mentions.insertMention(name, urn)
        }
    }

//    private val _messageText = MutableStateFlow("")
//    val messageText: StateFlow<String> = _messageText

    private val _uriList = MutableStateFlow<List<AttachedUri>>(emptyList())
    val uriList: StateFlow<List<AttachedUri>> = _uriList

    var shouldScrollToBottom: Boolean = true

    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun addUri(newUri: Uri, fileName: String, type: String) {
        val attachedUri = AttachedUri(newUri, fileName, type)
        _uriList.update { current -> current + attachedUri }
    }

    fun removeAttachedUri(attachedUri: AttachedUri) {
        _uriList.update { current -> current - attachedUri }
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

    private val states = mutableMapOf<String, MutableStateFlow<FileUiState>>()
    fun stateFor(uuid: String, fileName: String): StateFlow<FileUiState> {
        return states.getOrPut("$uuid:$fileName") {
            MutableStateFlow(FileUiState.Idle)
        }
    }
    fun load(uuid: String, fileName: String) {
        val key = "$uuid:$fileName"
        val flow = states.getOrPut(key) { MutableStateFlow(FileUiState.Idle) }
        viewModelScope.launch {
            flow.value = FileUiState.Loading
            when (val result = storage.loadOrDownload(uuid, fileName)) {
                is ApiResult.Success -> {
                    flow.value = FileUiState.Ready(result.value)
                }
                is ApiResult.Error -> {
                    flow.value = FileUiState.Error(result.error.message ?: "")
                }
            }
        }
    }

    fun onEditMessageClicked(message: MessageResponse) {
        _currentQuotedMessage.update { null }
        _quotedMessages.update { emptyList() }
        if (message.uuid != "") {
            editingMessage = message
            _editingMessageBackupText.value = message.payload.content
            mentions.setText(message.payload.content)
        }
    }

    fun onQuoteMessageClicked(message: MessageResponse) {
        editingMessage = null
        _editingMessageBackupText.value = null
        val newQuotedMessage = QuotedMessage(message, mentions.text)
        _quotedMessages.update { listOf(newQuotedMessage) }
        _currentQuotedMessage.update { newQuotedMessage }
    }

    fun onAddQuoteMessageClicked(message: MessageResponse) {
        editingMessage = null
        _editingMessageBackupText.value = null
        val newQuotedMessage = QuotedMessage(message, "")
        _quotedMessages.update { current ->
            current + newQuotedMessage
        }
        _currentQuotedMessage.update { newQuotedMessage }
        mentions.setText("")
    }

    fun enableAutoScroll() {
        shouldScrollToBottom = true
    }


    fun disableAutoScroll() {
        shouldScrollToBottom = false
    }


    fun clearEditingMessage() {
        _editingMessageBackupText.value = null
        editingMessage = null
        mentions.setText("")
    }

    fun clearQuotingMessage(quotedMessage: QuotedMessage) {
        _quotedMessages.update { current ->
            current - quotedMessage
        }
        val lastQuotedMessage = _quotedMessages.value.lastOrNull()
        if (lastQuotedMessage != null) {
            if (quotedMessage == currentQuotedMessage.value) {
                _currentQuotedMessage.update {
                    lastQuotedMessage
                }
                mentions.setText(lastQuotedMessage.text)
            }
        } else {
            _currentQuotedMessage.update {
                null
            }
            mentions.setText("")
        }
    }

    fun onMentionsChange(incoming: TextFieldValue) {
        mentions.onValueChange(incoming)
        _currentQuotedMessage.value?.text = mentions.text
    }

    fun onClickOnQuotedMessage(quotedMessage: QuotedMessage) {
        _currentQuotedMessage.update {
            quotedMessage
        }
        mentions.setText(quotedMessage.text)
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
        val attachmentList = _uriList.value
        var messageText = ""
        val baseText = if (quotedMessages.value.isEmpty()) {
            mentions.text
        } else {
            val quotedMessagesStrings = quotedMessages.value.map {
                "[${it.message.user?.displayableName() ?: "user"}](urn:quote:${it.message.uuid})\n\n${it.text}"
            }
            val resultString = quotedMessagesStrings.joinToString("\n\n")
            resultString
        }
        var attachmentsSuffix = ""
        for (attachment in attachmentList) {
            val response = client.uploadStreamFile(attachment.fileName, context, attachment.uri, chatId)
            when(response) {
                is ApiResult.Success -> {
                    attachmentsSuffix += "[${response.value.name}](urn:${attachment.type}:${response.value.uuid})\n\n"
                }
                is ApiResult.Error -> {

                }
            }
        }
        messageText += baseText
        if (!attachmentsSuffix.isEmpty()) {
            messageText += "\r\n"
            messageText += attachmentsSuffix
        }
        if (messageText.isBlank()) return
        sendTextMessage(messageText)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun sendTextMessage(messageText: String) {
        val userId = repo.currentUser.value?.uuid
        val uuid: Uuid = Uuid.random()
        var newMessage = MessageResponse(
            uuid.toString(),
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            chatId,
            topicUuid,
            userId ?: "",
            userId ?: "",
            MessageResponsePayload("markdown", messageText),
            true,
            emptyMap(),
            false
        )
        newMessage.user = repo.currentUser.value
        repo.updateMessagesPool(listOf(newMessage))
        repo.addMessageToStreamTopic(newMessage)
        possibleMessage = newMessage
        mentions.setText("")
        _uriList.update { emptyList() }
        editingMessage = null
        _editingMessageBackupText.value = null
        _currentQuotedMessage.update { null }
        _quotedMessages.update { emptyList() }
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
                deleteCurrentTopicDraftIfNeeded()
                possibleMessage = null
            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun deleteCurrentTopicDraftIfNeeded() {
        val draft = drafts.value.firstOrNull { it.streamUuid == chatId && it.topicUuid == topicUuid }
        if (draft != null) {
            val response = client.performRequest(DeleteDraftRequest(draft.uuid, draft.revision))
            when (response) {
                is ApiResult.Success -> {
                    repo.removeDraft(draft)
                }

                is ApiResult.Error -> {

                }
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
        mentions.setText("")
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
            val draft = drafts.value.firstOrNull { it.streamUuid == chatId && it.topicUuid == topicUuid }
            if (draft != null) {
                mentions.setText(draft.payload.content)
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
        val markMessagesReadResponse = client.performRequest(MarkMessagesReadUpToRequest(messageUuid))
        when(markMessagesReadResponse) {
            is ApiResult.Success -> {

            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun onMessagesVisible(newlyVisible: List<String>) {
        val messages = streamTopicMessages.value["${chatId}.${topicUuid}"]
        if (messages != null) {
            for (messageUuid in newlyVisible) {
                val message = messages.firstOrNull { it.uuid == messageUuid }
                if (message != null && !message.read) {
                    markMessageRead(messageUuid)
                }
            }
        }
    }

    suspend fun markMessageRead(messageUuid: String) {
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

@Serializable
data class QuotedMessage(
    val message: MessageResponse,
    var text: String
)

data class AttachedUri(
    val uri: Uri,
    val fileName: String,
    val type: String
)

object MarkdownPayloadParser {
    private val imageRegex = Regex("""!\[([^\]]*)\]\(urn:image:([^)]+)\)""")
    private val fileRegex = Regex("""\[([^\]]*)\]\(urn:file:([^)]+)\)""")
    private val quoteRegex = Regex("""\[([^\]]*)\]\(urn:quote:([^)]+)\)""")
    fun parse(content: String): List<MessageElement> {
        val input = content.replace("\\n", "\n").trim()
        if (input.isEmpty()) return emptyList()
        val elements = mutableListOf<MessageElement>()
        var index = 0
        while (index < input.length) {
            val next = findNextSpecial(input, index) ?: run {
                appendPlainText(elements, input.substring(index))
                break
            }
            if (next.start > index) {
                appendPlainText(elements, input.substring(index, next.start))
            }
            elements += next.element
            index = next.end
        }
        return elements
    }
    private fun appendPlainText(elements: MutableList<MessageElement>, raw: String) {
        val text = raw.trim()
        if (text.isNotEmpty()) {
            elements += MessageElement.PlainText(text)
        }
    }
    private data class SpecialMatch(
        val start: Int,
        val end: Int,
        val element: MessageElement,
    )
    private fun findNextSpecial(input: String, fromIndex: Int): SpecialMatch? {
        val image = imageRegex.find(input, fromIndex)
        val file = fileRegex.find(input, fromIndex)
        val quote = quoteRegex.find(input, fromIndex)
        val candidates = listOfNotNull(
            image?.let {
                SpecialMatch(
                    start = it.range.first,
                    end = it.range.last + 1,
                    element = MessageElement.Image(
                        fileName = it.groupValues[1],
                        uuid = it.groupValues[2],
                    ),
                )
            },
            file?.let {
                SpecialMatch(
                    start = it.range.first,
                    end = it.range.last + 1,
                    element = MessageElement.File(
                        fileName = it.groupValues[1],
                        uuid = it.groupValues[2],
                    ),
                )
            },
            quote?.let {
                SpecialMatch(
                    start = it.range.first,
                    end = it.range.last + 1,
                    element = MessageElement.Quote(
                        displayName = it.groupValues[1],
                        uuid = it.groupValues[2],
                        text = "", // remove if you dropped this field
                    ),
                )
            },
        )
        return candidates.minByOrNull { it.start }
    }
}

data class LocalAttachment(
    val uuid: String,
    val fileName: String,
    val localFile: File,
)
class AttachmentStorage(
    val context: Context,
    private val client: WorkspaceAPIClient,
) {
    private val dir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }
    fun localFile(uuid: String, fileName: String): File {
        val safeName = fileName.replace(Regex("""[^\w.\- ]"""), "_")
        return File(dir, "$uuid-$safeName")
    }
    fun isCached(uuid: String, fileName: String): Boolean {
        val file = localFile(uuid, fileName)
        return file.exists() && file.length() > 0
    }

    suspend fun loadOrDownload(uuid: String, fileName: String): ApiResult<LocalAttachment, ApiError> {
        val destination = localFile(uuid, fileName)
        if (isCached(uuid, fileName)) {
            return ApiResult.Success(
                LocalAttachment(
                    uuid = uuid,
                    fileName = fileName,
                    localFile = destination,
                )
            )
        }
        return when (
            val result = client.downloadFile(
                path = "/api/workspace/v1/messenger/files/$uuid/actions/download",
                destination = destination,
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(
                LocalAttachment(
                    uuid = uuid,
                    fileName = fileName,
                    localFile = result.value,
                )
            )
            is ApiResult.Error -> result
        }
    }
}