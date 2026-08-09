package ru.genesiscorporation.workspace.beta.data

import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeletedMessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.EpochRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.plus


class EventsRepository() {

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var client: WorkspaceAPIClient? = null
    var session: DefaultClientWebSocketSession? = null
    var latestEpoch: Int = 0
    var epochGeneration: String = ""

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _currentUser = MutableStateFlow<UserResponseData?>(null)
    val currentUser: StateFlow<UserResponseData?> = _currentUser.asStateFlow()

    fun updateCurrentUser(newValue: UserResponseData) {
        _currentUser.update { newValue }
    }
    private var isWebSocketOpen = false

    private val _streamTopicMessages = MutableStateFlow<Map<String, List<MessageResponse>>>(emptyMap())
    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = _streamTopicMessages.asStateFlow()

    fun addStreamTopicMessages(streamUuid: String, topicUuid: String, messages: List<MessageResponse>) {
        val messagesWithUser = messages.map { message ->
            message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
            message
        }
        val key = "$streamUuid.$topicUuid"
        _streamTopicMessages.update { current ->
            current + (key to messagesWithUser)
        }
    }
    fun addMessageToStreamTopic(message: MessageResponse) {
        val key = "${message.streamUuid}.${message.topicUuid}"
        message.user = users.value.firstOrNull { it.uuid == message.authorUuid }

        _streamTopicMessages.update { current ->
            if (current[key] != null ) {
                val existingMessages = current[key].orEmpty()
                val pendingMessages = existingMessages.filter {
                    it.uuid == "" && it.authorUuid == message.authorUuid && it.payload.content == message.payload.content
                }
                val filteredExistingMessages = existingMessages.filter { it.uuid == message.uuid }
                if (filteredExistingMessages.isEmpty() && pendingMessages.isEmpty()) {
                    current + (key to (existingMessages + message))
                } else {
                    current
                }
            } else {
                current
            }
        }
    }

    fun updateMessage(updatedMessage: MessageResponse) {
        val key = "${updatedMessage.streamUuid}.${updatedMessage.topicUuid}"
        _streamTopicMessages.update { current ->
            val messages = current[key] ?: return@update current
            val updatedMessages = messages.map { message ->
                if (message.uuid == updatedMessage.uuid) {
                    message.copy(
                        payload = updatedMessage.payload,
                        reactions = updatedMessage.reactions
                    )
                } else {
                    message
                }
            }

            if (updatedMessages == messages) return@update current
            current + (key to updatedMessages)
        }
    }

    private val _streamTopics = MutableStateFlow<Map<String, List<TopicsResponseData>>>(emptyMap())
    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = _streamTopics.asStateFlow()

    fun addStreamTopics(streamUuid: String, topics: List<TopicsResponseData>) {
        _streamTopics.update { current ->
            current + (streamUuid to topics)
        }
    }
    fun addTopicToStream(topic: TopicsResponseData) {
        _streamTopics.update { current ->
            if (current[topic.streamUuid] != null) {
                val existingTopics = current[topic.streamUuid].orEmpty()
                current + (topic.streamUuid to (existingTopics + topic))
            } else {
                current
            }
        }
    }

    fun updateTopic(updatedTopic: TopicsResponseData) {
        val message = messagesPool.value.firstOrNull { it.uuid == updatedTopic.lastMessageUuid }
        _streamTopics.update { current ->
            val topics = current[updatedTopic.streamUuid] ?: return@update current
            val updatedTopics = topics.map { topic ->
                if (topic.uuid == updatedTopic.uuid) {
                    topic.copy(
                        unreadCount = updatedTopic.unreadCount,
                        name = updatedTopic.name,
                        lastMessageUuid = updatedTopic.lastMessageUuid,
                        isDone = updatedTopic.isDone,
                        lastMessage = message,
                        notificationMode = updatedTopic.notificationMode,
                        summary = updatedTopic.summary
                    )
                } else {
                    topic
                }
            }

            if (updatedTopics == topics) return@update current
            current + (updatedTopic.streamUuid to updatedTopics)
        }
    }

    private val _streamBindings = MutableStateFlow<Map<String, List<StreamBindingResponseData>>>(emptyMap())
    val streamBindings: StateFlow<Map<String, List<StreamBindingResponseData>>> = _streamBindings.asStateFlow()

