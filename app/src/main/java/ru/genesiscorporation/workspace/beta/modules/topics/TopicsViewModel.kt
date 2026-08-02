package ru.genesiscorporation.workspace.beta.modules.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.modules.chatchannels.TopicHeader
import ru.genesiscorporation.workspace.beta.modules.chatchannels.orderTopicsForDisplay
import ru.genesiscorporation.workspace.beta.modules.chatdialog.forwardTopicLabel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.CreateTopicRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MarkTopicReadRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RenameTopicRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ToggleTopicDoneRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicNotificationsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import java.util.concurrent.atomic.AtomicLong

internal object TopicActionKind {
    const val CREATE = "create"
    const val RENAME = "rename"
    const val MARK_READ = "mark_read"
    const val TOGGLE_DONE = "toggle_done"
    const val NOTIFICATIONS = "notifications"
}

data class TopicActionResult(
    val requestId: Long,
    val kind: String,
    val topicUuid: String?,
    val success: Boolean,
)

class TopicsViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val channelName: String,
    val channelStreamId: String,
    private val repo: EventsRepository
): ViewModel() {

    val subscriptions: StateFlow<List<TopicHeader>> = repo.streamTopics
        .map { topicsByStream ->
            topicsByStream[channelStreamId]
                .orEmpty()
                .let(::orderTopicsForDisplay)
                .map { topic ->
                    TopicHeader.from(
                        topic = topic,
                        channelName = channelName,
                        channelId = channelStreamId,
                        lastMessage = topic.lastMessage,
                        displayTitle = forwardTopicLabel(
                            topic,
                            topicsByStream[channelStreamId].orEmpty(),
                        ),
                    )
                }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    private val _state = MutableStateFlow<QueryState>(QueryState.Idle)
    val state: StateFlow<QueryState> = _state
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError
    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress
    private val _lastActionResult = MutableStateFlow<TopicActionResult?>(null)
    val lastActionResult: StateFlow<TopicActionResult?> = _lastActionResult
    private val actionMutex = Mutex()
    private val nextActionRequestId = AtomicLong()

    var currentTopicName: String = ""

    init {
        viewModelScope.launch {
            loadTopics()
        }
    }


    suspend fun loadTopics() {
        _state.value = QueryState.Loading
        when (val topicsResponse = client.performRequest(TopicsRequest(channelStreamId))) {
            is ApiResult.Success -> {
                val topics = topicsResponse.value
                val messageIds = topics.mapNotNull { it.lastMessageUuid }
                val messages = if (messageIds.isEmpty()) {
                    emptyList()
                } else {
                    when (
                        val messagesResponse =
                            client.performRequest(MessagesByIdsRequest(messageIds))
                    ) {
                        is ApiResult.Success -> messagesResponse.value
                        is ApiResult.Error -> emptyList()
                    }
                }
                if (messages.isNotEmpty()) {
                    repo.updateMessagesPool(messages)
                }
                val messagesByUuid = messages.associateBy { it.uuid }
                val topicsWithMessages = topics.map { topic ->
                    topic.apply {
                        lastMessage = lastMessageUuid?.let(messagesByUuid::get)
                    }
                }
                repo.addStreamTopics(channelStreamId, topicsWithMessages)
                _state.value = QueryState.Success
            }

            is ApiResult.Error -> {
                _state.value = QueryState.Error(
                    topicsResponse.error.message ?: "Не удалось загрузить топики",
                )
            }
        }
    }

    fun retry() {
        if (_state.value is QueryState.Loading) return
        viewModelScope.launch { loadTopics() }
    }

    fun topic(topicUuid: String): TopicsResponseData? =
        repo.streamTopics.value[channelStreamId]
            .orEmpty()
            .firstOrNull { it.uuid == topicUuid }

    fun createTopic(name: String): Long = launchTopicAction(
        kind = TopicActionKind.CREATE,
        topicUuid = null,
    ) {
        createTopicInternal(name)
    }

    private suspend fun createTopicInternal(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _actionError.value = "Введите название топика"
            return false
        }
        return runTopicAction("Не удалось создать топик") {
            when (
                val response = client.performRequest(
                    CreateTopicRequest(normalizedName, channelStreamId),
                )
            ) {
                is ApiResult.Success -> {
                    if (repo.streamTopics.value[channelStreamId] == null) {
                        repo.addStreamTopics(channelStreamId, listOf(response.value))
                    } else {
                        repo.addTopicToStream(response.value)
                    }
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                    false
                }
            }
        }
    }

    fun renameTopic(
        topic: TopicsResponseData,
        name: String,
    ): Long = launchTopicAction(
        kind = TopicActionKind.RENAME,
        topicUuid = topic.uuid,
    ) {
        renameTopicInternal(topic, name)
    }

    private suspend fun renameTopicInternal(
        topic: TopicsResponseData,
        name: String,
    ): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _actionError.value = "Введите название топика"
            return false
        }
        if (normalizedName == topic.name) return true
        return updateTopic(
            fallbackError = "Не удалось переименовать топик",
            request = {
                client.performRequest(
                    RenameTopicRequest(topic.uuid, normalizedName),
                )
            },
        )
    }

    fun markTopicRead(topic: TopicsResponseData): Long = launchTopicAction(
        kind = TopicActionKind.MARK_READ,
        topicUuid = topic.uuid,
    ) {
        updateTopic("Не удалось отметить топик прочитанным") {
            client.performRequest(MarkTopicReadRequest(topic.uuid))
        }
    }

    fun toggleTopicDone(topic: TopicsResponseData): Long = launchTopicAction(
        kind = TopicActionKind.TOGGLE_DONE,
        topicUuid = topic.uuid,
    ) {
        updateTopic(
            if (topic.isDone) {
                "Не удалось вернуть топик в работу"
            } else {
                "Не удалось завершить топик"
            },
        ) {
            client.performRequest(ToggleTopicDoneRequest(topic.uuid))
        }
    }

    fun setTopicNotificationMode(
        topic: TopicsResponseData,
        notificationMode: String,
    ): Long = launchTopicAction(
        kind = TopicActionKind.NOTIFICATIONS,
        topicUuid = topic.uuid,
    ) {
        setTopicNotificationModeInternal(topic, notificationMode)
    }

    private suspend fun setTopicNotificationModeInternal(
        topic: TopicsResponseData,
        notificationMode: String,
    ): Boolean {
        if (notificationMode !in setOf("mute", "default", "unmute", "follow")) {
            _actionError.value = "Неизвестный режим уведомлений"
            return false
        }
        if (topic.notificationMode == notificationMode) return true
        return updateTopic("Не удалось изменить уведомления топика") {
            client.performRequest(
                TopicNotificationsRequest(topic.uuid, notificationMode),
            )
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    private fun launchTopicAction(
        kind: String,
        topicUuid: String?,
        action: suspend () -> Boolean,
    ): Long {
        val requestId = nextActionRequestId.incrementAndGet()
        viewModelScope.launch {
            _lastActionResult.value = TopicActionResult(
                requestId = requestId,
                kind = kind,
                topicUuid = topicUuid,
                success = action(),
            )
        }
        return requestId
    }

    private suspend fun updateTopic(
        fallbackError: String,
        request: suspend () -> ApiResult<
            TopicsResponseData,
            ru.genesiscorporation.workspace.beta.data.remote.ApiError
        >,
    ): Boolean = runTopicAction(fallbackError) {
        when (val response = request()) {
            is ApiResult.Success -> {
                repo.updateTopic(response.value)
                true
            }

            is ApiResult.Error -> {
                _actionError.value = response.error.message
                false
            }
        }
    }

    private suspend fun runTopicAction(
        fallbackError: String,
        action: suspend () -> Boolean,
    ): Boolean {
        if (!actionMutex.tryLock()) return false
        _actionInProgress.value = true
        _actionError.value = null
        return try {
            action().also { success ->
                if (!success && _actionError.value.isNullOrBlank()) {
                    _actionError.value = fallbackError
                }
            }
        } finally {
            _actionInProgress.value = false
            actionMutex.unlock()
        }
    }

}
