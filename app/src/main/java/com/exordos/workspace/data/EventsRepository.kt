package com.exordos.workspace.data

import android.content.Context
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageReaction
import com.exordos.workspace.data.remote.dto.FolderResponseData
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.DeletedMessageReaction
import com.exordos.workspace.data.uio.DraftUIO
import com.exordos.workspace.data.remote.dto.DraftsRequest
import com.exordos.workspace.data.remote.dto.EpochRequest
import com.exordos.workspace.data.remote.dto.EventsProbeRequest
import com.exordos.workspace.data.uio.FolderUIO
import com.exordos.workspace.data.remote.dto.FoldersRequest
import com.exordos.workspace.data.uio.MessageReactionUIO
import com.exordos.workspace.data.remote.dto.MessageReactionsRequest
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.remote.dto.MessagesRequest
import com.exordos.workspace.data.remote.dto.OwnUserRequest
import com.exordos.workspace.data.remote.ProtectedRequestAuthentication
import com.exordos.workspace.data.remote.dto.PresenceRequest
import com.exordos.workspace.data.remote.dto.ServerSettingsRequest
import com.exordos.workspace.data.remote.dto.StreamBindingsRequest
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.uio.StreamBindingUIO
import com.exordos.workspace.data.remote.dto.StreamsRequest
import com.exordos.workspace.data.remote.dto.TopicsRequest
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.data.remote.dto.UsersRequest
import com.exordos.workspace.data.uio.DraftDao
import com.exordos.workspace.data.uio.FolderDao
import com.exordos.workspace.data.uio.FolderItemDao
import com.exordos.workspace.data.uio.FolderItemUIO
import com.exordos.workspace.data.uio.LastMessageDao
import com.exordos.workspace.data.uio.LastMessageUIO
import com.exordos.workspace.data.uio.MapConverters
import com.exordos.workspace.data.uio.MessageDao
import com.exordos.workspace.data.uio.MessageReactionDao
import com.exordos.workspace.data.uio.StreamBindingDao
import com.exordos.workspace.data.uio.StreamDao
import com.exordos.workspace.data.uio.TopicDao
import com.exordos.workspace.data.uio.UserDao
import com.exordos.workspace.data.uio.toLastMessageUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.webrtc.ApplicationContextProvider
import java.net.URI
import kotlin.collections.map
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.plus

private const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
private const val MAX_RETRY_DELAY_MILLIS = 30_000L
private const val REALTIME_PROBE_INTERVAL_MILLIS = 30_000L
private const val EVENTS_CURSOR_EXPIRED_STATUS_CODE = 410

@Database(
    entities = [
        UserUIO::class,
        StreamUIO::class,
        TopicUIO::class,
        LastMessageUIO::class,
        DraftUIO::class,
        FolderUIO::class,
        FolderItemUIO::class,
        StreamBindingUIO::class,
        MessageUIO::class,
        MessageReactionUIO::class,
    ],
    version = 1
)

@ColumnTypeConverters(MapConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun streamDao(): StreamDao
    abstract fun topicDao(): TopicDao
    abstract fun lastMessageDao(): LastMessageDao
    abstract fun draftDao(): DraftDao
    abstract fun folderDao(): FolderDao
    abstract fun folderItemDao(): FolderItemDao
    abstract fun messageDao(): MessageDao
    abstract fun messageReactionDao(): MessageReactionDao
    abstract fun streamBindingDao(): StreamBindingDao
}

