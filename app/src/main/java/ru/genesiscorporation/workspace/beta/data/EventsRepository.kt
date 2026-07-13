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
import ru.genesiscorporation.workspace.beta.data.remote.dto.CustomProfileField
import ru.genesiscorporation.workspace.beta.data.remote.dto.EpochRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Presense
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData


class EventsRepository() {

    var client: WorkspaceAPIClient? = null
    var latestEpoch: Int = 0

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }


    private val _streamTopicMessages = MutableStateFlow<Map<String, List<MessageResponse>>>(emptyMap())
    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = _streamTopicMessages.asStateFlow()

    fun addStreamTopicMessages(streamUuid: String, topicUuid: String, messages: List<MessageResponse>) {
        val key = "$streamUuid.$topicUuid"
        _streamTopicMessages.update { current ->
            current + (key to messages)
        }
    }
    fun addMessageToStreamTopic(message: MessageResponse) {
        val key = "${message.streamUuid}.${message.topicUuid}"
        _streamTopicMessages.update { current ->
            if (current[key] != null ) {
                val existingTopics = current[key].orEmpty()
                current + (key to (existingTopics + message))
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
        _streamTopics.update { current ->
            val topics = current[updatedTopic.streamUuid] ?: return@update current
            val updatedTopics = topics.map { topic ->
                if (topic.uuid == updatedTopic.uuid) {
                    topic.copy(
                        unreadCount = updatedTopic.unreadCount,
                        name = updatedTopic.name,
                        lastMessageUuid = updatedTopic.lastMessageUuid,
                        isDone = updatedTopic.isDone
                    )
                } else {
                    topic
                }
            }

            if (updatedTopics == topics) return@update current
            current + (updatedTopic.streamUuid to updatedTopics)
        }
    }

    private val _messagesPool = MutableStateFlow<List<MessageResponse>>(emptyList())
    val messagesPool: StateFlow<List<MessageResponse>> = _messagesPool.asStateFlow()
    fun setInitialMessagesPool(newList: List<MessageResponse>) {
        _messagesPool.update {
            newList
        }
    }

    fun updateMessagesPool(newList: List<MessageResponse>) {
        _messagesPool.update { current ->
            current + newList
        }
    }

    private val _users = MutableStateFlow<List<UserResponseData>>(emptyList())
    val users: StateFlow<List<UserResponseData>> = _users.asStateFlow()
    fun updateUsers(newList: List<UserResponseData>) {
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
                        lastMessage = message
                    )
                } else {
                    stream
                }
            }
        }
    }

    fun addStream(newStream: Stream) {
        _streams.update { current ->
            current + newStream
        }
    }

    fun setInitialStreams(newList: List<Stream>) {
        _streams.update {
            newList
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

    private val _topics = MutableStateFlow<List<TopicsResponseData>>(emptyList())
    val topics: StateFlow<List<TopicsResponseData>> = _topics.asStateFlow()
    fun updateTopics(newList: List<TopicsResponseData>) {
        _topics.update {
            newList
        }
    }

    suspend fun start() {
        val webSocketClient = client
        if (webSocketClient != null) {
            val response = webSocketClient.performRequest(EpochRequest())
            when (response) {
                is ApiResult.Success -> {
                    latestEpoch = response.value.epochVersion
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
            try {
                webSocketClient.client.webSocket(
                    request = {
                        url {
                            protocol = URLProtocol.WS
                            this.host = baseUrl
                            path("/api/messenger/ws")
                            parameters.append("last_epoch_version", latestEpoch.toString())
                        }
                        headers {
                            append(HttpHeaders.SecWebSocketProtocol, "workspace.events.v1, bearer.$accessToken")
                        }
                    }
                ) {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val receivedText = frame.readText()
                                val jsonObject = json.decodeFromString<JsonObject>(receivedText)
                                val action = jsonObject["action"]?.jsonPrimitive?.contentOrNull
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
                                        else -> Log.d("WebSocket", "Received: $receivedText")
                                    }
                                }
                            }
                            is Frame.Binary -> Log.d("WebSocket", "Received binary data bundle")
                            is Frame.Close -> Log.d("WebSocket", "Connection closing reason: ${frame.readReason()}")
                            else -> Log.d("WebSocket", "Received control or ping/pong frame")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("WebSocket", "Failed: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun didReceiveUserEvent(payload: String, action: String) {
        when(action) {
            "created" -> {
                val user = json.decodeFromString<UserResponseData>(payload)
                updateUsers(listOf(user))
            }
            "updated" -> {
                val user = json.decodeFromString<UserResponseData>(payload)
                updateUsers(listOf(user))
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

    var customProfileFields: List<CustomProfileField> = emptyList()

    var jitsiServerUrl: String = ""
}

data class FlatPresense(
    val presense: Presense,
    val email: String
)

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