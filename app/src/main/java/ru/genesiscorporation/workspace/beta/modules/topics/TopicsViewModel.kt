package ru.genesiscorporation.workspace.beta.modules.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.DisplayRecipient
import ru.genesiscorporation.workspace.beta.MessageDto
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UnreadMessages
import kotlin.collections.flatMap

class TopicsViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val channelName: String,
    val channelStreamId: String,
    private val repo: EventsRepository
): ViewModel() {

    private val _subscriptions = MutableStateFlow<List<TopicHeader>>(emptyList())
    val subscriptions: StateFlow<List<TopicHeader>> = _subscriptions

    var currentTopicName: String = ""

    init {
        viewModelScope.launch {
            loadTopics()

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

    fun updateUnreadCount(topicName: String, newLastMessage: MessageDto?) {

        _subscriptions.value = _subscriptions.value.map { header ->
            if (header.title == topicName) {
                header.copy(
                    lastMessage = newLastMessage
                )
            } else {
                header
            }
        }
    }

    suspend fun loadTopics() {
        val response = client.performRequest(TopicsRequest(channelStreamId))
        when(response) {
            is ApiResult.Success -> {
                val messageIds = response.value.topics.map { it.max_id }
                val messagesResponse = client.performRequest(MessagesByIdsRequest(messageIds))
                when(messagesResponse) {
                    is ApiResult.Success -> {
                        _subscriptions.value = response.value.topics.map { topic ->
                            val unreadMessagesCount: Int
                            val unreadChannelMessages = repo.unreadMessages.value.streams.filter { it.streamId.toString() == channelStreamId && it.topic == topic.name }
                            if (unreadChannelMessages.isNotEmpty()) {
                                unreadMessagesCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                            } else {
                                unreadMessagesCount = 0
                            }
                            val lastMessage  = messagesResponse.value.messages.firstOrNull { it.id == topic.max_id }
                            TopicHeader.from(topic, channelName, channelStreamId, lastMessage, unreadMessagesCount)
                        }
                    }
                    is ApiResult.Error -> {
                        _subscriptions.value = response.value.topics.map { topic ->
                            val unreadMessagesCount: Int
                            val unreadChannelMessages = repo.unreadMessages.value.streams.filter { it.streamId.toString() == channelStreamId && it.topic == topic.name }
                            if (unreadChannelMessages.isNotEmpty()) {
                                unreadMessagesCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                            } else {
                                unreadMessagesCount = 0
                            }
                            TopicHeader.from(topic, channelName, channelStreamId, null, unreadMessagesCount)
                        }
                    }
                }
            }
            is ApiResult.Error -> {

            }
        }
    }

    suspend fun processNewMessages(messages: List<MessageDto>) {
        for (message in messages) {
            val displayRecipient = message.displayRecipient
            if (displayRecipient is DisplayRecipient.StreamName) {
                if (displayRecipient.value == channelName) {
                    updateUnreadCount(displayRecipient.value, null)
                }
            }
        }
    }

    fun processUnreadMessages(unreadMessages: UnreadMessages) {
        _subscriptions.value = _subscriptions.value.map { header ->
            val unreadChannelMessages = unreadMessages.streams.filter { it.streamId.toString() == channelStreamId && it.topic == header.title }
            if (unreadChannelMessages.isNotEmpty()) {
                val channelUnreadMessageCount = unreadChannelMessages.flatMap { it.unreadMessageIds }.size
                header.copy(
                    unreadCount = channelUnreadMessageCount
                )
            } else {
                header
            }
        }
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