    fun addStreamBindings(streamUuid: String, streamBindings: List<StreamBindingResponseData>) {
        _streamBindings.update { current ->
            current + (streamUuid to streamBindings)
        }
    }
    fun addBindingToStream(streamBinding: StreamBindingResponseData) {
        _streamBindings.update { current ->
            if (current[streamBinding.streamUuid] != null) {
                val existingTopics = current[streamBinding.streamUuid].orEmpty()
                current + (streamBinding.streamUuid to (existingTopics + streamBinding))
            } else {
                current
            }
        }
    }

    fun updateStreamBindings(updatedStreamBinding: StreamBindingResponseData) {
        _streamBindings.update { current ->
            val streamBindings = current[updatedStreamBinding.streamUuid] ?: return@update current
            val updatedStreamBindings = streamBindings.map { streamBinding ->
                if (streamBinding.uuid == updatedStreamBinding.uuid) {
                    streamBinding.copy(
                        role = updatedStreamBinding.role
                    )
                } else {
                    streamBinding
                }
            }

            if (updatedStreamBindings == streamBindings) return@update current
            current + (updatedStreamBinding.streamUuid to updatedStreamBindings)
        }
    }

    private val _messagesPool = MutableStateFlow<List<MessageResponse>>(emptyList())
    val messagesPool: StateFlow<List<MessageResponse>> = _messagesPool.asStateFlow()
    fun setInitialMessagesPool(newList: List<MessageResponse>) {
        val messagesWithUser = newList.map { message ->
            message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
            message
        }
        _messagesPool.update {
            messagesWithUser
        }
    }

    fun updateMessagesPool(newList: List<MessageResponse>) {
        val currentPoolIds = _messagesPool.value.map { it.uuid }
        val filteredMessages = newList.filter { !currentPoolIds.contains(it.uuid) }
        val messagesWithUser = filteredMessages.map { message ->
            message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
            message
        }
        _messagesPool.update { current ->
            current + messagesWithUser
        }
    }

    private val _userReactions = MutableStateFlow<List<MessageReaction>>(emptyList())

    val userReactions: StateFlow<List<MessageReaction>> = _userReactions.asStateFlow()

    fun setInitialMessageReactions(newList: List<MessageReaction>) {
        _userReactions.update {
            newList
        }
    }

    fun addReaction(reaction: MessageReaction) {
        val user = _currentUser.value
        if (user != null) {
            if (reaction.userUuid == user.uuid)
                _userReactions.update { current ->
                    current + reaction
                }
        }
    }

    fun deleteReaction(reaction: DeletedMessageReaction) {
        val reactionToDelete = _userReactions.value.firstOrNull { it.uuid == reaction.uuid && it.userUuid == reaction.userUuid }
        if (reactionToDelete != null) {
            _userReactions.update { current ->
                current.filterNot { it == reactionToDelete }
            }
        }
    }

    private val _users = MutableStateFlow<List<UserResponseData>>(emptyList())
    val users: StateFlow<List<UserResponseData>> = _users.asStateFlow()

    fun updateUser(updatedUser: UserResponseData) {
        _users.update { current ->
            current.map { user ->
                if (user.uuid == updatedUser.uuid) {
                    user.copy(
                        email = updatedUser.email,
                        firstName = updatedUser.firstName,
                        lastName = updatedUser.lastName,
                        status = updatedUser.status,
                        statusText = updatedUser.statusText,
                        statusEmoji = updatedUser.statusEmoji,
                        avatar = updatedUser.avatar
                    )
                } else {
                    user
                }
            }
        }
    }

    fun addUser(newUser: UserResponseData) {
        _users.update { current ->
            current + newUser
        }
    }

    fun setInitialUsers(newList: List<UserResponseData>) {
        _users.update {
            newList
        }
    }

    private val _streams = MutableStateFlow<List<Stream>>(emptyList())
    val streams: StateFlow<List<Stream>> = _streams.asStateFlow()

    fun updateStream(updatedStream: Stream) {
        val message = messagesPool.value.firstOrNull { it.uuid == updatedStream.lastMessageUuid }
        _streams.update { current ->
            current.map { stream ->
                if (stream.uuid == updatedStream.uuid) {
                    stream.copy(
                        lastMessageUuid = updatedStream.lastMessageUuid,
                        unreadCount = updatedStream.unreadCount,
                        activeUnreadCount = updatedStream.activeUnreadCount,
                        passiveUnreadCount = updatedStream.passiveUnreadCount,
                        notificationMode = updatedStream.notificationMode,
                        lastMessage = message
                    )
                } else {
                    stream
                }
            }
        }
    }

