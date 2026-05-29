package ru.genesiscorporation.workspace.beta

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventRegistrationRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendFcmTokenRequest
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.FlatPresense
import ru.genesiscorporation.workspace.beta.data.remote.dto.Presense
import ru.genesiscorporation.workspace.beta.data.remote.dto.PresenseAggregated
import java.net.URL

class WorkspaceViewModel(
    val client: WorkspaceAPIClient,
    private val repo: EventsRepository
): ViewModel() {
    private var pollingJob: Job? = null

    private var messagesQueueId: String = ""
    private var lastEventId: Int = -1

    private val _currentCallMessage = MutableStateFlow<MessageDto?>(null)
    val currentCallMessage: StateFlow<MessageDto?> = _currentCallMessage

    init {
        viewModelScope.launch {
            repo.queueId.collect { updated ->
                if (updated != null) {
                    messagesQueueId = updated
                    startLongPolling()
                }
            }
        }
    }
    fun setCurrentCallMessage(callMessage: MessageDto?) {
        _currentCallMessage.value = callMessage
    }

    suspend fun registerForEvents() {
        val response = client.performRequest(EventRegistrationRequest("[\"messages\"]", null))
        when(response) {
            is ApiResult.Success -> {
                messagesQueueId = response.value.queueId
                lastEventId = -1
                repo.updatePresenses(response.value.presences)
            }
            is ApiResult.Error -> {
                stopLongPolling()
            }
        }
    }

    suspend fun sendToken(token: String) {
        client.performRequest(SendFcmTokenRequest(token))
    }

    suspend fun startLongPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val messagesResponse = client.performRequest(EventRequest(messagesQueueId, "${lastEventId}"))
                    when(messagesResponse) {
                        is ApiResult.Success -> {
                            val events = parseEvents(messagesResponse.value)
                            val messages = events
                                .filterIsInstance<MessageEvent>()
                                .filter { it.type == "message" }
                                .map { it.message }
                            val maxEventId = events.map { it.id }.maxOrNull()
                            if (maxEventId != null) {
                                lastEventId = maxEventId
                            }
                            val newMessages = messages.filter { !repo.messages.value.contains(it) }
                            val callMessage = newMessages.firstOrNull() {
                                Patterns.WEB_URL.matcher(it.content).matches() && URL(it.content).host == "meet.example.com"
                            }
                            if (callMessage != null && "${callMessage.senderId}" != (client.userViewModel.userId.value ?: -1) && _currentCallMessage.value == null) {
                                _currentCallMessage.value = callMessage
                            }
                            if (!newMessages.isEmpty()) {
                                repo.updateMessages(newMessages)
                                val userId = client.userViewModel.userId.value?.toInt()
                                if (userId != null) {
                                    repo.updateUnreadsForNewMessages(newMessages, userId)
                                }
                            }
                            val presenses = events
                                .filterIsInstance<PresenceEvent>()
                                .mapNotNull { event ->
                                    event.presence["website"]?.let { agg ->
                                        event.email to Presense(aggregated = agg)
                                    }
                                }
                                .toMap()
                            if (!presenses.isEmpty()) {
                                val flatPresenses = presenses.map {
                                    FlatPresense(it.value, it.key)
                                }
                                repo.updateNewPresenses(flatPresenses)
                                repo.updatePresenses(presenses)
                            }
                        }
                        is ApiResult.Error -> {
                            if (messagesResponse.error.code == "BAD_EVENT_QUEUE_ID") {
                                registerForEvents()
                            }
                        }
                    }
                    delay(10000)
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun parseEvents(responseText: String): List<Event> {
        val root = json.decodeFromString(EventsResponse.serializer(), responseText)
        return root.events.mapNotNull { raw ->
            val obj = raw.jsonObject
            when (obj["type"]?.toString()?.trim('"')) {
                "message" -> json.decodeFromJsonElement<MessageEvent>(raw)
                "presence" -> json.decodeFromJsonElement<PresenceEvent>(raw)
                else -> null
            }
        }
    }
    fun stopLongPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    override fun onCleared() {
        stopLongPolling()
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class EventsResponse(
    val events: List<JsonElement> = emptyList()
)

sealed interface Event {
    val id: Int
}

@Serializable
data class MessageEvent(
    override val id: Int,
    val type: String,
    val message: MessageDto,
    val flags: List<String> = emptyList()
) : Event
@Serializable
data class PresenceEvent(
    override val id: Int,
    val type: String,
    @SerialName("user_id") val userId: Long,
    val email: String,
    @SerialName("server_timestamp") val serverTimestamp: Double,
    val presence: Map<String, PresenseAggregated>
) : Event

@Serializable
data class MessageDto(
    val id: Int,
    @SerialName("sender_id") val senderId: Int,
    val content: String,
    @SerialName("recipient_id") val recipientId: Int,
    val timestamp: Long,
    val client: String,
    val subject: String = "",
    @SerialName("topic_links") val topicLinks: List<JsonElement> = emptyList(),
    @SerialName("is_me_message") val isMeMessage: Boolean = false,
    val reactions: List<JsonElement> = emptyList(),
    val submessages: List<JsonElement> = emptyList(),
    @SerialName("sender_full_name") val senderFullName: String,
    @SerialName("sender_email") val senderEmail: String,
    @SerialName("sender_realm_str") val senderRealmStr: String = "",
    @SerialName("display_recipient")
    val displayRecipient: DisplayRecipient,
    val type: String, // "private" or "stream" (message type)
    @SerialName("stream_id") val streamId: Int? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val flags: List<String> = emptyList()
)

@Serializable
data class RecipientUser(
    val id: Int,
    val email: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("is_mirror_dummy") val isMirrorDummy: Boolean
)

@Serializable(with = DisplayRecipientSerializer::class)
sealed interface DisplayRecipient {
    @Serializable(with = UsersAsArraySerializer::class)
    data class Users(val value: List<RecipientUser>) : DisplayRecipient
    @Serializable(with = StreamNameAsPrimitiveSerializer::class)
    data class StreamName(val value: String) : DisplayRecipient
}
object DisplayRecipientSerializer :
    JsonContentPolymorphicSerializer<DisplayRecipient>(DisplayRecipient::class) {
    override fun selectDeserializer(element: JsonElement) = when (element) {
        is JsonArray -> DisplayRecipient.Users.serializer()
        is JsonPrimitive -> DisplayRecipient.StreamName.serializer()
        else -> throw IllegalArgumentException("Unexpected display_recipient: $element")
    }
}
object UsersAsArraySerializer : KSerializer<DisplayRecipient.Users> {
    override val descriptor: SerialDescriptor =
        ListSerializer(RecipientUser.serializer()).descriptor
    override fun deserialize(decoder: Decoder): DisplayRecipient.Users {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val users = jsonDecoder.json.decodeFromJsonElement(
            ListSerializer(RecipientUser.serializer()),
            element
        )
        return DisplayRecipient.Users(users)
    }
    override fun serialize(encoder: Encoder, value: DisplayRecipient.Users) {
        val jsonEncoder = encoder as JsonEncoder
        val element = jsonEncoder.json.encodeToJsonElement(
            ListSerializer(RecipientUser.serializer()),
            value.value
        )
        jsonEncoder.encodeJsonElement(element)
    }
}
object StreamNameAsPrimitiveSerializer : KSerializer<DisplayRecipient.StreamName> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DisplayRecipient.StreamName", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): DisplayRecipient.StreamName {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        val name = (element as JsonPrimitive).content
        return DisplayRecipient.StreamName(name)
    }
    override fun serialize(encoder: Encoder, value: DisplayRecipient.StreamName) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
    }
}