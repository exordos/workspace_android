package ru.genesiscorporation.workspace.beta.data

import android.util.Log
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.plus


class EventsRepository() {

    var client: WorkspaceAPIClient? = null
    var latestEpoch: Int = 0
    var epochGeneration: String = ""

    fun resetRealtimeCursor() {
        latestEpoch = 0
        epochGeneration = ""
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    var currentUser: UserResponseData? = null

    fun resetAccountState() {
        currentUser = null
        jitsiServerUrl = ""
        resetRealtimeCursor()
        _streamTopicMessages.value = emptyMap()
        _streamTopics.value = emptyMap()
        _messagesPool.value = emptyList()
        _userReactions.value = emptyList()
        _users.value = emptyList()
        _streams.value = emptyList()
        _folders.value = emptyList()
    }


    private val _streamTopicMessages = MutableStateFlow<Map<String, List<MessageResponse>>>(emptyMap())
    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = _streamTopicMessages.asStateFlow()

    fun addStreamTopicMessages(streamUuid: String, topicUuid: String, messages: List<MessageResponse>) {
        val messagesWithUser = messages.map { message ->
            message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
            message
        }
        val key = "$streamUuid.$topicUuid"
        _streamTopicMessages.update { current ->
            current + (key to mergeMessages(current[key].orEmpty(), messagesWithUser))
        }
    }

    fun addMessageToStreamTopic(message: MessageResponse) {
        val key = "${message.streamUuid}.${message.topicUuid}"
        message.user = users.value.firstOrNull { it.uuid == message.authorUuid }

        _streamTopicMessages.update { current ->
            current + (key to mergeMessages(current[key].orEmpty(), listOf(message)))
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

    fun updateMessageContent(
        streamUuid: String,
        topicUuid: String,
        messageUuid: String,
        content: String
    ) {
        val key = "$streamUuid.$topicUuid"
        _streamTopicMessages.update { current ->
            val messages = current[key] ?: return@update current
            current + (
                key to messages.map { message ->
                    if (message.uuid == messageUuid) {
                        message.copy(payload = message.payload.copy(content = content))
                    } else {
                        message
                    }
                }
            )
        }
    }

    fun replaceMessage(messageUuid: String, replacement: MessageResponse) {
        val key = "${replacement.streamUuid}.${replacement.topicUuid}"
        var confirmedMessage = replacement
        _streamTopicMessages.update { current ->
            val messages = current[key].orEmpty()
            confirmedMessage = messages.firstOrNull { it.uuid == replacement.uuid } ?: replacement
            val withoutTemporaryAndDuplicate = messages.filterNot {
                it.uuid == messageUuid || it.uuid == replacement.uuid
            }
            current + (key to (withoutTemporaryAndDuplicate + confirmedMessage))
        }
        updateMessagesPool(listOf(confirmedMessage))
    }

    fun removeMessage(streamUuid: String, topicUuid: String, messageUuid: String) {
        val key = "$streamUuid.$topicUuid"
        _streamTopicMessages.update { current ->
            val messages = current[key] ?: return@update current
            current + (key to messages.filterNot { it.uuid == messageUuid })
        }
        _messagesPool.update { current ->
            current.filterNot { it.uuid == messageUuid }
        }
        clearDeletedMessagePreviews(messageUuid)
    }

    fun removeMessageEverywhere(messageUuid: String) {
        _streamTopicMessages.update { current ->
            current.mapValues { (_, messages) ->
                messages.filterNot { it.uuid == messageUuid }
            }
        }
        _messagesPool.update { current ->
            current.filterNot { it.uuid == messageUuid }
        }
        clearDeletedMessagePreviews(messageUuid)
    }

    private fun clearDeletedMessagePreviews(messageUuid: String) {
        _streamTopics.update { current ->
            current.mapValues { (_, topics) ->
                topics.map { topic ->
                    if (topic.lastMessageUuid == messageUuid) {
                        topic.copy(
                            lastMessageUuid = null,
                            lastMessage = null,
                        )
                    } else {
                        topic
                    }
                }
            }
        }
        _streams.update { current ->
            current.map { stream ->
                if (stream.lastMessageUuid == messageUuid) {
                    stream.copy(
                        lastMessageUuid = null,
                        lastMessage = null,
                    )
                } else {
                    stream
                }
            }
        }
    }

    private fun mergeMessages(
        existing: List<MessageResponse>,
        incoming: List<MessageResponse>
    ): List<MessageResponse> {
        val merged = LinkedHashMap<String, MessageResponse>()
        existing.forEach { message ->
            merged[message.uuid] = message
        }
        incoming.forEach { message ->
            val current = merged[message.uuid]
            if (
                current == null ||
                messageUpdatedAt(message) >= messageUpdatedAt(current)
            ) {
                merged[message.uuid] = message
            }
        }
        return merged.values.toList()
    }

    private fun messageUpdatedAt(message: MessageResponse): Instant =
        runCatching { OffsetDateTime.parse(message.updatedAt).toInstant() }
            .getOrDefault(Instant.EPOCH)

    private val _streamTopics = MutableStateFlow<Map<String, List<TopicsResponseData>>>(emptyMap())
    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = _streamTopics.asStateFlow()

    fun addStreamTopics(streamUuid: String, topics: List<TopicsResponseData>) {
        _streamTopics.update { current ->
            val merged = LinkedHashMap<String, TopicsResponseData>()
            current[streamUuid].orEmpty().forEach { merged[it.uuid] = it }
            topics.forEach { incoming ->
                val existing = merged[incoming.uuid]
                if (
                    existing == null ||
                    topicUpdatedAt(incoming) >= topicUpdatedAt(existing)
                ) {
                    merged[incoming.uuid] = incoming
                }
            }
            current + (streamUuid to merged.values.toList())
        }
    }

    /**
     * Applies the authoritative all-topics catalog used by Inbox.
     * The endpoint returns every visible topic, so retaining absent rows here
     * would keep stale unread badges forever.
     */
    fun replaceAllStreamTopics(
        streamUuids: Set<String>,
        topics: List<TopicsResponseData>,
    ) {
        _streamTopics.value = buildMap {
            streamUuids.forEach { put(it, emptyList()) }
            topics
                .filter { it.streamUuid in streamUuids }
                .groupBy(TopicsResponseData::streamUuid)
                .forEach { (streamUuid, streamTopics) ->
                    put(streamUuid, streamTopics)
                }
        }
    }

    private fun topicUpdatedAt(topic: TopicsResponseData): Instant =
        runCatching { OffsetDateTime.parse(topic.updatedAt).toInstant() }
            .getOrDefault(Instant.EPOCH)
    fun addTopicToStream(topic: TopicsResponseData) {
        _streamTopics.update { current ->
            if (current[topic.streamUuid] != null) {
                val existingTopics = current[topic.streamUuid].orEmpty()
                current + (
                    topic.streamUuid to (
                        existingTopics.filterNot { it.uuid == topic.uuid } + topic
                    )
                )
            } else {
                current
            }
        }
    }

    fun updateTopic(updatedTopic: TopicsResponseData) {
        val updatedLastMessage = messagesPool.value
            .firstOrNull { it.uuid == updatedTopic.lastMessageUuid }
        var unreadDelta = 0
        _streamTopics.update { current ->
            val topics = current[updatedTopic.streamUuid] ?: return@update current
            val updatedTopics = topics.map { topic ->
                if (topic.uuid == updatedTopic.uuid) {
                    unreadDelta = updatedTopic.unreadCount - topic.unreadCount
                    topic.copy(
                        unreadCount = updatedTopic.unreadCount,
                        name = updatedTopic.name,
                        updatedAt = updatedTopic.updatedAt,
                        lastMessageUuid = updatedTopic.lastMessageUuid,
                        isDone = updatedTopic.isDone,
                        notificationMode = updatedTopic.notificationMode,
                        lastMessage = when {
                            updatedLastMessage != null -> updatedLastMessage
                            topic.lastMessageUuid == updatedTopic.lastMessageUuid ->
                                topic.lastMessage
                            else -> null
                        },
                    )
                } else {
                    topic
                }
            }

            if (updatedTopics == topics) return@update current
            current + (updatedTopic.streamUuid to updatedTopics)
        }
        if (unreadDelta != 0) {
            updateStreamUnreadProjection(updatedTopic.streamUuid, unreadDelta)
        }
    }

    private fun updateStreamUnreadProjection(
        streamUuid: String,
        delta: Int,
    ) {
        var projectedUnreadCount: Int? = null
        _streams.update { current ->
            current.map { stream ->
                if (stream.uuid == streamUuid) {
                    val updatedCount = (stream.unreadCount + delta).coerceAtLeast(0)
                    projectedUnreadCount = updatedCount
                    stream.copy(unreadCount = updatedCount)
                } else {
                    stream
                }
            }
        }
        val streamUnreadCount = projectedUnreadCount ?: return
        _folders.update { current ->
            current.map { folder ->
                if (folder.items.none { it.streamUuid == streamUuid }) {
                    folder
                } else {
                    val updatedItems = folder.items.map { item ->
                        if (item.streamUuid == streamUuid) {
                            item.copy(unreadCount = streamUnreadCount)
                        } else {
                            item
                        }
                    }
                    folder.copy(
                        items = updatedItems,
                        unreadCount = updatedItems.sumOf { it.unreadCount },
                    )
                }
            }
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
        val messagesWithUser = newList.map { message ->
            message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
            message
        }
        _messagesPool.update { current ->
            mergeMessages(current, messagesWithUser)
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
        val user = currentUser
        if (user != null) {
            if (reaction.userUuid == user.uuid) {
                _userReactions.update { current ->
                    current.filterNot { it.uuid == reaction.uuid } + reaction
                }
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

    fun removeUser(userUuid: String) {
        _users.update { current ->
            current.filterNot { it.uuid == userUuid }
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
                        name = updatedStream.name,
                        description = updatedStream.description,
                        isPrivate = updatedStream.isPrivate,
                        color = updatedStream.color ?: stream.color,
                        owner = updatedStream.owner ?: stream.owner,
                        userUuid = updatedStream.userUuid ?: stream.userUuid,
                        role = updatedStream.role,
                        notificationMode = updatedStream.notificationMode,
                        isArchived = updatedStream.isArchived,
                        inviteOnly = updatedStream.inviteOnly,
                        announce = updatedStream.announce,
                        defaultTopicUuid =
                            updatedStream.defaultTopicUuid ?: stream.defaultTopicUuid,
                        directUserUuid =
                            updatedStream.directUserUuid ?: stream.directUserUuid,
                        sourceName = updatedStream.sourceName,
                        lastMessageUuid = updatedStream.lastMessageUuid,
                        unreadCount = updatedStream.unreadCount,
                        lastMessage = message
                    )
                } else {
                    stream
                }
            }
        }
        _folders.update { current ->
            current.map { folder ->
                if (folder.items.none { it.streamUuid == updatedStream.uuid }) {
                    folder
                } else {
                    val updatedItems = folder.items.map { item ->
                        if (item.streamUuid == updatedStream.uuid) {
                            item.copy(unreadCount = updatedStream.unreadCount)
                        } else {
                            item
                        }
                    }
                    folder.copy(
                        items = updatedItems,
                        unreadCount = updatedItems.sumOf { it.unreadCount },
                    )
                }
            }
        }
    }

    fun addStream(newStream: Stream) {
        _streams.update { current ->
            current.filterNot { it.uuid == newStream.uuid } + newStream
        }
    }

    fun setInitialStreams(newList: List<Stream>) {
        _streams.update {
            newList
        }
    }

    fun removeStream(streamUuid: String) {
        _streams.update { current ->
            current.filterNot { it.uuid == streamUuid }
        }
        _streamTopics.update { current -> current - streamUuid }
        _streamTopicMessages.update { current ->
            current.filterKeys { key -> !key.startsWith("$streamUuid.") }
        }
        _folders.update { current ->
            current.map { folder ->
                val remainingItems = folder.items.filterNot { it.streamUuid == streamUuid }
                folder.copy(
                    items = remainingItems,
                    unreadCount = remainingItems.sumOf { it.unreadCount },
                )
            }
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
            current.filterNot { it.uuid == newFolder.uuid } + newFolder
        }
    }

    fun setInitialFolders(newList: List<FolderResponseData>) {
        _folders.update {
            newList
        }
    }

    fun removeFolder(folderUuid: String) {
        _folders.update { current ->
            current.filterNot { it.uuid == folderUuid }
        }
    }

    fun removeFolderItem(folderItemUuid: String) {
        _folders.update { current ->
            current.map { folder ->
                val remainingItems = folder.items.filterNot { it.uuid == folderItemUuid }
                folder.copy(
                    items = remainingItems,
                    unreadCount = remainingItems.sumOf { it.unreadCount },
                )
            }
        }
    }

    fun removeTopic(topicUuid: String) {
        _streamTopics.update { current ->
            current.mapValues { (_, topics) ->
                topics.filterNot { it.uuid == topicUuid }
            }
        }
        _streamTopicMessages.update { current ->
            current.filterKeys { key -> !key.endsWith(".$topicUuid") }
        }
    }

    suspend fun start() {
        val webSocketClient = client
        if (webSocketClient == null) return

        var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
        while (currentCoroutineContext().isActive) {
            if (epochGeneration.isBlank()) {
                when (val response = webSocketClient.performRequest(EpochRequest())) {
                    is ApiResult.Success -> {
                        latestEpoch = response.value.epochVersion
                        epochGeneration = response.value.epochGeneration
                    }
                    is ApiResult.Error -> {
                        Log.d("WebSocket", "Failed to load the initial event cursor")
                        delay(retryDelayMillis)
                        retryDelayMillis = (retryDelayMillis * 2)
                            .coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                        continue
                    }
                }
            }

            try {
                startWebsocketConnection(webSocketClient)
                retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Log.d(
                    "WebSocket",
                    "Connection failed: ${exception::class.simpleName}"
                )
            }

            if (currentCoroutineContext().isActive) {
                delay(retryDelayMillis)
                retryDelayMillis = (retryDelayMillis * 2)
                    .coerceAtMost(MAX_RETRY_DELAY_MILLIS)
            }
        }
    }

    suspend fun startWebsocketConnection(webSocketClient: WorkspaceAPIClient) {
        val accessToken = webSocketClient.userViewModel.accessToken.value
        val baseUrl = webSocketClient.userViewModel.baseUrl.value
        if (accessToken != null && baseUrl != null) {
            val baseUri = URI(baseUrl)
            webSocketClient.client.webSocket(
                request = {
                    url {
                        protocol = if (baseUri.scheme.equals("https", ignoreCase = true)) {
                            URLProtocol.WSS
                        } else {
                            URLProtocol.WS
                        }
                        host = requireNotNull(baseUri.host) {
                            "Workspace base URL must contain a host"
                        }
                        if (baseUri.port != -1) port = baseUri.port
                        path("/api/workspace/v1/events/ws")
                        parameters.append("last_epoch_version", latestEpoch.toString())
                        parameters.append("epoch_generation", epochGeneration)
                    }
                    headers {
                        append(
                            HttpHeaders.SecWebSocketProtocol,
                            "workspace.events.v1, bearer.$accessToken"
                        )
                    }
                }
            ) {
                Log.d("WebSocket", "Connected")
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val receivedText = frame.readText()
                            try {
                                processTextFrame(receivedText)
                            } catch (exception: Exception) {
                                Log.d(
                                    "WebSocket",
                                    "Ignored invalid event: " +
                                        exception::class.simpleName
                                )
                            }
                        }
                        is Frame.Binary -> Log.d("WebSocket", "Received binary data bundle")
                        is Frame.Close -> {
                            Log.d(
                                "WebSocket",
                                "Connection closing reason: ${frame.readReason()}"
                            )
                        }
                        else -> Log.d("WebSocket", "Received control or ping/pong frame")
                    }
                }
            }
        }
    }

    fun processTextFrame(receivedText: String) {
        val jsonObject = json.decodeFromString<JsonObject>(receivedText)
        val frameType = jsonObject["type"]?.jsonPrimitive?.contentOrNull
        if (frameType == "ready") {
            epochGeneration = jsonObject["epoch_generation"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: epochGeneration
            latestEpoch = maxOf(
                latestEpoch,
                jsonObject["epoch_version"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toIntOrNull()
                    ?: latestEpoch
            )
            return
        }

        val eventEpoch = jsonObject["epoch_version"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
        if (eventEpoch != null && eventEpoch <= latestEpoch) return

        val action = jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: return
        val payload = jsonObject["payload"]?.toString() ?: return
        when (jsonObject["object_type"]?.jsonPrimitive?.contentOrNull) {
            "message" -> didReceiveMessageEvent(payload, action)
            "user" -> didReceiveUserEvent(payload, action)
            "folder" -> didReceiveFolderEvent(payload, action)
            "folder_item" -> didReceiveFolderItemEvent(payload, action)
            "stream" -> didReceiveStreamEvent(payload, action)
            "topic" -> didReceiveTopicEvent(payload, action)
            "message_reaction" -> didReceiveReactionEvent(payload, action)
            else -> Log.d(
                "WebSocket",
                "Ignored unsupported event type: " +
                    jsonObject["object_type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
        if (eventEpoch != null) {
            latestEpoch = maxOf(latestEpoch, eventEpoch)
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
                updateUser(user)
            }
            "deleted" -> {
                removeUser(json.decodeFromString<DeletedObjectPayload>(payload).uuid)
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
                removeMessageEverywhere(
                    json.decodeFromString<DeletedObjectPayload>(payload).uuid,
                )
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
                removeFolder(json.decodeFromString<DeletedObjectPayload>(payload).uuid)
            }
        }
    }

    fun didReceiveFolderItemEvent(payload: String, action: String) {
        if (action == "deleted") {
            removeFolderItem(json.decodeFromString<DeletedObjectPayload>(payload).uuid)
        }
    }

    fun didReceiveReactionEvent(payload: String, action: String) {
        when(action) {
            "created", "updated" -> {
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
                removeStream(json.decodeFromString<DeletedObjectPayload>(payload).uuid)
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
                removeTopic(json.decodeFromString<DeletedObjectPayload>(payload).uuid)
            }
        }
    }

    var jitsiServerUrl: String = ""

    companion object {
        private const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
        private const val MAX_RETRY_DELAY_MILLIS = 30_000L
    }
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

@Serializable
data class DeletedObjectPayload(
    val uuid: String,
)
