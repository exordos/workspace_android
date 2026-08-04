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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddChatToFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteChatFromFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.FoldersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReactionsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageSortDirection
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
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

private data class MentionRefreshRequest(
    val ownerKey: String?,
    val unreadStreamVersions: Map<String, Int>,
    val unreadTopicVersions: Map<String, Int>,
)

private fun unreadMentionOwnerKey(
    baseUrl: String?,
    userId: String?,
    accessToken: String?
): String? {
    val normalizedBaseUrl = baseUrl?.trim()?.trimEnd('/')
    if (normalizedBaseUrl.isNullOrEmpty() || accessToken.isNullOrBlank()) return null
    return "$normalizedBaseUrl|${userId?.trim().orEmpty()}|${accessToken.hashCode()}"
}

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

internal fun unreadMentionTopicUuids(
    messages: List<MessageResponse>,
    unreadTopicUuids: Set<String>,
): Set<String> = messages
    .asSequence()
    .filter { message ->
        message.mentioned &&
            !message.read &&
            message.topicUuid in unreadTopicUuids
    }
    .map(MessageResponse::topicUuid)
    .toSet()

internal fun unreadMentionCount(messages: List<MessageResponse>): Int =
    messages.count { it.mentioned && !it.read }

class ChatViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val repo: EventsRepository,
    val pendingDeepLink: String?,
    val onDeepLinkHandled: () -> Unit
): ViewModel() {
    val streams: StateFlow<List<Stream>> = repo.streams
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val mentionedMessages: StateFlow<List<MessageResponse>> = repo.mentionedMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val unreadMentionStreamUuids: StateFlow<Set<String>> = combine(
        repo.mentionedMessages,
        repo.streams,
    ) { messages, streams ->
        unreadMentionStreamUuids(
            messages = messages,
            unreadStreamUuids = streams
                .filter { it.unreadCount > 0 }
                .mapTo(mutableSetOf(), Stream::uuid),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    val unreadMentionTopicUuids: StateFlow<Set<String>> = combine(
        repo.mentionedMessages,
        repo.streamTopics,
    ) { messages, topicsByStream ->
        unreadMentionTopicUuids(
            messages = messages,
            unreadTopicUuids = topicsByStream.values
                .flatten()
                .filter { it.unreadCount > 0 }
                .mapTo(mutableSetOf(), TopicsResponseData::uuid),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    val unreadMentionCount: StateFlow<Int> = repo.mentionedMessages
        .map(::unreadMentionCount)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
    private var lastMentionOwnerKey: String? = null

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
    private val _mentionsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val mentionsQueryState: StateFlow<QueryState> = _mentionsQueryState

    var createdStream: Stream? = null
    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState
    private val folderCreationFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private val _navEvents = MutableSharedFlow<ChatNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<ChatNavEvent> = _navEvents

    private val _chatToAdd = MutableStateFlow<Stream?>(null)
    var chatToAdd: StateFlow<Stream?> = _chatToAdd

    val map: Map<String, Int> = emptyMap()

    var currentTopicName: String = ""


    fun onForlderNameChange(newText: String) {
        _newFolderName.value = newText
    }

    init {
        viewModelScope.launch {
            loadServerSettings()
        }
        viewModelScope.launch {
            combine(
                combine(repo.streams, repo.streamTopics) { streams, topicsByStream ->
                    val unreadStreams = streams
                        .filter { it.unreadCount > 0 }
                        .associate { it.uuid to it.unreadCount }
                    val unreadTopics = topicsByStream.values
                        .flatten()
                        .filter { it.unreadCount > 0 }
                        .associate { it.uuid to it.unreadCount }
                    unreadStreams to unreadTopics
                },
                userViewModel.baseUrl,
                userViewModel.userId,
                userViewModel.accessToken,
            ) { unreadCatalog, baseUrl, userId, accessToken ->
                MentionRefreshRequest(
                    ownerKey = unreadMentionOwnerKey(baseUrl, userId, accessToken),
                    unreadStreamVersions = unreadCatalog.first,
                    unreadTopicVersions = unreadCatalog.second,
                )
            }
                .distinctUntilChanged()
                .collectLatest(::refreshMentions)
        }
    }

    private fun currentUnreadMentionOwnerKey(): String? = unreadMentionOwnerKey(
        baseUrl = userViewModel.baseUrl.value,
        userId = userViewModel.userId.value,
        accessToken = userViewModel.accessToken.value
    )

    private suspend fun refreshMentions(
        request: MentionRefreshRequest,
    ) {
        if (request.ownerKey != lastMentionOwnerKey) {
            lastMentionOwnerKey = request.ownerKey
            repo.setMentionedMessages(emptyList())
        }
        val ownerKey = request.ownerKey
        if (ownerKey == null) {
            repo.setMentionedMessages(emptyList())
            _mentionsQueryState.value = QueryState.Idle
            return
        }
        if (mentionedMessages.value.isEmpty()) {
            _mentionsQueryState.value = QueryState.Loading
        }

        when (
            val response = client.performRequest(
                MessagesRequest(
                    pageLimit = 50,
                    sortDirection = MessageSortDirection.DESCENDING,
                    mentioned = true,
                ),
            )
        ) {
            is ApiResult.Success -> {
                if (currentUnreadMentionOwnerKey() != ownerKey) return
                repo.setMentionedMessages(response.value)
                _mentionsQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                if (currentUnreadMentionOwnerKey() != ownerKey) return
                // Keep the last owner-scoped snapshot when a refresh fails.
                _mentionsQueryState.value = QueryState.Error(
                    "Не удалось загрузить упоминания",
                )
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
            if (streamTopics.value[newChat.uuid]?.isEmpty() ?: true) {
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

            }
        }
    }

    suspend fun loadUserInfo() {
        val response = client.performRequest(OwnUserRequest())
        when(response) {
            is ApiResult.Success -> {
                userViewModel.userData = response.value
                repo.updateCurrentUser(response.value)
                loadMessageReactions(response.value.uuid)
            }

            is ApiResult.Error -> {

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
                _queryState.value = QueryState.Error("")
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
                _queryState.value = QueryState.Error("")
            }
        }
    }
    suspend fun loadFolders() {
        val response = client.performRequest(FoldersRequest())
        when(response) {
            is ApiResult.Success -> {
                repo.setInitialFolders(response.value.sortedBy { LocalDateTime.parse(it.creationDate, folderCreationFormatter) })
                if (!folders.value.isEmpty()) {
                    _currentlySelectedFolder.value = folders.value.first()
                }
                loadSubscribedChannels()
            }

            is ApiResult.Error -> {
                _queryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadSubscribedChannels() {
        val response = client.performRequest(StreamsRequest())
        when(response) {
            is ApiResult.Success -> {
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
                            repo.start()
                        }

                        is ApiResult.Error -> {
                            repo.setInitialStreams(response.value)
                            _queryState.value = QueryState.Success
                            repo.start()
                        }
                    }
                } else {
                    repo.setInitialStreams(response.value)
                    _queryState.value = QueryState.Success
                    repo.start()
                }
            }

            is ApiResult.Error -> {
                _queryState.value = QueryState.Error("")
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
                _queryState.value = QueryState.Error("")
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

    suspend fun addChatFolder(chatId: Int, chatType: String, folderUuid: String) {
        val response = client.performRequest(AddChatToFolderRequest(folderUuid, chatId, chatType))
        when(response) {
            is ApiResult.Success -> {
                val foldersResponse = client.performRequest(FoldersRequest())
                when(foldersResponse) {
                    is ApiResult.Success -> {

                        _queryState.value = QueryState.Success
                    }

                    is ApiResult.Error -> {

                    }
                }
            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun deleteChatFromFolder(chatId: String, folder: FolderResponseData) {
        val folderChat = folder.items.firstOrNull() { it.streamUuid == chatId }
        if (folderChat != null) {
            val response = client.performRequest(DeleteChatFromFolderRequest(folder.uuid, folderChat.uuid))
            when (response) {
                is ApiResult.Success -> {
                    val foldersResponse = client.performRequest(FoldersRequest())
                    when (foldersResponse) {
                        is ApiResult.Success -> {

                            if (currentlySelectedFolder.value != null) {
                                val updatedCurrentlySelectedFolder = foldersResponse.value.firstOrNull() { it.uuid == currentlySelectedFolder.value?.uuid }
                                _currentlySelectedFolder.update { updatedCurrentlySelectedFolder }
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
                                _currentlySelectedFolder.update { updatedCurrentlySelectedFolder }
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