    fun addStream(newStream: Stream) {
        var streamWithUser = newStream
        val directUserUuid = newStream.directUserUuid
        if (directUserUuid != null) {
            val directUser = _users.value.firstOrNull { it.uuid == directUserUuid }
            streamWithUser.directUser = directUser
        }
        _streams.update { current ->
            current + streamWithUser
        }
    }

    fun setInitialStreams(newList: List<Stream>) {
        val streamsWithUsers  = newList.map { newStream ->
            var streamWithUser = newStream
            val directUserUuid = newStream.directUserUuid
            if (directUserUuid != null) {
                val directUser = _users.value.firstOrNull { it.uuid == directUserUuid }
                streamWithUser.directUser = directUser
            }
            streamWithUser
        }
        _streams.update {
            streamsWithUsers
        }
    }

    private val _folders = MutableStateFlow<List<FolderResponseData>>(emptyList())
    val folders: StateFlow<List<FolderResponseData>> = _folders.asStateFlow()
    fun updateFolder(updatedFolder: FolderResponseData) {
        _folders.update { current ->
            current.map { folder ->
                if (folder.uuid == updatedFolder.uuid) {
                    folder.copy(
                        unreadCount = updatedFolder.unreadCount,
                        title = updatedFolder.title,
                        items = updatedFolder.items
                    )
                } else {
                    folder
                }
            }
        }
    }

    fun addFolder(newFolder: FolderResponseData) {
        _folders.update { current ->
            current + newFolder
        }
    }

    fun setInitialFolders(newList: List<FolderResponseData>) {
        _folders.update {
            newList
        }
    }

    fun CoroutineScope.every30Seconds(block: suspend () -> Unit): Job = launch {
        while (isActive) {
            block()
            delay(30_000)
        }
    }

    suspend fun start() {
        val webSocketClient = client
        if (webSocketClient != null) {
            val response = webSocketClient.performRequest(EpochRequest())
            when (response) {
                is ApiResult.Success -> {
                    latestEpoch = response.value.epochVersion
                    epochGeneration = response.value.epochGeneration
                    startWebsocketConnection(webSocketClient)
                }

                is ApiResult.Error -> {

                }
            }
        }
    }

