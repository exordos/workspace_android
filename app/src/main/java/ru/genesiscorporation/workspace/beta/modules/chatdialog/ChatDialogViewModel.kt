package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.PersistedAttachment
import ru.genesiscorporation.workspace.beta.data.PersistedComposerDraft
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxEntry
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus
import ru.genesiscorporation.workspace.beta.data.PersistedServerDraftState
import ru.genesiscorporation.workspace.beta.data.deleteOwnedIncomingAttachment
import ru.genesiscorporation.workspace.beta.data.isOwnedIncomingAttachment
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.accountAttachmentCacheDirectory
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.CreateDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EditMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageSortDirection
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DEFAULT_MESSAGE_PAGE_SIZE
import ru.genesiscorporation.workspace.beta.data.remote.dto.RemoveMessageReactionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateDraftRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalDraftUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseDraftConflictBody
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateDraftResponse
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import java.time.OffsetDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.io.File
import kotlin.collections.filter

data class CallLaunchEvent(
    val roomName: String,
)

data class OpenSourceMessageEvent(
    val title: String,
    val streamUuid: String,
    val topicName: String?,
    val topicUuid: String,
    val isDirectMessages: Boolean,
    val messageUuid: String,
)

private data class ComposerSnapshot(
    val revision: Long,
    val text: String,
    val attachments: List<SelectedLocalAttachment>,
    val editingMessage: MessageResponse?,
    val quotedMessage: MessageResponse?,
)

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
    private val focusProviderMessageId: String? = null,
    private val focusMessageUuid: String? = null,
    private val beginForwardMessageUuid: String? = null,
    draftStorageSlot: String? = null,
    private val conversationStateStore: ConversationStateStore,
): ViewModel() {
    val hasExplicitMessageRoute: Boolean =
        focusMessageUuid != null || focusProviderMessageId != null

    private val selectedDraftStorageSlot =
        draftStorageSlot?.let(::canonicalDraftUuid)

    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = repo.streamTopicMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )
    val topicUnreadCount: StateFlow<Int> = combine(
        repo.streamTopics,
        repo.streamTopicMessages,
    ) { topicsByStream, messagesByConversation ->
        topicsByStream[chatId]
            ?.singleOrNull { it.uuid == topicUuid }
            ?.unreadCount
            ?: messagesByConversation["$chatId.$topicUuid"]
                .orEmpty()
                .count { !it.read && !it.isOwn }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError
    private val _readError = MutableStateFlow<String?>(null)
    val readError: StateFlow<String?> = _readError
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    private val _focusedMessageUuid = MutableStateFlow<String?>(null)
    val focusedMessageUuid: StateFlow<String?> = _focusedMessageUuid
    var editingMessage: MessageResponse? = null
    private val _editingMessageBackupText = MutableStateFlow<String?>(null)
    val editingMessageBackupText: StateFlow<String?> = _editingMessageBackupText

    private val _quotedMessage = MutableStateFlow<MessageResponse?>(null)
    val quotedMessage: StateFlow<MessageResponse?> = _quotedMessage


    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText

    private val _attachments = MutableStateFlow<List<SelectedLocalAttachment>>(emptyList())
    val attachments: StateFlow<List<SelectedLocalAttachment>> = _attachments
    private val _outboxEntries = MutableStateFlow<List<PersistedOutboxEntry>>(emptyList())
    val outboxEntries: StateFlow<List<PersistedOutboxEntry>> = _outboxEntries
    private val _draftSyncState =
        MutableStateFlow<PersistedServerDraftState?>(null)
    val draftSyncState: StateFlow<PersistedServerDraftState?> = _draftSyncState
    private val _verifyingOutbox = MutableStateFlow<Set<String>>(emptySet())
    val verifyingOutbox: StateFlow<Set<String>> = _verifyingOutbox
    private val _deletingMessageUuids = MutableStateFlow<Set<String>>(emptySet())
    val deletingMessageUuids: StateFlow<Set<String>> = _deletingMessageUuids
    private val _loadingOlderMessages = MutableStateFlow(false)
    val loadingOlderMessages: StateFlow<Boolean> = _loadingOlderMessages
    private val _hasOlderMessages = MutableStateFlow(false)
    val hasOlderMessages: StateFlow<Boolean> = _hasOlderMessages
    private val _olderMessagesError = MutableStateFlow<String?>(null)
    val olderMessagesError: StateFlow<String?> = _olderMessagesError
    private val _loadingNewerMessages = MutableStateFlow(false)
    val loadingNewerMessages: StateFlow<Boolean> = _loadingNewerMessages
    private val _hasNewerMessages = MutableStateFlow(false)
    val hasNewerMessages: StateFlow<Boolean> = _hasNewerMessages
    private val _newerMessagesError = MutableStateFlow<String?>(null)
    val newerMessagesError: StateFlow<String?> = _newerMessagesError
    private val _conversationStateReady = MutableStateFlow(false)
    val conversationStateReady: StateFlow<Boolean> = _conversationStateReady
    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus
    private val _downloadingAttachmentUuid = MutableStateFlow<String?>(null)
    val downloadingAttachmentUuid: StateFlow<String?> = _downloadingAttachmentUuid
    private val _forwardDialogState = MutableStateFlow<ForwardDialogState?>(null)
    internal val forwardDialogState: StateFlow<ForwardDialogState?> = _forwardDialogState
    private val _forwardQuoteResolutions =
        MutableStateFlow<Map<String, ForwardQuoteResolution>>(emptyMap())
    internal val forwardQuoteResolutions:
        StateFlow<Map<String, ForwardQuoteResolution>> = _forwardQuoteResolutions
    private val reactionOperationsMutex = Mutex()
    private val reactionOperations = mutableSetOf<String>()
    private val messageDeletionMutex = Mutex()
    private val forwardQuoteLoadMutex = Mutex()
    private val forwardQuoteLoadingUuids = mutableSetOf<String>()
    private val conversationPersistenceMutex = Mutex()
    private val remoteDraftMutex = Mutex()
    private val callLaunchChannel = Channel<CallLaunchEvent>(Channel.BUFFERED)
    val callLaunchEvents = callLaunchChannel.receiveAsFlow()
    private val openSourceMessageChannel =
        Channel<OpenSourceMessageEvent>(Channel.BUFFERED)
    val openSourceMessageEvents = openSourceMessageChannel.receiveAsFlow()
    private var conversationOwnerKey: String? = null
    private var pendingEditingMessageUuid: String? = null
    private var pendingQuotedMessageUuid: String? = null
    private var suspendedDraft: PersistedComposerDraft? = null
    private var composerRevision = 0L
    private var draftPersistenceJob: Job? = null
    private var remoteDraftSyncJob: Job? = null
    private var draftUpdatedAt: String? = null
    private var nextOlderPageMarker: String? = null
    private var contextWindowAnchorUuid: String? = focusMessageUuid
    private var olderMessagesJob: Job? = null
    private var refreshHistoryBeforeOlderRetry = false
    private var nextNewerPageMarker: String? = null
    private var newerMessagesJob: Job? = null
    private var refreshHistoryBeforeNewerRetry = false
    private var readMessagesJob: Job? = null
    private var pendingReadBoundaryUuid: String? = null
    private var failedReadBoundaryUuid: String? = null
    private var forwardCatalogJob: Job? = null
    private var forwardTopicLoadJob: Job? = null
    private var forwardSelectionGeneration = 0L
    private var forwardDeliveryAttempt: ForwardDeliveryAttempt? = null
    private var initialForwardRequestHandled = false

    fun onMessageChange(newText: String) {
        _messageText.value = newText.take(MAX_MESSAGE_CHARS)
        if (newText.length > MAX_MESSAGE_CHARS) {
            _actionError.value =
                "Сообщение не может быть длиннее $MAX_MESSAGE_CHARS символов"
        }
        composerChanged()
    }

    fun addAttachments(context: Context, uris: List<Uri>) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val existing = _attachments.value
            val availableSlots =
                (MAX_COMPOSER_ATTACHMENTS - existing.size).coerceAtLeast(0)
            val selected = uris
                .distinctBy(Uri::toString)
                .filterNot { uri -> existing.any { it.uri == uri } }
                .take(availableSlots)
                .mapNotNull { uri ->
                    persistReadPermission(appContext, uri)
                    describeAttachment(appContext, uri)
                }
            _attachments.value = existing + selected
            if (selected.isNotEmpty()) {
                composerChanged()
            }
            if (uris.size > availableSlots) {
                _actionError.value =
                    "Можно прикрепить не более $MAX_COMPOSER_ATTACHMENTS файлов"
            }
        }
    }

    fun removeAttachment(context: Context, uri: Uri) {
        val removed = _attachments.value.any { it.uri == uri }
        if (!removed) return
        _attachments.update { current -> current.filterNot { it.uri == uri } }
        composerChanged()
        if (!isOwnedIncomingAttachment(uri)) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            draftPersistenceJob?.cancelAndJoin()
            try {
                persistConversationState()
                if (_attachments.value.none { it.uri == uri }) {
                    if (!deleteOwnedIncomingAttachment(appContext, uri)) {
                        _actionError.value =
                            "Файл удалён из черновика, но временную копию " +
                                "не удалось очистить"
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _actionError.value =
                    "Файл удалён из черновика, но временную копию " +
                        "не удалось очистить"
            }
        }
    }

    fun nextMessageByUuid(currentUuid: String): MessageResponse? {
        val streamTopicKey = "${chatId}.${topicUuid}"
        val messages = streamTopicMessages.value[streamTopicKey]
            ?.sortedBy { messageSortInstant(it.createdAt) }
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
        val messages = streamTopicMessages.value[streamTopicKey]
            ?.sortedBy { messageSortInstant(it.createdAt) }
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
        if (editingMessage == null) {
            suspendedDraft = PersistedComposerDraft(
                text = _messageText.value,
                quotedMessageUuid = _quotedMessage.value?.uuid
                    ?: pendingQuotedMessageUuid,
                attachments = persistedAttachments(),
            )
        }
        _quotedMessage.value = null
        pendingQuotedMessageUuid = null
        _attachments.value = emptyList()
        if (message.uuid != "") {
            editingMessage = message
            pendingEditingMessageUuid = message.uuid
            _editingMessageBackupText.value = message.payload.content
            _messageText.value = message.payload.content
            composerChanged()
        }
    }

    fun onQuoteMessageClicked(message: MessageResponse) {
        if (editingMessage != null || pendingEditingMessageUuid != null) {
            restoreSuspendedDraftAfterEdit()
        }
        _quotedMessage.value = message
        pendingQuotedMessageUuid = message.uuid
        composerChanged()
    }

    internal fun beginForward(message: MessageResponse) {
        if (!canForwardMessage(message)) {
            _actionError.value = "Это сообщение нельзя переслать"
            return
        }
        forwardCatalogJob?.cancel()
        forwardTopicLoadJob?.cancel()
        forwardSelectionGeneration += 1
        forwardDeliveryAttempt = null
        val sourceSnapshot = message.copy(
            payload = message.payload.copy(),
            user = message.user?.copy(),
        )
        _forwardDialogState.value = ForwardDialogState(
            sourceMessage = sourceSnapshot,
            currentUserUuid =
                userViewModel.userId.value ?: userViewModel.userData?.uuid,
            catalogLoading = true,
        )
        forwardCatalogJob = viewModelScope.launch {
            refreshForwardCatalog(sourceSnapshot.uuid)
        }
    }

    internal fun dismissForward() {
        val state = _forwardDialogState.value ?: return
        if (state.submitting || state.verifying) return
        forwardCatalogJob?.cancel()
        forwardTopicLoadJob?.cancel()
        forwardSelectionGeneration += 1
        forwardDeliveryAttempt = null
        _forwardDialogState.value = null
    }

    internal fun selectForwardTargetKind(kind: ForwardTargetKind) {
        val state = _forwardDialogState.value ?: return
        if (
            state.submitting ||
            state.verifying ||
            state.deliveryStatus != ForwardDeliveryStatus.EDITING
        ) {
            return
        }
        _forwardDialogState.value = state.copy(
            targetKind = kind,
            error = null,
            canRetryUncertainSend = false,
        )
    }

    internal fun selectForwardStream(streamUuid: String) {
        val state = _forwardDialogState.value ?: return
        if (
            state.submitting ||
            state.verifying ||
            state.deliveryStatus != ForwardDeliveryStatus.EDITING
        ) {
            return
        }
        val stream = forwardableStreams(repo.streams.value)
            .firstOrNull { it.uuid == streamUuid }
        if (stream == null) {
            setForwardError("Канал больше недоступен")
            return
        }
        forwardTopicLoadJob?.cancel()
        val generation = ++forwardSelectionGeneration
        val cachedTopics = repo.streamTopics.value[stream.uuid]
            ?.let { forwardTopics(stream.uuid, it) }
        _forwardDialogState.value = state.copy(
            selectedStreamUuid = stream.uuid,
            selectedTopicUuid = cachedTopics?.preferredForwardTopicUuid(
                selectedStreamUuid = stream.uuid,
                currentStreamUuid = chatId,
                currentTopicUuid = topicUuid,
            ),
            topicsLoading = cachedTopics == null,
            deliveryStatus = ForwardDeliveryStatus.EDITING,
            error = cachedTopics
                ?.takeIf(List<TopicsResponseData>::isEmpty)
                ?.let { "В канале нет доступных топиков" },
            canRetryUncertainSend = false,
        )
        if (cachedTopics == null) {
            forwardTopicLoadJob = viewModelScope.launch {
                loadForwardTopics(
                    streamUuid = stream.uuid,
                    generation = generation,
                )
            }
        }
    }

    internal fun retryForwardTopics() {
        val state = _forwardDialogState.value ?: return
        val streamUuid = state.selectedStreamUuid ?: return
        if (
            state.submitting ||
            state.verifying ||
            state.topicsLoading ||
            state.deliveryStatus != ForwardDeliveryStatus.EDITING
        ) {
            return
        }
        forwardTopicLoadJob?.cancel()
        val generation = ++forwardSelectionGeneration
        _forwardDialogState.value = state.copy(
            topicsLoading = true,
            error = null,
        )
        forwardTopicLoadJob = viewModelScope.launch {
            loadForwardTopics(streamUuid, generation)
        }
    }

    internal fun selectForwardTopic(topicUuid: String) {
        val state = _forwardDialogState.value ?: return
        if (
            state.submitting ||
            state.verifying ||
            state.deliveryStatus != ForwardDeliveryStatus.EDITING
        ) {
            return
        }
        val streamUuid = state.selectedStreamUuid ?: return
        val topic = forwardTopics(
            streamUuid,
            repo.streamTopics.value[streamUuid].orEmpty(),
        ).firstOrNull { it.uuid == topicUuid }
        if (topic == null) {
            setForwardError("Топик больше недоступен")
            return
        }
        _forwardDialogState.value = state.copy(
            selectedTopicUuid = topic.uuid,
            deliveryStatus = ForwardDeliveryStatus.EDITING,
            error = null,
            canRetryUncertainSend = false,
        )
    }

    internal fun selectForwardUser(userUuid: String) {
        val state = _forwardDialogState.value ?: return
        if (
            state.submitting ||
            state.verifying ||
            state.deliveryStatus != ForwardDeliveryStatus.EDITING
        ) {
            return
        }
        val currentUserUuid = state.currentUserUuid
        val user = forwardUsers(repo.users.value, currentUserUuid)
            .firstOrNull { it.uuid == userUuid }
        if (user == null) {
            setForwardError("Пользователь больше недоступен")
            return
        }
        _forwardDialogState.value = state.copy(
            selectedUserUuid = user.uuid,
            deliveryStatus = ForwardDeliveryStatus.EDITING,
            error = null,
            canRetryUncertainSend = false,
        )
    }

    internal fun submitForward() {
        val state = _forwardDialogState.value ?: return
        if (
            state.submitting ||
            state.verifying ||
            state.catalogLoading ||
            state.topicsLoading ||
            state.deliveryStatus != ForwardDeliveryStatus.EDITING
        ) {
            return
        }
        viewModelScope.launch {
            submitForwardInternal()
        }
    }

    internal fun verifyForwardDelivery() {
        val state = _forwardDialogState.value ?: return
        if (state.submitting || state.verifying || forwardDeliveryAttempt == null) return
        viewModelScope.launch {
            verifyForwardDeliveryInternal(auto = false)
        }
    }

    internal fun retryUncertainForward() {
        val state = _forwardDialogState.value ?: return
        if (
            state.submitting ||
            state.verifying ||
            !state.canRetryUncertainSend ||
            state.deliveryStatus != ForwardDeliveryStatus.UNCERTAIN
        ) {
            return
        }
        viewModelScope.launch {
            submitForwardInternal()
        }
    }

    private suspend fun refreshForwardCatalog(sourceMessageUuid: String) {
        val current = _forwardDialogState.value
            ?.takeIf { it.sourceMessage.uuid == sourceMessageUuid }
            ?: return
        val credentials = userViewModel.repo.activeCredentialSnapshot()
        val ownerKey = credentials.ownerKey
        if (ownerKey.isNullOrBlank()) {
            _forwardDialogState.value = current.copy(
                catalogLoading = false,
                error = "Активный аккаунт недоступен",
            )
            return
        }
        var refreshError: String? = null
        try {
            coroutineScope {
                val streamsDeferred = async {
                    client.performRequest(StreamsRequest())
                }
                val usersDeferred = async {
                    client.performRequest(UsersRequest())
                }
                when (val streamsResponse = streamsDeferred.await()) {
                    is ApiResult.Success -> repo.setInitialStreams(streamsResponse.value)
                    is ApiResult.Error -> {
                        refreshError = streamsResponse.error.message
                            ?: "Не удалось обновить список каналов"
                    }
                }
                when (val usersResponse = usersDeferred.await()) {
                    is ApiResult.Success -> repo.setInitialUsers(usersResponse.value)
                    is ApiResult.Error -> {
                        val message = usersResponse.error.message
                            ?: "Не удалось обновить список пользователей"
                        refreshError = refreshError?.let { "$it. $message" } ?: message
                    }
                }
            }
            if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                refreshError = "Аккаунт изменился; откройте пересылку заново"
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            refreshError = "Не удалось обновить получателей"
        } finally {
            val latest = _forwardDialogState.value
                ?.takeIf { it.sourceMessage.uuid == sourceMessageUuid }
                ?: return
            _forwardDialogState.value = latest.copy(
                currentUserUuid = credentials.userId,
                catalogLoading = false,
                error = refreshError,
            )
        }
    }

    private suspend fun loadForwardTopics(
        streamUuid: String,
        generation: Long,
    ) {
        val result = try {
            client.performRequest(TopicsRequest(streamUuid))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            null
        }
        if (generation != forwardSelectionGeneration) return
        val state = _forwardDialogState.value
            ?.takeIf { it.selectedStreamUuid == streamUuid }
            ?: return
        when (result) {
            is ApiResult.Success -> {
                val topics = forwardTopics(streamUuid, result.value)
                repo.addStreamTopics(streamUuid, topics)
                _forwardDialogState.value = state.copy(
                    selectedTopicUuid = topics.preferredForwardTopicUuid(
                        selectedStreamUuid = streamUuid,
                        currentStreamUuid = chatId,
                        currentTopicUuid = topicUuid,
                    ),
                    topicsLoading = false,
                    error = if (topics.isEmpty()) {
                        "В канале нет доступных топиков"
                    } else {
                        null
                    },
                )
            }

            is ApiResult.Error -> {
                _forwardDialogState.value = state.copy(
                    topicsLoading = false,
                    error = result.error.message
                        ?: "Не удалось загрузить топики",
                )
            }

            null -> {
                _forwardDialogState.value = state.copy(
                    topicsLoading = false,
                    error = "Не удалось загрузить топики",
                )
            }
        }
    }

    private suspend fun submitForwardInternal() {
        val initialState = _forwardDialogState.value ?: return
        var sendStarted = false
        val content = buildWorkspaceForwardMarkdown(initialState.sourceMessage)
        if (content == null) {
            setForwardError("Исходное сообщение повреждено")
            return
        }
        val credentials = userViewModel.repo.activeCredentialSnapshot()
        val ownerKey = credentials.ownerKey
        if (ownerKey.isNullOrBlank()) {
            setForwardError("Активный аккаунт недоступен")
            return
        }
        _forwardDialogState.value = initialState.copy(
            submitting = true,
            verifying = false,
            deliveryStatus = ForwardDeliveryStatus.EDITING,
            error = null,
            canRetryUncertainSend = false,
        )
        try {
            val destination = when (initialState.targetKind) {
                ForwardTargetKind.CHANNEL -> {
                    val streamUuid = initialState.selectedStreamUuid
                    val topicUuid = initialState.selectedTopicUuid
                    val streamExists = forwardableStreams(repo.streams.value)
                        .any { it.uuid == streamUuid }
                    val topicExists = repo.streamTopics.value[streamUuid]
                        .orEmpty()
                        .any { it.uuid == topicUuid && it.streamUuid == streamUuid }
                    if (
                        streamUuid.isNullOrBlank() ||
                        topicUuid.isNullOrBlank() ||
                        !streamExists ||
                        !topicExists
                    ) {
                        setForwardError("Выберите доступные канал и топик")
                        null
                    } else {
                        ForwardDestination(streamUuid, topicUuid)
                    }
                }

                ForwardTargetKind.DIRECT -> {
                    val userUuid = initialState.selectedUserUuid
                    val userExists = forwardUsers(
                        repo.users.value,
                        credentials.userId,
                    ).any { it.uuid == userUuid }
                    if (userUuid.isNullOrBlank() || !userExists) {
                        setForwardError("Выберите доступного пользователя")
                        null
                    } else {
                        resolveDirectForwardDestination(userUuid)
                    }
                }
            } ?: return

            val preflight = client.performRequest(
                MessagesRequest(
                    streamId = destination.streamUuid,
                    topicId = destination.topicUuid,
                    pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
                    sortDirection = MessageSortDirection.DESCENDING,
                ),
            )
            val knownMatches = when (preflight) {
                is ApiResult.Success -> {
                    val targetMessages = preflight.value.filter {
                        it.streamUuid == destination.streamUuid &&
                            it.topicUuid == destination.topicUuid
                    }
                    repo.addStreamTopicMessages(
                        destination.streamUuid,
                        destination.topicUuid,
                        targetMessages,
                    )
                    targetMessages
                        .filter {
                            it.isOwn &&
                                it.payload.content == content &&
                                parseCanonicalMessageUuid(it.uuid) != null
                        }
                        .mapTo(mutableSetOf(), MessageResponse::uuid)
                }

                is ApiResult.Error -> {
                    setForwardError(
                        preflight.error.message
                            ?: "Не удалось проверить целевой чат перед отправкой",
                    )
                    return
                }
            }
            if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                setForwardError("Аккаунт изменился до отправки")
                return
            }
            val attempt = ForwardDeliveryAttempt(
                ownerKey = ownerKey,
                destination = destination,
                content = content,
                knownMatchingMessageUuids = knownMatches,
            )
            forwardDeliveryAttempt = attempt
            sendStarted = true
            val postResult = client.performRequest(
                    SendMessageRequest(
                        streamUuid = destination.streamUuid,
                        topicUuid = destination.topicUuid,
                        content = content,
                    ),
                )
            when (val decision = decideForwardPostResult(attempt, postResult)) {
                is ForwardPostDecision.Completed -> {
                    markForwardCompleted(decision.message)
                }

                is ForwardPostDecision.Verify -> {
                    markForwardUncertain(decision.reason)
                    verifyForwardDeliveryInternal(auto = true)
                }

                is ForwardPostDecision.Failed -> {
                    setForwardError(decision.reason)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (
                unexpectedForwardFailureNeedsVerification(
                    sendStarted,
                    forwardDeliveryAttempt,
                )
            ) {
                markForwardUncertain(
                    "Соединение прервалось после начала отправки; " +
                        "проверяю целевой чат",
                )
                try {
                    verifyForwardDeliveryInternal(auto = true)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    markForwardUncertain(
                        "Не удалось проверить результат отправки. " +
                            "Повторная отправка пока отключена.",
                    )
                }
            } else {
                setForwardError("Не удалось переслать сообщение")
            }
        } finally {
            _forwardDialogState.update { state ->
                state?.copy(submitting = false)
            }
        }
    }

    private suspend fun resolveDirectForwardDestination(
        userUuid: String,
    ): ForwardDestination? {
        existingDirectForwardDestination(
            userUuid,
            repo.streams.value,
            repo.streamTopics.value,
        )?.let { return it }

        repo.streams.value
            .firstOrNull {
                it.isPrivate &&
                    !it.isArchived &&
                    it.directUserUuid == userUuid
            }
            ?.let { existing ->
                resolveForwardDefaultTopic(existing)?.let { return it }
            }

        val refreshedStreams = when (
            val response = client.performRequest(StreamsRequest())
        ) {
            is ApiResult.Success -> response.value.also(repo::setInitialStreams)
            is ApiResult.Error -> {
                setForwardError(
                    response.error.message
                        ?: "Не удалось проверить существующий личный чат",
                )
                return null
            }
        }
        existingDirectForwardDestination(
            userUuid,
            refreshedStreams,
            repo.streamTopics.value,
        )?.let { return it }
        refreshedStreams
            .firstOrNull {
                it.isPrivate &&
                    !it.isArchived &&
                    it.directUserUuid == userUuid
            }
            ?.let { existing ->
                resolveForwardDefaultTopic(existing)?.let { return it }
                setForwardError("Основной топик личного чата недоступен")
                return null
            }

        return when (
            val response = client.performRequest(
                AddStreamRequest(
                    name = "Direct",
                    description = "Private workspace",
                    directUserUuid = userUuid,
                ),
            )
        ) {
            is ApiResult.Success -> {
                repo.addStream(response.value)
                resolveForwardDefaultTopic(response.value)
                    ?: run {
                        setForwardError(
                            "Личный чат создан, но сервер не вернул основной топик. " +
                                "Повторите пересылку: новый чат будет найден без дублирования.",
                        )
                        null
                    }
            }

            is ApiResult.Error -> {
                if (response.error.kind == ApiErrorKind.CONFLICT) {
                    when (val retryRefresh = client.performRequest(StreamsRequest())) {
                        is ApiResult.Success -> {
                            repo.setInitialStreams(retryRefresh.value)
                            val existing = retryRefresh.value.firstOrNull {
                                it.isPrivate &&
                                    !it.isArchived &&
                                    it.directUserUuid == userUuid
                            }
                            existing?.let { resolveForwardDefaultTopic(it) }
                                ?: run {
                                    setForwardError(
                                        "Личный чат уже существует, но его не удалось открыть",
                                    )
                                    null
                                }
                        }

                        is ApiResult.Error -> {
                            setForwardError(
                                retryRefresh.error.message
                                    ?: "Личный чат уже существует, но каталог недоступен",
                            )
                            null
                        }
                    }
                } else {
                    setForwardError(
                        response.error.message
                            ?: "Не удалось создать личный чат",
                    )
                    null
                }
            }
        }
    }

    private suspend fun resolveForwardDefaultTopic(
        stream: Stream,
    ): ForwardDestination? {
        parseCanonicalMessageUuid(stream.defaultTopicUuid.orEmpty())
            ?.let { return ForwardDestination(stream.uuid, it) }
        repo.streamTopics.value[stream.uuid]
            .orEmpty()
            .singleOrNull(TopicsResponseData::isDefault)
            ?.uuid
            ?.let { return ForwardDestination(stream.uuid, it) }

        when (val topicsResponse = client.performRequest(TopicsRequest(stream.uuid))) {
            is ApiResult.Success -> {
                val topics = topicsResponse.value.filter {
                    it.streamUuid == stream.uuid
                }
                repo.addStreamTopics(stream.uuid, topics)
                topics.singleOrNull(TopicsResponseData::isDefault)
                    ?.uuid
                    ?.let { return ForwardDestination(stream.uuid, it) }
            }

            is ApiResult.Error -> Unit
        }
        return when (val streamsResponse = client.performRequest(StreamsRequest())) {
            is ApiResult.Success -> {
                repo.setInitialStreams(streamsResponse.value)
                streamsResponse.value
                    .firstOrNull { it.uuid == stream.uuid }
                    ?.defaultTopicUuid
                    ?.let(::parseCanonicalMessageUuid)
                    ?.let { ForwardDestination(stream.uuid, it) }
            }

            is ApiResult.Error -> null
        }
    }

    private fun markForwardUncertain(message: String) {
        _forwardDialogState.update { state ->
            state?.copy(
                submitting = false,
                verifying = false,
                deliveryStatus = ForwardDeliveryStatus.UNCERTAIN,
                error = message,
                canRetryUncertainSend = false,
            )
        }
    }

    private suspend fun verifyForwardDeliveryInternal(auto: Boolean) {
        val attempt = forwardDeliveryAttempt ?: return
        _forwardDialogState.update { state ->
            state?.copy(
                submitting = false,
                verifying = true,
                deliveryStatus = ForwardDeliveryStatus.UNCERTAIN,
                error = "Проверяю, появилось ли сообщение в целевом чате…",
                canRetryUncertainSend = false,
            )
        }
        val delays = if (auto) {
            listOf(0L, 700L, 1_600L)
        } else {
            listOf(0L)
        }
        var completedRead = false
        var lastError: String? = null
        for (waitMillis in delays) {
            if (waitMillis > 0) delay(waitMillis)
            if (!userViewModel.repo.isActiveCredentialOwner(attempt.ownerKey)) {
                lastError = "Аккаунт изменился; результат нужно проверить в исходном аккаунте"
                break
            }
            when (
                val response = client.performRequest(
                    MessagesRequest(
                        streamId = attempt.destination.streamUuid,
                        topicId = attempt.destination.topicUuid,
                        pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
                        sortDirection = MessageSortDirection.DESCENDING,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    completedRead = true
                    val messages = response.value.filter {
                        it.streamUuid == attempt.destination.streamUuid &&
                            it.topicUuid == attempt.destination.topicUuid
                    }
                    repo.addStreamTopicMessages(
                        attempt.destination.streamUuid,
                        attempt.destination.topicUuid,
                        messages,
                    )
                    val matches = messages.filter { message ->
                        isExpectedForwardConfirmation(attempt, message) &&
                            message.uuid !in attempt.knownMatchingMessageUuids
                    }
                    if (matches.size == 1) {
                        markForwardCompleted(matches.single())
                        return
                    }
                    if (matches.size > 1) {
                        _forwardDialogState.update { state ->
                            state?.copy(
                                verifying = false,
                                deliveryStatus = ForwardDeliveryStatus.UNCERTAIN,
                                error =
                                    "В целевом чате найдено несколько одинаковых " +
                                        "сообщений; повторная отправка отключена",
                                canRetryUncertainSend = false,
                            )
                        }
                        return
                    }
                }

                is ApiResult.Error -> {
                    lastError = response.error.message
                        ?: "Не удалось проверить целевой чат"
                }
            }
        }
        _forwardDialogState.update { state ->
            state?.copy(
                verifying = false,
                deliveryStatus = ForwardDeliveryStatus.UNCERTAIN,
                error = if (completedRead) {
                    "Подтверждение не найдено. Можно проверить ещё раз; " +
                        "повторная отправка может создать дубль."
                } else {
                    lastError ?: "Не удалось проверить результат отправки"
                },
                canRetryUncertainSend = completedRead,
            )
        }
    }

    private fun markForwardCompleted(message: MessageResponse) {
        repo.addStreamTopicMessages(
            message.streamUuid,
            message.topicUuid,
            listOf(message),
        )
        forwardDeliveryAttempt = null
        _forwardDialogState.update { state ->
            state?.copy(
                submitting = false,
                verifying = false,
                deliveryStatus = ForwardDeliveryStatus.COMPLETED,
                error = null,
                canRetryUncertainSend = false,
            )
        }
    }

    private fun setForwardError(message: String) {
        _forwardDialogState.update { state ->
            state?.copy(
                submitting = false,
                verifying = false,
                error = message,
            )
        }
    }

    internal fun requestForwardQuote(messageUuid: String) {
        val canonicalUuid = parseCanonicalMessageUuid(messageUuid)
        if (canonicalUuid == null) {
            putForwardQuoteResolution(
                messageUuid,
                ForwardQuoteResolution.Unavailable,
            )
            return
        }
        viewModelScope.launch {
            val shouldLoad = forwardQuoteLoadMutex.withLock {
                if (
                    canonicalUuid in forwardQuoteLoadingUuids ||
                    _forwardQuoteResolutions.value[canonicalUuid] is
                    ForwardQuoteResolution.Ready
                ) {
                    false
                } else {
                    forwardQuoteLoadingUuids += canonicalUuid
                    putForwardQuoteResolution(
                        canonicalUuid,
                        ForwardQuoteResolution.Loading,
                    )
                    true
                }
            }
            if (!shouldLoad) return@launch
            try {
                val response = client.performRequest(
                    MessagesByIdsRequest(listOf(canonicalUuid)),
                )
                val resolution = when (response) {
                    is ApiResult.Success -> {
                        val message = response.value
                            .filter { it.uuid == canonicalUuid }
                            .singleOrNull()
                        if (message == null) {
                            ForwardQuoteResolution.Unavailable
                        } else {
                            repo.addStreamTopicMessages(
                                message.streamUuid,
                                message.topicUuid,
                                listOf(message),
                            )
                            ForwardQuoteResolution.Ready(message)
                        }
                    }

                    is ApiResult.Error -> ForwardQuoteResolution.Unavailable
                }
                putForwardQuoteResolution(canonicalUuid, resolution)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                putForwardQuoteResolution(
                    canonicalUuid,
                    ForwardQuoteResolution.Unavailable,
                )
            } finally {
                forwardQuoteLoadMutex.withLock {
                    forwardQuoteLoadingUuids -= canonicalUuid
                }
            }
        }
    }

    internal fun retryForwardQuote(messageUuid: String) {
        val canonicalUuid = parseCanonicalMessageUuid(messageUuid) ?: return
        _forwardQuoteResolutions.update { current -> current - canonicalUuid }
        requestForwardQuote(canonicalUuid)
    }

    internal fun openForwardQuoteSource(messageUuid: String) {
        val canonicalUuid = parseCanonicalMessageUuid(messageUuid)
        val message = canonicalUuid
            ?.let(_forwardQuoteResolutions.value::get)
            ?.let { it as? ForwardQuoteResolution.Ready }
            ?.message
        if (message == null) {
            requestForwardQuote(messageUuid)
            _actionError.value =
                "Исходное сообщение ещё загружается; повторите после загрузки"
            return
        }
        if (message.streamUuid == chatId && message.topicUuid == topicUuid) {
            requestMessageFocus(message.uuid)
            return
        }
        viewModelScope.launch {
            val ownerKey = userViewModel.repo.activeCredentialSnapshot().ownerKey
            if (ownerKey.isNullOrBlank()) {
                _actionError.value = "Активный аккаунт недоступен"
                return@launch
            }
            val stream = resolveForwardSourceStream(message.streamUuid)
            if (stream == null) {
                _actionError.value = "Чат исходного сообщения недоступен"
                return@launch
            }
            val topic = resolveForwardSourceTopic(
                streamUuid = message.streamUuid,
                topicUuid = message.topicUuid,
            )
            if (topic == null) {
                _actionError.value = "Топик исходного сообщения недоступен"
                return@launch
            }
            if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                _actionError.value =
                    "Аккаунт изменился; откройте исходное сообщение заново"
                return@launch
            }
            val isDirect = stream.isDirectProviderChat()
            openSourceMessageChannel.send(
                OpenSourceMessageEvent(
                    title = stream.name,
                    streamUuid = stream.uuid,
                    topicName = topic.name.takeUnless { isDirect },
                    topicUuid = topic.uuid,
                    isDirectMessages = isDirect,
                    messageUuid = message.uuid,
                ),
            )
        }
    }

    private suspend fun resolveForwardSourceStream(streamUuid: String): Stream? {
        repo.streams.value.firstOrNull { it.uuid == streamUuid }?.let { return it }
        return when (val response = client.performRequest(StreamsRequest())) {
            is ApiResult.Success -> {
                repo.setInitialStreams(response.value)
                response.value.firstOrNull { it.uuid == streamUuid }
            }

            is ApiResult.Error -> null
        }
    }

    private suspend fun resolveForwardSourceTopic(
        streamUuid: String,
        topicUuid: String,
    ): TopicsResponseData? {
        repo.streamTopics.value[streamUuid]
            .orEmpty()
            .firstOrNull { it.uuid == topicUuid }
            ?.let { return it }
        return when (val response = client.performRequest(TopicsRequest(streamUuid))) {
            is ApiResult.Success -> {
                val topics = response.value.filter { it.streamUuid == streamUuid }
                repo.addStreamTopics(streamUuid, topics)
                topics.firstOrNull { it.uuid == topicUuid }
            }

            is ApiResult.Error -> null
        }
    }

    private fun putForwardQuoteResolution(
        messageUuid: String,
        resolution: ForwardQuoteResolution,
    ) {
        _forwardQuoteResolutions.update { current ->
            val next = LinkedHashMap(current)
            next.remove(messageUuid)
            next[messageUuid] = resolution
            while (next.size > MAX_FORWARD_QUOTE_CACHE_ENTRIES) {
                next.remove(next.keys.first())
            }
            next
        }
    }

    fun deleteMessage(message: MessageResponse) {
        if (
            !canDeleteMessage(message) ||
            message.streamUuid != chatId ||
            message.topicUuid != topicUuid
        ) {
            _actionError.value = "Это сообщение нельзя удалить"
            return
        }
        viewModelScope.launch {
            val started = messageDeletionMutex.withLock {
                if (message.uuid in _deletingMessageUuids.value) {
                    false
                } else {
                    _deletingMessageUuids.update { it + message.uuid }
                    true
                }
            }
            if (!started) return@launch
            _actionError.value = null
            try {
                when (
                    val response = client.performRequest(
                        DeleteMessageRequest(message.uuid),
                    )
                ) {
                    is ApiResult.Success -> {
                        repo.removeMessage(
                            streamUuid = message.streamUuid,
                            topicUuid = message.topicUuid,
                            messageUuid = message.uuid,
                        )
                        clearDeletedComposerReference(message.uuid)
                        persistConversationStateSafely(
                            failureMessage =
                                "Сообщение удалено, но черновик не удалось обновить",
                        )
                    }

                    is ApiResult.Error -> {
                        _actionError.value = response.error.message
                            ?: "Не удалось удалить сообщение"
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: IllegalArgumentException) {
                _actionError.value = "Идентификатор сообщения повреждён"
            } catch (exception: Exception) {
                _actionError.value = "Не удалось удалить сообщение"
            } finally {
                _deletingMessageUuids.update { it - message.uuid }
            }
        }
    }

    fun clearEditingMessage() {
        restoreSuspendedDraftAfterEdit()
        composerChanged()
    }

    fun clearQuotedMessage() {
        _quotedMessage.value = null
        pendingQuotedMessageUuid = null
        composerChanged()
    }

    fun hasMyReaction(reaction: String, messageUuid: String): Boolean {
        return  !repo.userReactions.value.none { it.emojiName == reaction && it.messageUuid == messageUuid }
    }

    fun onSendClicked(context: Context) {
        if (_sending.value || !_conversationStateReady.value) return
        val snapshot = currentComposerSnapshot()
        if (
            snapshot.text.isBlank() &&
            snapshot.attachments.isEmpty() &&
            snapshot.editingMessage == null &&
            snapshot.quotedMessage == null
        ) {
            return
        }
        if (
            snapshot.editingMessage != null &&
            snapshot.text.isBlank()
        ) {
            _actionError.value = "Сообщение не может быть пустым"
            return
        }
        _sending.value = true
        _actionError.value = null
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val edit = snapshot.editingMessage
                if (edit != null) {
                    sendEditSnapshot(edit, snapshot)
                } else {
                    buildOutgoingContent(appContext, snapshot)?.let { content ->
                        enqueueAndSend(
                            content = content,
                            composerSnapshot = snapshot,
                            attachmentContext = appContext,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _actionError.value =
                    "Не удалось сохранить сообщение в очередь отправки"
            } finally {
                _uploadStatus.value = null
                _sending.value = false
            }
        }
    }

    fun startCall(callUrl: String, roomName: String) {
        if (
            _sending.value ||
            !_conversationStateReady.value ||
            callUrl.isBlank() ||
            roomName.isBlank()
        ) {
            return
        }
        _sending.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                if (enqueueAndSend(content = callUrl, composerSnapshot = null)) {
                    callLaunchChannel.send(CallLaunchEvent(roomName))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _actionError.value =
                    "Не удалось сохранить приглашение к звонку в очередь"
            } finally {
                _sending.value = false
            }
        }
    }

    private suspend fun buildOutgoingContent(
        context: Context,
        snapshot: ComposerSnapshot,
    ): String? {
        var content = ""
        snapshot.quotedMessage?.let { quoted ->
            content +=
                "[${quoted.user?.displayableName() ?: ""}]" +
                "(urn:user:${quoted.authorUuid}) " +
                "[said](urn:message:${quoted.uuid})\n" +
                "```quote\n${quoted.payload.content}\n```\n"
        }
        if (snapshot.text.isNotBlank()) {
            content += snapshot.text
        }
        if (content.length > MAX_MESSAGE_CHARS) {
            _actionError.value =
                "Сообщение с цитатой длиннее $MAX_MESSAGE_CHARS символов"
            return null
        }
        if (snapshot.attachments.isNotEmpty()) {
            val links = mutableListOf<String>()
            for ((index, attachment) in snapshot.attachments.withIndex()) {
                _uploadStatus.value =
                    "Загрузка ${index + 1} из ${snapshot.attachments.size}: " +
                    attachment.fileName
                when (
                    val response = client.uploadFile(
                        context,
                        attachment.uri,
                        chatId,
                    )
                ) {
                    is ApiResult.Success -> links += buildWorkspaceAttachmentMarkdown(
                        response.value.copy(
                            contentType = response.value.contentType
                                .ifBlank { attachment.contentType },
                            sizeBytes = response.value.sizeBytes
                                ?: attachment.sizeBytes,
                        ),
                    )

                    is ApiResult.Error -> {
                        _actionError.value = response.error.message
                            ?: "Не удалось загрузить файл"
                        return null
                    }
                }
            }
            if (content.isNotBlank()) content += "\n"
            content += links.joinToString("\n")
        }
        _uploadStatus.value = null
        if (content.length > MAX_MESSAGE_CHARS) {
            _actionError.value =
                "Сообщение с вложениями длиннее $MAX_MESSAGE_CHARS символов"
            return null
        }
        return content.takeIf(String::isNotBlank)
    }

    private suspend fun enqueueAndSend(
        content: String,
        composerSnapshot: ComposerSnapshot?,
        existingEntry: PersistedOutboxEntry? = null,
        attachmentContext: Context? = null,
    ): Boolean {
        val ownerKey = ensureConversationOwnerKey()
        val currentUserUuid = userViewModel.repo.activeCredentialSnapshot().userId
        if (ownerKey.isNullOrBlank() || currentUserUuid.isNullOrBlank()) {
            _actionError.value = "Активный аккаунт недоступен"
            return false
        }
        if (
            existingEntry == null &&
            _outboxEntries.value.size >= MAX_OUTBOX_ENTRIES
        ) {
            _actionError.value =
                "Очередь отправки заполнена. Проверьте или удалите старые записи."
            return false
        }
        val now = OffsetDateTime.now()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val knownMatchingMessageUuids = (
            existingEntry?.knownMatchingMessageUuids.orEmpty() +
                currentMatchingServerMessageUuids(content)
        ).distinct().takeLast(MAX_KNOWN_MATCHING_MESSAGE_UUIDS)
        val entry = (
            existingEntry ?: PersistedOutboxEntry(
                localMessageUuid = "local-${UUID.randomUUID()}",
                streamUuid = chatId,
                topicUuid = topicUuid,
                content = content,
                createdAt = now,
                status = PersistedOutboxStatus.SENDING,
            )
        ).copy(
            lastAttemptAt = now,
            knownMatchingMessageUuids = knownMatchingMessageUuids,
            status = PersistedOutboxStatus.SENDING,
            errorMessage = null,
        )
        draftPersistenceJob?.cancelAndJoin()
        val previousOutbox = _outboxEntries.value
        val nextOutbox = previousOutbox
            .filterNot { it.localMessageUuid == entry.localMessageUuid } + entry
        _outboxEntries.value = nextOutbox
        try {
            persistEnqueuedState(
                ownerKey = ownerKey,
                nextOutbox = nextOutbox,
                composerSnapshot = composerSnapshot,
            )
        } catch (cancellation: CancellationException) {
            _outboxEntries.value = previousOutbox
            throw cancellation
        } catch (exception: Exception) {
            _outboxEntries.value = previousOutbox
            throw exception
        }
        addOrReplaceOptimisticMessage(entry, currentUserUuid)
        requestMessageFocus(entry.localMessageUuid)
        composerSnapshot?.let { snapshot ->
            removeSentSnapshotFromComposer(snapshot)
            try {
                persistConversationState()
                var cleanupFailed = false
                if (attachmentContext != null) {
                    snapshot.attachments
                        .filter { isOwnedIncomingAttachment(it.uri) }
                        .forEach { attachment ->
                            if (
                                !deleteOwnedIncomingAttachment(
                                    attachmentContext,
                                    attachment.uri,
                                )
                            ) {
                                cleanupFailed = true
                            }
                        }
                }
                if (cleanupFailed) {
                    _actionError.value =
                        "Сообщение в очереди, но временную копию файла " +
                            "не удалось очистить"
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _actionError.value =
                    "Сообщение в очереди, но новый черновик не удалось сохранить"
            }
        }
        if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
            updateOutboxFailure(
                localMessageUuid = entry.localMessageUuid,
                status = PersistedOutboxStatus.FAILED,
                errorMessage =
                    "Аккаунт изменился до отправки. Вернитесь к исходному аккаунту.",
            )
            return false
        }

        return when (
            val response = client.performRequest(
                SendMessageRequest(
                    streamUuid = entry.streamUuid,
                    topicUuid = entry.topicUuid,
                    content = entry.content,
                ),
            )
        ) {
            is ApiResult.Success -> {
                if (isExpectedSendConfirmation(entry, response.value)) {
                    repo.replaceMessage(entry.localMessageUuid, response.value)
                    requestMessageFocus(response.value.uuid)
                    removeConfirmedOutboxEntry(entry.localMessageUuid)
                    true
                } else {
                    updateOutboxFailure(
                        localMessageUuid = entry.localMessageUuid,
                        status = PersistedOutboxStatus.UNCERTAIN,
                        errorMessage =
                            "Сервер вернул подтверждение из другого чата",
                    )
                    verifyOutboxOnServer(
                        localMessageUuid = entry.localMessageUuid,
                        reportMissing = false,
                    )
                    false
                }
            }

            is ApiResult.Error -> {
                val status = classifyOutboxFailure(response.error)
                val errorText = if (response.error.code == "ACCOUNT_CHANGED") {
                    "Аккаунт изменился во время отправки. " +
                        "Вернитесь к исходному аккаунту и проверьте результат."
                } else {
                    response.error.message
                        ?: "Не удалось отправить сообщение"
                }
                updateOutboxFailure(entry.localMessageUuid, status, errorText)
                if (
                    status == PersistedOutboxStatus.UNCERTAIN &&
                    response.error.code != "ACCOUNT_CHANGED"
                ) {
                    verifyOutboxOnServer(
                        localMessageUuid = entry.localMessageUuid,
                        reportMissing = false,
                    )
                } else {
                    _actionError.value = errorText
                }
                false
            }
        }
    }

    fun openAttachment(
        context: Context,
        attachment: WorkspaceAttachment,
    ) {
        if (_downloadingAttachmentUuid.value != null) return
        val accountOwnerKey = userViewModel.activeAccountId.value
        if (accountOwnerKey.isNullOrBlank()) {
            _actionError.value = "Активный аккаунт недоступен"
            return
        }
        val appContext = context.applicationContext
        _actionError.value = null
        _downloadingAttachmentUuid.value = attachment.uuid
        viewModelScope.launch {
            try {
                when (val response = client.downloadFile(attachment.uuid)) {
                    is ApiResult.Success -> {
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val directory = accountAttachmentCacheDirectory(
                                    appContext.cacheDir,
                                    accountOwnerKey,
                                )
                                if (!directory.exists() && !directory.mkdirs()) {
                                    throw java.io.IOException(
                                        "Attachment cache is unavailable",
                                    )
                                }
                                pruneAttachmentCache(
                                    directory,
                                    response.value.size.toLong(),
                                )
                                File(
                                    directory,
                                    "${attachment.uuid.take(8)}-" +
                                        safeLocalFileName(attachment.fileName),
                                ).apply { writeBytes(response.value) }
                            }
                            val contentUri = FileProvider.getUriForFile(
                                appContext,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(contentUri, attachment.contentType)
                                .addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_ACTIVITY_NEW_TASK,
                                )
                            runCatching {
                                appContext.startActivity(intent)
                            }.onFailure {
                                _actionError.value =
                                    "Нет приложения для открытия этого файла"
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            _actionError.value =
                                "Не удалось подготовить файл к открытию"
                        }
                    }

                    is ApiResult.Error -> {
                        _actionError.value = response.error.message
                            ?: "Не удалось скачать файл"
                    }
                }
            } finally {
                _downloadingAttachmentUuid.value = null
            }
        }
    }

    private suspend fun describeAttachment(
        context: Context,
        uri: Uri,
    ): SelectedLocalAttachment? = withContext(Dispatchers.IO) {
        try {
            var displayName: String? = null
            var sizeBytes: Long? = null
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { displayName = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { sizeBytes = cursor.getLong(it) }
                }
            }
            val contentType = context.contentResolver.getType(uri)
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.matches(Regex("""[a-z0-9.+-]+/[a-z0-9.+-]+""")) }
                ?: "application/octet-stream"
            if (sizeBytes == 0L) {
                _actionError.value = "Нельзя прикрепить пустой файл"
                return@withContext null
            }
            if ((sizeBytes ?: 0L) > MAX_COMPOSER_ATTACHMENT_BYTES) {
                _actionError.value = "Файл больше 25 MiB"
                return@withContext null
            }
            SelectedLocalAttachment(
                uri = uri,
                fileName = safeLocalFileName(
                    displayName ?: uri.lastPathSegment ?: "file",
                ),
                contentType = contentType,
                sizeBytes = sizeBytes,
            )
        } catch (security: SecurityException) {
            _actionError.value = "Выбранный файл больше недоступен"
            null
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            _actionError.value = "Не удалось прочитать выбранный файл"
            null
        }
    }

    private fun persistReadPermission(context: Context, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (exception: Exception) {
            _actionError.value =
                "Провайдер файла не разрешил сохранить доступ после перезапуска"
        }
    }

    private fun currentComposerSnapshot(): ComposerSnapshot =
        ComposerSnapshot(
            revision = composerRevision,
            text = _messageText.value,
            attachments = _attachments.value,
            editingMessage = editingMessage,
            quotedMessage = _quotedMessage.value,
        )

    private fun composerChanged() {
        composerRevision += 1
        draftUpdatedAt = OffsetDateTime.now()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        if (!_conversationStateReady.value) return
        draftPersistenceJob?.cancel()
        draftPersistenceJob = viewModelScope.launch {
            delay(DRAFT_PERSISTENCE_DEBOUNCE_MILLIS)
            persistConversationStateSafely(
                failureMessage = "Не удалось сохранить черновик",
            )
        }
        scheduleRemoteDraftSync()
    }

    private fun removeSentSnapshotFromComposer(snapshot: ComposerSnapshot) {
        // A user may start editing another message while this request is still
        // in flight. That edit is a different intent and must never be consumed
        // just because its text happens to share the sent prefix.
        if (editingMessage != null || pendingEditingMessageUuid != null) return
        var changed = false
        if (composerRevision == snapshot.revision) {
            _messageText.value = ""
            _attachments.value = emptyList()
            _quotedMessage.value = null
            pendingQuotedMessageUuid = null
            changed = true
        } else {
            val currentText = _messageText.value
            if (
                snapshot.text.isNotEmpty() &&
                currentText.startsWith(snapshot.text)
            ) {
                _messageText.value = currentText.removePrefix(snapshot.text)
                changed = true
            }
            val sentAttachmentUris = snapshot.attachments
                .mapTo(mutableSetOf()) { it.uri }
            if (sentAttachmentUris.isNotEmpty()) {
                val remainingAttachments = _attachments.value.filterNot {
                    it.uri in sentAttachmentUris
                }
                if (remainingAttachments != _attachments.value) {
                    _attachments.value = remainingAttachments
                    changed = true
                }
            }
            if (
                snapshot.quotedMessage?.uuid != null &&
                _quotedMessage.value?.uuid == snapshot.quotedMessage.uuid
            ) {
                _quotedMessage.value = null
                pendingQuotedMessageUuid = null
                changed = true
            }
        }
        if (changed) {
            composerRevision += 1
            draftUpdatedAt = OffsetDateTime.now()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            scheduleRemoteDraftSync()
        }
    }

    private suspend fun restoreConversationState() {
        val credentials = userViewModel.repo.activeCredentialSnapshot()
        val ownerKey = credentials.ownerKey
        conversationOwnerKey = ownerKey
        if (ownerKey.isNullOrBlank()) return
        val initialRevision = composerRevision
        val selectedState = conversationStateStore.read(
            ownerKey = ownerKey,
            streamUuid = chatId,
            topicUuid = topicUuid,
            draftStorageSlot = selectedDraftStorageSlot,
        )?.let { state ->
            sanitizePersistedConversationState(
                state = state,
                expectedStreamUuid = chatId,
                expectedTopicUuid = topicUuid,
            )
        }
        val baseState = if (selectedDraftStorageSlot == null) {
            selectedState
        } else {
            conversationStateStore.read(
                ownerKey = ownerKey,
                streamUuid = chatId,
                topicUuid = topicUuid,
                draftStorageSlot = null,
            )?.let { state ->
                sanitizePersistedConversationState(
                    state = state,
                    expectedStreamUuid = chatId,
                    expectedTopicUuid = topicUuid,
                )
            }
        }
        if (selectedState == null && baseState == null) return
        val restored = selectedState ?: PersistedConversationState(
            draftStorageSlot = selectedDraftStorageSlot,
        )
        val restoredOutbox = baseState
            ?.outbox
            .orEmpty()
            .map(::interruptedOutboxEntry)
        _outboxEntries.value = restoredOutbox
        _draftSyncState.value = restored.serverDraft
        draftUpdatedAt = restored.draftUpdatedAt
        credentials.userId?.takeIf(String::isNotBlank)?.let { currentUserUuid ->
            restoredOutbox.forEach { entry ->
                addOrReplaceOptimisticMessage(entry, currentUserUuid)
            }
        }
        if (composerRevision == initialRevision) {
            _messageText.value = restored.draftText
            _attachments.value = restored.attachments
                .mapNotNull(::selectedAttachment)
            pendingEditingMessageUuid = restored.editingMessageUuid
            pendingQuotedMessageUuid = restored.quotedMessageUuid
            suspendedDraft = restored.suspendedDraft
        }
        if (restoredOutbox != baseState?.outbox.orEmpty()) {
            persistConversationStateSafely(
                failureMessage =
                    "Не удалось обновить состояние прерванной отправки",
            )
        }
    }

    private suspend fun ensureConversationOwnerKey(): String? {
        conversationOwnerKey?.takeIf(String::isNotBlank)?.let { return it }
        return userViewModel.repo.activeCredentialSnapshot().ownerKey
            ?.takeIf(String::isNotBlank)
            ?.also { conversationOwnerKey = it }
    }

    private fun persistedAttachments(
        attachments: List<SelectedLocalAttachment> = _attachments.value,
    ): List<PersistedAttachment> =
        attachments.map { attachment ->
            PersistedAttachment(
                uri = attachment.uri.toString(),
                fileName = attachment.fileName,
                contentType = attachment.contentType,
                sizeBytes = attachment.sizeBytes,
            )
        }

    private fun selectedAttachment(
        attachment: PersistedAttachment,
    ): SelectedLocalAttachment? {
        val uri = runCatching { Uri.parse(attachment.uri) }.getOrNull()
            ?: return null
        return SelectedLocalAttachment(
            uri = uri,
            fileName = attachment.fileName,
            contentType = attachment.contentType,
            sizeBytes = attachment.sizeBytes,
        )
    }

    private fun restoreSuspendedDraftAfterEdit() {
        val restored = suspendedDraft
        editingMessage = null
        pendingEditingMessageUuid = null
        _editingMessageBackupText.value = null
        suspendedDraft = null
        _messageText.value = restored?.text.orEmpty()
        _attachments.value = restored?.attachments
            .orEmpty()
            .mapNotNull(::selectedAttachment)
        pendingQuotedMessageUuid = restored?.quotedMessageUuid
        _quotedMessage.value = restored?.quotedMessageUuid?.let { quotedUuid ->
            repo.messagesPool.value.firstOrNull { it.uuid == quotedUuid }
                ?: repo.streamTopicMessages.value
                    .values
                    .flatten()
                    .firstOrNull { it.uuid == quotedUuid }
        }
    }

    private fun clearDeletedComposerReference(messageUuid: String) {
        var changed = false
        if (
            editingMessage?.uuid == messageUuid ||
            pendingEditingMessageUuid == messageUuid
        ) {
            restoreSuspendedDraftAfterEdit()
            changed = true
        }
        if (
            _quotedMessage.value?.uuid == messageUuid ||
            pendingQuotedMessageUuid == messageUuid
        ) {
            _quotedMessage.value = null
            pendingQuotedMessageUuid = null
            changed = true
        }
        if (suspendedDraft?.quotedMessageUuid == messageUuid) {
            suspendedDraft = suspendedDraft?.copy(quotedMessageUuid = null)
            changed = true
        }
        if (changed) {
            composerRevision += 1
        }
    }

    private fun currentPersistedConversationState(
        outbox: List<PersistedOutboxEntry> = _outboxEntries.value,
    ): PersistedConversationState =
        PersistedConversationState(
            route = PersistedConversationRoute(
                streamUuid = chatId,
                topicUuid = topicUuid,
                chatTitle = chatTitle.trim().take(MAX_PERSISTED_ROUTE_NAME_CHARS),
                topicName = topicName
                    ?.trim()
                    ?.take(MAX_PERSISTED_ROUTE_NAME_CHARS)
                    ?.takeIf(String::isNotBlank),
                isDirectMessages = isDirectMessages,
            ),
            draftStorageSlot = selectedDraftStorageSlot,
            draftText = _messageText.value,
            editingMessageUuid = editingMessage?.uuid
                ?: pendingEditingMessageUuid,
            quotedMessageUuid = _quotedMessage.value?.uuid
                ?: pendingQuotedMessageUuid,
            attachments = persistedAttachments(),
            suspendedDraft = suspendedDraft,
            outbox = outbox,
            draftUpdatedAt = draftUpdatedAt,
            serverDraft = _draftSyncState.value,
        )

    private suspend fun persistEnqueuedState(
        ownerKey: String,
        nextOutbox: List<PersistedOutboxEntry>,
        composerSnapshot: ComposerSnapshot?,
    ) {
        conversationPersistenceMutex.withLock {
            var state = currentPersistedConversationState(outbox = nextOutbox)
            if (
                composerSnapshot != null &&
                composerRevision == composerSnapshot.revision
            ) {
                state = state.copy(
                    draftText = "",
                    editingMessageUuid = null,
                    quotedMessageUuid = null,
                    attachments = emptyList(),
                    suspendedDraft = null,
                )
            }
            persistStateAndSharedOutbox(ownerKey, state)
        }
    }

    private suspend fun persistConversationState() {
        val ownerKey = ensureConversationOwnerKey() ?: return
        conversationPersistenceMutex.withLock {
            val state = currentPersistedConversationState()
            persistStateAndSharedOutbox(ownerKey, state)
        }
    }

    private suspend fun persistStateAndSharedOutbox(
        ownerKey: String,
        state: PersistedConversationState,
    ) {
        val existingBase = if (selectedDraftStorageSlot == null) {
            null
        } else {
            conversationStateStore.read(
                ownerKey = ownerKey,
                streamUuid = chatId,
                topicUuid = topicUuid,
                draftStorageSlot = null,
            )
        }
        val plan = planConversationStateStorage(state, existingBase)
        plan.baseState?.let { base ->
            persistStorageEntry(
                ownerKey = ownerKey,
                state = base,
                draftStorageSlot = null,
            )
        }
        persistStorageEntry(
            ownerKey = ownerKey,
            state = plan.selectedState,
            draftStorageSlot = selectedDraftStorageSlot,
        )
    }

    private suspend fun persistStorageEntry(
        ownerKey: String,
        state: PersistedConversationState,
        draftStorageSlot: String?,
    ) {
        if (state.hasConversationWork()) {
            conversationStateStore.write(
                ownerKey = ownerKey,
                streamUuid = chatId,
                topicUuid = topicUuid,
                state = state,
                draftStorageSlot = draftStorageSlot,
            )
        } else {
            conversationStateStore.remove(
                ownerKey = ownerKey,
                streamUuid = chatId,
                topicUuid = topicUuid,
                draftStorageSlot = draftStorageSlot,
            )
        }
    }

    private suspend fun persistConversationStateSafely(
        failureMessage: String,
    ) {
        try {
            persistConversationState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            _actionError.value = failureMessage
        }
    }

    private suspend fun persistBeforeRemoteDraftMutation(
        failureMessage: String,
    ): Boolean = try {
        persistConversationState()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        _actionError.value = failureMessage
        false
    }

    private fun scheduleRemoteDraftSync(
        initialDelayMillis: Long = REMOTE_DRAFT_SYNC_DEBOUNCE_MILLIS,
    ) {
        if (!_conversationStateReady.value) return
        remoteDraftSyncJob?.cancel()
        remoteDraftSyncJob = viewModelScope.launch {
            var delayMillis = initialDelayMillis
            var retry = 0
            while (true) {
                delay(delayMillis)
                val shouldRetry = syncCurrentDraftSafely()
                if (!shouldRetry) break
                retry += 1
                delayMillis = REMOTE_DRAFT_RETRY_DELAYS_MILLIS[
                    retry.coerceAtMost(
                        REMOTE_DRAFT_RETRY_DELAYS_MILLIS.lastIndex,
                    )
                ]
            }
        }
    }

    private suspend fun syncCurrentDraftSafely(): Boolean =
        remoteDraftMutex.withLock {
            if (
                editingMessage != null ||
                pendingEditingMessageUuid != null
            ) {
                return@withLock false
            }
            val credentials = userViewModel.repo.activeCredentialSnapshot()
            val ownerKey = credentials.ownerKey?.takeIf(String::isNotBlank)
                ?: return@withLock false
            val projectId = credentials.projectId?.takeIf(String::isNotBlank)
                ?: return@withLock false
            val userUuid = credentials.userId?.takeIf(String::isNotBlank)
                ?: return@withLock false
            if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                return@withLock false
            }
            val localContent = _messageText.value
            var state = try {
                beginDraftSync(_draftSyncState.value, localContent)
            } catch (exception: IllegalArgumentException) {
                _actionError.value =
                    "Черновик не соответствует требованиям сервера"
                return@withLock false
            }
            _draftSyncState.value = state
            persistConversationStateSafely(
                failureMessage =
                    "Не удалось сохранить очередь синхронизации черновика",
            )
            if (state == null || state.status == PersistedDraftSyncStatus.CONFLICT) {
                return@withLock false
            }

            if (state.entityTag == null) {
                val sentContent = try {
                    draftCreatePayload(state, localContent)
                } catch (exception: IllegalArgumentException) {
                    _draftSyncState.value = null
                    persistConversationStateSafely(
                        failureMessage =
                            "Не удалось очистить пустой локальный черновик",
                    )
                    return@withLock false
                }
                state = markDraftSaving(state)
                _draftSyncState.value = state
                if (!persistBeforeRemoteDraftMutation(
                    failureMessage =
                        "Не удалось подготовить создание черновика",
                )) {
                    return@withLock false
                }
                when (
                    val response = client.performRequest(
                        CreateDraftRequest(
                            draftUuid = state.draftUuid,
                            streamUuid = chatId,
                            topicUuid = topicUuid,
                            content = sentContent,
                        ),
                    )
                ) {
                    is ApiResult.Success -> {
                        if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                            return@withLock false
                        }
                        val server = try {
                            validateDraftResponse(
                                response = response.value,
                                expectedDraftUuid = state.draftUuid,
                                expectedProjectId = projectId,
                                expectedUserUuid = userUuid,
                                expectedStreamUuid = chatId,
                                expectedTopicUuid = topicUuid,
                                responseEntityTag =
                                    response.metadata.entityTag,
                            )
                        } catch (exception: IllegalArgumentException) {
                            _draftSyncState.value = markDraftSyncFailed(
                                state,
                                ru.genesiscorporation.workspace.beta.data.remote.ApiError(
                                    "Сервер вернул некорректный черновик",
                                    "MALFORMED_RESPONSE",
                                    ApiErrorKind.MALFORMED_RESPONSE,
                                ),
                            )
                            persistConversationStateSafely(
                                failureMessage =
                                    "Не удалось сохранить ошибку синхронизации",
                            )
                            return@withLock true
                        }
                        state = applyDraftSaveSuccess(
                            state = state,
                            server = server,
                            sentContent = sentContent,
                            currentLocalContent = _messageText.value,
                        )
                        _draftSyncState.value = state
                        persistConversationStateSafely(
                            failureMessage =
                                "Черновик сохранён на сервере, но локальное подтверждение потеряно",
                        )
                    }

                    is ApiResult.Error -> {
                        if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                            return@withLock false
                        }
                        logDraftRemoteFailure("create", response.error)
                        _draftSyncState.value =
                            markDraftSyncFailed(state, response.error)
                        persistConversationStateSafely(
                            failureMessage =
                                "Не удалось сохранить ошибку синхронизации",
                        )
                        return@withLock isRetryableDraftError(response.error)
                    }
                }
            }

            state = _draftSyncState.value ?: return@withLock false
            if (state.deleteRequested || _messageText.value.isBlank()) {
                return@withLock deleteServerDraft(
                    state = state.copy(
                        deleteRequested = true,
                        status = PersistedDraftSyncStatus.DELETING,
                    ),
                    ownerKey = ownerKey,
                    projectId = projectId,
                    userUuid = userUuid,
                )
            }
            if (
                normalizeRemoteDraftText(state.syncedContent.orEmpty()) ==
                    normalizeRemoteDraftText(_messageText.value)
            ) {
                _draftSyncState.value = state.copy(
                    status = PersistedDraftSyncStatus.SAVED,
                    errorMessage = null,
                )
                persistConversationStateSafely(
                    failureMessage =
                        "Не удалось сохранить подтверждение черновика",
                )
                return@withLock false
            }

            val entityTag = state.entityTag ?: return@withLock true
            val sentContent = _messageText.value
            state = markDraftSaving(state)
            _draftSyncState.value = state
            if (!persistBeforeRemoteDraftMutation(
                failureMessage =
                    "Не удалось подготовить обновление черновика",
            )) {
                return@withLock false
            }
            when (
                val response = client.performRequest(
                    UpdateDraftRequest(
                        draftUuid = state.draftUuid,
                        content = sentContent,
                        entityTag = entityTag,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                        return@withLock false
                    }
                    val server = try {
                        validateDraftResponse(
                            response = response.value,
                            expectedDraftUuid = state.draftUuid,
                            expectedProjectId = projectId,
                            expectedUserUuid = userUuid,
                            expectedStreamUuid = chatId,
                            expectedTopicUuid = topicUuid,
                            responseEntityTag = response.metadata.entityTag,
                        )
                    } catch (exception: IllegalArgumentException) {
                        _draftSyncState.value = markDraftSyncFailed(
                            state,
                            ru.genesiscorporation.workspace.beta.data.remote.ApiError(
                                "Сервер вернул некорректный черновик",
                                "MALFORMED_RESPONSE",
                                ApiErrorKind.MALFORMED_RESPONSE,
                            ),
                        )
                        persistConversationStateSafely(
                            failureMessage =
                                "Не удалось сохранить ошибку синхронизации",
                        )
                        return@withLock true
                    }
                    _draftSyncState.value = applyDraftSaveSuccess(
                        state = state,
                        server = server,
                        sentContent = sentContent,
                        currentLocalContent = _messageText.value,
                    )
                    persistConversationStateSafely(
                        failureMessage =
                            "Черновик обновлён, но локальное подтверждение потеряно",
                    )
                    _draftSyncState.value?.status ==
                        PersistedDraftSyncStatus.LOCAL
                }

                is ApiResult.Error -> {
                    if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                        return@withLock false
                    }
                    logDraftRemoteFailure("update", response.error)
                    if (response.error.httpStatus == 404) {
                        _draftSyncState.value = beginDraftSync(
                            existing = null,
                            localContent = _messageText.value,
                        )
                        persistConversationStateSafely(
                            failureMessage =
                                "Не удалось подготовить новый черновик после внешнего удаления",
                        )
                        return@withLock _draftSyncState.value != null
                    }
                    val conflict = parseDraftConflictBody(
                        body = response.error.conflictBody,
                        entityTag = response.error.entityTag,
                        expectedDraftUuid = state.draftUuid,
                        expectedProjectId = projectId,
                        expectedUserUuid = userUuid,
                        expectedStreamUuid = chatId,
                        expectedTopicUuid = topicUuid,
                    )
                    if (response.error.httpStatus == 412 && conflict != null) {
                        _draftSyncState.value = applyDraftConflict(
                            state,
                            conflict,
                            _messageText.value,
                        )
                        persistConversationStateSafely(
                            failureMessage =
                                "Не удалось сохранить конфликт черновика",
                        )
                        return@withLock false
                    }
                    _draftSyncState.value =
                        markDraftSyncFailed(state, response.error)
                    persistConversationStateSafely(
                        failureMessage =
                            "Не удалось сохранить ошибку синхронизации",
                    )
                    isRetryableDraftError(response.error)
                }
            }
        }

    private suspend fun deleteServerDraft(
        state: PersistedServerDraftState,
        ownerKey: String,
        projectId: String,
        userUuid: String,
    ): Boolean {
        var deleting = state
        _draftSyncState.value = deleting
        if (!persistBeforeRemoteDraftMutation(
            failureMessage =
                "Не удалось сохранить удаление черновика до запроса",
        )) {
            return false
        }
        repeat(MAX_DRAFT_DELETE_CONFLICT_RETRIES + 1) {
            val entityTag = deleting.entityTag ?: return true
            when (
                val response = client.performRequest(
                    DeleteDraftRequest(
                        draftUuid = deleting.draftUuid,
                        entityTag = entityTag,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                        return false
                    }
                    _draftSyncState.value = null
                    persistConversationStateSafely(
                        failureMessage =
                            "Черновик удалён на сервере, но локальная запись осталась",
                    )
                    return false
                }

                is ApiResult.Error -> {
                    if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                        return false
                    }
                    logDraftRemoteFailure("delete", response.error)
                    if (response.error.httpStatus == 404) {
                        _draftSyncState.value = null
                        persistConversationStateSafely(
                            failureMessage =
                                "Не удалось очистить уже удалённый черновик",
                        )
                        return false
                    }
                    val conflict = parseDraftConflictBody(
                        body = response.error.conflictBody,
                        entityTag = response.error.entityTag,
                        expectedDraftUuid = deleting.draftUuid,
                        expectedProjectId = projectId,
                        expectedUserUuid = userUuid,
                        expectedStreamUuid = chatId,
                        expectedTopicUuid = topicUuid,
                    )
                    if (response.error.httpStatus == 412 && conflict != null) {
                        if (
                            canRetryMatchingDraftDeleteConflict(
                                deleting,
                                conflict,
                            )
                        ) {
                            deleting = deleting.copy(
                                entityTag = conflict.entityTag,
                                serverRevision = conflict.response.revision,
                                serverUpdatedAt = conflict.response.updatedAt,
                            )
                            _draftSyncState.value = deleting
                            if (!persistBeforeRemoteDraftMutation(
                                failureMessage =
                                    "Не удалось сохранить новую ревизию удаления",
                            )) {
                                return false
                            }
                            return@repeat
                        }
                        _draftSyncState.value = applyDraftConflict(
                            deleting,
                            conflict,
                            currentLocalContent = "",
                        )
                        persistConversationStateSafely(
                            failureMessage =
                                "Не удалось сохранить конфликт удаления",
                        )
                        return false
                    }
                    _draftSyncState.value =
                        markDraftSyncFailed(deleting, response.error)
                    persistConversationStateSafely(
                        failureMessage =
                            "Не удалось сохранить ошибку удаления черновика",
                    )
                    return isRetryableDraftError(response.error)
                }
            }
        }
        return false
    }

    private fun addOrReplaceOptimisticMessage(
        entry: PersistedOutboxEntry,
        currentUserUuid: String,
    ) {
        val optimistic = MessageResponse(
            uuid = entry.localMessageUuid,
            updatedAt = entry.createdAt,
            createdAt = entry.createdAt,
            streamUuid = entry.streamUuid,
            topicUuid = entry.topicUuid,
            userUuid = currentUserUuid,
            authorUuid = currentUserUuid,
            payload = MessageResponsePayload("markdown", entry.content),
            isOwn = true,
            reactions = emptyMap(),
        )
        repo.replaceMessage(entry.localMessageUuid, optimistic)
    }

    private fun currentMatchingServerMessageUuids(
        content: String,
    ): List<String> {
        val key = "$chatId.$topicUuid"
        return repo.streamTopicMessages.value[key]
            .orEmpty()
            .asSequence()
            .filter { message ->
                message.isOwn &&
                    !message.uuid.startsWith("local-") &&
                    message.payload.content == content
            }
            .map(MessageResponse::uuid)
            .toList()
    }

    private suspend fun removeConfirmedOutboxEntry(localMessageUuid: String) {
        _outboxEntries.update { current ->
            current.filterNot { it.localMessageUuid == localMessageUuid }
        }
        persistConversationStateSafely(
            failureMessage = "Сообщение отправлено, но очередь не удалось очистить",
        )
    }

    private suspend fun updateOutboxFailure(
        localMessageUuid: String,
        status: PersistedOutboxStatus,
        errorMessage: String,
    ) {
        _outboxEntries.update { current ->
            current.map { entry ->
                if (entry.localMessageUuid == localMessageUuid) {
                    entry.copy(
                        status = status,
                        errorMessage = errorMessage,
                    )
                } else {
                    entry
                }
            }
        }
        persistConversationStateSafely(
            failureMessage = "Не удалось сохранить состояние очереди отправки",
        )
    }

    private suspend fun verifyOutboxOnServer(
        localMessageUuid: String,
        reportMissing: Boolean,
    ): Boolean {
        val entry = _outboxEntries.value
            .firstOrNull { it.localMessageUuid == localMessageUuid }
            ?: return true
        if (localMessageUuid in _verifyingOutbox.value) return false
        _verifyingOutbox.update { it + localMessageUuid }
        try {
            return when (
                val response = client.performRequest(
                    MessagesRequest(entry.streamUuid, entry.topicUuid),
                )
            ) {
                is ApiResult.Success -> {
                    repo.addStreamTopicMessages(
                        entry.streamUuid,
                        entry.topicUuid,
                        response.value,
                    )
                    reconcileOutboxWithServer(response.value)
                    val confirmed = _outboxEntries.value.none {
                        it.localMessageUuid == localMessageUuid
                    }
                    if (!confirmed && reportMissing) {
                        _actionError.value =
                            "Подтверждение на сервере не найдено. " +
                            "Повторная отправка может создать дубль."
                    }
                    confirmed
                }

                is ApiResult.Error -> {
                    if (reportMissing) {
                        _actionError.value = response.error.message
                            ?: "Не удалось проверить отправку на сервере"
                    }
                    false
                }
            }
        } finally {
            _verifyingOutbox.update { it - localMessageUuid }
        }
    }

    private fun logDraftRemoteFailure(
        operation: String,
        error: ApiError,
    ) {
        Log.w(
            DRAFT_SYNC_LOG_TAG,
            "$operation failed: status=${error.httpStatus ?: "none"}, kind=${error.kind}",
        )
    }

    private suspend fun reconcileOutboxWithServer(
        serverMessages: List<MessageResponse>,
    ) {
        val matches = reconcileUncertainOutbox(
            outbox = _outboxEntries.value,
            serverMessages = serverMessages,
        )
        if (matches.isEmpty()) return
        matches.forEach { match ->
            repo.replaceMessage(
                match.localMessageUuid,
                match.serverMessage,
            )
        }
        val confirmedLocalUuids = matches
            .mapTo(mutableSetOf(), OutboxReconciliation::localMessageUuid)
        _outboxEntries.update { current ->
            current.filterNot { it.localMessageUuid in confirmedLocalUuids }
        }
        persistConversationStateSafely(
            failureMessage =
                "Отправка подтверждена, но очередь не удалось очистить",
        )
    }

    private fun restoreComposerReferences(messages: List<MessageResponse>) {
        pendingEditingMessageUuid?.let { editingUuid ->
            messages.firstOrNull { it.uuid == editingUuid }?.let { message ->
                editingMessage = message
                _editingMessageBackupText.value = message.payload.content
            }
        }
        pendingQuotedMessageUuid?.let { quotedUuid ->
            messages.firstOrNull { it.uuid == quotedUuid }?.let { message ->
                _quotedMessage.value = message
            }
        }
    }

    private fun finalizeRestoredComposerReferences() {
        if (pendingEditingMessageUuid != null && editingMessage == null) {
            val recoveredEditText = _messageText.value
            restoreSuspendedDraftAfterEdit()
            _messageText.value = mergeRecoveredDraftTexts(
                originalDraft = _messageText.value,
                recoveredEdit = recoveredEditText,
            )
            composerRevision += 1
            _actionError.value =
                "Исходное сообщение для редактирования недоступно; " +
                "оба текста сохранены в обычном черновике"
        }
        if (pendingQuotedMessageUuid != null && _quotedMessage.value == null) {
            pendingQuotedMessageUuid = null
            _actionError.value =
                "Исходное сообщение для цитаты больше недоступно"
        }
    }

    private companion object {
        const val DRAFT_SYNC_LOG_TAG = "WorkspaceDraftSync"
        const val MAX_COMPOSER_ATTACHMENTS = 10
        const val MAX_COMPOSER_ATTACHMENT_BYTES = 25L * 1024L * 1024L
        const val MAX_MESSAGE_CHARS = 40_000
        private const val MAX_PERSISTED_ROUTE_NAME_CHARS = 512
        const val MAX_OUTBOX_ENTRIES = 100
        const val MAX_KNOWN_MATCHING_MESSAGE_UUIDS = 50
        const val MAX_FORWARD_QUOTE_CACHE_ENTRIES = 200
        const val DRAFT_PERSISTENCE_DEBOUNCE_MILLIS = 350L
        const val REMOTE_DRAFT_SYNC_DEBOUNCE_MILLIS = 1_000L
        const val MAX_DRAFT_DELETE_CONFLICT_RETRIES = 2
        val REMOTE_DRAFT_RETRY_DELAYS_MILLIS =
            longArrayOf(1_000L, 2_000L, 5_000L, 15_000L, 60_000L)
    }

    private suspend fun sendEditSnapshot(
        messageBeingEdited: MessageResponse,
        snapshot: ComposerSnapshot,
    ) {
        val editMessageRequest = EditMessageRequest(
            messageBeingEdited.uuid,
            snapshot.text,
        )
        val response = client.performRequest(editMessageRequest)
        when(response) {
            is ApiResult.Success -> {
                repo.updateMessageContent(
                    messageBeingEdited.streamUuid,
                    messageBeingEdited.topicUuid,
                    messageBeingEdited.uuid,
                    snapshot.text,
                )
                if (composerRevision == snapshot.revision) {
                    restoreSuspendedDraftAfterEdit()
                    composerRevision += 1
                }
                persistConversationStateSafely(
                    failureMessage =
                        "Изменения сохранены, но локальный черновик не удалось очистить",
                )
            }
            is ApiResult.Error -> {
                _actionError.value = response.error.message
                    ?: "Не удалось сохранить изменения"
            }
        }
    }

    init {
        val initialRecoveryVersion = repo.realtimeRecoveryVersion.value
        _isLoading.value = true
        viewModelScope.launch {
            try {
                restoreConversationState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _actionError.value =
                    "Не удалось восстановить защищённый черновик"
            }
            if (
                pendingEditingMessageUuid == null &&
                pendingQuotedMessageUuid == null
            ) {
                _conversationStateReady.value = true
            }
            var messagesLoaded = false
            try {
                messagesLoaded = loadInitialMessages()
                if (messagesLoaded) {
                    finalizeRestoredComposerReferences()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _isLoading.value = false
                _loadError.value = "Не удалось загрузить сообщения"
            } finally {
                _conversationStateReady.value =
                    messagesLoaded ||
                    (
                        pendingEditingMessageUuid == null &&
                            pendingQuotedMessageUuid == null
                    )
                persistConversationStateSafely(
                    failureMessage = "Не удалось сохранить восстановленный черновик",
                )
                if (
                    _messageText.value.isNotBlank() ||
                    _draftSyncState.value != null
                ) {
                    scheduleRemoteDraftSync(initialDelayMillis = 0)
                }
            }
            repo.realtimeRecoveryVersion
                .dropWhile { it <= initialRecoveryVersion }
                .collectLatest {
                    loadLatestMessages(resolveMessageFocus = false)
                }
        }
    }

    private suspend fun loadInitialMessages(): Boolean =
        focusMessageUuid
            ?.let { loadMessageWindowAround(it, focusAnchor = true) }
            ?: loadFirstUnreadWindowOrLatest()

    private suspend fun loadFirstUnreadWindowOrLatest(): Boolean {
        _isLoading.value = true
        val (unreadResponse, latestResponse) = try {
            coroutineScope {
                val unread = async {
                    client.performRequest(
                        MessagesRequest(
                            streamId = chatId,
                            topicId = topicUuid,
                            pageLimit = 1,
                            sortDirection = MessageSortDirection.ASCENDING,
                            read = false,
                            isOwn = false,
                        ),
                    )
                }
                val latest = async {
                    client.performRequest(
                        MessagesRequest(
                            streamId = chatId,
                            topicId = topicUuid,
                            pageLimit = MESSAGE_HISTORY_PAGE_SIZE,
                            sortDirection = MessageSortDirection.DESCENDING,
                        ),
                    )
                }
                unread.await() to latest.await()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            val cachedFallbackAvailable = hasCachedServerHistory()
            if (!cachedFallbackAvailable) {
                _actionError.value =
                    "Не удалось определить первое непрочитанное сообщение"
            }
            return loadLatestMessages().also { loaded ->
                if (loaded && cachedFallbackAvailable) {
                    _actionError.value = null
                }
            }
        }
        return when (unreadResponse) {
            is ApiResult.Success -> {
                val state = validateFirstUnreadPage(
                    messages = unreadResponse.value,
                    expectedStreamUuid = chatId,
                    expectedTopicUuid = topicUuid,
                )
                when {
                    state.error != null -> {
                        _actionError.value = state.error
                        loadLatestMessages(preloadedResponse = latestResponse)
                    }

                    state.message == null -> loadLatestMessages(
                        preloadedResponse = latestResponse,
                    )
                    latestResponse is ApiResult.Success &&
                        latestResponse.value.any {
                            it.uuid == state.message.uuid
                        } -> loadLatestMessages(
                        preloadedResponse = latestResponse,
                    )

                    else -> loadMessageWindowAround(
                        requestedUuid = state.message.uuid,
                        focusAnchor = false,
                    )
                }
            }

            is ApiResult.Error -> {
                val cachedFallbackAvailable =
                    latestResponse is ApiResult.Error &&
                        hasCachedServerHistory()
                if (!cachedFallbackAvailable) {
                    _actionError.value = unreadResponse.error.message
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            "Не удалось определить первое непрочитанное сообщение: $it"
                        }
                        ?: "Не удалось определить первое непрочитанное сообщение"
                }
                loadLatestMessages(
                    preloadedResponse = latestResponse,
                ).also { loaded ->
                    if (loaded && cachedFallbackAvailable) {
                        _actionError.value = null
                    }
                }
            }
        }
    }

    private suspend fun loadMessageWindowAround(
        requestedUuid: String,
        focusAnchor: Boolean,
    ): Boolean {
        _isLoading.value = true
        _loadError.value = null
        _olderMessagesError.value = null
        _newerMessagesError.value = null
        refreshHistoryBeforeOlderRetry = false
        refreshHistoryBeforeNewerRetry = false

        val requestOwnerKey = userViewModel.repo
            .activeCredentialSnapshot()
            .ownerKey
        var anchorError: ApiError? = null
        var anchorScopeMismatch = false
        val anchor = when (
            val response = client.performRequest(MessageRequest(requestedUuid))
        ) {
            is ApiResult.Success -> response.value.takeIf { message ->
                val matches =
                    message.uuid == requestedUuid &&
                    message.streamUuid == chatId &&
                    message.topicUuid == topicUuid
                anchorScopeMismatch = !matches
                matches
            }

            is ApiResult.Error -> {
                anchorError = response.error
                null
            }
        }
        if (anchor == null) {
            _actionError.value = when {
                anchorScopeMismatch ->
                    "Сервер вернул сообщение из другого чата; ссылка не открыта"

                anchorError?.kind == ApiErrorKind.NOT_FOUND ->
                    "Чат открыт, но ссылка на сообщение больше недоступна"

                else -> anchorError?.message
                    ?.takeIf(String::isNotBlank)
                    ?.let { "Не удалось загрузить сообщение по ссылке: $it" }
                    ?: "Не удалось загрузить сообщение по ссылке"
            }
            _isLoading.value = false
            return loadLatestMessages(resolveMessageFocus = false)
        }
        if (
            requestOwnerKey.isNullOrBlank() ||
            !userViewModel.repo.isActiveCredentialOwner(requestOwnerKey)
        ) {
            _isLoading.value = false
            return false
        }

        // Render the exact route anchor before its two context pages finish.
        // This keeps a slow page request from leaving a valid message link on
        // an unrelated latest-history loading screen.
        repo.replaceStreamTopicMessages(chatId, topicUuid, listOf(anchor))
        contextWindowAnchorUuid = anchor.uuid
        nextOlderPageMarker = anchor.uuid
        nextNewerPageMarker = anchor.uuid
        _hasOlderMessages.value = true
        _hasNewerMessages.value = true
        _loadingOlderMessages.value = true
        _loadingNewerMessages.value = true
        if (focusAnchor) {
            requestMessageFocus(anchor.uuid)
        }
        fun finishInitialContextLoading() {
            _loadingOlderMessages.value = false
            _loadingNewerMessages.value = false
        }

        val (olderResponse, newerResponse) = try {
            coroutineScope {
                val older = async {
                    client.performRequest(
                        MessagesRequest(
                            streamId = chatId,
                            topicId = topicUuid,
                            pageLimit = MESSAGE_HISTORY_PAGE_SIZE,
                            pageMarker = anchor.uuid,
                            sortDirection = MessageSortDirection.DESCENDING,
                        ),
                    )
                }
                val newer = async {
                    client.performRequest(
                        MessagesRequest(
                            streamId = chatId,
                            topicId = topicUuid,
                            pageLimit = MESSAGE_HISTORY_PAGE_SIZE,
                            pageMarker = anchor.uuid,
                            sortDirection = MessageSortDirection.ASCENDING,
                        ),
                    )
                }
                older.await() to newer.await()
            }
        } catch (cancellation: CancellationException) {
            finishInitialContextLoading()
            _isLoading.value = false
            throw cancellation
        } catch (exception: Exception) {
            finishInitialContextLoading()
            _isLoading.value = false
            throw exception
        }
        try {
            if (!userViewModel.repo.isActiveCredentialOwner(requestOwnerKey)) {
                _isLoading.value = false
                return false
            }

            val loadedMessages = mutableListOf(anchor)
            when (olderResponse) {
                is ApiResult.Success -> {
                    val messages = olderResponse.value.filter {
                        it.streamUuid == chatId && it.topicUuid == topicUuid
                    }
                    val pageState = validateMessageWindowPageState(
                        messages = messages,
                        nextMarkerHeader = olderResponse.metadata.nextPageMarker,
                        rawMessageCount = olderResponse.value.size,
                        boundary = anchor,
                        direction = MessageWindowDirection.OLDER,
                    )
                    if (pageState.error == null) {
                        loadedMessages += messages
                        nextOlderPageMarker = pageState.nextMarker
                        _hasOlderMessages.value = pageState.nextMarker != null
                    } else {
                        nextOlderPageMarker = anchor.uuid
                        _hasOlderMessages.value = true
                        refreshHistoryBeforeOlderRetry = true
                    }
                    _olderMessagesError.value = pageState.error
                }

                is ApiResult.Error -> {
                    nextOlderPageMarker = anchor.uuid
                    _hasOlderMessages.value = true
                    _olderMessagesError.value = olderResponse.error.message
                        ?: "Не удалось загрузить предыдущие сообщения"
                }
            }
            when (newerResponse) {
                is ApiResult.Success -> {
                    val messages = newerResponse.value.filter {
                        it.streamUuid == chatId && it.topicUuid == topicUuid
                    }
                    val pageState = validateMessageWindowPageState(
                        messages = messages,
                        nextMarkerHeader = newerResponse.metadata.nextPageMarker,
                        rawMessageCount = newerResponse.value.size,
                        boundary = anchor,
                        direction = MessageWindowDirection.NEWER,
                    )
                    if (pageState.error == null) {
                        loadedMessages += messages
                        nextNewerPageMarker = pageState.nextMarker
                        _hasNewerMessages.value = pageState.nextMarker != null
                    } else {
                        nextNewerPageMarker = anchor.uuid
                        _hasNewerMessages.value = true
                        refreshHistoryBeforeNewerRetry = true
                    }
                    _newerMessagesError.value = pageState.error
                }

                is ApiResult.Error -> {
                    nextNewerPageMarker = anchor.uuid
                    _hasNewerMessages.value = true
                    _newerMessagesError.value = newerResponse.error.message
                        ?: "Не удалось загрузить следующие сообщения"
                }
            }

            val missingComposerMessageUuids = listOfNotNull(
                pendingEditingMessageUuid,
                pendingQuotedMessageUuid,
                suspendedDraft?.quotedMessageUuid,
            ).distinct().filter { messageUuid ->
                loadedMessages.none { it.uuid == messageUuid }
            }
            var composerReferenceMessages = emptyList<MessageResponse>()
            if (missingComposerMessageUuids.isNotEmpty()) {
                when (
                    val response = client.performRequest(
                        MessagesByIdsRequest(missingComposerMessageUuids),
                    )
                ) {
                    is ApiResult.Success -> {
                        composerReferenceMessages = response.value.filter {
                            it.uuid in missingComposerMessageUuids &&
                                it.streamUuid == chatId &&
                                it.topicUuid == topicUuid
                        }
                    }

                    is ApiResult.Error -> Unit
                }
            }
            if (!userViewModel.repo.isActiveCredentialOwner(requestOwnerKey)) {
                _isLoading.value = false
                return false
            }

            repo.replaceStreamTopicMessages(chatId, topicUuid, loadedMessages)
            restoreComposerReferences(loadedMessages + composerReferenceMessages)
            reconcileOutboxWithServer(loadedMessages)
            focusProviderMessageId?.let { providerMessageId ->
                loadedMessages
                    .filter { it.provider?.externalId == providerMessageId }
                    .singleOrNull()
                    ?.let { requestMessageFocus(it.uuid) }
            }
            beginForwardMessageUuid
                ?.takeUnless { initialForwardRequestHandled }
                ?.let { messageUuid ->
                    initialForwardRequestHandled = true
                    loadedMessages
                        .filter { it.uuid == messageUuid }
                        .singleOrNull()
                        ?.let(::beginForward)
                        ?: run {
                            _actionError.value =
                                "Чат открыт, но сообщение для пересылки недоступно"
                        }
                }
            // Re-apply exact-route focus after status-row and context layout
            // changes. First-unread windows use the marker instead.
            if (focusAnchor) {
                requestMessageFocus(anchor.uuid)
            }
            _isLoading.value = false
            return true
        } finally {
            finishInitialContextLoading()
        }
    }

    suspend fun loadLatestMessages(
        resolveMessageFocus: Boolean = true,
        preloadedResponse: ApiResult<List<MessageResponse>, ApiError>? = null,
    ): Boolean {
        _isLoading.value = true
        _loadError.value = null
        contextWindowAnchorUuid = null
        nextNewerPageMarker = null
        _hasNewerMessages.value = false
        _newerMessagesError.value = null
        refreshHistoryBeforeNewerRetry = false
        val messagesRequest = MessagesRequest(
            streamId = chatId,
            topicId = topicUuid,
            pageLimit = MESSAGE_HISTORY_PAGE_SIZE,
            sortDirection = MessageSortDirection.DESCENDING,
        )
        val messagesResponse =
            preloadedResponse ?: client.performRequest(messagesRequest)
        return when(messagesResponse) {
            is ApiResult.Success -> {
                val latestPageMessages = messagesResponse.value.filter {
                    it.streamUuid == chatId && it.topicUuid == topicUuid
                }
                val pageState = validateMessagePageState(
                    messages = latestPageMessages,
                    nextMarkerHeader = messagesResponse.metadata.nextPageMarker,
                    rawMessageCount = messagesResponse.value.size,
                )
                nextOlderPageMarker = pageState.nextMarker
                _hasOlderMessages.value = pageState.nextMarker != null
                _olderMessagesError.value = null
                refreshHistoryBeforeOlderRetry = false
                if (pageState.error != null) {
                    _loadError.value = pageState.error
                }
                var loadedMessages = latestPageMessages
                focusMessageUuid?.takeIf { resolveMessageFocus }?.let { requestedUuid ->
                    if (loadedMessages.none { it.uuid == requestedUuid }) {
                        when (
                            val focusedResponse = client.performRequest(
                                MessagesByIdsRequest(listOf(requestedUuid)),
                            )
                        ) {
                            is ApiResult.Success -> {
                                val focusedMessage = focusedResponse.value
                                    .filter {
                                        it.uuid == requestedUuid &&
                                            it.streamUuid == chatId &&
                                            it.topicUuid == topicUuid
                                    }
                                    .singleOrNull()
                                if (focusedMessage == null) {
                                    _actionError.value =
                                        "Чат открыт, но ссылка на сообщение больше недоступна"
                                } else {
                                    loadedMessages = loadedMessages + focusedMessage
                                }
                            }

                            is ApiResult.Error -> {
                                _actionError.value =
                                    "Чат открыт, но сообщение по ссылке не удалось загрузить"
                            }
                        }
                    }
                }
                val visibleMessages = loadedMessages.toList()
                val missingComposerMessageUuids = listOfNotNull(
                    pendingEditingMessageUuid,
                    pendingQuotedMessageUuid,
                    suspendedDraft?.quotedMessageUuid,
                ).distinct().filter { requestedUuid ->
                    loadedMessages.none { it.uuid == requestedUuid }
                }
                if (missingComposerMessageUuids.isNotEmpty()) {
                    when (
                        val composerMessagesResponse = client.performRequest(
                            MessagesByIdsRequest(missingComposerMessageUuids),
                        )
                    ) {
                        is ApiResult.Success -> {
                            loadedMessages += composerMessagesResponse.value.filter {
                                it.uuid in missingComposerMessageUuids &&
                                    it.streamUuid == chatId &&
                                    it.topicUuid == topicUuid
                            }
                        }

                        is ApiResult.Error -> Unit
                    }
                }
                repo.replaceStreamTopicMessages(
                    chatId,
                    topicUuid,
                    visibleMessages,
                )
                restoreComposerReferences(loadedMessages)
                reconcileOutboxWithServer(visibleMessages)
                focusProviderMessageId?.let { providerMessageId ->
                    val matchingMessages = visibleMessages.filter {
                        it.provider?.externalId == providerMessageId
                    }
                    val matchingMessage = matchingMessages.singleOrNull()
                    if (matchingMessage != null) {
                        requestMessageFocus(matchingMessage.uuid)
                    } else {
                        _actionError.value =
                            "Чат открыт, но сообщение из уведомления уже не загружено"
                    }
                }
                focusMessageUuid?.takeIf { resolveMessageFocus }?.let { requestedUuid ->
                    if (loadedMessages.any { it.uuid == requestedUuid }) {
                        requestMessageFocus(requestedUuid)
                    }
                }
                beginForwardMessageUuid
                    ?.takeUnless { initialForwardRequestHandled }
                    ?.let { requestedUuid ->
                        initialForwardRequestHandled = true
                        val source = loadedMessages
                            .filter { it.uuid == requestedUuid }
                            .singleOrNull()
                        if (source == null) {
                            _actionError.value =
                                "Чат открыт, но сообщение для пересылки недоступно"
                        } else {
                            beginForward(source)
                        }
                    }
                _isLoading.value = false
                true
            }
            is ApiResult.Error -> {
                if (restoreCachedHistory(resolveMessageFocus)) {
                    true
                } else {
                    _isLoading.value = false
                    _loadError.value = messagesResponse.error.message
                        ?: "Не удалось загрузить сообщения"
                    false
                }
            }
        }
    }

    private fun restoreCachedHistory(
        resolveMessageFocus: Boolean,
    ): Boolean {
        val cachedMessages = cachedConversationMessages()
        if (!hasCachedServerHistory(cachedMessages)) return false
        _loadError.value = null
        _olderMessagesError.value = null
        _newerMessagesError.value = null
        _hasOlderMessages.value = false
        _hasNewerMessages.value = false
        nextOlderPageMarker = null
        nextNewerPageMarker = null
        restoreComposerReferences(cachedMessages)
        focusProviderMessageId?.let { providerMessageId ->
            cachedMessages
                .filter { it.provider?.externalId == providerMessageId }
                .singleOrNull()
                ?.let { requestMessageFocus(it.uuid) }
        }
        focusMessageUuid
            ?.takeIf { resolveMessageFocus }
            ?.takeIf { requestedUuid ->
                cachedMessages.any { it.uuid == requestedUuid }
            }
            ?.let(::requestMessageFocus)
        beginForwardMessageUuid
            ?.takeUnless { initialForwardRequestHandled }
            ?.let { requestedUuid ->
                initialForwardRequestHandled = true
                cachedMessages
                    .filter { it.uuid == requestedUuid }
                    .singleOrNull()
                    ?.let(::beginForward)
            }
        _isLoading.value = false
        return true
    }

    private fun cachedConversationMessages(): List<MessageResponse> =
        repo.streamTopicMessages.value["$chatId.$topicUuid"].orEmpty()

    private fun hasCachedServerHistory(): Boolean =
        hasCachedServerHistory(cachedConversationMessages())

    fun retryLoad() {
        if (_isLoading.value) return
        viewModelScope.launch {
            try {
                if (loadInitialMessages()) {
                    finalizeRestoredComposerReferences()
                    _conversationStateReady.value = true
                    persistConversationStateSafely(
                        failureMessage =
                            "Не удалось сохранить восстановленный черновик",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _isLoading.value = false
                _loadingOlderMessages.value = false
                _loadingNewerMessages.value = false
                _loadError.value = "Не удалось загрузить сообщения"
            }
        }
    }

    fun loadOlderMessages(): Boolean {
        if (
            !_hasOlderMessages.value ||
            _loadingOlderMessages.value ||
            _olderMessagesError.value != null ||
            refreshHistoryBeforeOlderRetry
        ) {
            return false
        }
        return startOlderMessagesLoad()
    }

    fun retryOlderMessages(): Boolean {
        if (_loadingOlderMessages.value) return false
        _olderMessagesError.value = null
        if (refreshHistoryBeforeOlderRetry) {
            contextWindowAnchorUuid?.let { anchorUuid ->
                refreshHistoryBeforeOlderRetry = false
                nextOlderPageMarker = anchorUuid
                _hasOlderMessages.value = true
                return startOlderMessagesLoad()
            }
            return refreshHistoryForOlderRetry()
        }
        if (!_hasOlderMessages.value) return false
        return startOlderMessagesLoad()
    }

    private fun refreshHistoryForOlderRetry(): Boolean {
        if (olderMessagesJob?.isActive == true || _isLoading.value) return false
        _loadingOlderMessages.value = true
        olderMessagesJob = viewModelScope.launch {
            try {
                if (!loadLatestMessages()) {
                    _olderMessagesError.value = _loadError.value
                        ?: "Не удалось обновить историю сообщений"
                    _loadError.value = null
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                _loadingOlderMessages.value = false
            }
        }
        return true
    }

    private fun startOlderMessagesLoad(): Boolean {
        val marker = nextOlderPageMarker ?: run {
            _hasOlderMessages.value = false
            return false
        }
        if (olderMessagesJob?.isActive == true) return false
        _loadingOlderMessages.value = true
        olderMessagesJob = viewModelScope.launch {
            try {
                when (
                    val response = client.performRequest(
                        MessagesRequest(
                            streamId = chatId,
                            topicId = topicUuid,
                            pageLimit = MESSAGE_HISTORY_PAGE_SIZE,
                            pageMarker = marker,
                            sortDirection = MessageSortDirection.DESCENDING,
                        ),
                    )
                ) {
                    is ApiResult.Success -> {
                        val pageMessages = response.value.filter {
                            it.streamUuid == chatId && it.topicUuid == topicUuid
                        }
                        val boundary = streamTopicMessages.value[
                            "$chatId.$topicUuid"
                        ]?.singleOrNull { it.uuid == marker }
                        val pageState =
                            if (
                                contextWindowAnchorUuid != null &&
                                boundary != null
                            ) {
                                validateMessageWindowPageState(
                                    messages = pageMessages,
                                    nextMarkerHeader =
                                        response.metadata.nextPageMarker,
                                    rawMessageCount = response.value.size,
                                    boundary = boundary,
                                    direction = MessageWindowDirection.OLDER,
                                )
                            } else {
                                validateMessagePageState(
                                    messages = pageMessages,
                                    nextMarkerHeader =
                                        response.metadata.nextPageMarker,
                                    previousMarker = marker,
                                    rawMessageCount = response.value.size,
                                )
                            }
                        if (pageState.error == null) {
                            repo.addStreamTopicMessages(
                                chatId,
                                topicUuid,
                                pageMessages,
                            )
                            nextOlderPageMarker = pageState.nextMarker
                            _hasOlderMessages.value = pageState.nextMarker != null
                            refreshHistoryBeforeOlderRetry = false
                        } else {
                            refreshHistoryBeforeOlderRetry = true
                            nextOlderPageMarker = null
                            _hasOlderMessages.value = false
                        }
                        _olderMessagesError.value = pageState.error
                    }

                    is ApiResult.Error -> {
                        refreshHistoryBeforeOlderRetry =
                            response.error.kind == ApiErrorKind.NOT_FOUND ||
                                response.error.kind == ApiErrorKind.VALIDATION ||
                                response.error.kind == ApiErrorKind.MALFORMED_RESPONSE
                        if (refreshHistoryBeforeOlderRetry) {
                            nextOlderPageMarker = null
                            _hasOlderMessages.value = false
                        }
                        _olderMessagesError.value = response.error.message
                            ?: "Не удалось загрузить предыдущие сообщения"
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                _loadingOlderMessages.value = false
            }
        }
        return true
    }

    fun loadNewerMessages(): Boolean {
        if (
            contextWindowAnchorUuid == null ||
            !_hasNewerMessages.value ||
            _loadingNewerMessages.value ||
            _newerMessagesError.value != null ||
            refreshHistoryBeforeNewerRetry
        ) {
            return false
        }
        return startNewerMessagesLoad()
    }

    fun retryNewerMessages(): Boolean {
        if (
            _loadingNewerMessages.value ||
            contextWindowAnchorUuid == null
        ) {
            return false
        }
        _newerMessagesError.value = null
        if (refreshHistoryBeforeNewerRetry) {
            refreshHistoryBeforeNewerRetry = false
            nextNewerPageMarker = contextWindowAnchorUuid
            _hasNewerMessages.value = true
        }
        if (!_hasNewerMessages.value) return false
        return startNewerMessagesLoad()
    }

    private fun startNewerMessagesLoad(): Boolean {
        val marker = nextNewerPageMarker ?: run {
            _hasNewerMessages.value = false
            return false
        }
        if (newerMessagesJob?.isActive == true) return false
        _loadingNewerMessages.value = true
        newerMessagesJob = viewModelScope.launch {
            try {
                when (
                    val response = client.performRequest(
                        MessagesRequest(
                            streamId = chatId,
                            topicId = topicUuid,
                            pageLimit = MESSAGE_HISTORY_PAGE_SIZE,
                            pageMarker = marker,
                            sortDirection = MessageSortDirection.ASCENDING,
                        ),
                    )
                ) {
                    is ApiResult.Success -> {
                        val pageMessages = response.value.filter {
                            it.streamUuid == chatId && it.topicUuid == topicUuid
                        }
                        val boundary = streamTopicMessages.value[
                            "$chatId.$topicUuid"
                        ]?.singleOrNull { it.uuid == marker }
                        val pageState = if (boundary == null) {
                            malformedMessagePageState()
                        } else {
                            validateMessageWindowPageState(
                                messages = pageMessages,
                                nextMarkerHeader =
                                    response.metadata.nextPageMarker,
                                rawMessageCount = response.value.size,
                                boundary = boundary,
                                direction = MessageWindowDirection.NEWER,
                            )
                        }
                        if (pageState.error == null) {
                            repo.addStreamTopicMessages(
                                chatId,
                                topicUuid,
                                pageMessages,
                            )
                            nextNewerPageMarker = pageState.nextMarker
                            _hasNewerMessages.value = pageState.nextMarker != null
                            refreshHistoryBeforeNewerRetry = false
                        } else {
                            refreshHistoryBeforeNewerRetry = true
                            nextNewerPageMarker = null
                            _hasNewerMessages.value = false
                        }
                        _newerMessagesError.value = pageState.error
                    }

                    is ApiResult.Error -> {
                        refreshHistoryBeforeNewerRetry =
                            response.error.kind == ApiErrorKind.NOT_FOUND ||
                                response.error.kind == ApiErrorKind.VALIDATION ||
                                response.error.kind ==
                                    ApiErrorKind.MALFORMED_RESPONSE
                        if (refreshHistoryBeforeNewerRetry) {
                            nextNewerPageMarker = null
                            _hasNewerMessages.value = false
                        }
                        _newerMessagesError.value = response.error.message
                            ?: "Не удалось загрузить следующие сообщения"
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                _loadingNewerMessages.value = false
            }
        }
        return true
    }

    fun verifyOutbox(localMessageUuid: String) {
        if (localMessageUuid in _verifyingOutbox.value) return
        viewModelScope.launch {
            verifyOutboxOnServer(
                localMessageUuid = localMessageUuid,
                reportMissing = true,
            )
        }
    }

    fun retryOutbox(localMessageUuid: String) {
        if (_sending.value || !_conversationStateReady.value) return
        val entry = _outboxEntries.value
            .firstOrNull { it.localMessageUuid == localMessageUuid }
            ?: return
        _sending.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                enqueueAndSend(
                    content = entry.content,
                    composerSnapshot = null,
                    existingEntry = entry,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                updateOutboxFailure(
                    localMessageUuid = localMessageUuid,
                    status = PersistedOutboxStatus.UNCERTAIN,
                    errorMessage =
                        "Не удалось подтвердить результат повторной отправки",
                )
            } finally {
                _sending.value = false
            }
        }
    }

    fun removeOutbox(localMessageUuid: String) {
        val entry = _outboxEntries.value
            .firstOrNull { it.localMessageUuid == localMessageUuid }
            ?: return
        if (entry.status == PersistedOutboxStatus.SENDING) return
        _outboxEntries.update { current ->
            current.filterNot { it.localMessageUuid == localMessageUuid }
        }
        repo.removeMessage(entry.streamUuid, entry.topicUuid, localMessageUuid)
        viewModelScope.launch {
            persistConversationStateSafely(
                failureMessage = "Не удалось удалить сообщение из очереди",
            )
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun retryDraftSync() {
        if (_draftSyncState.value?.status != PersistedDraftSyncStatus.FAILED) {
            return
        }
        scheduleRemoteDraftSync(initialDelayMillis = 0)
    }

    fun acceptServerDraft() {
        val resolution = _draftSyncState.value
            ?.let(::acceptServerDraftVersion)
            ?: return
        _messageText.value = resolution.first
        _draftSyncState.value = resolution.second
        composerRevision += 1
        draftUpdatedAt = OffsetDateTime.now()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        viewModelScope.launch {
            persistConversationStateSafely(
                failureMessage =
                    "Не удалось сохранить выбранную серверную версию",
            )
        }
    }

    fun keepLocalDraft() {
        val next = _draftSyncState.value
            ?.let(::keepLocalDraftVersion)
            ?: return
        _draftSyncState.value = next
        viewModelScope.launch {
            persistConversationStateSafely(
                failureMessage =
                    "Не удалось сохранить выбор локальной версии",
            )
            scheduleRemoteDraftSync(initialDelayMillis = 0)
        }
    }

    fun deleteConflictedDraft() {
        val next = _draftSyncState.value
            ?.let(::deleteConflictedServerDraft)
            ?: return
        _messageText.value = ""
        _draftSyncState.value = next
        composerRevision += 1
        draftUpdatedAt = OffsetDateTime.now()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        viewModelScope.launch {
            persistConversationStateSafely(
                failureMessage =
                    "Не удалось сохранить запрос удаления черновика",
            )
            scheduleRemoteDraftSync(initialDelayMillis = 0)
        }
    }

    fun reportActionError(message: String) {
        _actionError.value = message
    }

    fun requestMessageFocus(messageUuid: String) {
        _focusedMessageUuid.value = messageUuid.takeIf(String::isNotBlank)
    }

    fun clearMessageFocus() {
        _focusedMessageUuid.value = null
    }

    fun markVisibleMessagesRead(visibleMessageUuids: Collection<String>) {
        val messages = streamTopicMessages.value["$chatId.$topicUuid"]
            .orEmpty()
        val target = newestVisibleUnreadBoundary(
            messages = messages,
            visibleMessageUuids = visibleMessageUuids,
        ) ?: return
        pendingReadBoundaryUuid = newestMessageUuid(
            messages = messages,
            firstUuid = pendingReadBoundaryUuid,
            secondUuid = target.uuid,
        )
        startPendingReadRequest()
    }

    private fun startPendingReadRequest() {
        if (readMessagesJob?.isActive == true) return
        val requestedUuid = pendingReadBoundaryUuid ?: return
        pendingReadBoundaryUuid = null
        readMessagesJob = viewModelScope.launch {
            try {
                val currentTarget = streamTopicMessages.value[
                    "$chatId.$topicUuid"
                ].orEmpty().singleOrNull {
                    it.uuid == requestedUuid && !it.read && !it.isOwn
                } ?: return@launch
                when (
                    val response = client.performRequest(
                        MarkMessagesReadRequest(currentTarget.uuid),
                    )
                ) {
                    is ApiResult.Success -> {
                        val confirmed = response.value
                        if (isConfirmedReadThrough(
                                expectedMessageUuid = currentTarget.uuid,
                                expectedStreamUuid = chatId,
                                expectedTopicUuid = topicUuid,
                                confirmed = confirmed,
                            )
                        ) {
                            repo.markStreamTopicMessagesReadThrough(
                                streamUuid = chatId,
                                topicUuid = topicUuid,
                                boundaryUuid = currentTarget.uuid,
                            )
                            failedReadBoundaryUuid = null
                            _readError.value = null
                        } else {
                            failedReadBoundaryUuid = currentTarget.uuid
                            _readError.value =
                                "Сервер не подтвердил прочтение сообщений"
                        }
                    }

                    is ApiResult.Error -> {
                        failedReadBoundaryUuid = currentTarget.uuid
                        _readError.value = response.error.message
                            ?.takeIf(String::isNotBlank)
                            ?.let { "Не удалось отметить сообщения прочитанными: $it" }
                            ?: "Не удалось отметить сообщения прочитанными"
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                failedReadBoundaryUuid = requestedUuid
                _readError.value =
                    "Не удалось отметить сообщения прочитанными"
            } finally {
                readMessagesJob = null
                if (pendingReadBoundaryUuid != null) {
                    startPendingReadRequest()
                }
            }
        }
    }

    fun retryReadMessages() {
        val failedUuid = failedReadBoundaryUuid ?: return
        failedReadBoundaryUuid = null
        _readError.value = null
        val messages = streamTopicMessages.value["$chatId.$topicUuid"]
            .orEmpty()
        pendingReadBoundaryUuid = newestMessageUuid(
            messages = messages,
            firstUuid = pendingReadBoundaryUuid,
            secondUuid = failedUuid,
        )
        startPendingReadRequest()
    }

    fun clearReadError() {
        failedReadBoundaryUuid = null
        _readError.value = null
    }

    fun onMessageReactionTap(messageUuid: String, emoji: String) {
        viewModelScope.launch {
            withReactionOperation(messageUuid, emoji) {
                val reaction = repo.userReactions.value.firstOrNull {
                    it.emojiName == emoji && it.messageUuid == messageUuid
                }
                if (reaction != null) {
                    val removeReactionResponse =
                        client.performRequest(
                            RemoveMessageReactionRequest(reaction.uuid),
                        )
                    when (removeReactionResponse) {
                        is ApiResult.Success -> Unit

                        is ApiResult.Error -> {
                            _actionError.value =
                                removeReactionResponse.error.message
                                    ?: "Не удалось удалить реакцию"
                        }
                    }
                } else {
                    val addReactionResponse =
                        client.performRequest(
                            AddMessageReactionRequest(messageUuid, emoji),
                        )
                    when (addReactionResponse) {
                        is ApiResult.Success -> Unit

                        is ApiResult.Error -> {
                            _actionError.value =
                                addReactionResponse.error.message
                                    ?: "Не удалось добавить реакцию"
                        }
                    }
                }
            }
        }
    }

    private suspend fun withReactionOperation(
        messageUuid: String,
        emoji: String,
        operation: suspend () -> Unit,
    ) {
        val key = "$messageUuid\u0000$emoji"
        val accepted = reactionOperationsMutex.withLock {
            reactionOperations.add(key)
        }
        if (!accepted) return
        try {
            operation()
        } finally {
            reactionOperationsMutex.withLock {
                reactionOperations.remove(key)
            }
        }
    }
}

internal data class MessagePageState(
    val nextMarker: String?,
    val error: String? = null,
)

internal fun validateMessagePageState(
    messages: List<MessageResponse>,
    nextMarkerHeader: String?,
    previousMarker: String? = null,
    rawMessageCount: Int = messages.size,
): MessagePageState {
    if (rawMessageCount != messages.size) {
        return malformedMessagePageState()
    }
    val rawMarker = nextMarkerHeader?.trim().orEmpty()
    if (rawMarker.isEmpty()) {
        return MessagePageState(nextMarker = null)
    }
    val marker = runCatching { UUID.fromString(rawMarker).toString() }.getOrNull()
        ?: return malformedMessagePageState()
    if (
        !marker.equals(rawMarker, ignoreCase = true) ||
        marker == previousMarker ||
        messages.lastOrNull()?.uuid?.equals(marker, ignoreCase = true) != true
    ) {
        return malformedMessagePageState()
    }
    return MessagePageState(nextMarker = marker)
}

internal enum class MessageWindowDirection {
    OLDER,
    NEWER,
}

internal fun validateMessageWindowPageState(
    messages: List<MessageResponse>,
    nextMarkerHeader: String?,
    rawMessageCount: Int,
    boundary: MessageResponse,
    direction: MessageWindowDirection,
): MessagePageState {
    val baseState = validateMessagePageState(
        messages = messages,
        nextMarkerHeader = nextMarkerHeader,
        previousMarker = boundary.uuid,
        rawMessageCount = rawMessageCount,
    )
    if (baseState.error != null) return baseState

    val boundaryPosition = messagePosition(boundary)
        ?: return malformedMessagePageState()
    val positions = messages.map { message ->
        messagePosition(message) ?: return malformedMessagePageState()
    }
    if (positions.map { it.uuid }.toSet().size != positions.size) {
        return malformedMessagePageState()
    }

    val belongsToWindowSide = positions.all { position ->
        val comparison = compareMessagePositions(position, boundaryPosition)
        when (direction) {
            MessageWindowDirection.OLDER -> comparison < 0
            MessageWindowDirection.NEWER -> comparison > 0
        }
    }
    val followsServerOrder = positions.zipWithNext().all { (left, right) ->
        val comparison = compareMessagePositions(left, right)
        when (direction) {
            MessageWindowDirection.OLDER -> comparison > 0
            MessageWindowDirection.NEWER -> comparison < 0
        }
    }
    return if (belongsToWindowSide && followsServerOrder) {
        baseState
    } else {
        malformedMessagePageState()
    }
}

internal data class FirstUnreadPageState(
    val message: MessageResponse? = null,
    val error: String? = null,
)

internal fun validateFirstUnreadPage(
    messages: List<MessageResponse>,
    expectedStreamUuid: String,
    expectedTopicUuid: String,
): FirstUnreadPageState {
    if (messages.isEmpty()) return FirstUnreadPageState()
    val message = messages.singleOrNull()
        ?: return FirstUnreadPageState(
            error = "Сервер вернул некорректный список непрочитанных сообщений",
        )
    val valid =
        message.streamUuid == expectedStreamUuid &&
            message.topicUuid == expectedTopicUuid &&
            !message.read &&
            !message.isOwn &&
            messagePosition(message) != null
    return if (valid) {
        FirstUnreadPageState(message = message)
    } else {
        FirstUnreadPageState(
            error = "Сервер вернул некорректное первое непрочитанное сообщение",
        )
    }
}

internal fun newestVisibleUnreadBoundary(
    messages: List<MessageResponse>,
    visibleMessageUuids: Collection<String>,
): MessageResponse? {
    val visible = visibleMessageUuids.toSet()
    return messages
        .asSequence()
        .filter { message ->
            message.uuid in visible &&
                !message.read &&
                !message.isOwn
        }
        .mapNotNull { message ->
            messagePosition(message)?.let { message to it }
        }
        .maxWithOrNull { left, right ->
            compareMessagePositions(left.second, right.second)
        }
        ?.first
}

internal fun isConfirmedReadThrough(
    expectedMessageUuid: String,
    expectedStreamUuid: String,
    expectedTopicUuid: String,
    confirmed: MessageResponse,
): Boolean =
    confirmed.uuid == expectedMessageUuid &&
        confirmed.streamUuid == expectedStreamUuid &&
        confirmed.topicUuid == expectedTopicUuid &&
        confirmed.read

private fun newestMessageUuid(
    messages: List<MessageResponse>,
    firstUuid: String?,
    secondUuid: String,
): String {
    val candidates = setOfNotNull(firstUuid, secondUuid)
    return messages
        .asSequence()
        .filter { it.uuid in candidates }
        .mapNotNull { message ->
            messagePosition(message)?.let { message.uuid to it }
        }
        .maxWithOrNull { left, right ->
            compareMessagePositions(left.second, right.second)
        }
        ?.first
        ?: secondUuid
}

private data class MessagePosition(
    val createdAt: Instant,
    val uuid: String,
)

private fun messagePosition(message: MessageResponse): MessagePosition? {
    val createdAt = runCatching {
        OffsetDateTime.parse(message.createdAt).toInstant()
    }.getOrNull() ?: return null
    val uuid = parseCanonicalMessageUuid(message.uuid) ?: return null
    return MessagePosition(createdAt = createdAt, uuid = uuid)
}

private fun compareMessagePositions(
    left: MessagePosition,
    right: MessagePosition,
): Int {
    val timeComparison = left.createdAt.compareTo(right.createdAt)
    return if (timeComparison != 0) {
        timeComparison
    } else {
        left.uuid.compareTo(right.uuid)
    }
}

internal fun malformedMessagePageState() = MessagePageState(
    nextMarker = null,
    error = "Сервер вернул некорректную страницу истории",
)

private const val MESSAGE_HISTORY_PAGE_SIZE = DEFAULT_MESSAGE_PAGE_SIZE

internal fun messageSortInstant(createdAt: String): Instant =
    runCatching { OffsetDateTime.parse(createdAt).toInstant() }
        .getOrDefault(Instant.EPOCH)

internal fun hasCachedServerHistory(
    messages: List<MessageResponse>,
): Boolean = messages.any { !it.uuid.startsWith("local-") }

internal fun mergeRecoveredDraftTexts(
    originalDraft: String,
    recoveredEdit: String,
): String =
    when {
        recoveredEdit.isBlank() -> originalDraft
        originalDraft.isBlank() -> recoveredEdit
        originalDraft == recoveredEdit -> originalDraft
        else ->
            "$originalDraft\n\n---\nВосстановленный текст редактирования:\n" +
                recoveredEdit
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

}
