package ru.genesiscorporation.workspace.beta.modules.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

class TopicsViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val channelName: String,
    val channelStreamId: String
): ViewModel() {

    var items: List<TopicHeader> = emptyList()
    private val _subscriptions = MutableStateFlow<List<TopicHeader>>(emptyList())
    val subscriptions: StateFlow<List<TopicHeader>> = _subscriptions

    init {
        viewModelScope.launch {
            loadTopics()
        }
    }

    suspend fun loadTopics() {
        val response = client.performRequest(TopicsRequest(channelStreamId))
        when(response) {
            is ApiResult.Success -> {
                _subscriptions.value = response.value.topics.map { TopicHeader.from(it, channelName, channelStreamId) }
            }
            is ApiResult.Error -> {

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
    val lastMessage: MessageData?
) {
    companion object {
        fun from(topic: TopicsResponseData, channelName: String, channelId: String) = TopicHeader(
            topic.name,
            null,
            channelName,
            channelId,
            null
        )
    }
}