class EventsRepository(
    val serverId: String,
    private val config: ServerConfig,
    private val tokenStore: SecureTokenStore,
    var client: WorkspaceAPIClient,
    val context: Context
) {

    private val originalServer = config.copy()
    internal var onPushTopicUpdated: (TopicUIO, Long, String) -> Unit = { _, _, _ -> }
    internal var onPushRefreshNeeded: () -> Unit = {}
    fun notificationRead() { onPushRefreshNeeded() }

    val db =
        Room.databaseBuilder<AppDatabase>(context, "database-${config.id}")
            .setDriver(AndroidSQLiteDriver())
            .build()

    internal fun matchesServerConfiguration(server: ServerConfig): Boolean =
        server.id == serverId && server.baseUrl == originalServer.baseUrl && server.projectUuid == originalServer.projectUuid

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun close() {
        invalidateVerifiedOwnerAuthentication()
        scope.cancel()
    }

    @Volatile
    var latestEpoch: Int = 0
    @Volatile
    var epochGeneration: String = ""
    private val connectionMutex = Mutex()
    private val realtimeStateLock = Any()

    private val pageLimit = 25
    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null
    @Volatile
    private var messageResyncRequired = false
    private data class OpenConversation(val owner: Any, val streamUuid: String, val topicUuid: String) {
        val key: String get() = "$streamUuid.$topicUuid"
    }
    private val openConversation = MutableStateFlow<OpenConversation?>(null)
    private var historyGeneration = 0L
    private val streamRemovalRevisions = mutableMapOf<String, Long>()

    fun streamRemovalRevision(streamUuid: String): Long = synchronized(realtimeStateLock) {
        streamRemovalRevisions[streamUuid] ?: 0L
    }

    /** Linearize plaintext publication with removal, independently of flow collector timing. */
    fun <T : Any> withStreamRevision(streamUuid: String, expected: Long, block: () -> T): T? = synchronized(realtimeStateLock) {
        if (streamRemovalRevision(streamUuid) == expected) block() else null
    }
    private val staleConversations = mutableSetOf<String>()
    private val pendingMessageUuids = MutableStateFlow<Set<String>>(emptySet())
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



    private val _currentUser = MutableStateFlow<UserUIO?>(null)
    val currentUser: StateFlow<UserUIO?> = _currentUser.asStateFlow()
    private val _verifiedOwnerAuthentication = MutableStateFlow<VerifiedOwnerAuthentication?>(null)
    val verifiedOwnerAuthentication: StateFlow<VerifiedOwnerAuthentication?> = _verifiedOwnerAuthentication.asStateFlow()
    @Volatile private var ownerVerificationRevision = 0L
    private val ownerVerificationLineage = Any()

    fun invalidateVerifiedOwnerAuthentication() = synchronized(realtimeStateLock) {
        ownerVerificationRevision++
        _verifiedOwnerAuthentication.value = null
    }

    fun updateCurrentUser(newValue: UserUIO) {
        _currentUser.update { newValue }
    }

    private var didLoadData: Boolean = false

    private val _streamsQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val streamsQueryState: StateFlow<QueryState> = _streamsQueryState


    private val _additionalQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val additionalQueryState: StateFlow<QueryState> = _additionalQueryState


    private val _currentlySelectedFolder = MutableStateFlow<FolderUIO?>(null)
    var currentlySelectedFolder: StateFlow<FolderUIO?> = _currentlySelectedFolder

    private val _messageDeletedEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messageDeletedEvents = _messageDeletedEvents.asSharedFlow()
    private val _messageUpdatedEvents = MutableSharedFlow<MessageUIO>(extraBufferCapacity = 64)
    val messageUpdatedEvents = _messageUpdatedEvents.asSharedFlow()
    private val _streamDeletedEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val streamDeletedEvents = _streamDeletedEvents.asSharedFlow()

    val userDao = db.userDao()
    val streamDao = db.streamDao()
    val topicDao = db.topicDao()
    val lastMessageDao = db.lastMessageDao()
    val draftDao = db.draftDao()
    val folderDao = db.folderDao()
    val folderItemDao = db.folderItemDao()
    val messageDao = db.messageDao()
    val messageReactionDao = db.messageReactionDao()
    val streamBindingDao = db.streamBindingDao()

    val users: Flow<List<UserUIO>> = userDao.observeUsers()

    fun updateCurrentlySelectedFolder(newFolder: FolderUIO?) {
        _currentlySelectedFolder.update { newFolder }
    }

    internal fun openConversation(owner: Any, streamUuid: String, topicUuid: String) {
        synchronized(realtimeStateLock) {
            val conversation = OpenConversation(owner, streamUuid, topicUuid)
            if (messageResyncRequired) staleConversations += conversation.key
            openConversation.value = conversation
        }
    }

    internal fun closeConversation(owner: Any) {
        synchronized(realtimeStateLock) {
            if (openConversation.value?.owner === owner) openConversation.value = null
        }
    }

    internal fun isConversationStale(streamUuid: String, topicUuid: String): Boolean =
        synchronized(realtimeStateLock) { "$streamUuid.$topicUuid" in staleConversations }

    fun addPendingMessage(message: MessageUIO) {
        pendingMessageUuids.update { it + message.uuid }
        scope.launch {
            addLastMessage(message.toLastMessageUIO())
            addMessageToStreamTopic(message)
        }
    }
    suspend fun addMessageToStreamTopic(message: MessageUIO) {
        messageDao.upsert(message)
    }

    suspend fun updateMessage(updatedMessage: MessageUIO) {
        messageDao.upsert(updatedMessage)
    }

    suspend fun addMessages(messageList: List<MessageUIO>) {
        messageDao.upsert(messageList)
    }

    suspend fun removeMessage(messageUuid: String) {
        val messageToDelete = messageDao.getMessage(messageUuid)
        if (messageToDelete != null) {
            messageDao.delete(messageToDelete)
        }
        if (!_messageDeletedEvents.tryEmit(messageUuid)) {
            scope.launch { _messageDeletedEvents.emit(messageUuid) }
        }
    }

    /** Reconcile HTTP confirmation with either ordering of the matching WS event. */
    fun reconcileSentMessage(pendingUuid: String, confirmed: MessageUIO) {
        scope.launch {
            updateMessage(confirmed)
            pendingMessageUuids.update { it - pendingUuid }
        }
    }

    /** One repository belongs to one server/account projection; invalidate before removing its caches. */
    fun removeStream(streamUuid: String) {
        synchronized(realtimeStateLock) {
            streamRemovalRevisions[streamUuid] = streamRemovalRevision(streamUuid) + 1
            historyGeneration++ // An older HTTP history response must not restore a deleted conversation.
            staleConversations.removeAll { it.startsWith("$streamUuid.") }
            if (openConversation.value?.streamUuid == streamUuid) openConversation.value = null
            if (!_streamDeletedEvents.tryEmit(streamUuid)) scope.launch { _streamDeletedEvents.emit(streamUuid) }
        }
    }

    private fun TopicUIO.withoutDeletedMessage(messageUuid: String): TopicUIO =
        if (lastMessageUuid == messageUuid) {
            copy(lastMessageUuid = null)
        } else {
            this
        }

    val messages: Flow<List<MessageUIO>> = messageDao.observeMessages()

    val drafts: Flow<List<DraftUIO>> = draftDao.observeDrafts()

    suspend fun setInitialDrafts(newList: List<DraftUIO>) {
        draftDao.upsert(newList)
    }

    suspend fun addDraft(newValue: DraftUIO) {
        draftDao.upsert(newValue)
    }

    suspend fun updateDraft(updatedDraft: DraftUIO) {
        draftDao.upsert(updatedDraft)
    }

    suspend fun removeDraft(valueToRemove: DraftUIO) {
        draftDao.delete(valueToRemove)
    }

    val userReactions: Flow<List<MessageReactionUIO>> = messageReactionDao.observeMessageReactions()

    suspend fun setInitialMessageReactions(newList: List<MessageReactionUIO>) {
        messageReactionDao.upsert(newList)
    }

    suspend fun addReaction(reaction: MessageReactionUIO) {
        messageReactionDao.upsert(reaction)
    }

    suspend fun deleteReaction(reaction: DeletedMessageReaction) {
        val reactionToDelete = messageReactionDao.getMessageReaction(reaction.uuid)
        if (reactionToDelete != null) {
            messageReactionDao.delete(reactionToDelete)
        }
    }

    suspend fun updateUser(updatedUser: UserUIO) {
        userDao.upsert(updatedUser)
    }

    suspend fun addUser(newUser: UserUIO) {
        userDao.upsert(newUser)
    }

    suspend fun setInitialUsers(newList: List<UserUIO>) {
        userDao.upsert(newList)
    }

    val streams: Flow<List<StreamUIO>> = streamDao.observeStreams()

    suspend fun updateStream(updatedStream: StreamUIO) {
        streamDao.upsert(updatedStream)
    }

    suspend fun addStream(newStream: StreamUIO) {
        streamDao.upsert(newStream)
    }

    suspend fun setInitialStreams(newList: List<StreamUIO>) {
        streamDao.upsert(newList)
    }

    suspend fun updateStreamBinding(updatedStreamBinding: StreamBindingUIO) {
        streamBindingDao.upsert(updatedStreamBinding)
    }

    suspend fun addStreamBinding(newStreamBinding: StreamBindingUIO) {
        streamBindingDao.upsert(newStreamBinding)
    }

    suspend fun setInitialStreamBindings(newList: List<StreamBindingUIO>) {
        streamBindingDao.upsert(newList)
    }

    suspend fun deleteStreamBinding(streamBinding: StreamBindingUIO) {
        streamBindingDao.delete(streamBinding)
    }

    val topics: Flow<List<TopicUIO>> = topicDao.observeTopics()

    suspend fun updateTopic(updatedTopic: TopicUIO) {
        topicDao.upsert(updatedTopic)
    }

    suspend fun addTopic(newTopic: TopicUIO) {
        topicDao.upsert(newTopic)
    }

    suspend fun setInitialTopics(newList: List<TopicUIO>) {
        topicDao.upsert(newList)
    }


    val lastMessages: Flow<List<LastMessageUIO>> = lastMessageDao.observeLastMessages()

    suspend fun addLastMessage(newMessage: LastMessageUIO) {
        lastMessageDao.upsert(newMessage)
    }

    suspend fun addLastMessages(messagesList: List<LastMessageUIO>) {
        lastMessageDao.upsert(messagesList)
    }

    suspend fun setInitialLastMessages(newList: List<LastMessageUIO>) {
        lastMessageDao.upsert(newList)
    }

    val folders: Flow<List<FolderUIO>> = folderDao.observeFolders()
    suspend fun updateFolder(updatedFolder: FolderUIO) {
        folderDao.upsert(updatedFolder)
    }

    suspend fun removeFolder(folderUuid: String) {
        val folderToDelete = folderDao.getFolder(folderUuid)
        if (folderToDelete != null) {
            folderDao.delete(folderToDelete)
        }
    }

    suspend fun addFolder(newFolder: FolderUIO) {
        folderDao.upsert(newFolder)
    }

    suspend fun setInitialFolders(newList: List<FolderUIO>) {
        folderDao.upsert(newList)
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
                ),
                serverId,
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
                val invalidated = synchronized(realtimeStateLock) {
                    if (response.error.httpStatusCode == EVENTS_CURSOR_EXPIRED_STATUS_CODE &&
                        activeSession === probedSession &&
                        latestEpoch == probedEpochVersion &&
                        epochGeneration == probedEpochGeneration
                    ) {
                        invalidateRealtimeCursor()
                        true
                    } else false
                }
                if (invalidated) {
                    Log.d("WebSocket", "Event cursor expired; reconnecting with a fresh cursor")
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
                ),
                serverId,
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
            val currentBaseUrl = config.baseUrl

            if (epochGeneration.isBlank()) {
                when (
                    val response = webSocketClient.performRequest(
                        EpochRequest(),
                        serverId,
                        config,
                    )
                ) {
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

    private fun invalidateRealtimeCursor() = synchronized(realtimeStateLock) {
        historyGeneration++
        openConversation.value?.let { staleConversations += it.key }
        messageResyncRequired = true
        latestEpoch = 0
        epochGeneration = ""
    }

    private suspend fun startWebsocketConnection(
        webSocketClient: WorkspaceAPIClient,
        baseUrl: String,
        onConnected: () -> Unit
    ) {
        val accessToken = tokenStore.get(serverId)?.accessToken
        val selectedServer = webSocketClient.requireUserViewModel().getServer(serverId)?.copy()
        val ownerUuid = _currentUser.value?.uuid
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
                synchronized(realtimeStateLock) { activeSession = this }
                try {
                    onConnected()
                    onPushRefreshNeeded()
                    Log.d("WebSocket", "Connected: ${endpoint.displayUrl()}")
                    for (frame in incoming) {
                        if (tokenStore.get(serverId)?.accessToken != accessToken ||
                            webSocketClient.requireUserViewModel().getServer(serverId) != selectedServer ||
                            _currentUser.value?.uuid != ownerUuid) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Account context changed"))
                            return@webSocket
                        }
                        when (frame) {
                            is Frame.Text -> {
                                val receivedText = frame.readText()
                                val jsonObject = json.decodeFromString<JsonObject>(receivedText)
                                if (isExpiredRealtimeCursorFrame(jsonObject)) {
                                    invalidateRealtimeCursor()
                                    close(CloseReason(CloseReason.Codes.NORMAL, "Event cursor expired"))
                                    return@webSocket
                                }
                                val action = jsonObject["action"]?.jsonPrimitive?.contentOrNull
                                val newEpochVersion =
                                    jsonObject["epoch_version"]?.jsonPrimitive?.contentOrNull?.toInt()
                                synchronized(realtimeStateLock) {
                                    // A frame from the socket being closed must not revive an expired cursor.
                                    if (messageResyncRequired) return@webSocket
                                    if (jsonObject["type"]?.jsonPrimitive?.contentOrNull == "ready") {
                                        epochGeneration = jsonObject["epoch_generation"]
                                            ?.jsonPrimitive?.contentOrNull ?: epochGeneration
                                    }
                                    if (newEpochVersion != null) latestEpoch = newEpochVersion
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

                                        "stream_bindings", "stream_binding" -> {
                                            didReceiveStreamBindingEvent(payload, action)
                                        }

                                        "topic" -> {
                                            didReceiveTopicEvent(payload, action, newEpochVersion?.toLong(), epochGeneration)
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
                    synchronized(realtimeStateLock) { activeSession = null }
                    val reason = closeReason.await()
                    if (reason?.code?.toInt() == 4401) {
                        webSocketClient.refreshSession(serverId, accessToken)
                    } else if (reason?.code?.toInt() == 4410) {
                        // The error frame can be missing; the close code alone is sufficient.
                        invalidateRealtimeCursor()
                    }
                    Log.d("WebSocket", "Disconnected: $reason")
                }
            }
        }
    }

    fun didReceiveStreamBindingEvent(payload: String, action: String) {
        when (action) {
            "created" -> {
                val streamBindingUIO = json.decodeFromString<StreamBindingUIO>(payload)
                scope.launch {
                    addStreamBinding(streamBindingUIO)
                }
            }
            "updated" -> {
                val streamBindingUIO = json.decodeFromString<StreamBindingUIO>(payload)
                scope.launch {
                    updateStreamBinding(streamBindingUIO)
                }
            }
            "deleted" -> {
                val streamBindingUIO = json.decodeFromString<StreamBindingUIO>(payload)
                scope.launch {
                    deleteStreamBinding(streamBindingUIO)
                }
            }
        }
    }

    fun didReceiveUserEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                scope.launch {
                    val user = json.decodeFromString<UserResponseData>(payload).toUio()
                    addUser(user)
                }
            }
            "updated" -> {
                scope.launch {
                    val user = json.decodeFromString<UserResponseData>(payload).toUio()
                    val currentUser = _currentUser.value
                    if (currentUser?.uuid == user.uuid) {
                        updateCurrentUser(user)
                    }
                    updateUser(user)
                }
            }
            "deleted" -> {

            }
        }
    }

    fun didReceiveMessageEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val message = json.decodeFromString<MessageResponse>(payload).toUio()
                scope.launch {
                    addLastMessage(message.toLastMessageUIO())
                    addMessageToStreamTopic(message)
                }
            }
            "updated" -> {
                val message = json.decodeFromString<MessageResponse>(payload).toUio()
                scope.launch {
                    addLastMessage(message.toLastMessageUIO())
                    updateMessage(message)
                }
            }
            "deleted" -> {
                val messageUuid = json.decodeFromString<JsonObject>(payload)["uuid"]?.jsonPrimitive?.contentOrNull
                if (messageUuid != null) {
                    scope.launch {
                        removeMessage(messageUuid)
                    }
                }
            }
        }
    }

    fun didReceiveFolderEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val folder = json.decodeFromString<FolderResponseData>(payload).toUio()
                scope.launch {
                    addFolder(folder)
                }
            }
            "updated" -> {
                val folder = json.decodeFromString<FolderResponseData>(payload).toUio()
                scope.launch {
                    updateFolder(folder)
                }
            }
            "deleted" -> {
                val folderUuid = json.decodeFromString<JsonObject>(payload)["uuid"]?.jsonPrimitive?.contentOrNull
                if (folderUuid != null) {
                    scope.launch {
                        removeFolder(folderUuid)
                    }
                }
            }
        }
    }

    fun didReceiveReactionEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val reaction = json.decodeFromString<MessageReaction>(payload).toUio()
                scope.launch {
                    addReaction(reaction)
                }
            }
            "deleted" -> {
                val reaction = json.decodeFromString<DeletedMessageReaction>(payload)
                scope.launch {
                    deleteReaction(reaction)
                }
            }
        }
    }

    fun didReceiveStreamEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val stream = json.decodeFromString<Stream>(payload).toUio()
                scope.launch {
                    addStream(stream)
                }
            }
            "updated" -> {
                val stream = json.decodeFromString<Stream>(payload).toUio()
                scope.launch {
                    updateStream(stream)
                }
            }
            "deleted" -> {
                json.decodeFromString<JsonObject>(payload)["uuid"]?.jsonPrimitive?.contentOrNull?.let(::removeStream)
            }
        }
    }

    fun didReceiveTopicEvent(payload: String, action: String, epochVersion: Long? = null, generation: String? = null) {
        onPushRefreshNeeded()
        when(action) {
            "created" -> {
                val topic = json.decodeFromString<TopicsResponseData>(payload).toUio()
                scope.launch {
                    addTopic(topic)
                }
            }
            "updated" -> {
                val topic = json.decodeFromString<TopicsResponseData>(payload).toUio()
                scope.launch {
                    updateTopic(topic)
                    if (epochVersion != null && generation != null) onPushTopicUpdated(
                        topic,
                        epochVersion,
                        generation
                    )
                }
            }
            "deleted" -> {

            }
        }
    }

    fun startLoader() {
        if (streamDao.countObjects() > 0 && topicDao.countObjects() >0 && lastMessageDao.countObjects() > 0) {
            _additionalQueryState.value = QueryState.Loading
            _streamsQueryState.value = QueryState.Success
        } else {
            _streamsQueryState.value = QueryState.Loading
        }
    }

    fun loadServerSettings() {
        scope.launch {
            val webSocketClient = client
            startLoader()
            val response = webSocketClient.performRequest(
                ServerSettingsRequest(
                    config.baseUrl,
                ),
                serverId,
            )
            when (response) {
                is ApiResult.Success -> {
                    jitsiServerUrl = response.value.meetUrl
                    loadUserInfo()
                }

                is ApiResult.Error -> {
                    _streamsQueryState.value = QueryState.Error("")
                    _additionalQueryState.value = QueryState.Error("")
                }
            }
        }
    }

    suspend fun loadUserInfo() {
        val webSocketClient = client ?: return
        val userState = webSocketClient.requireUserViewModel()
        val captured = userState.getServer(serverId)?.copy() ?: return
        val token = userState.getTokens(serverId)?.accessToken?.takeIf { it.isNotBlank() } ?: return
        var authentication = ProtectedRequestAuthentication(token)
        val verificationRevision = ownerVerificationRevision
        fun current() = ownerVerificationRevision == verificationRevision &&
            verifiedOwnerRequestStillCurrent(captured, userState.servers.value.firstOrNull { it.id == serverId }, authentication,
                userState.getTokens(serverId)?.accessToken)
        if (!current()) return
        var response = webSocketClient.performRequest(authentication.pin(OwnUserRequest()), serverId, captured)
        // Preserve the existing one-refresh /me recovery, while recording the credential that actually verified the owner.
        if (response is ApiResult.Error && response.error.httpStatusCode == 401) {
            if (!current()) return
            when (val refresh = webSocketClient.refreshSession(serverId, token)) {
                is ApiResult.Success -> {
                    authentication = ProtectedRequestAuthentication(refresh.value.accessToken)
                    if (!current()) return
                    response = webSocketClient.performRequest(authentication.pin(OwnUserRequest()), serverId, captured)
                    if (!current()) return
                    if (response is ApiResult.Error && response.error.httpStatusCode == 401)
                        userState.repo.setNeedsToReloginIfTokensCurrent(serverId, refresh.value)
                }
                is ApiResult.Error -> response = refresh
            }
        }
        when(response) {
            is ApiResult.Success -> {
                val confirmed = userState.repo.withCurrentSession(captured, authentication) {
                    synchronized(realtimeStateLock) {
                        if (!current()) false else {
                            tokenStore.rememberConfirmedOwner(captured, response.value.uuid)
                            updateCurrentUser(response.value)
                            _verifiedOwnerAuthentication.value = VerifiedOwnerAuthentication(serverId, captured.baseUrl,
                                response.value.uuid, authentication, ++ownerVerificationRevision, captured.copy(), ownerVerificationLineage)
                            true
                        }
                    }
                }
                if (confirmed != true) return
                loadMessageReactions(response.value.uuid)
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadMessageReactions(userUuid: String) {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(MessageReactionsRequest(userUuid), serverId)
        when(response) {
            is ApiResult.Success -> {
                setInitialMessageReactions(response.value)
                loadAllUsersInfo()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadAllUsersInfo() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(UsersRequest(), serverId)
        when(response) {
            is ApiResult.Success -> {
                setInitialUsers(response.value)
                loadFolders()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }
    suspend fun loadFolders() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(FoldersRequest(), serverId)
        when(response) {
            is ApiResult.Success -> {
                val sortedFolders = response.value.sortedBy { WorkspaceDateTime.parse(it.creationDate) }
                setInitialFolders(sortedFolders)
                if (!sortedFolders.isEmpty()) {
                    _currentlySelectedFolder.value = sortedFolders.first()
                }
                loadStreamBindings()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadStreamBindings() {
        val response = client.performRequest(StreamBindingsRequest(), serverId)
        when(response) {
            is ApiResult.Success -> {
                setInitialStreamBindings(response.value)
                loadSubscribedChannels()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadSubscribedChannels() {
        val response = client.performRequest(StreamsRequest(), serverId)
        when(response) {
            is ApiResult.Success -> {
                loadTopics(response.value)
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadTopics(streams: List<StreamUIO>) {
        val response = client.performRequest(TopicsRequest(), serverId)
        when(response) {
            is ApiResult.Success -> {
                loadLastMessages(streams, response.value)
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Error("")
                _additionalQueryState.value = QueryState.Error("")
            }
        }
    }

    suspend fun loadLastMessages(streams: List<StreamUIO>, topics: List<TopicUIO>) {

        val streamMessageIds = streams.mapNotNull { it.lastMessageUuid }
        val topicMessageIds = topics.mapNotNull { it.lastMessageUuid }
        val messageIds = streamMessageIds + topicMessageIds
        if (!messageIds.isEmpty()) {
            for (messageIdsBatch in messageIds.chunked(100)) {
                when (val messagesResponse = client.performRequest(
                    MessagesByIdsRequest(messageIdsBatch),
                    serverId,
                )) {
                    is ApiResult.Success -> {
                        addLastMessages(messagesResponse.value.map { it.toLastMessageUIO() })
                    }
                    is ApiResult.Error -> {
                        _streamsQueryState.value = QueryState.Error("")
                        _additionalQueryState.value = QueryState.Error("")
                    }
                }
            }
        }
        setInitialStreams(streams)
        setInitialTopics(topics)
        loadDrafts()
    }

    suspend fun loadDrafts() {
        val webSocketClient = client ?: return
        val response = webSocketClient.performRequest(DraftsRequest(), serverId)
        when(response) {
            is ApiResult.Success -> {
                setInitialDrafts(response.value)
                _streamsQueryState.value = QueryState.Success
                _additionalQueryState.value = QueryState.Success
                start()
            }

            is ApiResult.Error -> {
                _streamsQueryState.value = QueryState.Success
                _additionalQueryState.value = QueryState.Success
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

internal fun isExpiredRealtimeCursorFrame(frame: JsonObject): Boolean =
    frame["code"]?.jsonPrimitive?.intOrNull == EVENTS_CURSOR_EXPIRED_STATUS_CODE &&
        frame["error"]?.jsonPrimitive?.contentOrNull == "epoch_pruned"

@Serializable
data class PongMessage(
    val type: String,
    val ts: String
)

@Serializable
private data class CreatedStreamBindingsEvent(val uuid: String, val items: List<StreamBindingResponseData>)

@Serializable
private data class DeletedStreamBindingEvent(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("user_uuid") val userUuid: String,
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

/** In-memory evidence from a successful existing /me request; never persisted with message caches. */
data class VerifiedOwnerAuthentication(val serverId: String, val baseUrl: String, val userUuid: String,
                                       val authentication: ProtectedRequestAuthentication, val revision: Long,
                                       val capturedServer: ServerConfig? = null, val lineage: Any = Any())

internal fun verifiedOwnerRequestStillCurrent(captured: ServerConfig, current: ServerConfig?,
                                               authentication: ProtectedRequestAuthentication, token: String?): Boolean =
    captured == current && authentication.matchesAccessToken(token)
