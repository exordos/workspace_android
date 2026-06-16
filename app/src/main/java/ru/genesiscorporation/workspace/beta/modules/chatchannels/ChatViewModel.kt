package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.MessageDto
import ru.genesiscorporation.workspace.beta.DisplayRecipient
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddChatToFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteChatFromFolderRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventRegistrationRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.FoldersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RecentPrivateConversation
import ru.genesiscorporation.workspace.beta.data.remote.dto.Subscription
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UnreadMessages
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.topics.TopicHeader
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
    private val _subscriptions = MutableStateFlow<List<ChatHeader>>(emptyList())
    val subscriptions: StateFlow<List<ChatHeader>> = _subscriptions


    private val _currentlySelectedSubscription = MutableStateFlow<ChatHeader?>(null)
    var currentlySelectedSubscription: StateFlow<ChatHeader?> = _currentlySelectedSubscription

    private val _topics = MutableStateFlow<List<TopicHeader>>(emptyList())
    val topics: StateFlow<List<TopicHeader>> = _topics
    private var users: List<UsersResponseData> = emptyList()

    private val _folders = MutableStateFlow<List<FolderResponseData>>(emptyList())
    val folders: StateFlow<List<FolderResponseData>> = _folders
    private val _currentlySelectedFolder = MutableStateFlow<FolderResponseData?>(null)
    var currentlySelectedFolder: StateFlow<FolderResponseData?> = _currentlySelectedFolder

    private val _newFolderName = MutableStateFlow("")
    val newFolderName: StateFlow<String> = _newFolderName

    private var initialUnreaMessages: UnreadMessages? = null

    private var loadedSubscriptions: List<Subscription> = emptyList()
    private var recentPrivateConversations: List<RecentPrivateConversation> = emptyList()

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState
    private val folderCreationFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS")

    private val _navEvents = MutableSharedFlow<ChatNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<ChatNavEvent> = _navEvents

    val map: Map<String, Int> = emptyMap()

    var currentTopicName: String = ""


    fun onForlderNameChange(newText: String) {
        _newFolderName.value = newText
    }

    init {
        viewModelScope.launch {
            registerForEvents()

            repo.messages.collect { updated ->
                processNewMessages(updated)
            }
        }

        viewModelScope.launch {
            repo.unreadMessages.collect { updated ->
                processUnreadMessages(updated)
            }
        }
    }

    suspend fun registerForEvents() {
        val response = client.performRequest(EventRegistrationRequest("[\"messages\"]", null))
        when(response) {
            is ApiResult.Success -> {
                repo.updateQueueId(response.value.queueId)
                repo.updatePresenses(response.value.presences)
                repo.updateUnreadMessages(response.value.unreadMessages)
                repo.customProfileFields = response.value.customProfileFields ?: emptyList()
                loadedSubscriptions = response.value.subscriptions
                recentPrivateConversations = response.value.recentPrivateConversations
                initialUnreaMessages = response.value.unreadMessages
                loadUserInfo()
            }
            is ApiResult.Error -> {

            }
        }
    }

    var currentStreamId: String = ""

    fun updateUnreadCount(streamId: String, newLastMessage: MessageDto?) {

            _subscriptions.value = _subscriptions.value.map { header ->
                if (header.streamId == streamId) {
                    header.copy(
                        lastMessage = newLastMessage
                    )
                } else {
                    header
                }
            }
    }

    fun updateCurrentlySelectedFolder(newFolder: FolderResponseData) {
        if (newFolder.uuid != currentlySelectedFolder.value?.uuid) {
            _currentlySelectedFolder.update { newFolder }
        }
    }

    suspend fun updateSelectedChat(newChat: ChatHeader?) {
        _currentlySelectedSubscription.update { newChat }
        if (newChat != null) {
            loadTopics(newChat)
        } else {
            _topics.update { emptyList() }
        }
    }

    suspend fun loadUserInfo() {
        val response = client.performRequest(OwnUserRequest())
        when(response) {
            is ApiResult.Success -> {
                userViewModel.userData = response.value
                loadAllUsersInfo()
            }

            is ApiResult.Error -> {

            }
        }
    }

    suspend fun loadAllUsersInfo() {
        val response = client.performRequest(UsersRequest())
        when(response) {
            is ApiResult.Success -> {
                users = response.value.members
                repo.updateUsers(response.value.members)
                loadFolders()
            }

            is ApiResult.Error -> {

            }
        }
    }
    suspend fun loadFolders() {
        val response = client.performRequest(FoldersRequest())
        when(response) {
            is ApiResult.Success -> {
                _folders.value = response.value.sortedBy { LocalDateTime.parse(it.creationDate, folderCreationFormatter) }
                if (!folders.value.isEmpty()) {
                    _currentlySelectedFolder.value = folders.value.first()
                }
                loadSubscribedChannels()
            }

            is ApiResult.Error -> {

            }
        }
    }

    fun unreadCountForFolder(folder: FolderResponseData): Int {
        val folderItems = folder.items.map { it.chatId }
        val folderChats = _subscriptions.value.filter { folderItems.contains(it.chatId) }
        return folderChats.sumOf { it.unreadCount }
    }

    suspend fun loadSubscribedChannels() {
        val privateMessageIds = recentPrivateConversations.map { it.maxMessageId }
        val subscriptionMessageIds = loadedSubscriptions.map { it.firstMessageId }
        val messageIds = privateMessageIds + subscriptionMessageIds
        val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
        when(messagesResponse) {
            is ApiResult.Success -> {
                val currentUserId = userViewModel.userData?.user_id
                for (conversation in recentPrivateConversations.listIterator()) {
                    val message = messagesResponse.value.messages.firstOrNull { it.id == conversation.maxMessageId }
                    val userId = conversation.userIds.firstOrNull()
                    if (userId != null) {
                        val user = users.firstOrNull { it.userId == userId }
                        if (user != null && message != null) {
                            val unreadUserMessagesCount = initialUnreaMessages?.pms?.firstOrNull { it.otherUserId == user.userId }?.unreadMessageIds?.size
                            val chatHeader = ChatHeader.from(user, message, "$currentUserId", unreadUserMessagesCount)
                            _subscriptions.update { current -> current + chatHeader }
                        }
                    }
                }
                val channelChatHeaders = loadedSubscriptions.mapNotNull { subscription ->
                    var channelUnreadMessageCount: Int? = null
                    val message = messagesResponse.value.messages.firstOrNull {
                        when (it.displayRecipient) {
                            is DisplayRecipient.Users -> {
                                false
                            }
                            is DisplayRecipient.StreamName -> {
                                it.displayRecipient.value == subscription.name
                            }
                        }
                    }
                    val unreadChannelMessages = initialUnreaMessages?.streams?.filter { it.streamId == subscription.streamId }
                    if (unreadChannelMessages != null) {
                        if (unreadChannelMessages.size > 0) {
                            channelUnreadMessageCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                        }
                    }
                    if (message != null) {
                        ChatHeader.from(subscription, channelUnreadMessageCount, message)
                    } else {
                        null
                    }
                }
                _subscriptions.update { current -> current + channelChatHeaders }
                if (pendingDeepLink != null) {
                    when {
                        pendingDeepLink.startsWith("dialog/") -> {
                            val userId = pendingDeepLink.removePrefix("dialog/").substringBefore("/").toInt()
                            if (userId != null) {
                                val messageUser = repo.users.value.firstOrNull { it.userId == userId }
                                if (messageUser != null && currentUserId != null) {
                                    _navEvents.tryEmit(
                                        ChatNavEvent.OpenDialog(
                                            title = messageUser.fullName,
                                            chatId = "[${messageUser.userId}, ${currentUserId}]",
                                            null,
                                            true,
                                            userId = messageUser.userId
                                        )
                                    )
                                    onDeepLinkHandled()
                                }
                            }
                        }
                        pendingDeepLink.startsWith("stream/") -> {
                            val rest = pendingDeepLink.removePrefix("stream/")
                            val parts = rest.split("/", limit = 2) // [channelName, topic]
                            val channelName = parts[0]
                            val topic = parts[1]
                            val channel = _subscriptions.value.firstOrNull { it.title == channelName }
                            if (channel != null) {
                                _navEvents.tryEmit(
                                    ChatNavEvent.OpenDialog(
                                        title = channelName,
                                        "${channel.chatId}",
                                        topic,
                                        false,
                                        channel.streamId.toInt()
                                    )
                                )
                                onDeepLinkHandled()
                            }
                        }
                    }
                }
            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun loadTopics(subscription: ChatHeader) {
        val response = client.performRequest(TopicsRequest(subscription.streamId))
        when(response) {
            is ApiResult.Success -> {
                val messageIds = response.value.topics.map { it.max_id }
                val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
                when(messagesResponse) {
                    is ApiResult.Success -> {
                        _topics.value = response.value.topics.map { topic ->
                            val unreadMessagesCount: Int
                            val unreadChannelMessages = repo.unreadMessages.value.streams.filter { it.streamId.toString() == subscription.streamId && it.topic == topic.name }
                            if (unreadChannelMessages.isNotEmpty()) {
                                unreadMessagesCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                            } else {
                                unreadMessagesCount = 0
                            }
                            val lastMessage  = messagesResponse.value.messages.firstOrNull { it.id == topic.max_id }
                            TopicHeader.from(topic, subscription.title, subscription.streamId, lastMessage, unreadMessagesCount)
                        }
                    }
                    is ApiResult.Error -> {
                        _topics.value = response.value.topics.map { topic ->
                            val unreadMessagesCount: Int
                            val unreadChannelMessages = repo.unreadMessages.value.streams.filter { it.streamId.toString()  == subscription.streamId && it.topic == topic.name }
                            if (unreadChannelMessages.isNotEmpty()) {
                                unreadMessagesCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                            } else {
                                unreadMessagesCount = 0
                            }
                            TopicHeader.from(topic, subscription.title, subscription.streamId, null, unreadMessagesCount)
                        }
                    }
                }
            }
            is ApiResult.Error -> {

            }
        }
    }

    fun processNewMessages(messages: List<MessageDto>) {
        for (message in messages) {
            when (val displayRecipient = message.displayRecipient) {
                is DisplayRecipient.Users -> {
                    val currentUserId = userViewModel.userData?.user_id
                    if (currentUserId != null ) {
                        val filteredRecipients = displayRecipient.value.filter {
                            it.id != currentUserId
                        }
                        val firstRecipient = filteredRecipients.first()
                        val streamId = "[${firstRecipient.id}, ${currentUserId}]"
                        val chatHeader = _subscriptions.value.firstOrNull { it.streamId == streamId }
                        if (chatHeader != null) {
                            updateUnreadCount(streamId, message)
                        } else {
                                val user = users.firstOrNull { it.userId == firstRecipient.id }
                                if (user != null) {
                                    val unreadUserMessagesCount = initialUnreaMessages?.pms?.firstOrNull { it.otherUserId == user.userId }?.unreadMessageIds?.size
                                    val chatHeader = ChatHeader.from(user, message, "$currentUserId", unreadUserMessagesCount)
                                    _subscriptions.update { current -> current + chatHeader }
                                }
                        }
                    }
                }
                is DisplayRecipient.StreamName -> {
                    val streamId = displayRecipient.value
                    updateUnreadCount(streamId, null)
                }
            }
        }
    }

    fun processUnreadMessages(unreadMessages: UnreadMessages) {
        _subscriptions.value = _subscriptions.value.map { header ->
            val user = header.user
            if (header.isDirectMessages && user != null) {
                val unreadUserMessagesCount = unreadMessages.pms.firstOrNull { it.otherUserId == user.userId }?.unreadMessageIds?.size
                if (unreadUserMessagesCount != null) {
                    header.copy(
                        unreadCount = unreadUserMessagesCount
                    )
                } else {
                    header
                }
            } else {
                var channelUnreadMessageCount: Int?
                val unreadChannelMessages = unreadMessages.streams.filter { it.streamId.toString() == header.streamId }
                if (unreadChannelMessages.isNotEmpty()) {
                    channelUnreadMessageCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                    header.copy(
                        unreadCount = channelUnreadMessageCount
                    )
                } else {
                    header
                }
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
                        _folders.update { foldersResponse.value }
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
                        _folders.update { foldersResponse.value }
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

    suspend fun deleteChatFromFolder(chatId: Int, folder: FolderResponseData) {
        val folderChat = folder.items.firstOrNull() { it.chatId == chatId }
        if (folderChat != null) {
            val response = client.performRequest(DeleteChatFromFolderRequest(folder.uuid, folderChat.uuid))
            when (response) {
                is ApiResult.Success -> {
                    val foldersResponse = client.performRequest(FoldersRequest())
                    when (foldersResponse) {
                        is ApiResult.Success -> {
                            _folders.update { foldersResponse.value }
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
                            _folders.update { foldersResponse.value }
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
}

@Serializable
data class ChatHeader(
    val chatId: Int,
    val title: String,
    val gravatar: String?,
    val narrow: String,
    val streamId: String,
    var lastMessage: MessageDto?,
    val isDirectMessages: Boolean,
    val color: String?,
    val user: UsersResponseData?,
    var unreadCount: Int
) {
    companion object {
        fun from(subscription: Subscription, unreadCount: Int?, firstMessage: MessageDto) = ChatHeader(
            subscription.streamId,
            subscription.name,
            null,
            "[{\"operand\": \"${subscription.name}\", \"operator\": \"channel\"}]",
            subscription.streamId.toString(),
            null,
            isDirectMessages = false,
            subscription.color,
            null,
            unreadCount ?: 0
        )

        fun from(user: UsersResponseData, lastMessage: MessageDto, currentUserId: String, unreadCount: Int?) = ChatHeader(
            user.userId,
            user.fullName,
            user.avatarUrl,
            "[{\"operand\": [${user.userId}, ${currentUserId}], \"operator\": \"dm\"}]",
            "[${user.userId}, ${currentUserId}]",
            lastMessage,
            isDirectMessages = true,
            null,
            user,
            unreadCount ?: 0
        )

    }
}

@Serializable
data class TopicHeader(
    val title: String,
    val gravatar: String?,
    val channelName: String,
    val channelId: String,
    val lastMessage: MessageDto?,
    var unreadCount: Int
) {
    companion object {
        fun from(topic: TopicsResponseData, channelName: String, channelId: String, lastMessage: MessageDto?, unreadCount: Int) = TopicHeader(
            topic.name,
            null,
            channelName,
            channelId,
            lastMessage,
            unreadCount
        )
    }
}
