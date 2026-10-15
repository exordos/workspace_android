package ru.genesiscorporation.workspace.beta.modules.chatchannels

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.ConfirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.confirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyStorage
import ru.genesiscorporation.workspace.beta.data.crypto.EncryptedChatKeyStore
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatRuntime
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddChatToFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddTopicRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteChatFromFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.FoldersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkMessagesReadUpToRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkStreamMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkTopicMessagesReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReactionsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ToggleTopicDoneRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateStreamNotificationModeRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateTopicNotificationModeRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
class ChatViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val eventsRepositoryStore: EventsRepositoryStore,
    val pendingDeepLink: String?,
    val onDeepLinkHandled: () -> Unit
): ViewModel() {
//    val repo = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    private val currentServerId: Flow<String?> = userViewModel.selectedServerId

    val eventsRepo: StateFlow<EventsRepository?> = combine(
        currentServerId,
        userViewModel.servers,
    ) { id, servers ->
        id?.let { eventsRepositoryStore.getOrCreateForId(it, servers) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @Volatile private var previewContext: Context? = null
    @Volatile private var previewStorage: ChatKeyStorage? = null
    private val previewRowCounts = mutableMapOf<String, Int>()
    private val previewRows = MutableStateFlow<Set<String>>(emptySet())
    private val previewAttached = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val previewCacheLock = Any()
    private var previewCacheKey: List<Any?>? = null
    private var previewCache: EncryptedPreviewSnapshot? = null
    private val previewProjector = EncryptedChatListPreviewProjector(::previewSnapshot, {
        previewStorage ?: EncryptedChatKeyStore(requireNotNull(previewContext)).also { previewStorage = it }
    })
    val encryptedPreviews: StateFlow<Map<String, EncryptedChatListPreview>> = previewProjector.previews
    private var previewRefresh: Job? = null
    private val previewObserver = viewModelScope.launch {
        merge(
            eventsRepo.flatMapLatest { repo -> if (repo == null) flowOf(Unit) else merge(
                repo.streams.map { Unit }, repo.currentUser.map { Unit }, repo.messagesPool.map { Unit }, repo.streamBindings.map { Unit },
                repo.streamTopicMessages.map { Unit }, repo.users.map { Unit }, repo.verifiedOwnerAuthentication.map { Unit }) },
            userViewModel.selectedServer.map { Unit }, userViewModel.selectedServerId.map { Unit },
            userViewModel.accessToken.map { Unit }, previewRows.map { Unit }, previewAttached,
            EncryptedChatRuntime.localKeyChanges.filter(previewProjector::affectedBy).map { Unit },
            ConfirmedAccountContext.invalidations.filter { previewProjector.accountChanged() }.map { Unit },
        ).collect {
            previewProjector.invalidate()
            previewRefresh?.cancel()
            if (previewContext != null && previewRows.value.isNotEmpty()) previewRefresh = launch {
                try { previewProjector.refresh() }
                catch (cancelled: CancellationException) { throw cancelled }
            }
        }
    }

    fun attachEncryptedPreviews(context: Context) {
        if (previewContext == null) { previewContext = context.applicationContext; previewAttached.tryEmit(Unit) }
    }

    fun showEncryptedPreview(streamUuid: String, visible: Boolean) {
        val count = (previewRowCounts[streamUuid] ?: 0) + if (visible) 1 else -1
        if (count <= 0) previewRowCounts.remove(streamUuid) else previewRowCounts[streamUuid] = count
        previewRows.value = previewRowCounts.keys.toSet()
    }

    fun encryptedPreview(streamUuid: String): EncryptedChatListPreview? = previewProjector.preview(streamUuid)

    private fun previewSnapshot(): EncryptedPreviewSnapshot? {
        val repo = eventsRepo.value ?: return null
        val selected = userViewModel.selectedServer.value ?: return null
        val own = repo.currentUser.value ?: return null
        if (selected.id != repo.serverId || selected.id != userViewModel.selectedServerId.value || selected.needsToRelogin ||
            selected.projectUuid.isNullOrBlank()) return null
        val proof = repo.verifiedOwnerAuthentication.value
        val account = confirmedAccountContext(client, repo) ?: return null
        val pool = repo.messagesPool.value
        val conversations = repo.streamTopicMessages.value
        val bindings = repo.streamBindings.value
        val users = repo.users.value
        val currentStreams = repo.streams.value
        val visible = previewRows.value
        val cacheKey = listOf(repo, selected.copy(), own.uuid, proof,
            currentStreams, pool, conversations, bindings, users, visible)
        synchronized(previewCacheLock) {
            if (previewCacheKey == cacheKey) return previewCache
            val loadedConversations = conversations.values.flatten()
            val rows = currentStreams.filter { it.uuid in visible }.mapNotNull { stream ->
                val message = latestLoadedEncryptedPreviewMessage(stream, pool, loadedConversations)
                val withAuthor = message?.let { it.copy(user = it.user ?: users.firstOrNull { user -> user.uuid == it.authorUuid }) }
                val last = pool.firstOrNull { it.uuid == stream.lastMessageUuid } ?: stream.lastMessage
                encryptedPreviewSource(stream, withAuthor, own.uuid, requireNotNull(selected.projectUuid), bindings[stream.uuid], last)
            }.sortedBy { it.streamUuid }
            return EncryptedPreviewSnapshot(account, rows).also { previewCacheKey = cacheKey; previewCache = it }
        }
    }

    override fun onCleared() { previewProjector.invalidate(); previewRefresh?.cancel(); super.onCleared() }

    val streams: StateFlow<List<Stream>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streams ?: flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _currentlySelectedStream = MutableStateFlow<Stream?>(null)
    var currentlySelectedStream: StateFlow<Stream?> = _currentlySelectedStream

    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streamTopics ?: flowOf(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )
    val folders: StateFlow<List<FolderResponseData>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.folders ?: flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val streamBindings: StateFlow<Map<String, List<StreamBindingResponseData>>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streamBindings ?: flowOf(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )
    val currentlySelectedFolder: StateFlow<FolderResponseData?> = eventsRepo
        .flatMapLatest { repo ->
            repo?.currentlySelectedFolder ?: flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    private val _searchQuery = MutableStateFlow("")
    var searchQuery: StateFlow<String> = _searchQuery
    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState


    private val _shouldShowCreateTopicView = MutableStateFlow<Boolean>(false)
    var shouldShowCreateTopicView: StateFlow<Boolean> = _shouldShowCreateTopicView

    val streamsQueryState: StateFlow<QueryState> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streamsQueryState ?: flowOf(QueryState.Idle)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueryState.Idle,
        )

    private val _topicsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val topicsQueryState: StateFlow<QueryState> = _topicsQueryState

    var createdStream: Stream? = null
    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    private val _navEvents = MutableSharedFlow<ChatNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<ChatNavEvent> = _navEvents

    private val _chatToAdd = MutableStateFlow<Stream?>(null)
    var chatToAdd: StateFlow<Stream?> = _chatToAdd

    val map: Map<String, Int> = emptyMap()

    var currentTopicName: String = ""


    fun onForlderNameChange(newText: String) {
        _newFolderName.value = newText
    }

    var currentStreamId: String = ""

    fun poolMessage(uuid: String?): MessageResponse? {
        return eventsRepo.value?.messagesPool?.value?.firstOrNull { it.uuid == uuid }
    }

    fun updateCurrentlySelectedFolder(newFolder: FolderResponseData) {
        if (newFolder.uuid != currentlySelectedFolder.value?.uuid) {
            eventsRepo.value?.updateCurrentlySelectedFolder(newFolder)
        }
    }

    fun onCreateTopicButtonTap() {
        _shouldShowCreateTopicView.value = true
    }

    fun onDismissTopicCreationButtonTap() {
        _shouldShowCreateTopicView.value = false
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onChatToAddChange(chatToAdd: Stream?) {
        _chatToAdd.value = chatToAdd
    }

    suspend fun updateSelectedChat(newChat: Stream?) {
        _currentlySelectedStream.update { newChat }
        if (newChat != null) {
            val currentStreamBindings = streamBindings.value[newChat.uuid]
            if (currentStreamBindings?.isEmpty() ?: true) {
                loadStreamBindings(newChat)
            } else if (streamTopics.value[newChat.uuid]?.isEmpty() ?: true) {
                loadTopics(newChat)
            }
        }
    }

    suspend fun loadServerSettings() {
        eventsRepo.value?.loadServerSettings()
    }

    suspend fun loadStreamBindings(stream: Stream) {
        _topicsQueryState.value = QueryState.Loading
        val streamBindingsResponse = client.performRequest(StreamBindingsRequest(stream.uuid))
        when(streamBindingsResponse) {
            is ApiResult.Success -> {
                eventsRepo.value?.addStreamBindings(stream.uuid, streamBindingsResponse.value)
                loadTopics(stream)
            }
            is ApiResult.Error -> {
                loadTopics(stream)
            }
        }
    }

    suspend fun loadTopics(stream: Stream) {
        _topicsQueryState.value = QueryState.Loading
        val response = client.performRequest(TopicsRequest(listOf(stream.uuid)))
        when(response) {
            is ApiResult.Success -> {
                val messageIds = response.value.mapNotNull { it.lastMessageUuid }
                if (!messageIds.isEmpty()) {
                    val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
                    when (messagesResponse) {
                        is ApiResult.Success -> {
                            eventsRepo.value?.updateMessagesPool(messagesResponse.value)
                            val topicsWithMessages = response.value.map { topic ->
                                var updatedTopic = topic
                                updatedTopic.lastMessage = poolMessage(topic.lastMessageUuid)
                                updatedTopic
                            }
                            eventsRepo.value?.addStreamTopics(stream.uuid, topicsWithMessages)
                            _topicsQueryState.value = QueryState.Success
                        }

                        is ApiResult.Error -> {
                            eventsRepo.value?.addStreamTopics(stream.uuid, response.value)
                            _topicsQueryState.value = QueryState.Success
                        }
                    }
                } else {
                    eventsRepo.value?.addStreamTopics(stream.uuid, response.value)
                    _topicsQueryState.value = QueryState.Success
                }
            }
            is ApiResult.Error -> {
                _topicsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun addFolder(name: String) {
        val response = client.performRequest(AddFolderRequest(name))
        when(response) {
            is ApiResult.Success -> {
                val foldersResponse = client.performRequest(FoldersRequest())
                when(foldersResponse) {
                    is ApiResult.Success -> {

                    }

                    is ApiResult.Error -> {

                    }
                }
            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun addTopic(name: String) {
        val currentlySelectedStream = _currentlySelectedStream.value
        if (currentlySelectedStream != null) {
            val response = client.performRequest(AddTopicRequest(name, currentlySelectedStream.uuid))
            when (response) {
                is ApiResult.Success -> {

                }

                is ApiResult.Error -> {

                }
            }
        }
    }

    suspend fun setNextNotificationMode(topic: TopicsResponseData) {
        when (topic.notificationMode) {
            "mute" -> setTopicNotificationMode(topic.uuid, "default")
            "default" -> setTopicNotificationMode(topic.uuid, "follow")
            else -> setTopicNotificationMode(topic.uuid, "mute")
        }
    }

    suspend fun setStreamNotificationMode(streamUuid: String, notificationMode: String) {
        val response = client.performRequest(
            UpdateStreamNotificationModeRequest(
                streamUuid,
                notificationMode
            )
        )
        when(response) {
            is ApiResult.Success -> {

            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun setTopicNotificationMode(topicUuid: String, notificationMode: String) {
        val response = client.performRequest(UpdateTopicNotificationModeRequest(topicUuid, notificationMode))
        when(response) {
            is ApiResult.Success -> {

            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun toggleTopicDone(topicUuid: String) {
        val response = client.performRequest(ToggleTopicDoneRequest(topicUuid))
        when(response) {
            is ApiResult.Success -> {

            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun addChatFolder(chatUuid: String, chatType: String, folderUuid: String) {
        val response = client.performRequest(AddChatToFolderRequest(folderUuid, chatUuid, chatType))
        when(response) {
            is ApiResult.Success -> {
                _queryState.value = QueryState.Success
            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun deleteChatFromFolder(chatId: String, folder: FolderResponseData) {
        val folderChat = folder.items.firstOrNull() { it.streamUuid == chatId }
        if (folderChat != null) {
            val response = client.performRequest(DeleteChatFromFolderRequest(folderChat.uuid))
            when (response) {
                is ApiResult.Success -> {
                    val foldersResponse = client.performRequest(FoldersRequest())
                    when (foldersResponse) {
                        is ApiResult.Success -> {

                            if (currentlySelectedFolder.value != null) {
                                val updatedCurrentlySelectedFolder = foldersResponse.value.firstOrNull() { it.uuid == currentlySelectedFolder.value?.uuid }
                                eventsRepo.value?.updateCurrentlySelectedFolder(updatedCurrentlySelectedFolder)
                            }
                            _queryState.value = QueryState.Success
                        }

                        is ApiResult.Error -> {

                        }
                    }
                }

                is ApiResult.Error -> {
                    val foldersResponse = client.performRequest(FoldersRequest())
                    when (foldersResponse) {
                        is ApiResult.Success -> {

                            if (currentlySelectedFolder.value != null) {
                                val updatedCurrentlySelectedFolder = foldersResponse.value.firstOrNull() { it.uuid == currentlySelectedFolder.value?.uuid }

                            }
                            _queryState.value = QueryState.Success
                        }

                        is ApiResult.Error -> {

                        }
                    }
                }
            }
        }
    }

    suspend fun createPrivateStream(user: UserResponseData) {
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

    suspend fun markStreamMessagesRead(streamUuid: String) {
        val markMessagesReadResponse = client.performRequest(MarkStreamMessagesReadRequest(streamUuid))
        when(markMessagesReadResponse) {
            is ApiResult.Success -> {

            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun markTopicMessagesRead(topicUuid: String) {
        val markMessagesReadResponse = client.performRequest(MarkTopicMessagesReadRequest(topicUuid))
        when(markMessagesReadResponse) {
            is ApiResult.Success -> {

            }

            is ApiResult.Error -> {

            }
        }
    }
}

@Serializable
data class TopicHeader(
    val title: String,
    val uuid: String,
    val gravatar: String?,
    val channelName: String,
    val channelId: String,
    val lastMessage: MessageResponse?,
    val unreadCount: Int
) {
    companion object {
        fun from(topic: TopicsResponseData, channelName: String, channelId: String, lastMessage: MessageResponse?) = TopicHeader(
            topic.name,
            topic.uuid,
            null,
            channelName,
            channelId,
            lastMessage,
            topic.unreadCount
        )
    }
}
