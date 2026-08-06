package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
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

    private val _currentlySelectedStream = MutableStateFlow<Stream?>(null)
    var currentlySelectedStream: StateFlow<Stream?> = _currentlySelectedStream

    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = repo.streamTopics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )
    private var users: List<UserResponseData> = emptyList()

    val folders: StateFlow<List<FolderResponseData>> = repo.folders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val streamBindings: StateFlow<Map<String, List<StreamBindingResponseData>>> = repo.streamBindings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
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
            if (streamBindings.value[newChat.uuid]?.isEmpty() ?: true) {
                loadStreamBindings(newChat)
            } else if (streamTopics.value[newChat.uuid]?.isEmpty() ?: true) {
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
                users = response.value
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

    suspend fun loadStreamBindings(stream: Stream) {
        _queryState.value = QueryState.Loading
        val streamBindingsResponse = client.performRequest(StreamBindingsRequest(stream.uuid))
        when(streamBindingsResponse) {
            is ApiResult.Success -> {
                repo.addStreamBindings(stream.uuid, streamBindingsResponse.value)
                loadTopics(stream)
            }
            is ApiResult.Error -> {
                loadTopics(stream)
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

    suspend fun setNextNotificationMode(topic: TopicsResponseData) {
        when (topic.notificationMode) {
            "mute" -> setTopicNotificationMode(topic.uuid, "default")
            "default" -> setTopicNotificationMode(topic.uuid, "follow")
            else -> setTopicNotificationMode(topic.uuid, "mute")
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
