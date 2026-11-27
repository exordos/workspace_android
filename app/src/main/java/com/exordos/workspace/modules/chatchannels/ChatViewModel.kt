package com.exordos.workspace.modules.chatchannels

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.ConfirmedAccountContext
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.crypto.ChatKeyStorage
import com.exordos.workspace.data.crypto.EncryptedChatKeyStore
import com.exordos.workspace.modules.encryptedchat.EncryptedChatRuntime
import com.exordos.workspace.data.remote.dto.AddChatToFolderRequest
import com.exordos.workspace.data.remote.dto.AddFolderRequest
import com.exordos.workspace.data.remote.dto.AddStreamRequest
import com.exordos.workspace.data.remote.dto.AddTopicRequest
import com.exordos.workspace.data.remote.dto.DeleteChatFromFolderRequest
import com.exordos.workspace.data.uio.FolderItemUIO
import com.exordos.workspace.data.uio.FolderUIO
import com.exordos.workspace.data.remote.dto.FoldersRequest
import com.exordos.workspace.data.remote.dto.MarkMessagesReadUpToRequest
import com.exordos.workspace.data.remote.dto.MarkStreamMessagesReadRequest
import com.exordos.workspace.data.remote.dto.MarkTopicMessagesReadRequest
import com.exordos.workspace.data.remote.dto.MessageReactionsRequest
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.remote.dto.OwnUserRequest
import com.exordos.workspace.data.remote.dto.ServerSettingsRequest
import com.exordos.workspace.data.remote.dto.StreamsRequest
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.uio.StreamBindingUIO
import com.exordos.workspace.data.remote.dto.ToggleTopicDoneRequest
import com.exordos.workspace.data.remote.dto.TopicsRequest
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.data.remote.dto.UpdateStreamNotificationModeRequest
import com.exordos.workspace.data.remote.dto.UpdateTopicNotificationModeRequest
import com.exordos.workspace.data.remote.dto.UsersRequest
import com.exordos.workspace.data.uio.LastMessageUIO
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.data.uio.toMessageUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette
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

    private data class ChatListData(
        val repository: EventsRepository,
        val users: Map<String, UserUIO>,
        val lastMessages: Map<String, LastMessageUIO>,
        val messages: List<MessageUIO>,
    )

    private val chatListData = eventsRepo.flatMapLatest { repo ->
        if (repo == null) return@flatMapLatest flowOf(null)
        val data: Flow<ChatListData?> = combine(
            repo.users, repo.lastMessageDao.observeLastMessages(), repo.messages,
        ) { users, lastMessages, messages ->
            ChatListData(repo, users.associateBy(UserUIO::uuid), lastMessages.associateBy(LastMessageUIO::uuid), messages)
        }
        data.onStart { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    internal val users = chatListData.map { it?.users.orEmpty() }
    internal val lastMessages = chatListData.map { it?.lastMessages.orEmpty() }

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
        val catalog = chatListData.value?.takeIf { it.repository === repo } ?: return null
        val bindings = previewBindings.value?.takeIf { it.first === repo }?.second.orEmpty()
        val currentStreams = streams.value
        val visible = previewRows.value
        val cacheKey = listOf(repo, selected.copy(), own.uuid, proof,
            currentStreams, catalog, bindings, visible)
        synchronized(previewCacheLock) {
            if (previewCacheKey == cacheKey) return previewCache
            val rows = currentStreams.filter { it.uuid in visible }.mapNotNull { stream ->
                val lastMessage = catalog.lastMessages[stream.lastMessageUuid]?.toMessageUIO()
                val message = latestLoadedEncryptedPreviewMessage(stream, catalog.messages, emptyList(), lastMessage)
                encryptedPreviewSource(
                    stream, message, own.uuid, requireNotNull(selected.projectUuid),
                    bindings[stream.uuid].orEmpty(), lastMessage, catalog.users[message?.authorUuid]?.displayableName(),
                )
            }.sortedBy { it.streamUuid }
            return EncryptedPreviewSnapshot(account, rows).also { previewCacheKey = cacheKey; previewCache = it }
        }
    }

    override fun onCleared() { previewProjector.invalidate(); previewRefresh?.cancel(); super.onCleared() }

    val streams: StateFlow<List<StreamUIO>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.streams ?: flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val previewBindings = combine(eventsRepo, previewRows, streams) { repo, visible, available ->
        repo to available.filter { it.encryption && it.uuid in visible }.map { it.uuid }
    }.distinctUntilChanged().flatMapLatest { (repo, streamUuids) ->
        if (repo == null || streamUuids.isEmpty()) return@flatMapLatest flowOf(null)
        combine(streamUuids.map(repo.streamBindingDao::observeStreamBindings)) { lists ->
            repo to streamUuids.zip(lists.asList()).toMap()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val previewObserver = viewModelScope.launch {
        merge(
            eventsRepo.flatMapLatest { repo -> if (repo == null) flowOf(Unit) else merge(
                repo.currentUser.map { Unit },
                repo.verifiedOwnerAuthentication.map { Unit },
            ) },
            streams.map { Unit }, chatListData.map { Unit }, previewBindings.map { Unit },
            userViewModel.selectedServer.map { Unit }, userViewModel.selectedServerId.map { Unit },
            userViewModel.accessToken.map { Unit }, previewRows.map { Unit }, previewAttached,
            EncryptedChatRuntime.localKeyChanges.filter(previewProjector::affectedBy).map { Unit },
            ConfirmedAccountContext.invalidations.filter { previewProjector.accountChanged() }.map { Unit },
        ).collect {
            previewProjector.invalidate()
            previewRefresh?.cancel()
            if (previewContext != null && previewRows.value.isNotEmpty()) previewRefresh = launch {
                previewProjector.refresh()
            }
        }
    }

    private val _currentlySelectedStream = MutableStateFlow<StreamUIO?>(null)
    var currentlySelectedStream: StateFlow<StreamUIO?> = _currentlySelectedStream

    val folders: StateFlow<List<FolderUIO>> = eventsRepo
        .flatMapLatest { repo ->
            repo?.folders ?: flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val currentlySelectedFolder: StateFlow<FolderUIO?> = eventsRepo
        .flatMapLatest { repo ->
            repo?.currentlySelectedFolder ?: flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    internal val selectedFolderItems: Flow<List<FolderItemUIO>> = combine(eventsRepo, currentlySelectedFolder) { repo, folder ->
        if (folder == null || folder.systemType == "all") flowOf(emptyList())
        else repo?.folderItemDao?.observeFolderItems(folder.uuid)?.onStart { emit(emptyList()) } ?: flowOf(emptyList())
    }.flatMapLatest { it }

    internal val selectedStreamTopics: Flow<List<TopicUIO>> = combine(eventsRepo, currentlySelectedStream) { repo, stream ->
        if (stream == null) flowOf(emptyList())
        else repo?.topicDao?.observeTopics(stream.uuid)?.onStart { emit(emptyList()) } ?: flowOf(emptyList())
    }.flatMapLatest { it }

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

    var createdStream: StreamUIO? = null
    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    private val _navEvents = MutableSharedFlow<ChatNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<ChatNavEvent> = _navEvents

    private val _chatToAdd = MutableStateFlow<StreamUIO?>(null)
    var chatToAdd: StateFlow<StreamUIO?> = _chatToAdd

    val map: Map<String, Int> = emptyMap()

    var currentTopicName: String = ""


    fun onForlderNameChange(newText: String) {
        _newFolderName.value = newText
    }

    var currentStreamId: String = ""

    fun updateCurrentlySelectedFolder(newFolder: FolderUIO) {
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

    fun onChatToAddChange(chatToAdd: StreamUIO?) {
        _chatToAdd.value = chatToAdd
    }

    suspend fun updateSelectedChat(newChat: StreamUIO?) {
        _currentlySelectedStream.update { newChat }
        _topicsQueryState.value = if (newChat == null) QueryState.Idle else QueryState.Success
    }

    suspend fun loadServerSettings() {
        eventsRepo.value?.loadServerSettings()
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

    suspend fun setNextNotificationMode(topic: TopicUIO) {
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

    suspend fun deleteChatFromFolder(chatId: String, folder: FolderUIO) {
        val repo = eventsRepo.value ?: return
        val folderChat = repo.folderItemDao.observeFolderItems(folder.uuid).first()
            .firstOrNull { it.streamUuid == chatId }
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

data class TopicHeader(
    val title: String,
    val uuid: String,
    val gravatar: String?,
    val channelName: String,
    val channelId: String,
    val lastMessage: MessageUIO?,
    val unreadCount: Int
) {
    companion object {
        fun from(topic: TopicUIO, channelName: String, channelId: String, lastMessage: MessageUIO?) = TopicHeader(
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