    suspend fun startWebsocketConnection(webSocketClient: WorkspaceAPIClient) {
        val accessToken = webSocketClient.userViewModel.accessToken.value
        val baseUrl = webSocketClient.userViewModel.baseUrl.value?.removePrefix("https://")
        if (accessToken != null && baseUrl != null) {
            isWebSocketOpen = true
            val job = scope.every30Seconds {
                if (!isWebSocketOpen) {
                    val webSocketClient = client
                    if (webSocketClient != null) {
                        startWebsocketConnection(webSocketClient)
                    }
                } else {
                    val webSocketClient = client
                    if (webSocketClient != null) {
                        val response = webSocketClient.performRequest(EpochRequest())
                        when (response) {
                            is ApiResult.Success -> {
                                if (latestEpoch < response.value.epochVersion || epochGeneration != response.value.epochGeneration) {
                                    session?.close(CloseReason(CloseReason.Codes.NORMAL, "Lost connection to server"))
                                    startWebsocketConnection(webSocketClient)
                                }
                            }

                            is ApiResult.Error -> {

                            }
                        }
                    }
                }
            }
            try {
                val session = webSocketClient.client.webSocketSession {
                    url {
                        protocol = URLProtocol.WS
                        this.host = baseUrl
                        path("/api/workspace/v1/events/ws")
                        parameters.append("last_epoch_version", latestEpoch.toString())
                        parameters.append("epoch_generation", epochGeneration)
                    }
                    headers {
                        append(HttpHeaders.SecWebSocketProtocol, "workspace.events.v1, bearer.$accessToken")
                    }
                }
                this.session = session
                isWebSocketOpen = !session.closeReason.isCompleted
                try {
                    for (frame in session.incoming) {
                        when (frame) {
                            is Frame.Text -> {val receivedText = frame.readText()
                                val jsonObject = json.decodeFromString<JsonObject>(receivedText)
                                val action = jsonObject["action"]?.jsonPrimitive?.contentOrNull
                                val newEpochVersion =
                                    jsonObject["epoch_version"]?.jsonPrimitive?.contentOrNull?.toInt()
                                if (newEpochVersion != null) {
                                    latestEpoch = newEpochVersion
                                }
                                val payload = jsonObject["payload"]?.toString()
                                if (action != null && payload != null) {
                                    when (jsonObject["object_type"]?.toString()?.trim('"')) {
                                        "message" -> {
                                            didReceiveMessageEvent(payload, action)
                                        }

                                        "user" -> {
                                            didReceiveUserEvent(payload, action)
                                        }

                                        "folder" -> {
                                            didReceiveFolderEvent(payload, action)
                                        }

                                        "stream" -> {
                                            didReceiveStreamEvent(payload, action)
                                        }

                                        "topic" -> {
                                            didReceiveTopicEvent(payload, action)
                                        }

                                        "message_reaction" -> {
                                            didReceiveReactionEvent(payload, action)
                                        }

                                        else -> Log.d("WebSocket", "Received: $receivedText")
                                    }
                                }
                            }
                            is Frame.Binary -> Log.d("WebSocket", "Received binary data bundle")
                            is Frame.Close -> Log.d(
                                "WebSocket",
                                "Connection closing reason: ${frame.readReason()}"
                            )
                            else -> Log.d("WebSocket", "Received control or ping/pong frame")
                        }
                    }
                } finally {
                    val reason = session.closeReason.await()
                    if (reason?.code?.toInt() == 4401) {
                        client?.refreshToken()
                    }
                }
            } catch (e: Exception) {
                Log.d("WebSocket", "Failed: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
            } finally {
                this.session = null
                isWebSocketOpen = false
                Log.d("WebSocket", "Is inactive")
            }
        }
    }

    fun didReceiveUserEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val user = json.decodeFromString<UserResponseData>(payload)
                addUser(user)
            }
            "updated" -> {
                val user = json.decodeFromString<UserResponseData>(payload)
                val currentUser = _currentUser.value
                if (currentUser?.uuid == user.uuid) {
                    updateCurrentUser(user)
                }
                updateUser(user)
            }
            "deleted" -> {

            }
        }
    }

    fun didReceiveMessageEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val message = json.decodeFromString<MessageResponse>(payload)
                updateMessagesPool(listOf(message))
                addMessageToStreamTopic(message)
            }
            "updated" -> {
                val message = json.decodeFromString<MessageResponse>(payload)
                updateMessagesPool(listOf(message))
                updateMessage(message)
            }
            "deleted" -> {

            }
        }
    }

    fun didReceiveFolderEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val folder = json.decodeFromString<FolderResponseData>(payload)
                addFolder(folder)
            }
            "updated" -> {
                val folder = json.decodeFromString<FolderResponseData>(payload)
                updateFolder(folder)
            }
            "deleted" -> {

            }
        }
    }

    fun didReceiveReactionEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val reaction = json.decodeFromString<MessageReaction>(payload)
                addReaction(reaction)
            }
            "deleted" -> {
                val reaction = json.decodeFromString<DeletedMessageReaction>(payload)
                deleteReaction(reaction)
            }
        }
    }

    fun didReceiveStreamEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val stream = json.decodeFromString<Stream>(payload)
                addStream(stream)
            }
            "updated" -> {
                val stream = json.decodeFromString<Stream>(payload)
                updateStream(stream)
            }
            "deleted" -> {

            }
        }
    }

    fun didReceiveTopicEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val topic = json.decodeFromString< TopicsResponseData>(payload)
                addTopicToStream(topic)
            }
            "updated" -> {
                val topic = json.decodeFromString< TopicsResponseData>(payload)
                updateTopic(topic)
            }
            "deleted" -> {

            }
        }
    }

    var pushId: String? = null

    var jitsiServerUrl: String = ""
}

@Serializable
data class PongMessage(
    val type: String,
    val ts: String
)

@Serializable
data class MessageEvent(
    val type: String,
    val message: MessageResponse,
    @SerialName("epoch_version") val epoch_version: Int
)

@Serializable
data class StreamEvent(
    val type: String,
    val stream: Stream,
    @SerialName("epoch_version") val epoch_version: Int
)