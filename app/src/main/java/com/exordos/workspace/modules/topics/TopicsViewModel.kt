package com.exordos.workspace.modules.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.modules.chatchannels.TopicHeader
import kotlin.collections.flatMap

class TopicsViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val channelName: String,
    val channelStreamId: String,
    private val eventsRepositoryStore: EventsRepositoryStore
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


    suspend fun loadTopics() {

    }



}
