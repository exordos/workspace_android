package ru.genesiscorporation.workspace.beta.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.genesiscorporation.workspace.beta.DisplayRecipient
import ru.genesiscorporation.workspace.beta.MessageDto
import ru.genesiscorporation.workspace.beta.data.remote.dto.CustomProfileField
import ru.genesiscorporation.workspace.beta.data.remote.dto.Presense
import ru.genesiscorporation.workspace.beta.data.remote.dto.PresenseAggregated
import ru.genesiscorporation.workspace.beta.data.remote.dto.UnreadMessages
import ru.genesiscorporation.workspace.beta.data.remote.dto.UnreadPrivateMessage
import ru.genesiscorporation.workspace.beta.data.remote.dto.UnreadStreamMessage
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersResponseData


class EventsRepository {

    private val _queueId = MutableStateFlow<String?>(null)
    val queueId: StateFlow<String?> = _queueId.asStateFlow()
    fun updateQueueId(newQueueid: String) {
        _queueId.update {
            newQueueid
        }
    }
    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()
    fun updateMessages(newList: List<MessageDto>) {
        _messages.update {
            newList
        }
    }
    private val _users = MutableStateFlow<List<UsersResponseData>>(emptyList())
    val users: StateFlow<List<UsersResponseData>> = _users.asStateFlow()
    fun updateUsers(newList: List<UsersResponseData>) {
        _users.update {
            newList
        }
    }

    private val _unreadMessages = MutableStateFlow<UnreadMessages>(UnreadMessages(emptyList(), emptyList()))
    val unreadMessages: StateFlow<UnreadMessages> = _unreadMessages.asStateFlow()

    fun updateUnreadMessages(newUnreadMessages: UnreadMessages) {
        _unreadMessages.update {
            newUnreadMessages
        }
    }

    fun didReadChannelMessages(messageIds: List<Int>, streamId: Int, topicName: String) {
        val streamsUnreadMessages = _unreadMessages.value.streams
        val filteredStreamsUnreadMessages = streamsUnreadMessages.map { stream ->
            if (stream.streamId == streamId && stream.topic == topicName) {
                val filteredStreamUnreadIds = stream.unreadMessageIds.filter { !messageIds.contains(it) }
                stream.copy(
                    unreadMessageIds = filteredStreamUnreadIds
                )
            } else {
                stream
            }
        }
        _unreadMessages.update { current ->
            UnreadMessages(current.pms, filteredStreamsUnreadMessages)
        }
    }

    fun didReadDirectMessages(messageIds: List<Int>, userId: Int) {
        val directUnreadMessages = _unreadMessages.value.pms
        val filteredDirectUnreadMessages = directUnreadMessages.map { pm ->
            if (pm.otherUserId == userId) {
                val filteredStreamUnreadIds = pm.unreadMessageIds.filter { !messageIds.contains(it) }
                pm.copy(
                    unreadMessageIds = filteredStreamUnreadIds
                )
            } else {
                pm
            }
        }
        _unreadMessages.update { current ->
            UnreadMessages(filteredDirectUnreadMessages, current.streams)
        }
    }

    fun updateUnreadsForNewMessages(newMessages: List<MessageDto>, currentUserId: Int) {
        for (message in newMessages) {
            when (val displayRecipient = message.displayRecipient) {
                is DisplayRecipient.Users -> {
                    val filteredRecipients = displayRecipient.value.filter {
                        it.id != currentUserId
                    }
                    val firstRecipient = filteredRecipients.first()
                    val directUnreadMessages = _unreadMessages.value.pms
                    var chatFound = false
                    var filteredDirectUnreadMessages = directUnreadMessages.map { pm ->
                        if (pm.otherUserId == firstRecipient.id) {
                            chatFound = true
                            val updatedUnreadMessageIds = pm.unreadMessageIds + message.id
                            pm.copy(
                                unreadMessageIds = updatedUnreadMessageIds
                            )
                        } else {
                            pm
                        }
                    }
                    if (!chatFound) {
                        filteredDirectUnreadMessages += UnreadPrivateMessage(firstRecipient.id, listOf(message.id))
                    }
                    _unreadMessages.update { current ->
                        UnreadMessages(filteredDirectUnreadMessages, current.streams)
                    }
                }
                is DisplayRecipient.StreamName -> {
                    val streamsUnreadMessages = _unreadMessages.value.streams
                    var chatFound = false
                    var filteredStreamsUnreadMessages = streamsUnreadMessages.map { stream ->
                        if (stream.streamId == message.streamId && stream.topic == message.subject) {
                            chatFound = true
                            val updatedUnreadMessageIds = stream.unreadMessageIds + message.id
                            stream.copy(
                                unreadMessageIds = updatedUnreadMessageIds
                            )
                        } else {
                            stream
                        }
                    }
                    val streamId = message.streamId
                    if (!chatFound && streamId != null) {
                        filteredStreamsUnreadMessages += UnreadStreamMessage(streamId, message.subject, listOf(message.id))
                    }
                    _unreadMessages.update { current ->
                        UnreadMessages(current.pms, filteredStreamsUnreadMessages)
                    }
                }
            }
        }
    }

    private val _presences = MutableStateFlow<Map<String, Presense>>(emptyMap())
    val presences: StateFlow<Map<String, Presense>> = _presences.asStateFlow()
    fun updatePresenses(newMap: Map<String, Presense>) {
        _presences.update {
            current -> current + newMap
        }
    }


    private val _newPresences = MutableStateFlow<List<FlatPresense>>(emptyList())
    val newPresences: StateFlow<List<FlatPresense>> = _newPresences.asStateFlow()
    fun updateNewPresenses(newPresenses: List<FlatPresense>) {
        _newPresences.update {
            newPresenses
        }
    }

    var pushId: String? = null

    var customProfileFields: List<CustomProfileField> = emptyList()
}

data class FlatPresense(
    val presense: Presense,
    val email: String
)