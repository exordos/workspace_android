package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.WorkspaceTimelineKind
import ru.genesiscorporation.workspace.beta.data.WorkspaceTimelineSnapshot
import ru.genesiscorporation.workspace.beta.data.navigation.WorkspaceDeepLink
import ru.genesiscorporation.workspace.beta.data.navigation.WorkspaceDeepLinkTarget
import ru.genesiscorporation.workspace.beta.data.push.PushNavigationRequest
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddChatToFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamMembersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.CreateTopicRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteChatFromFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkStreamReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkTopicReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.FoldersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReactionsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.PinFolderItemRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RenameTopicRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamNotificationsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.ToggleTopicDoneRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicNotificationsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UnpinFolderItemRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.inbox.InboxSyncState
import ru.genesiscorporation.workspace.beta.modules.inbox.InboxCatalogApplyDecision
import ru.genesiscorporation.workspace.beta.modules.inbox.decideInboxCatalogApply
import ru.genesiscorporation.workspace.beta.modules.inbox.validateInboxCatalog
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.first

sealed interface ChatNavEvent {
    data class OpenDialog(
        val title: String,
        val chatId: String,
        val topicId: String?,
        val isDirectMessages: Boolean,
        val userId: Int
    ) : ChatNavEvent
}

sealed interface ResolvedDeepLinkDestination {
    data class Dialog(val route: ChatFlow.ChatDialog) : ResolvedDeepLinkDestination
    data class TopicList(val route: ChatFlow.ChatTopic) : ResolvedDeepLinkDestination
}

data class FeedMessageNavigationResult(
    val route: ChatFlow.ChatDialog? = null,
    val error: String? = null,
)

internal object CatalogActionKind {
    const val CREATE_TOPIC = "create_topic"
    const val RENAME_TOPIC = "rename_topic"
    const val MARK_TOPIC_READ = "mark_topic_read"
    const val TOGGLE_TOPIC_DONE = "toggle_topic_done"
    const val TOPIC_NOTIFICATIONS = "topic_notifications"
    const val RENAME_FOLDER = "rename_folder"
    const val DELETE_FOLDER = "delete_folder"
}

private val STREAM_NOTIFICATION_MODES = setOf(
    "mentions_only",
    "muted",
    "all_messages",
)

data class CatalogActionResult(
    val requestId: Long,
    val kind: String,
    val targetUuid: String?,
    val success: Boolean,
)

data class FolderCreationResult(
    val requestId: Long,
    val createdFolderUuid: String? = null,
    val requestedChatCount: Int = 0,
    val addedChatCount: Int = 0,
    val message: String? = null,
) {
    val folderCreated: Boolean
        get() = createdFolderUuid != null
}

data class FolderDraft(
    val name: String,
    val streams: List<Stream>,
)

private data class UnreadMentionRefreshRequest(
    val ownerKey: String?,
    val unreadStreamUuids: Set<String>,
    val recoveryVersion: Long,
)

internal fun unreadMentionStreamUuids(
    messages: List<MessageResponse>,
    unreadStreamUuids: Set<String>,
): Set<String> = messages
    .asSequence()
    .filter { message ->
        message.mentioned &&
            !message.read &&
            message.streamUuid in unreadStreamUuids
    }
    .map(MessageResponse::streamUuid)
    .toSet()

class ChatViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val repo: EventsRepository,
    private val conversationStateStore: ConversationStateStore,
): ViewModel() {
    val streams: StateFlow<List<Stream>> = repo.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _unreadMentionStreamUuids = MutableStateFlow<Set<String>>(emptySet())
    val unreadMentionStreamUuids: StateFlow<Set<String>> =
        _unreadMentionStreamUuids
    private var unreadMentionOwnerKey: String? = null

    private val _currentlySelectedStream = MutableStateFlow<Stream?>(null)
    var currentlySelectedStream: StateFlow<Stream?> = _currentlySelectedStream

    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = repo.streamTopics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )
    val users: StateFlow<List<UserResponseData>> = repo.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val streamBindings: StateFlow<List<StreamBindingResponseData>> = repo.streamBindings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val folders: StateFlow<List<FolderResponseData>> = repo.folders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    private val _currentlySelectedFolder = MutableStateFlow<FolderResponseData?>(null)
    var currentlySelectedFolder: StateFlow<FolderResponseData?> = _currentlySelectedFolder

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    private val _searchQuery = MutableStateFlow("")
    var searchQuery: StateFlow<String> = _searchQuery
    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState

    var createdStream: Stream? = null
    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState
    private val _navEvents = MutableSharedFlow<ChatNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<ChatNavEvent> = _navEvents

    private val _chatToAdd = MutableStateFlow<Stream?>(null)
    var chatToAdd: StateFlow<Stream?> = _chatToAdd
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError
    private val _inboxSyncState = MutableStateFlow(InboxSyncState())
    val inboxSyncState: StateFlow<InboxSyncState> = _inboxSyncState
    private val _topicActionInProgress = MutableStateFlow(false)
    val topicActionInProgress: StateFlow<Boolean> = _topicActionInProgress
    private val _folderActionInProgress = MutableStateFlow(false)
    val folderActionInProgress: StateFlow<Boolean> = _folderActionInProgress
    private val _lastCatalogActionResult =
        MutableStateFlow<CatalogActionResult?>(null)
    val lastCatalogActionResult: StateFlow<CatalogActionResult?> =
        _lastCatalogActionResult
    private val _folderCreationResult =
        MutableStateFlow<FolderCreationResult?>(null)
    val folderCreationResult: StateFlow<FolderCreationResult?> =
        _folderCreationResult
    private val topicMutationMutex = Mutex()
    private val streamMutationMutex = Mutex()
    private val createMutationMutex = Mutex()
    private val folderMutationMutex = Mutex()
    private val inboxRefreshMutex = Mutex()
    private val nextCatalogActionRequestId = AtomicLong()

    val map: Map<String, Int> = emptyMap()

    var currentTopicName: String = ""


    fun onForlderNameChange(newText: String) {
        _newFolderName.value = newText
    }

    init {
        val initialRecoveryVersion = repo.realtimeRecoveryVersion.value
        viewModelScope.launch {
            loadServerSettings()
            repo.realtimeRecoveryVersion
                .dropWhile { it <= initialRecoveryVersion }
                .collectLatest {
                    loadServerSettings()
                }
        }
        viewModelScope.launch {
            combine(
                repo.folders,
                repo.selectedFolderUuid,
            ) { currentFolders, selectedFolderUuid ->
                currentFolders to selectedFolderUuid
            }.collectLatest { (currentFolders, sharedSelectedUuid) ->
                val selectedUuid = sharedSelectedUuid
                    ?: _currentlySelectedFolder.value?.uuid
                _currentlySelectedFolder.value =
                    currentFolders.firstOrNull { it.uuid == selectedUuid }
                        ?: currentFolders.firstOrNull()
                repo.selectFolder(_currentlySelectedFolder.value?.uuid)
            }
        }
        viewModelScope.launch {
            combine(
                userViewModel.activeAccountId,
                userViewModel.baseUrl,
                userViewModel.accessToken,
            ) { accountId, baseUrl, accessToken ->
                if (accessToken == null) null else accountId ?: baseUrl
            }
                .distinctUntilChanged()
                .collectLatest(::restoreInboxSnapshotAvailability)
        }
        viewModelScope.launch {
            val activeOwnerKey = combine(
                userViewModel.activeAccountId,
                userViewModel.baseUrl,
                userViewModel.accessToken,
            ) { accountId, baseUrl, accessToken ->
                if (accessToken == null) null else accountId ?: baseUrl
            }
            combine(
                repo.streams.map { currentStreams ->
                    currentStreams
                        .asSequence()
                        .filter { it.unreadCount > 0 }
                        .associate { it.uuid to it.unreadCount }
                },
                activeOwnerKey,
                repo.realtimeRecoveryVersion,
            ) { unreadCounts, ownerKey, recoveryVersion ->
                UnreadMentionRefreshRequest(
                    ownerKey = ownerKey,
                    unreadStreamUuids = unreadCounts.keys,
                    recoveryVersion = recoveryVersion,
                )
            }
                .distinctUntilChanged()
                .collectLatest(::refreshUnreadMentionStreams)
        }
    }

    private suspend fun refreshUnreadMentionStreams(
        request: UnreadMentionRefreshRequest,
    ) {
        val ownerKey = request.ownerKey?.takeIf(String::isNotBlank)
        if (ownerKey != unreadMentionOwnerKey) {
            unreadMentionOwnerKey = ownerKey
            _unreadMentionStreamUuids.value = emptySet()
        }
        if (ownerKey == null || request.unreadStreamUuids.isEmpty()) {
            _unreadMentionStreamUuids.value = emptySet()
            return
        }
        when (
            val response = client.performRequest(
                MessagesRequest(read = false, mentioned = true),
                expectedOwnerKey = ownerKey,
            )
        ) {
            is ApiResult.Success -> {
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) return
                _unreadMentionStreamUuids.value = unreadMentionStreamUuids(
                    messages = response.value,
                    unreadStreamUuids = request.unreadStreamUuids,
                )
            }
            is ApiResult.Error -> {
                _unreadMentionStreamUuids.update { current ->
                    current.intersect(request.unreadStreamUuids)
                }
            }
        }
    }

    var currentStreamId: String = ""

    fun poolMessage(uuid: String?): MessageResponse? {
        return repo.messagesPool.value.firstOrNull { it.uuid == uuid }
    }

    fun updateCurrentlySelectedFolder(newFolder: FolderResponseData) {
        if (newFolder.uuid != currentlySelectedFolder.value?.uuid) {
            _currentlySelectedFolder.update { newFolder }
        }
        repo.selectFolder(newFolder.uuid)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onChatToAddChange(chatToAdd: Stream?) {
        _chatToAdd.value = chatToAdd
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun refreshInbox() {
        viewModelScope.launch {
            refreshInboxInternal()
        }
    }

    fun reportActionError(message: String) {
        _actionError.value = message
    }

    suspend fun resolvePushNavigation(
        request: PushNavigationRequest,
    ): ChatFlow.ChatDialog? {
        _actionError.value = null
        val matchingStreams = repo.streams.value.filter { stream ->
            providerKeysMatch(
                requested = request.providerChatKey,
                actual = stream.provider?.externalId,
            )
        }
        val stream = matchingStreams.singleOrNull()
        if (stream == null) {
            _actionError.value = if (matchingStreams.isEmpty()) {
                "Чат из уведомления недоступен или ещё не синхронизирован"
            } else {
                "Не удалось однозначно определить чат из уведомления"
            }
            return null
        }

        if (request.providerChatKey.startsWith("channel:")) {
            if (repo.streamTopics.value[stream.uuid].isNullOrEmpty()) {
                loadTopics(stream)
            }
            val topicName = request.topicName.orEmpty()
            val exactMatches = repo.streamTopics.value[stream.uuid]
                .orEmpty()
                .filter { it.name == topicName }
            val topic = exactMatches.singleOrNull()
                ?: repo.streamTopics.value[stream.uuid]
                    .orEmpty()
                    .filter { it.name.equals(topicName, ignoreCase = true) }
                    .singleOrNull()
            if (topic == null) {
                _actionError.value =
                    "Тема из уведомления недоступна или была переименована"
                return null
            }
            return ChatFlow.ChatDialog(
                title = stream.name,
                chatId = stream.uuid,
                topicName = topic.name,
                topicUuid = topic.uuid,
                isDirectMessages = false,
                userId = null,
                focusProviderMessageId = request.workspaceMessageId.toString(),
            )
        }

        var defaultTopicUuid = stream.defaultTopicUuid
        if (defaultTopicUuid.isNullOrBlank()) {
            if (repo.streamTopics.value[stream.uuid].isNullOrEmpty()) {
                loadTopics(stream)
            }
            defaultTopicUuid = repo.streamTopics.value[stream.uuid]
                .orEmpty()
                .singleOrNull { it.isDefault }
                ?.uuid
        }
        if (defaultTopicUuid.isNullOrBlank()) {
            _actionError.value = "У чата из уведомления нет доступной темы"
            return null
        }
        return ChatFlow.ChatDialog(
            title = stream.name,
            chatId = stream.uuid,
            topicName = null,
            topicUuid = defaultTopicUuid,
            isDirectMessages = true,
            userId = null,
            focusProviderMessageId = request.workspaceMessageId.toString(),
        )
    }

    suspend fun resolveDeepLinkNavigation(
        deepLink: WorkspaceDeepLink,
    ): ResolvedDeepLinkDestination? {
        _actionError.value = null
        resolveCachedDeepLinkNavigation(deepLink)?.let { return it }
        val targetStreamUuid = when (val target = deepLink.target) {
            is WorkspaceDeepLinkTarget.Stream -> target.streamUuid
            is WorkspaceDeepLinkTarget.Topic -> target.streamUuid
            is WorkspaceDeepLinkTarget.Message -> {
                val message = loadDeepLinkedMessage(target.messageUuid)
                    ?: return null
                return resolveMessageDeepLink(message)
            }
        }
        val stream = findDeepLinkedStream(targetStreamUuid) ?: return null
        return when (val target = deepLink.target) {
            is WorkspaceDeepLinkTarget.Stream -> {
                if (stream.isDirectProviderChat()) {
                    resolveDefaultTopic(stream)?.let {
                        ResolvedDeepLinkDestination.Dialog(it)
                    }
                } else {
                    ResolvedDeepLinkDestination.TopicList(
                        ChatFlow.ChatTopic(stream.name, stream.uuid),
                    )
                }
            }

            is WorkspaceDeepLinkTarget.Topic ->
                resolveTopicDialog(stream, target.topicUuid)?.let {
                    ResolvedDeepLinkDestination.Dialog(it)
                }

            is WorkspaceDeepLinkTarget.Message -> null
        }
    }

    fun resolveCachedDeepLinkNavigation(
        deepLink: WorkspaceDeepLink,
    ): ResolvedDeepLinkDestination? =
        resolveCachedTopicDeepLink(
            deepLink = deepLink,
            streams = repo.streams.value,
            topicsByStream = repo.streamTopics.value,
        )

    suspend fun resolvePersistedDeepLinkNavigation(
        deepLink: WorkspaceDeepLink,
    ): ResolvedDeepLinkDestination? {
        _actionError.value = null
        val target = deepLink.target as? WorkspaceDeepLinkTarget.Topic
            ?: return null
        val ownerKey = userViewModel.repo.activeCredentialSnapshot().ownerKey
            ?.takeIf(String::isNotBlank)
            ?: return null
        val state = try {
            conversationStateStore.read(
                ownerKey = ownerKey,
                streamUuid = target.streamUuid,
                topicUuid = target.topicUuid,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            _actionError.value =
                "Не удалось открыть сохранённый офлайн-чат"
            return null
        }
        return resolvePersistedConversationRoute(target, state)
            ?.let(ResolvedDeepLinkDestination::Dialog)
    }

    private suspend fun resolveMessageDeepLink(
        message: MessageResponse,
    ): ResolvedDeepLinkDestination? {
        val stream = findDeepLinkedStream(message.streamUuid) ?: return null
        val dialog = resolveTopicDialog(stream, message.topicUuid)
            ?: return null
        return ResolvedDeepLinkDestination.Dialog(
            dialog.copy(focusMessageUuid = message.uuid),
        )
    }

    private suspend fun loadDeepLinkedMessage(
        messageUuid: String,
    ): MessageResponse? {
        val cachedMatches = repo.streamTopicMessages.value
            .values
            .asSequence()
            .flatten()
            .filter { it.uuid == messageUuid }
            .toList()
        cachedMatches.singleOrNull()?.let { return it }
        if (cachedMatches.size > 1) {
            _actionError.value =
                "Кэш содержит противоречивые данные сообщения"
            return null
        }
        return when (
            val response = client.performRequest(
                MessagesByIdsRequest(listOf(messageUuid)),
            )
        ) {
            is ApiResult.Success -> {
                response.value
                    .filter { it.uuid == messageUuid }
                    .singleOrNull()
                    .also { message ->
                        if (message == null) {
                            _actionError.value =
                                "Сообщение по ссылке недоступно или было удалено"
                        }
                    }
            }

            is ApiResult.Error -> {
                _actionError.value = response.error.message
                    ?: "Не удалось открыть сообщение по ссылке"
                null
            }
        }
    }

    private fun findDeepLinkedStream(streamUuid: String): Stream? {
        val matches = repo.streams.value.filter { it.uuid == streamUuid }
        return matches.singleOrNull().also { stream ->
            if (stream == null) {
                _actionError.value =
                    "Чат по ссылке недоступен или ещё не синхронизирован"
            }
        }
    }

    private suspend fun resolveTopicDialog(
        stream: Stream,
        topicUuid: String,
    ): ChatFlow.ChatDialog? {
        if (repo.streamTopics.value[stream.uuid].isNullOrEmpty()) {
            loadTopics(stream)
        }
        val matches = repo.streamTopics.value[stream.uuid]
            .orEmpty()
            .filter { it.uuid == topicUuid }
        val topic = matches.singleOrNull()
        if (topic == null) {
            _actionError.value =
                "Тема по ссылке недоступна или была удалена"
            return null
        }
        return ChatFlow.ChatDialog(
            title = stream.name,
            chatId = stream.uuid,
            topicName = topic.name.takeUnless { stream.isDirectProviderChat() },
            topicUuid = topic.uuid,
            isDirectMessages = stream.isDirectProviderChat(),
            userId = null,
        )
    }

    private suspend fun resolveDefaultTopic(
        stream: Stream,
    ): ChatFlow.ChatDialog? {
        if (
            stream.defaultTopicUuid.isNullOrBlank() ||
            repo.streamTopics.value[stream.uuid].isNullOrEmpty()
        ) {
            loadTopics(stream)
        }
        val defaultTopicUuid = stream.defaultTopicUuid
            ?.takeIf(String::isNotBlank)
            ?: repo.streamTopics.value[stream.uuid]
                .orEmpty()
                .singleOrNull { it.isDefault }
                ?.uuid
        if (defaultTopicUuid.isNullOrBlank()) {
            _actionError.value = "У чата по ссылке нет доступной темы"
            return null
        }
        return ChatFlow.ChatDialog(
            title = stream.name,
            chatId = stream.uuid,
            topicName = null,
            topicUuid = defaultTopicUuid,
            isDirectMessages = true,
            userId = null,
        )
    }

    suspend fun updateSelectedChat(newChat: Stream?) {
        _currentlySelectedStream.update { newChat }
        if (newChat != null) {
            if (repo.streamTopics.value[newChat.uuid]?.isEmpty() ?: true) {
                loadTopics(newChat)
            }
        }
    }

    suspend fun loadServerSettings() {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(ServerSettingsRequest(client.userViewModel.baseUrl.value ?: ""))
        when(response) {
            is ApiResult.Success -> {
                repo.jitsiServerUrl = response.value.meetUrl
                loadUserInfo()
            }
            is ApiResult.Error -> {
                _actionError.value =
                    "Настройки звонков временно недоступны; чаты продолжат загрузку"
                loadUserInfo()
            }
        }
    }

    suspend fun loadUserInfo() {
        val response = client.performRequest(OwnUserRequest())
        when(response) {
            is ApiResult.Success -> {
                val expectedUserId = userViewModel.userId.value
                if (
                    expectedUserId != null &&
                    response.value.uuid != expectedUserId
                ) {
                    _queryState.value = QueryState.Error(
                        "Профиль не соответствует активной учётной записи",
                    )
                    return
                }
                userViewModel.repo.saveCurrentAccountProfile(
                    userId = response.value.uuid,
                    displayName = response.value.displayableName(),
                    email = response.value.email,
                    avatarUrn = response.value.avatar,
                )
                userViewModel.userData = response.value
                repo.currentUser = response.value
                loadMessageReactions(response.value.uuid)
            }

            is ApiResult.Error -> {
                _actionError.value =
                    "Профиль временно недоступен; чаты продолжат загрузку"
                loadAllUsersInfo()
            }
        }
    }

    suspend fun loadMessageReactions(userUuid: String) {
        val response = client.performRequest(MessageReactionsRequest(userUuid))
        when(response) {
            is ApiResult.Success -> {
                repo.setInitialMessageReactions(response.value)
                loadAllUsersInfo()
            }

            is ApiResult.Error -> {
                _actionError.value =
                    "Мои реакции временно не синхронизированы"
                loadAllUsersInfo()
            }
        }
    }

    suspend fun loadAllUsersInfo() {
        val response = client.performRequest(UsersRequest())
        when(response) {
            is ApiResult.Success -> {
                repo.setInitialUsers(response.value)
                loadFolders()
            }

            is ApiResult.Error -> {
                _actionError.value =
                    "Профили участников временно недоступны"
                loadFolders()
            }
        }
    }
    suspend fun loadFolders() {
        val response = client.performRequest(FoldersRequest())
        when(response) {
            is ApiResult.Success -> {
                repo.setInitialFolders(
                    response.value.sortedBy { folder ->
                        parseTime(folder.creationDate)
                    },
                )
                _currentlySelectedFolder.value = folders.value.firstOrNull {
                    it.uuid == repo.selectedFolderUuid.value
                } ?: folders.value.firstOrNull()
                repo.selectFolder(_currentlySelectedFolder.value?.uuid)
                loadSubscribedChannels()
            }

            is ApiResult.Error -> {
                _actionError.value = if (repo.folders.value.isEmpty()) {
                    "Папки временно недоступны; показаны все чаты"
                } else {
                    "Не удалось обновить папки; показаны сохранённые данные"
                }
                loadSubscribedChannels()
            }
        }
    }

    suspend fun loadSubscribedChannels() {
        val response = client.performRequest(StreamsRequest())
        when(response) {
            is ApiResult.Success -> {
                refreshStreamBindings()
                val messageIds = response.value.mapNotNull { it.lastMessageUuid }
                if (!messageIds.isEmpty()) {
                    val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
                    when (messagesResponse) {
                        is ApiResult.Success -> {
                            repo.setInitialMessagesPool(messagesResponse.value)
                            val streamsWithMessages = response.value.map { stream ->
                                var updatedStream = stream
                                updatedStream.lastMessage = poolMessage(stream.lastMessageUuid)
                                updatedStream
                            }
                            repo.setInitialStreams(streamsWithMessages)
                            _queryState.value = QueryState.Success
                        }

                        is ApiResult.Error -> {
                            repo.setInitialStreams(response.value)
                            _queryState.value = QueryState.Success
                        }
                    }
                } else {
                    repo.setInitialStreams(response.value)
                    _queryState.value = QueryState.Success
                }
            }

            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(
                    response.error.message ?: "Не удалось загрузить чаты",
                )
            }
        }
    }

    private fun refreshStreamBindings() {
        val ownerKey = userViewModel.activeAccountId.value ?: return
        viewModelScope.launch {
            when (
                val response =
                    client.performRequest(StreamBindingsRequest())
            ) {
                is ApiResult.Success -> {
                    userViewModel.repo.withActiveCredentialOwner(ownerKey) {
                        repo.setInitialStreamBindings(response.value)
                    }
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    suspend fun loadTopics(stream: Stream) {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(TopicsRequest(stream.uuid))
        when(response) {
            is ApiResult.Success -> {
                val messageIds = response.value.mapNotNull { it.lastMessageUuid }
                if (!messageIds.isEmpty()) {
                    val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
                    when (messagesResponse) {
                        is ApiResult.Success -> {
                            repo.updateMessagesPool(messagesResponse.value)
                            val topicsWithMessages = response.value.map { topic ->
                                var updatedTopic = topic
                                updatedTopic.lastMessage = poolMessage(topic.lastMessageUuid)
                                updatedTopic
                            }
                            repo.addStreamTopics(stream.uuid, topicsWithMessages)
                            _queryState.value = QueryState.Success
                        }

                        is ApiResult.Error -> {
                            repo.addStreamTopics(stream.uuid, response.value)
                            _queryState.value = QueryState.Success
                        }
                    }
                } else {
                    repo.addStreamTopics(stream.uuid, response.value)
                    _queryState.value = QueryState.Success
                }
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(
                    response.error.message ?: "Не удалось загрузить темы",
                )
            }
        }
    }

    private suspend fun refreshInboxInternal() {
        if (!inboxRefreshMutex.tryLock()) return
        try {
            val ownerKey = userViewModel.repo
                .activeCredentialSnapshot()
                .ownerKey
                ?.takeIf(String::isNotBlank)
            if (ownerKey == null) {
                _inboxSyncState.value = InboxSyncState(
                    hasLoaded = true,
                    error = "Не удалось определить активную учётную запись",
                )
                return
            }
            val previousState = _inboxSyncState.value
                .takeIf { it.ownerKey == ownerKey }
                ?: InboxSyncState(ownerKey = ownerKey)
            _inboxSyncState.value = previousState.copy(
                refreshing = true,
                error = null,
            )

            repeat(INBOX_CATALOG_REFRESH_ATTEMPTS) { attempt ->
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    failInboxRefresh("Учётная запись изменилась во время обновления")
                    return
                }
                val catalogBeforeRequest = repo.inboxCatalogReference()

                val streamsResponse = client.performRequest(StreamsRequest())
                val refreshedStreams = when (streamsResponse) {
                    is ApiResult.Success -> streamsResponse.value
                    is ApiResult.Error -> {
                        failInboxRefresh(
                            "Не удалось обновить список чатов",
                            streamsResponse.error.message,
                        )
                        return
                    }
                }
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    failInboxRefresh("Учётная запись изменилась во время обновления")
                    return
                }

                val topicsResponse = client.performRequest(TopicsRequest())
                val refreshedTopics = when (topicsResponse) {
                    is ApiResult.Success -> topicsResponse.value
                    is ApiResult.Error -> {
                        failInboxRefresh(
                            "Не удалось обновить темы",
                            topicsResponse.error.message,
                        )
                        return
                    }
                }
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    failInboxRefresh("Учётная запись изменилась во время обновления")
                    return
                }

                val catalogError = validateInboxCatalog(
                    streams = refreshedStreams,
                    topics = refreshedTopics,
                )
                if (catalogError != null) {
                    failInboxRefresh("Сервер вернул противоречивый каталог")
                    return
                }

                val messagesByUuid = repo.messagesPool.value
                    .associateBy(MessageResponse::uuid)
                val streamsWithMessages = refreshedStreams.map { stream ->
                    stream.copy(
                        lastMessage =
                            stream.lastMessageUuid?.let(messagesByUuid::get),
                    )
                }
                val topicsWithMessages = refreshedTopics.map { topic ->
                    topic.copy(
                        lastMessage =
                            topic.lastMessageUuid?.let(messagesByUuid::get),
                    )
                }
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    failInboxRefresh("Учётная запись изменилась во время обновления")
                    return
                }

                val catalogApplied =
                    userViewModel.repo.withActiveCredentialOwner(ownerKey) {
                        repo.applyInboxCatalogIfUnchanged(
                            expected = catalogBeforeRequest,
                            streams = streamsWithMessages,
                            topics = topicsWithMessages,
                        )
                    } == true
                if (
                    !catalogApplied &&
                    !userViewModel.repo.isActiveCredentialOwner(ownerKey)
                ) {
                    failInboxRefresh(
                        "Учётная запись изменилась во время обновления",
                    )
                    return
                }
                when (
                    decideInboxCatalogApply(
                        catalogChangedDuringRequest = !catalogApplied,
                        attempt = attempt,
                        maxAttempts = INBOX_CATALOG_REFRESH_ATTEMPTS,
                    )
                ) {
                    InboxCatalogApplyDecision.RETRY -> return@repeat
                    InboxCatalogApplyDecision.FAIL_BUSY -> {
                        failInboxRefresh(
                            "Каталог продолжает обновляться. " +
                                "Повторите через несколько секунд",
                        )
                        return
                    }
                    InboxCatalogApplyDecision.APPLY -> check(catalogApplied)
                }
                _inboxSyncState.value = InboxSyncState(
                    ownerKey = ownerKey,
                    refreshing = false,
                    hasLoaded = true,
                    hasUsableSnapshot = true,
                    error = null,
                )
                persistInboxSnapshot(ownerKey)
                return
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            failInboxRefresh("Не удалось обновить входящие")
        } finally {
            _inboxSyncState.value = _inboxSyncState.value.copy(refreshing = false)
            inboxRefreshMutex.unlock()
        }
    }

    private suspend fun restoreInboxSnapshotAvailability(ownerKey: String?) {
        if (ownerKey.isNullOrBlank()) {
            _inboxSyncState.value = InboxSyncState()
            return
        }
        val markerAvailable = try {
            userViewModel.workspaceSnapshotStore.readTimeline(
                ownerKey = ownerKey,
                kind = WorkspaceTimelineKind.INBOX,
            ) != null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }
        userViewModel.repo.withActiveCredentialOwner(ownerKey) {
            val current = _inboxSyncState.value
            _inboxSyncState.value = if (current.ownerKey == ownerKey) {
                current.copy(
                    hasLoaded = current.hasLoaded || markerAvailable,
                    hasUsableSnapshot =
                        current.hasUsableSnapshot || markerAvailable,
                )
            } else {
                InboxSyncState(
                    ownerKey = ownerKey,
                    hasLoaded = markerAvailable,
                    hasUsableSnapshot = markerAvailable,
                )
            }
        }
    }

    private suspend fun persistInboxSnapshot(ownerKey: String) {
        try {
            userViewModel.repo.withActiveCredentialOwner(ownerKey) {
                userViewModel.workspaceSnapshotStore.write(
                    ownerKey = ownerKey,
                    snapshot = repo.workspaceSnapshot(),
                )
                userViewModel.workspaceSnapshotStore.writeTimeline(
                    ownerKey = ownerKey,
                    kind = WorkspaceTimelineKind.INBOX,
                    snapshot = WorkspaceTimelineSnapshot(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A cache write failure never turns a valid online Inbox into error.
        }
    }

    private fun failInboxRefresh(
        summary: String,
        detail: String? = null,
    ) {
        val safeDetail = detail
            ?.replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.take(160)
        _inboxSyncState.value = _inboxSyncState.value.copy(
            error = if (safeDetail == null) summary else "$summary: $safeDetail",
        )
    }

    suspend fun resolveInboxStreamNavigation(
        streamUuid: String,
    ): ResolvedDeepLinkDestination? {
        _actionError.value = null
        val stream = repo.streams.value
            .filter { it.uuid == streamUuid && !it.isArchived }
            .singleOrNull()
        if (stream == null) {
            _actionError.value = "Чат больше недоступен"
            return null
        }
        return if (stream.isDirectProviderChat()) {
            resolveDefaultTopic(stream)?.let(ResolvedDeepLinkDestination::Dialog)
        } else {
            ResolvedDeepLinkDestination.TopicList(
                ChatFlow.ChatTopic(stream.name, stream.uuid),
            )
        }
    }

    suspend fun resolveFeedMessageNavigation(
        streamUuid: String,
        topicUuid: String,
        messageUuid: String,
        beginForward: Boolean,
    ): FeedMessageNavigationResult = resolveActivityItemNavigation(
        streamUuid = streamUuid,
        topicUuid = topicUuid,
        messageUuid = messageUuid,
        beginForward = beginForward,
        itemLabel = "сообщения",
    )

    suspend fun resolveDraftNavigation(
        streamUuid: String,
        topicUuid: String,
    ): FeedMessageNavigationResult = resolveActivityItemNavigation(
        streamUuid = streamUuid,
        topicUuid = topicUuid,
        messageUuid = null,
        beginForward = false,
        itemLabel = "черновика",
    )

    private suspend fun resolveActivityItemNavigation(
        streamUuid: String,
        topicUuid: String,
        messageUuid: String?,
        beginForward: Boolean,
        itemLabel: String,
    ): FeedMessageNavigationResult {
        val ownerKey = userViewModel.repo
            .activeCredentialSnapshot()
            .ownerKey
            ?.takeIf(String::isNotBlank)
            ?: return FeedMessageNavigationResult(
                error = "Не удалось определить активную учётную запись",
            )
        if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
            return FeedMessageNavigationResult(
                error = "Учётная запись изменилась до открытия сообщения",
            )
        }

        var streamSelection = selectFeedStream(
            streamUuid = streamUuid,
            candidates = repo.streams.value,
        )
        if (streamSelection.conflicting) {
            return FeedMessageNavigationResult(
                error = "Каталог содержит противоречивый список чатов",
            )
        }
        var stream = streamSelection.value
        if (stream == null) {
            val refreshedStreams = when (
                val response = client.performRequest(StreamsRequest())
            ) {
                is ApiResult.Success -> response.value

                is ApiResult.Error -> {
                    return FeedMessageNavigationResult(
                        error = response.error.message
                            ?: "Не удалось обновить список чатов",
                    )
                }
            }
            if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                return FeedMessageNavigationResult(
                    error = "Учётная запись изменилась во время открытия сообщения",
                )
            }
            streamSelection = selectFeedStream(
                streamUuid = streamUuid,
                candidates = refreshedStreams,
            )
            if (streamSelection.conflicting) {
                return FeedMessageNavigationResult(
                    error = "Сервер вернул противоречивый список чатов",
                )
            }
            stream = streamSelection.value
            stream?.let(repo::addStream)
        }
        if (stream == null) {
            return FeedMessageNavigationResult(
                error = "Чат для этого $itemLabel больше недоступен",
            )
        }
        if (stream.isArchived) {
            return FeedMessageNavigationResult(
                error = "Архивный чат пока нельзя открыть в мобильном приложении",
            )
        }

        var topicSelection = selectFeedTopic(
            streamUuid = streamUuid,
            topicUuid = topicUuid,
            candidates = repo.streamTopics.value[streamUuid].orEmpty(),
        )
        if (topicSelection.conflicting) {
            return FeedMessageNavigationResult(
                error = "Каталог содержит противоречивый список топиков",
            )
        }
        var topic = topicSelection.value
        if (topic == null) {
            val refreshedTopics = when (
                val response = client.performRequest(TopicsRequest(streamUuid))
            ) {
                is ApiResult.Success -> response.value

                is ApiResult.Error -> {
                    return FeedMessageNavigationResult(
                        error = response.error.message
                            ?: "Не удалось обновить топики чата",
                    )
                }
            }
            if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                return FeedMessageNavigationResult(
                    error = "Учётная запись изменилась во время открытия сообщения",
                )
            }
            topicSelection = selectFeedTopic(
                streamUuid = streamUuid,
                topicUuid = topicUuid,
                candidates = refreshedTopics,
            )
            if (topicSelection.conflicting) {
                return FeedMessageNavigationResult(
                    error = "Сервер вернул противоречивый список топиков",
                )
            }
            topic = topicSelection.value
            if (topic != null) {
                repo.addStreamTopics(
                    streamUuid,
                    listOf(topic),
                )
            }
        }
        if (topic == null) {
            return FeedMessageNavigationResult(
                error = "Топик для этого $itemLabel больше недоступен",
            )
        }

        return FeedMessageNavigationResult(
            route = ChatFlow.ChatDialog(
                title = stream.name,
                chatId = stream.uuid,
                topicName = topic.name.takeUnless {
                    stream.isDirectProviderChat()
                },
                topicUuid = topic.uuid,
                isDirectMessages = stream.isDirectProviderChat(),
                userId = null,
                focusMessageUuid = messageUuid,
                beginForwardMessageUuid =
                    messageUuid?.takeIf { beginForward },
            ),
        )
    }

    fun addFolder(name: String) {
        viewModelScope.launch { addFolderInternal(name) }
    }

    fun createFolderWithChats(
        name: String,
        selectedStreams: List<Stream>,
    ): Long {
        val requestId = nextCatalogActionRequestId.incrementAndGet()
        viewModelScope.launch {
            createFolderWithChatsInternal(
                requestId = requestId,
                name = name,
                selectedStreams = selectedStreams,
            )
        }
        return requestId
    }

    private suspend fun createFolderWithChatsInternal(
        requestId: Long,
        name: String,
        selectedStreams: List<Stream>,
    ) {
        val draft = validateFolderDraft(name, selectedStreams)
        if (draft == null) {
            val message = folderDraftError(name)
            _actionError.value = message
            _folderCreationResult.value = FolderCreationResult(
                requestId = requestId,
                message = message,
            )
            return
        }
        if (!folderMutationMutex.tryLock()) {
            _folderCreationResult.value = FolderCreationResult(
                requestId = requestId,
                requestedChatCount = draft.streams.size,
                message = "Дождитесь завершения предыдущего действия",
            )
            return
        }
        _folderActionInProgress.value = true
        _actionError.value = null
        try {
            val ownerKey = userViewModel.repo
                .activeCredentialSnapshot()
                .ownerKey
                ?.takeIf(String::isNotBlank)
            if (ownerKey == null) {
                val message = "Не удалось определить активную учётную запись"
                _actionError.value = message
                _folderCreationResult.value = FolderCreationResult(
                    requestId = requestId,
                    requestedChatCount = draft.streams.size,
                    message = message,
                )
                return
            }
            val createResponse = client.performRequest(
                AddFolderRequest(draft.name),
                expectedOwnerKey = ownerKey,
            )
            val createdFolderUuid = when (createResponse) {
                is ApiResult.Success -> createResponse.value.uuid
                is ApiResult.Error -> {
                    val message = createResponse.error.message
                        ?: "Не удалось создать папку"
                    _actionError.value = message
                    _folderCreationResult.value = FolderCreationResult(
                        requestId = requestId,
                        requestedChatCount = draft.streams.size,
                        message = message,
                    )
                    return
                }
            }
            var addedChatCount = 0
            var assignmentError: String? = null
            for ((index, stream) in draft.streams.withIndex()) {
                if (!userViewModel.repo.isActiveCredentialOwner(ownerKey)) {
                    assignmentError =
                        "Учётная запись изменилась во время создания папки"
                    break
                }
                when (
                    val addResponse = client.performRequest(
                        AddChatToFolderRequest(
                            folderUuid = createdFolderUuid,
                            streamUuid = stream.uuid,
                            chatType = stream.folderItemChatType(),
                            orderIndex = index,
                        ),
                        expectedOwnerKey = ownerKey,
                    )
                ) {
                    is ApiResult.Success -> addedChatCount += 1
                    is ApiResult.Error -> {
                        assignmentError = addResponse.error.message
                            ?: "Не удалось добавить один из чатов"
                    }
                }
            }
            refreshFolders(expectedOwnerKey = ownerKey)
            val message = when {
                addedChatCount == draft.streams.size ->
                    "Папка создана"
                assignmentError != null ->
                    "Папка создана: добавлено $addedChatCount из ${draft.streams.size} чатов"
                else -> "Папка создана"
            }
            if (addedChatCount != draft.streams.size) {
                _actionError.value = message
            }
            _folderCreationResult.value = FolderCreationResult(
                requestId = requestId,
                createdFolderUuid = createdFolderUuid,
                requestedChatCount = draft.streams.size,
                addedChatCount = addedChatCount,
                message = message,
            )
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    private suspend fun addFolderInternal(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _actionError.value = "Введите название папки"
            return
        }
        if (!folderMutationMutex.tryLock()) return
        _folderActionInProgress.value = true
        _actionError.value = null
        try {
            when (
                val response = client.performRequest(AddFolderRequest(normalizedName))
            ) {
                is ApiResult.Success -> {
                    refreshFolders()
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось создать папку"
                }
            }
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    fun createTopic(
        stream: Stream,
        name: String,
    ): Long = launchCatalogAction(
        kind = CatalogActionKind.CREATE_TOPIC,
        targetUuid = stream.uuid,
    ) {
        createTopicInternal(stream, name) != null
    }

    private suspend fun createTopicInternal(
        stream: Stream,
        name: String,
    ): TopicsResponseData? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _actionError.value = "Введите название топика"
            return null
        }
        if (!topicMutationMutex.tryLock()) return null
        _topicActionInProgress.value = true
        return try {
            _actionError.value = null
            when (
                val response = client.performRequest(
                    CreateTopicRequest(normalizedName, stream.uuid),
                )
            ) {
                is ApiResult.Success -> {
                    if (repo.streamTopics.value[stream.uuid] == null) {
                        repo.addStreamTopics(stream.uuid, listOf(response.value))
                    } else {
                        repo.addTopicToStream(response.value)
                    }
                    response.value
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось создать топик"
                    null
                }
            }
        } finally {
            _topicActionInProgress.value = false
            topicMutationMutex.unlock()
        }
    }

    fun renameTopic(
        topic: TopicsResponseData,
        name: String,
    ): Long = launchCatalogAction(
        kind = CatalogActionKind.RENAME_TOPIC,
        targetUuid = topic.uuid,
    ) {
        renameTopicInternal(topic, name)
    }

    private suspend fun renameTopicInternal(
        topic: TopicsResponseData,
        name: String,
    ): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _actionError.value = "Введите название топика"
            return false
        }
        if (normalizedName == topic.name) return true
        return applyTopicMutation(
            fallbackError = "Не удалось переименовать топик",
        ) {
            client.performRequest(RenameTopicRequest(topic.uuid, normalizedName))
        }
    }

    fun toggleTopicDone(topic: TopicsResponseData): Long = launchCatalogAction(
        kind = CatalogActionKind.TOGGLE_TOPIC_DONE,
        targetUuid = topic.uuid,
    ) {
        applyTopicMutation(
            fallbackError = if (topic.isDone) {
                "Не удалось вернуть топик в работу"
            } else {
                "Не удалось завершить топик"
            },
        ) {
            client.performRequest(ToggleTopicDoneRequest(topic.uuid))
        }
    }

    fun setTopicNotificationMode(
        topic: TopicsResponseData,
        notificationMode: String,
    ): Long = launchCatalogAction(
        kind = CatalogActionKind.TOPIC_NOTIFICATIONS,
        targetUuid = topic.uuid,
    ) {
        setTopicNotificationModeInternal(topic, notificationMode)
    }

    private suspend fun setTopicNotificationModeInternal(
        topic: TopicsResponseData,
        notificationMode: String,
    ): Boolean {
        if (
            notificationMode !in setOf("mute", "default", "unmute", "follow")
        ) {
            _actionError.value = "Неизвестный режим уведомлений"
            return false
        }
        if (topic.notificationMode == notificationMode) return true
        return applyTopicMutation(
            fallbackError = "Не удалось изменить уведомления топика",
        ) {
            client.performRequest(
                TopicNotificationsRequest(topic.uuid, notificationMode),
            )
        }
    }

    fun markTopicRead(topic: TopicsResponseData): Long = launchCatalogAction(
        kind = CatalogActionKind.MARK_TOPIC_READ,
        targetUuid = topic.uuid,
    ) {
        applyTopicMutation(
            fallbackError = "Не удалось отметить топик прочитанным",
        ) {
            client.performRequest(MarkTopicReadRequest(topic.uuid))
        }
    }

    fun markStreamRead(stream: Stream) {
        viewModelScope.launch { markStreamReadInternal(stream) }
    }

    fun setStreamNotificationMode(
        stream: Stream,
        notificationMode: String,
    ) {
        viewModelScope.launch {
            setStreamNotificationModeInternal(stream, notificationMode)
        }
    }

    private suspend fun setStreamNotificationModeInternal(
        stream: Stream,
        notificationMode: String,
    ): Boolean {
        if (notificationMode !in STREAM_NOTIFICATION_MODES) {
            _actionError.value = "Неизвестный режим уведомлений"
            return false
        }
        if (stream.notificationMode == notificationMode) return true
        if (!streamMutationMutex.tryLock()) return false
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(
                    StreamNotificationsRequest(stream.uuid, notificationMode),
                )
            ) {
                is ApiResult.Success -> {
                    repo.updateStream(response.value)
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось изменить уведомления чата"
                    false
                }
            }
        } finally {
            streamMutationMutex.unlock()
        }
    }

    private suspend fun markStreamReadInternal(stream: Stream): Boolean {
        if (!streamMutationMutex.tryLock()) return false
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(MarkStreamReadRequest(stream.uuid))
            ) {
                is ApiResult.Success -> {
                    repo.updateStream(response.value)
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось отметить чат прочитанным"
                    false
                }
            }
        } finally {
            streamMutationMutex.unlock()
        }
    }

    private suspend fun applyTopicMutation(
        fallbackError: String,
        request: suspend () -> ApiResult<TopicsResponseData, ApiError>,
    ): Boolean {
        if (!topicMutationMutex.tryLock()) return false
        _topicActionInProgress.value = true
        _actionError.value = null
        return try {
            when (val response = request()) {
                is ApiResult.Success -> {
                    repo.updateTopic(response.value)
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message ?: fallbackError
                    false
                }
            }
        } finally {
            _topicActionInProgress.value = false
            topicMutationMutex.unlock()
        }
    }

    fun renameFolder(
        folder: FolderResponseData,
        name: String,
    ): Long = launchCatalogAction(
        kind = CatalogActionKind.RENAME_FOLDER,
        targetUuid = folder.uuid,
    ) {
        renameFolderInternal(folder, name)
    }

    private suspend fun renameFolderInternal(
        folder: FolderResponseData,
        name: String,
    ): Boolean {
        val normalizedName = name.trim()
        if (!folder.isUserManaged() || normalizedName.isEmpty()) {
            _actionError.value = "Эту папку нельзя переименовать"
            return false
        }
        if (!folderMutationMutex.tryLock()) return false
        _folderActionInProgress.value = true
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(
                    UpdateFolderRequest(
                        folderUuid = folder.uuid,
                        title = normalizedName,
                        backgroundColorValue = folder.backgroundColorValue,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    repo.updateFolder(response.value)
                    refreshFolders()
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось переименовать папку"
                    false
                }
            }
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    fun deleteFolder(folder: FolderResponseData): Long = launchCatalogAction(
        kind = CatalogActionKind.DELETE_FOLDER,
        targetUuid = folder.uuid,
    ) {
        deleteFolderInternal(folder)
    }

    private suspend fun deleteFolderInternal(
        folder: FolderResponseData,
    ): Boolean {
        if (!folder.isUserManaged()) {
            _actionError.value = "Системную папку нельзя удалить"
            return false
        }
        if (!folderMutationMutex.tryLock()) return false
        _folderActionInProgress.value = true
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(DeleteFolderRequest(folder.uuid))
            ) {
                is ApiResult.Success -> {
                    repo.removeFolder(folder.uuid)
                    refreshFolders()
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось удалить папку"
                    false
                }
            }
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    fun addChatFolder(streamUuid: String, chatType: String, folderUuid: String) {
        viewModelScope.launch {
            addChatFolderInternal(streamUuid, chatType, folderUuid)
        }
    }

    private suspend fun addChatFolderInternal(
        streamUuid: String,
        chatType: String,
        folderUuid: String,
    ) {
        if (!folderMutationMutex.tryLock()) return
        _folderActionInProgress.value = true
        _actionError.value = null
        try {
            when (
                val response = client.performRequest(
                    AddChatToFolderRequest(folderUuid, streamUuid, chatType),
                )
            ) {
                is ApiResult.Success -> {
                    refreshFolders()
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось добавить чат в папку"
                }
            }
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    fun deleteChatFromFolder(chatId: String, folder: FolderResponseData) {
        viewModelScope.launch { deleteChatFromFolderInternal(chatId, folder) }
    }

    private suspend fun deleteChatFromFolderInternal(
        chatId: String,
        folder: FolderResponseData,
    ) {
        if (!folderMutationMutex.tryLock()) return
        _folderActionInProgress.value = true
        _actionError.value = null
        try {
            val folderChat = folder.items.firstOrNull { it.streamUuid == chatId }
            if (folderChat != null) {
                when (
                    val response = client.performRequest(
                        DeleteChatFromFolderRequest(folderChat.uuid),
                    )
                ) {
                    is ApiResult.Success -> {
                        refreshFolders()
                    }

                    is ApiResult.Error -> {
                        _actionError.value = response.error.message
                            ?: "Не удалось удалить чат из папки"
                    }
                }
            } else {
                _actionError.value = "Чат уже отсутствует в этой папке"
            }
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    fun setFolderItemPinned(
        folder: FolderResponseData,
        streamUuid: String,
        pinned: Boolean,
    ) {
        viewModelScope.launch {
            setFolderItemPinnedInternal(folder, streamUuid, pinned)
        }
    }

    private suspend fun setFolderItemPinnedInternal(
        folder: FolderResponseData,
        streamUuid: String,
        pinned: Boolean,
    ) {
        if (!folderMutationMutex.tryLock()) return
        _folderActionInProgress.value = true
        _actionError.value = null
        try {
            val folderItem = folder.items.firstOrNull { it.streamUuid == streamUuid }
            if (folderItem == null) {
                _actionError.value = "Чат уже отсутствует в этой папке"
                return
            }
            val response = if (pinned) {
                client.performRequest(PinFolderItemRequest(folderItem.uuid))
            } else {
                client.performRequest(UnpinFolderItemRequest(folderItem.uuid))
            }
            when (response) {
                is ApiResult.Success -> refreshFolders()
                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: if (pinned) {
                            "Не удалось закрепить чат"
                        } else {
                            "Не удалось открепить чат"
                        }
                    }
                }
        } finally {
            _folderActionInProgress.value = false
            folderMutationMutex.unlock()
        }
    }

    private fun launchCatalogAction(
        kind: String,
        targetUuid: String?,
        action: suspend () -> Boolean,
    ): Long {
        val requestId = nextCatalogActionRequestId.incrementAndGet()
        viewModelScope.launch {
            _lastCatalogActionResult.value = CatalogActionResult(
                requestId = requestId,
                kind = kind,
                targetUuid = targetUuid,
                success = action(),
            )
        }
        return requestId
    }

    private suspend fun refreshFolders(expectedOwnerKey: String? = null) {
        when (
            val foldersResponse = client.performRequest(
                FoldersRequest(),
                expectedOwnerKey = expectedOwnerKey,
            )
        ) {
            is ApiResult.Success -> {
                repo.setInitialFolders(foldersResponse.value)
                val selectedUuid = repo.selectedFolderUuid.value
                    ?: currentlySelectedFolder.value?.uuid
                _currentlySelectedFolder.value = foldersResponse.value
                    .firstOrNull { it.uuid == selectedUuid }
                    ?: foldersResponse.value.firstOrNull()
                repo.selectFolder(_currentlySelectedFolder.value?.uuid)
                _queryState.value = QueryState.Success
            }

            is ApiResult.Error -> {
                _actionError.value = foldersResponse.error.message
                    ?: "Изменение сохранено, но список папок не обновился"
            }
        }
    }

    fun createPrivateStream(user: UserResponseData) {
        viewModelScope.launch { createPrivateStreamInternal(user) }
    }

    private suspend fun createPrivateStreamInternal(user: UserResponseData) {
        if (!createMutationMutex.tryLock()) return
        _createQueryState.value = QueryState.Loading
        _actionError.value = null
        try {
            val response = client.performRequest(
                AddStreamRequest("Direct", "Private workspace", user.uuid),
            )
            when(response) {
                is ApiResult.Success -> {
                    finishCreatedStream(response.value)
                }
                is ApiResult.Error -> {
                    val message = response.error.message
                        ?: "Не удалось создать личный чат"
                    _createQueryState.value = QueryState.Error(message)
                    _actionError.value = message
                }
            }
        } finally {
            createMutationMutex.unlock()
        }
    }

    fun createChannel(
        name: String,
        description: String,
        inviteOnly: Boolean,
        announce: Boolean,
        memberUserUuids: Collection<String>,
    ) {
        viewModelScope.launch {
            createChannelInternal(
                name = name,
                description = description,
                inviteOnly = inviteOnly,
                announce = announce,
                memberUserUuids = memberUserUuids,
            )
        }
    }

    private suspend fun createChannelInternal(
        name: String,
        description: String,
        inviteOnly: Boolean,
        announce: Boolean,
        memberUserUuids: Collection<String>,
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _createQueryState.value = QueryState.Error("Введите название канала")
            _actionError.value = "Введите название канала"
            return
        }
        if (!createMutationMutex.tryLock()) return
        _createQueryState.value = QueryState.Loading
        _actionError.value = null
        try {
            when (
                val response = client.performRequest(
                    AddStreamRequest(
                        name = normalizedName,
                        description = description.trim(),
                        directUserUuid = null,
                        inviteOnly = inviteOnly,
                        announce = announce,
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    val completedStream = finishCreatedStream(
                        newStream = response.value,
                        publishSuccess = false,
                    )
                    val members = memberUserUuids
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct()
                        .filterNot { it == userViewModel.userId.value }
                    if (members.isNotEmpty()) {
                        when (
                            val memberResponse = client.performRequest(
                                AddStreamMembersRequest(completedStream.uuid, members),
                            )
                        ) {
                            is ApiResult.Success -> Unit
                            is ApiResult.Error -> {
                                appendActionError(
                                    memberResponse.error.message
                                        ?: "Канал создан, но не всех участников удалось добавить",
                                )
                            }
                        }
                    }
                    _createQueryState.value = QueryState.Success
                }

                is ApiResult.Error -> {
                    val message = response.error.message ?: "Не удалось создать канал"
                    _createQueryState.value = QueryState.Error(message)
                    _actionError.value = message
                }
            }
        } finally {
            createMutationMutex.unlock()
        }
    }

    fun consumeCreatedStream() {
        createdStream = null
        _createQueryState.value = QueryState.Idle
    }

    private suspend fun finishCreatedStream(
        newStream: Stream,
        publishSuccess: Boolean = true,
    ): Stream {
        repo.addStream(newStream)
        var topics = emptyList<TopicsResponseData>()
        when (
            val topicsResponse = client.performRequest(TopicsRequest(newStream.uuid))
        ) {
            is ApiResult.Success -> {
                topics = topicsResponse.value
                repo.addStreamTopics(newStream.uuid, topics)
            }

            is ApiResult.Error -> {
                appendActionError(
                    topicsResponse.error.message
                        ?: "Чат создан, но загрузить его топики пока не удалось",
                )
            }
        }

        var refreshedStream: Stream? = null
        var defaultTopicUuid = resolveCreatedStreamDefaultTopicUuid(
            responseDefaultTopicUuid = newStream.defaultTopicUuid,
            topics = topics,
            refreshedDefaultTopicUuid = null,
        )
        if (defaultTopicUuid.isNullOrBlank()) {
            when (val streamsResponse = client.performRequest(StreamsRequest())) {
                is ApiResult.Success -> {
                    repo.setInitialStreams(streamsResponse.value)
                    refreshedStream = streamsResponse.value
                        .firstOrNull { it.uuid == newStream.uuid }
                    defaultTopicUuid = resolveCreatedStreamDefaultTopicUuid(
                        responseDefaultTopicUuid = newStream.defaultTopicUuid,
                        topics = topics,
                        refreshedDefaultTopicUuid =
                            refreshedStream?.defaultTopicUuid,
                    )
                }

                is ApiResult.Error -> {
                    appendActionError(
                        streamsResponse.error.message
                            ?: "Чат создан, но каталог пока не обновился",
                    )
                }
            }
        }

        val effectiveStream = refreshedStream ?: newStream
        if (defaultTopicUuid.isNullOrBlank()) {
            appendActionError(
                "Чат создан, но сервер не вернул его основной топик; " +
                    "откройте его из обновлённого каталога",
            )
        }
        val completedStream = effectiveStream.copy(
            defaultTopicUuid = defaultTopicUuid,
        )
        repo.updateStream(completedStream)
        createdStream = completedStream
        if (publishSuccess) {
            _createQueryState.value = QueryState.Success
        }
        return completedStream
    }

    private fun appendActionError(message: String) {
        _actionError.value = _actionError.value
            ?.takeIf(String::isNotBlank)
            ?.let { existing ->
                if (message in existing) existing else "$existing. $message"
            }
            ?: message
    }

    private fun providerKeysMatch(requested: String, actual: String?): Boolean {
        if (actual == null) return false
        if (requested == actual) return true
        val requestedPrefix = requested.substringBefore(':')
        val actualPrefix = actual.substringBefore(':')
        if (requestedPrefix != actualPrefix) return false
        val requestedIds = requested.substringAfter(':')
            .split(',')
            .mapNotNull(String::toLongOrNull)
            .toSet()
        val actualIds = actual.substringAfter(':')
            .split(',')
            .mapNotNull(String::toLongOrNull)
            .toSet()
        if (requestedIds.isEmpty() || actualIds.isEmpty()) return false
        return when (requestedPrefix) {
            "direct" ->
                actualIds == requestedIds ||
                    (actualIds.size == 1 && actualIds.single() in requestedIds)

            "group_direct" ->
                actualIds == requestedIds ||
                    (actualIds.size + 1 == requestedIds.size &&
                        actualIds.all(requestedIds::contains))

            else -> false
        }
    }
}

internal fun resolveCachedTopicDeepLink(
    deepLink: WorkspaceDeepLink,
    streams: List<Stream>,
    topicsByStream: Map<String, List<TopicsResponseData>>,
): ResolvedDeepLinkDestination.Dialog? {
    val target = deepLink.target as? WorkspaceDeepLinkTarget.Topic
        ?: return null
    val stream = streams
        .filter { it.uuid == target.streamUuid }
        .singleOrNull()
        ?: return null
    val topic = topicsByStream[stream.uuid]
        .orEmpty()
        .filter { it.uuid == target.topicUuid }
        .singleOrNull()
        ?: return null
    return ResolvedDeepLinkDestination.Dialog(
        ChatFlow.ChatDialog(
            title = stream.name,
            chatId = stream.uuid,
            topicName =
                topic.name.takeUnless { stream.isDirectProviderChat() },
            topicUuid = topic.uuid,
            isDirectMessages = stream.isDirectProviderChat(),
            userId = null,
        ),
    )
}

internal fun resolvePersistedConversationRoute(
    target: WorkspaceDeepLinkTarget,
    state: PersistedConversationState?,
): ChatFlow.ChatDialog? {
    val topicTarget = target as? WorkspaceDeepLinkTarget.Topic ?: return null
    val route = state?.route ?: return null
    val hasRetainedLocalWork =
        state.draftText.isNotEmpty() ||
            state.replySession.tabs.isNotEmpty() ||
            state.editingMessageUuid != null ||
            state.quotedMessageUuid != null ||
            state.attachments.isNotEmpty() ||
            state.suspendedDraft != null ||
            state.outbox.any { entry ->
                entry.streamUuid == topicTarget.streamUuid &&
                    entry.topicUuid == topicTarget.topicUuid
            }
    if (
        !hasRetainedLocalWork ||
        route.streamUuid != topicTarget.streamUuid ||
        route.topicUuid != topicTarget.topicUuid
    ) {
        return null
    }
    val chatTitle = route.chatTitle
        .trim()
        .take(PERSISTED_ROUTE_NAME_CHARS)
        .takeIf(String::isNotBlank)
        ?: return null
    val topicName = route.topicName
        ?.trim()
        ?.take(PERSISTED_ROUTE_NAME_CHARS)
        ?.takeIf(String::isNotBlank)
    if (!route.isDirectMessages && topicName == null) return null
    return ChatFlow.ChatDialog(
        title = chatTitle,
        chatId = route.streamUuid,
        topicName = topicName.takeUnless { route.isDirectMessages },
        topicUuid = route.topicUuid,
        isDirectMessages = route.isDirectMessages,
        userId = null,
    )
}

private const val PERSISTED_ROUTE_NAME_CHARS = 512
private const val INBOX_CATALOG_REFRESH_ATTEMPTS = 2

internal fun Stream.isDirectProviderChat(): Boolean =
    (
        isPrivate &&
            !directUserUuid.isNullOrBlank()
    ) ||
        provider?.externalId
            ?.let { externalId ->
                val prefix = externalId.substringBefore(':')
                val remoteIds = externalId.substringAfter(':', "")
                remoteIds.isNotBlank() &&
                    (prefix == "direct" || prefix == "group_direct")
            } == true

internal fun Stream.folderItemChatType(): String =
    if (isDirectProviderChat()) "private" else "stream"

internal fun resolveCreatedStreamDefaultTopicUuid(
    responseDefaultTopicUuid: String?,
    topics: List<TopicsResponseData>,
    refreshedDefaultTopicUuid: String?,
): String? =
    responseDefaultTopicUuid?.takeIf(String::isNotBlank)
        ?: topics.singleOrNull { it.isDefault }?.uuid
        ?: refreshedDefaultTopicUuid?.takeIf(String::isNotBlank)

internal fun FolderResponseData.isUserManaged(): Boolean =
    systemType == null || systemType == "created"

internal fun FolderResponseData.isAllChatsFolder(): Boolean =
    uuid == ALL_CHATS_FOLDER_UUID

internal fun FolderResponseData.localizedTitle(): String = when {
    isAllChatsFolder() -> "Все чаты"
    uuid == PERSONAL_FOLDER_UUID -> "Личные"
    uuid == CHANNELS_FOLDER_UUID -> "Каналы"
    else -> title
}

internal const val FOLDER_TITLE_MAX_LENGTH = 64
internal const val ALL_CHATS_FOLDER_UUID =
    "00000000-0000-0000-0000-000000000000"
internal const val PERSONAL_FOLDER_UUID =
    "00000000-0000-0000-0000-000000000001"
internal const val CHANNELS_FOLDER_UUID =
    "00000000-0000-0000-0000-000000000002"

internal fun validateFolderDraft(
    name: String,
    selectedStreams: List<Stream>,
): FolderDraft? {
    val normalizedName = name.trim()
    if (
        normalizedName.isEmpty() ||
        normalizedName.length > FOLDER_TITLE_MAX_LENGTH
    ) {
        return null
    }
    return FolderDraft(
        name = normalizedName,
        streams = selectedStreams
            .filter { it.uuid.isNotBlank() }
            .distinctBy(Stream::uuid),
    )
}

internal fun folderDraftError(name: String): String =
    if (name.trim().length > FOLDER_TITLE_MAX_LENGTH) {
        "Название папки должно быть не длиннее $FOLDER_TITLE_MAX_LENGTH символов"
    } else {
        "Введите название папки"
    }

@Serializable
data class TopicHeader(
    val title: String,
    val displayTitle: String = title,
    val uuid: String,
    val gravatar: String?,
    val channelName: String,
    val channelId: String,
    val lastMessage: MessageResponse?,
    val unreadCount: Int,
    val isDone: Boolean = false,
) {
    companion object {
        fun from(
            topic: TopicsResponseData,
            channelName: String,
            channelId: String,
            lastMessage: MessageResponse?,
            displayTitle: String = topic.name,
        ) = TopicHeader(
            title = topic.name,
            displayTitle = displayTitle,
            uuid = topic.uuid,
            gravatar = null,
            channelName = channelName,
            channelId = channelId,
            lastMessage = lastMessage,
            unreadCount = topic.unreadCount,
            isDone = topic.isDone,
        )
    }
}
