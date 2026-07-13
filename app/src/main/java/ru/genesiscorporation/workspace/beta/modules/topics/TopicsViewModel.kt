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
import ru.genesiscorporation.workspace.beta.modules.chatchannels.TopicHeader
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

//            repo.messages.collect { updated ->
//                processNewMessages(updated)
//            }
        }
    }

    fun updateUnreadCount(topicName: String, newLastMessage: MessageDto?) {


    }

    suspend fun loadTopics() {

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


}