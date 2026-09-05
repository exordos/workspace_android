package ru.genesiscorporation.workspace.beta.data

import android.util.Log
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeletedMessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.Draft
import ru.genesiscorporation.workspace.beta.data.remote.dto.DraftsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EpochRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventsProbeRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.FoldersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReactionsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.PresenceRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.plus

private const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
private const val MAX_RETRY_DELAY_MILLIS = 30_000L
private const val REALTIME_PROBE_INTERVAL_MILLIS = 30_000L
private const val EVENTS_CURSOR_EXPIRED_STATUS_CODE = "410"

class EventsRepository() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun close() {
        scope.cancel()
    }

    var client: WorkspaceAPIClient? = null
    @Volatile
    var latestEpoch: Int = 0
    @Volatile
    var epochGeneration: String = ""
    private val connectionMutex = Mutex()
    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null
    private val heartbeatJob: Job = scope.every30Seconds {
        val webSocketClient = client ?: return@every30Seconds
        heartbeatTask(webSocketClient)
    }

    var pushId: String? = null

    var jitsiServerUrl: String = ""

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }


    private val folderCreationFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private val _currentUser = MutableStateFlow<UserResponseData?>(null)
    val currentUser: StateFlow<UserResponseData?> = _currentUser.asStateFlow()

    fun updateCurrentUser(newValue: UserResponseData) {
        _currentUser.update { newValue }
    }

    private val _streamsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val streamsQueryState: StateFlow<QueryState> = _streamsQueryState


    private val _currentlySelectedFolder = MutableStateFlow<FolderResponseData?>(null)
    var currentlySelectedFolder: StateFlow<FolderResponseData?> = _currentlySelectedFolder

    private val _streamTopicMessages = MutableStateFlow<Map<String, List<MessageResponse>>>(emptyMap())
    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = _streamTopicMessages.asStateFlow()

    fun updateCurrentlySelectedFolder(newFolder: FolderResponseData?) {
        _currentlySelectedFolder.update { newFolder }
    }
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
        appendItemsToTopicsPool(topics)
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
        appendItemsToTopicsPool(listOf(topic))
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
        updateTopicInTopicsPool(updatedTopic)
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

    private val _topicsPool = MutableStateFlow<List<TopicsResponseData>>(emptyList())
    val topicsPool: StateFlow<List<TopicsResponseData>> = _topicsPool.asStateFlow()

    fun appendItemsToTopicsPool(newList: List<TopicsResponseData>) {
        val currentPoolIds = _messagesPool.value.map { it.uuid }
        val filteredTopics = newList.filter { !currentPoolIds.contains(it.uuid) }

        _topicsPool.update { current ->
            current + filteredTopics
        }
    }

    fun updateTopicInTopicsPool(updatedTopic: TopicsResponseData) {
        _topicsPool.update { current ->
            current.map { topic ->
                if (topic.uuid == updatedTopic.uuid) {
                    topic.copy(
                        unreadCount = updatedTopic.unreadCount,
                        name = updatedTopic.name,
                        lastMessageUuid = updatedTopic.lastMessageUuid,
                        isDone = updatedTopic.isDone,
                        notificationMode = updatedTopic.notificationMode,
                        summary = updatedTopic.summary
                    )
                } else {
                    topic
                }
            }
        }
    }

    private val _draftsPool = MutableStateFlow<List<Draft>>(emptyList())
    val draftsPool: StateFlow<List<Draft>> = _draftsPool.asStateFlow()

    fun setInitialDraftsPool(newList: List<Draft>) {
        _draftsPool.update {
            newList
        }
    }

    fun addDraft(newValue: Draft) {
        _draftsPool.update { current ->
            current + newValue
        }
    }

    fun updateDraft(updatedDraft: Draft) {
        _draftsPool.update { current ->
            current.map { draft ->
                if (draft.uuid == updatedDraft.uuid) {
                    draft.copy(
                        payload = updatedDraft.payload
                    )
                } else {
                    draft
                }
            }
        }
    }

    fun removeDraft(valueToRemove: Draft) {
        _draftsPool.update { current ->
            current - valueToRemove
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

    fun poolMessage(uuid: String?): MessageResponse? {
        return messagesPool.value.firstOrNull { it.uuid == uuid }
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

    private fun CoroutineScope.every30Seconds(block: suspend () -> Unit): Job = launch {
        while (isActive) {
            delay(REALTIME_PROBE_INTERVAL_MILLIS)
            block()
        }
    }

    private suspend fun probeVisibleEvents() {
        val webSocketClient = client ?: return
        val probedSession = activeSession ?: return
        val probedEpochVersion = latestEpoch
        val probedEpochGeneration = epochGeneration
        if (probedEpochGeneration.isBlank()) return

        when (
            val response = webSocketClient.performRequest(
                EventsProbeRequest(
                    afterEpochVersion = probedEpochVersion,
                    epochGeneration = probedEpochGeneration
                )
            )
        ) {
            is ApiResult.Success -> {
                if (
                    activeSession === probedSession &&
                    shouldReconnectForEventsProbe(
                        savedEpochVersion = latestEpoch,
                        probeEpochVersions = response.value.map { it.epochVersion }
                    )
                ) {
                    Log.d(
                        "WebSocket",
                        "Visible events are pending; reconnecting from the saved event cursor"
                    )
                    probedSession.close(
                        CloseReason(CloseReason.Codes.NORMAL, "Visible events are pending")
                    )
                }
            }

            is ApiResult.Error -> {
                if (response.error.code == EVENTS_CURSOR_EXPIRED_STATUS_CODE &&
                    activeSession === probedSession &&
                    latestEpoch == probedEpochVersion &&
                    epochGeneration == probedEpochGeneration
                ) {
                    Log.d("WebSocket", "Event cursor expired; reconnecting with a fresh cursor")
                    clearRealtimeCursor()
                    probedSession.close(
                        CloseReason(CloseReason.Codes.NORMAL, "Event cursor expired")
                    )
                } else {
                    Log.d("WebSocket", "Failed to probe visible events")
                }
            }
        }
    }

    private suspend fun heartbeatTask(webSocketClient: WorkspaceAPIClient) {
        probeVisibleEvents()

        val myUser = currentUser.value ?: return
        val myStatus = if (myUser.status == "offline" || myUser.status == "idle") {
            "active"
        } else {
            myUser.status
        }
        when (
            webSocketClient.performRequest(
                PresenceRequest(
                    myUser.uuid,
                    myStatus,
                    myUser.statusEmoji,
                    myUser.statusText
                )
            )
        ) {
            is ApiResult.Success -> Unit
            is ApiResult.Error -> Log.d("Presence", "Failed to update presence")
        }
    }

    suspend fun start() = connectionMutex.withLock {
        val webSocketClient = client
        if (webSocketClient == null) return@withLock

        var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
        while (currentCoroutineContext().isActive) {
            val currentBaseUrl = webSocketClient.userViewModel.baseUrl.value
            if (currentBaseUrl == null) {
                delay(retryDelayMillis)
                retryDelayMillis = nextRetryDelayMillis(retryDelayMillis)
                continue
            }

            if (epochGeneration.isBlank()) {
                when (val response = webSocketClient.performRequest(EpochRequest())) {
                    is ApiResult.Success -> {
                        latestEpoch = response.value.epochVersion
                        epochGeneration = response.value.epochGeneration
                    }

                    is ApiResult.Error -> {
                        Log.d("WebSocket", "Failed to load the initial event cursor")
                        delay(retryDelayMillis)
                        retryDelayMillis = nextRetryDelayMillis(retryDelayMillis)
                        continue
                    }
                }
            }

            var connectionEstablished = false
            try {
                startWebsocketConnection(webSocketClient, currentBaseUrl) {
                    connectionEstablished = true
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Log.d(
                    "WebSocket",
                    "Connection failed: ${exception::class.simpleName}: ${exception.message}"
                )
            }

            if (currentCoroutineContext().isActive) {
                if (connectionEstablished) {
                    retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                }
                Log.d("WebSocket", "Reconnecting in ${retryDelayMillis}ms")
                delay(retryDelayMillis)
                retryDelayMillis = nextRetryDelayMillis(retryDelayMillis)
            }
        }
    }

    private fun clearRealtimeCursor() {
        latestEpoch = 0
        epochGeneration = ""
    }

    private suspend fun startWebsocketConnection(
        webSocketClient: WorkspaceAPIClient,
        baseUrl: String,
        onConnected: () -> Unit
    ) {
        val accessToken = webSocketClient.userViewModel.accessToken.value
        if (accessToken != null) {
            val endpoint = resolveWebSocketEndpoint(baseUrl)
            Log.d("WebSocket", "Connecting: ${endpoint.displayUrl()}")
            webSocketClient.client.webSocket(
                request = {
                    url {
                        protocol = endpoint.protocol
                        host = endpoint.host
                        endpoint.port?.let { port = it }
                        path("/api/workspace/v1/events/ws")
                        parameters.append("last_epoch_version", latestEpoch.toString())
                        parameters.append("epoch_generation", epochGeneration)
                    }
                    headers {
                        append(HttpHeaders.SecWebSocketProtocol, "workspace.events.v1, bearer.$accessToken")
                    }
                }
            ) {
                activeSession = this
                onConnected()
                Log.d("WebSocket", "Connected: ${endpoint.displayUrl()}")
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val receivedText = frame.readText()
                                val jsonObject = json.decodeFromString<JsonObject>(receivedText)
                                if (jsonObject["type"]?.jsonPrimitive?.contentOrNull == "ready") {
                                    epochGeneration = jsonObject["epoch_generation"]
                                        ?.jsonPrimitive
                                        ?.contentOrNull
                                        ?: epochGeneration
                                }
                                val action = jsonObject["action"]?.jsonPrimitive?.contentOrNull
                                val newEpochVersion =
                                    jsonObject["epoch_version"]?.jsonPrimitive?.contentOrNull?.toInt()
                                if (newEpochVersion != null) {
                                    latestEpoch = newEpochVersion
                                }
                                if (jsonObject["type"]?.jsonPrimitive?.contentOrNull == "ready") {
                                    Log.d(
                                        "WebSocket",
                                        "Ready: epoch_version=$latestEpoch, " +
                                            "epoch_generation=$epochGeneration"
                                    )
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
                    activeSession = null
                    val reason = closeReason.await()
                    if (reason?.code?.toInt() == 4401) {
                        webSocketClient.refreshToken()
                    }
                    Log.d("WebSocket", "Disconnected: $reason")
                }
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

    suspend fun loadServerSettings() {
        val webSocketClient = client ?: return
        _streamsQueryState.value = QueryState.Loading
        val response = webSocketClient.performRequest(ServerSettingsRequest(webSocketClient.userViewModel.baseUrl.value ?: ""))
        when(response) {
            is ApiResult.Success -> {
                jitsiServerUrl = response.value.meetUrl
                loadUserInfo()
            }
            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadUserInfo() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(OwnUserRequest())
        when(response) {
            is ApiResult.Success -> {
                updateCurrentUser(response.value)
                loadMessageReactions(response.value.uuid)
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadMessageReactions(userUuid: String) {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(MessageReactionsRequest(userUuid))
        when(response) {
            is ApiResult.Success -> {
                setInitialMessageReactions(response.value)
                loadAllUsersInfo()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadAllUsersInfo() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(UsersRequest())
        when(response) {
            is ApiResult.Success -> {
                setInitialUsers(response.value)
                loadFolders()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
            }
        }
    }
    suspend fun loadFolders() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(FoldersRequest())
        when(response) {
            is ApiResult.Success -> {
                setInitialFolders(response.value.sortedBy { LocalDateTime.parse(it.creationDate, folderCreationFormatter) })
                if (!folders.value.isEmpty()) {
                    _currentlySelectedFolder.value = folders.value.first()
                }
                loadSubscribedChannels()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _streamsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadSubscribedChannels() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(StreamsRequest())
        when(response) {
            is ApiResult.Success -> {
                val messageIds = response.value.mapNotNull { it.lastMessageUuid }
                if (!messageIds.isEmpty()) {
                    val messagesResponse = webSocketClient.performRequest(MessagesByIdsRequest(messageIds))
                    when (messagesResponse) {
                        is ApiResult.Success -> {
                            setInitialMessagesPool(messagesResponse.value)
                            val streamsWithMessages = response.value.map { stream ->
                                var updatedStream = stream
                                updatedStream.lastMessage = poolMessage(stream.lastMessageUuid)
                                updatedStream
                            }
                            setInitialStreams(streamsWithMessages)
                            loadDrafts()
                        }

                        is ApiResult.Error -> {
                            setInitialStreams(response.value)
                            loadDrafts()
                        }
                    }
                } else {
                    setInitialStreams(response.value)
                    loadDrafts()
                }
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadDrafts() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(DraftsRequest())
        when(response) {
            is ApiResult.Success -> {
                setInitialDraftsPool(response.value)
                _streamsQueryState.value = QueryState.Success
                start()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Success
                start()
            }
        }
    }
}

internal data class WebSocketEndpoint(
    val protocol: URLProtocol,
    val host: String,
    val port: Int?
) {
    fun displayUrl(): String = buildString {
        append(protocol.name)
        append("://")
        append(host)
        port?.let {
            append(":")
            append(it)
        }
    }
}

internal fun resolveWebSocketEndpoint(baseUrl: String): WebSocketEndpoint {
    val uri = URI(baseUrl)
    val protocol = when (uri.scheme?.lowercase()) {
        "https" -> URLProtocol.WSS
        "http" -> URLProtocol.WS
        else -> throw IllegalArgumentException("Workspace base URL must use HTTP or HTTPS")
    }
    val host = requireNotNull(uri.host) { "Workspace base URL must contain a host" }
    return WebSocketEndpoint(
        protocol = protocol,
        host = host,
        port = uri.port.takeIf { it != -1 }
    )
}

internal fun nextRetryDelayMillis(currentDelayMillis: Long): Long =
    (currentDelayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)

internal fun shouldReconnectForEventsProbe(
    savedEpochVersion: Int,
    probeEpochVersions: List<Int>
): Boolean = probeEpochVersions.any { it > savedEpochVersion }

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
