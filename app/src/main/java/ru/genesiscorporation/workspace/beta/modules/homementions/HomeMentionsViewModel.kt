package ru.genesiscorporation.workspace.beta.modules.homementions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.ConfirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.confirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.ProtectedRequestAuthentication
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MentionedMessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import kotlin.collections.map
import java.util.concurrent.atomic.AtomicLong

class HomeMentionsViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    private val serverId = client.getCurrentServerId()
    val eventsRepository = eventsRepositoryStore.get(serverId) ?: error("Cannot get current event repository")
    val topicsPool: StateFlow<List<TopicsResponseData>> = eventsRepository.topicsPool

    private val serverConfig = client.requireUserViewModel().selectedServer.value?.copy()
    private var accountContext: ConfirmedAccountContext? = null
    private var accountAuthentication: ProtectedRequestAuthentication? = null
    private val queryGeneration = AtomicLong()
    private var reloadWhenCurrent = false
    private var observedServerId = client.requireUserViewModel().selectedServerId.value
    private var observedServer = client.requireUserViewModel().selectedServer.value?.copy()
    private var observedAuthentication = client.requireUserViewModel().getTokens(serverId)?.accessToken
        ?.takeIf { it.isNotBlank() }?.let(::ProtectedRequestAuthentication)

    fun isCurrentAccount(): Boolean {
        val context = accountContext ?: return false
        val authentication = accountAuthentication ?: return false
        return isContextCurrent(context, authentication)
    }

    private fun isSelectedServer(): Boolean {
        val selected = client.requireUserViewModel().selectedServer.value ?: return false
        val captured = serverConfig ?: return false
        return client.getCurrentServerId() == serverId && selected.id == serverId &&
            selected.baseUrl == captured.baseUrl && selected.projectUuid == captured.projectUuid &&
            !selected.needsToRelogin && !client.requireUserViewModel().getTokens(serverId)?.accessToken.isNullOrBlank()
    }

    private fun isContextCurrent(context: ConfirmedAccountContext, authentication: ProtectedRequestAuthentication): Boolean {
        val current = confirmedAccountContext(client, eventsRepository) ?: return false
        // A repeated /me with the same credential must not invalidate visible rows.
        return context.sameOwner(current) && context.serverConfig() == current.serverConfig() &&
            authentication.matchesAccessToken(client.requireUserViewModel().getTokens(serverId)?.accessToken)
    }

    private val _messagesQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val messagesQueryState: StateFlow<QueryState> = _messagesQueryState

    val users: StateFlow<List<UserResponseData>> = eventsRepository.users
    val streams: StateFlow<List<Stream>> = eventsRepository.streams

    private val _messages = MutableStateFlow<List<MessageResponse>>(emptyList())
    val messages: StateFlow<List<MessageResponse>> = _messages
    private val _topicLookupLoading = MutableStateFlow(false)
    val topicLookupLoading: StateFlow<Boolean> = _topicLookupLoading

    init {
        viewModelScope.launch {
            val user = client.requireUserViewModel()
            combine(listOf(
                user.selectedServerId.map { Unit }, user.selectedServer.map { Unit },
                user.accessToken.map { Unit }, eventsRepository.currentUser.map { Unit },
                eventsRepository.verifiedOwnerAuthentication.map { Unit },
                ConfirmedAccountContext.invalidations.map { Unit }
            )) { Unit }.collect {
                val selectedId = user.selectedServerId.value
                val selected = user.selectedServer.value
                val token = user.getTokens(serverId)?.accessToken?.takeIf { it.isNotBlank() }
                val sameAuthentication = if (token == null) observedAuthentication == null
                    else observedAuthentication?.matchesAccessToken(token) == true
                val identityChanged = selectedId != observedServerId || selected != observedServer || !sameAuthentication
                observedServerId = selectedId
                observedServer = selected?.copy()
                observedAuthentication = token?.let(::ProtectedRequestAuthentication)
                if (identityChanged || (accountContext != null && !isCurrentAccount())) {
                    discardMessages()
                }
                if (reloadWhenCurrent && isSelectedServer()) {
                    reloadWhenCurrent = false
                    viewModelScope.launch { loadMentionedMessages(retryChangedContext = false) }
                }
            }
        }
        viewModelScope.launch {
            loadMentionedMessages()
        }
    }

    suspend fun loadMentionedMessages() = loadMentionedMessages(retryChangedContext = true)

    private suspend fun loadMentionedMessages(retryChangedContext: Boolean) {
        if (accountContext != null && !isCurrentAccount()) discardMessages()
        reloadWhenCurrent = false
        val generation = queryGeneration.incrementAndGet()
        if (!isSelectedServer()) {
            discardMessages()
            return
        }
        var context = confirmedAccountContext(client, eventsRepository)
        if (context == null) {
            // /me also preserves the existing one-refresh recovery for expired credentials.
            eventsRepository.verifyCurrentAccount()
            if (generation != queryGeneration.get()) return
            if (!isSelectedServer()) {
                discardMessages()
                return
            }
            context = confirmedAccountContext(client, eventsRepository)
        }
        if (context == null) {
            _messagesQueryState.value = QueryState.Error("")
            return
        }
        val authentication = client.requireUserViewModel().getTokens(serverId)?.accessToken
            ?.takeIf { it.isNotBlank() }?.let(::ProtectedRequestAuthentication)
        if (authentication == null || !isContextCurrent(context, authentication)) {
            discardMessages()
            return
        }
        accountContext = context
        accountAuthentication = authentication
        if (_messages.value.isEmpty()) _messagesQueryState.value = QueryState.Loading
        val messagesResponse = client.performRequest(if (retryChangedContext) authentication.recoverSession(MentionedMessagesRequest())
            else authentication.pin(MentionedMessagesRequest()),
            serverId = serverId, serverConfig = context.serverConfig())
        if (generation != queryGeneration.get()) return
        if (!isContextCurrent(context, authentication)) {
            discardMessages()
            // Never use the old response. A bounded fresh load can recover an ordinary
            // credential change only after /me confirms the selected account.
            if (retryChangedContext) loadMentionedMessages(retryChangedContext = false)
            return
        }
        when(messagesResponse) {
            is ApiResult.Success -> {
                val messages = messagesResponse.value
                val directoryReady = eventsRepository.ensureCurrentAccountDirectory()
                if (generation != queryGeneration.get()) return
                if (!isContextCurrent(context, authentication)) {
                    discardMessages()
                    if (retryChangedContext) loadMentionedMessages(retryChangedContext = false)
                    return
                }
                if (!directoryReady) {
                    _messagesQueryState.value = QueryState.Error("")
                    return
                }
                val topicsResolved = resolveMissingTopics(messages, context, authentication, generation, retryChangedContext)
                if (generation != queryGeneration.get()) return
                if (!isContextCurrent(context, authentication)) {
                    discardMessages()
                    if (retryChangedContext) loadMentionedMessages(retryChangedContext = false)
                    return
                }
                if (!topicsResolved && messages.none { message -> topicsPool.value.any {
                        it.uuid == message.topicUuid && it.streamUuid == message.streamUuid
                    } }) {
                    _messagesQueryState.value = QueryState.Error("")
                    return
                }
                val messagesWithUser = messages.map { message ->
                    message.copy(user = users.value.firstOrNull { it.uuid == message.authorUuid })
                }
                _messages.value = messagesWithUser
                _messagesQueryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _messagesQueryState.value = QueryState.Error("")
            }
        }
    }
    suspend fun retryMissingTopics() {
        if (_topicLookupLoading.value) return
        val context = accountContext
        val authentication = accountAuthentication
        if (context == null || authentication == null || !isContextCurrent(context, authentication)) {
            discardMessages()
            return
        }
        val generation = queryGeneration.get()
        resolveMissingTopics(_messages.value, context, authentication, generation, recoverSession = true)
        if (generation != queryGeneration.get()) return
        if (!isContextCurrent(context, authentication)) {
            discardMessages()
            loadMentionedMessages(retryChangedContext = false)
        }
    }

    private suspend fun resolveMissingTopics(messages: List<MessageResponse>, context: ConfirmedAccountContext,
                                             authentication: ProtectedRequestAuthentication, generation: Long, recoverSession: Boolean): Boolean {
        val missingTopicIds = messages.filter { message ->
            message.topicUuid.isNotBlank() && topicsPool.value.none {
                it.uuid == message.topicUuid && it.streamUuid == message.streamUuid
            }
        }.map { it.topicUuid }.distinct()
        if (missingTopicIds.isEmpty()) return true
        val topicScope = eventsRepository.captureTopicCacheScope()
        _topicLookupLoading.value = true
        try {
            val response = client.performRequest(if (recoverSession) authentication.recoverSession(TopicsByIdsRequest(missingTopicIds))
                else authentication.pin(TopicsByIdsRequest(missingTopicIds)),
                serverId = serverId, serverConfig = context.serverConfig())
            if (generation != queryGeneration.get() || !isContextCurrent(context, authentication)) return false
            return when (response) {
                is ApiResult.Success -> {
                    eventsRepository.appendItemsToTopicsPool(response.value.filter { topic ->
                        topic.uuid in missingTopicIds && messages.any {
                            it.topicUuid == topic.uuid && it.streamUuid == topic.streamUuid
                        }
                    }, topicScope)
                }
                is ApiResult.Error -> false
            }
        } finally { if (generation == queryGeneration.get()) _topicLookupLoading.value = false }
    }

    private fun discardMessages() {
        reloadWhenCurrent = true
        queryGeneration.incrementAndGet()
        _topicLookupLoading.value = false
        accountContext = null
        accountAuthentication = null
        _messages.value = emptyList()
        _messagesQueryState.value = QueryState.Idle
    }

}
