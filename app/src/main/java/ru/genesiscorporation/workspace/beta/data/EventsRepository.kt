package ru.genesiscorporation.workspace.beta.data

import android.util.Log
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeletedMessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.EpochRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalExternalIntegrationUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalOperationResponse
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.plus

sealed interface MessageProjectionEvent {
    data class Upsert(
        val message: MessageResponse,
    ) : MessageProjectionEvent

    data class Read(
        val messageUuids: List<String>,
    ) : MessageProjectionEvent

    data class Deleted(
        val messageUuid: String,
    ) : MessageProjectionEvent
}

data class OwnedMessageProjectionEvent(
    val ownerKey: String,
    val sequence: Long,
    val event: MessageProjectionEvent,
)

data class InboxCatalogReference(
    val streams: List<Stream>,
    val topics: Map<String, List<TopicsResponseData>>,
)

class EventsRepository(
    private val cursorStore: RealtimeCursorStore =
        InMemoryRealtimeCursorStore(),
) {

    var client: WorkspaceAPIClient? = null
    var latestEpoch: Int = 0
    var epochGeneration: String = ""

    fun resetRealtimeCursor() {
        latestEpoch = 0
        epochGeneration = ""
    }

    private val _appForeground = MutableStateFlow(false)
    private val _realtimeConnectionState =
        MutableStateFlow(RealtimeConnectionState.PAUSED)
    val realtimeConnectionState: StateFlow<RealtimeConnectionState> =
        _realtimeConnectionState.asStateFlow()
    private val manualRealtimeReconnectRequests =
        Channel<Unit>(capacity = Channel.CONFLATED)
    private val realtimeControlLock = Any()
    private val _realtimeRecoveryVersion = MutableStateFlow(0L)
    val realtimeRecoveryVersion: StateFlow<Long> =
        _realtimeRecoveryVersion.asStateFlow()
    private val externalProjectionLock = Any()
    private val externalAccountRevisions =
        mutableMapOf<String, ExternalProjectionRevision>()
    private val externalChatRevisions =
        mutableMapOf<String, ExternalProjectionRevision>()
    private val externalOperationRevisions =
        mutableMapOf<String, ExternalProjectionRevision>()
    private val _externalAccounts =
        MutableStateFlow<List<ExternalAccountResponse>>(emptyList())
    val externalAccounts: StateFlow<List<ExternalAccountResponse>> =
        _externalAccounts.asStateFlow()
    private val _externalChats =
        MutableStateFlow<Map<String, List<ExternalChatResponse>>>(emptyMap())
    val externalChats:
        StateFlow<Map<String, List<ExternalChatResponse>>> =
            _externalChats.asStateFlow()
    private val _externalOperations =
        MutableStateFlow<Map<String, List<ExternalOperationResponse>>>(emptyMap())
    val externalOperations:
        StateFlow<Map<String, List<ExternalOperationResponse>>> =
            _externalOperations.asStateFlow()
    private val _messageProjectionEvents =
        MutableSharedFlow<OwnedMessageProjectionEvent>(
            extraBufferCapacity = MESSAGE_PROJECTION_EVENT_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val messageProjectionEvents: SharedFlow<OwnedMessageProjectionEvent> =
        _messageProjectionEvents
    private val messageProjectionSequence = AtomicLong(0L)

    fun setAppForeground(foreground: Boolean) {
        synchronized(realtimeControlLock) {
            if (!foreground) {
                clearPendingRealtimeReconnectRequests()
            }
            _appForeground.value = foreground
        }
    }

    fun pauseRealtimeForAuthentication() {
        synchronized(realtimeControlLock) {
            clearPendingRealtimeReconnectRequests()
            _realtimeConnectionState.value =
                RealtimeConnectionState.PAUSED
        }
    }

    fun requestRealtimeReconnect(): Boolean =
        synchronized(realtimeControlLock) {
            if (
                !shouldAcceptRealtimeReconnect(
                    state = _realtimeConnectionState.value,
                    appForeground = _appForeground.value,
                )
            ) {
                return@synchronized false
            }
            if (
                !_realtimeConnectionState.compareAndSet(
                    expect = RealtimeConnectionState.BACKING_OFF,
                    update = RealtimeConnectionState.CONNECTING,
                )
            ) {
                return@synchronized false
            }
            val accepted =
                manualRealtimeReconnectRequests.trySend(Unit).isSuccess
            if (!accepted) {
                _realtimeConnectionState.compareAndSet(
                    expect = RealtimeConnectionState.CONNECTING,
                    update = RealtimeConnectionState.BACKING_OFF,
                )
            }
            accepted
        }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    var currentUser: UserResponseData? = null
    private val catalogProjectionLock = Any()
    private var foldersProjectionInitialized = false
    private var usersProjectionInitialized = false
    private var streamBindingsProjectionInitialized = false

    private fun mutateStreams(
        transform: (List<Stream>) -> List<Stream>,
    ) {
        synchronized(catalogProjectionLock) {
            _streams.value = transform(_streams.value)
        }
    }

    private fun mutateStreamTopics(
        transform: (
            Map<String, List<TopicsResponseData>>,
        ) -> Map<String, List<TopicsResponseData>>,
    ) {
        synchronized(catalogProjectionLock) {
            _streamTopics.value = transform(_streamTopics.value)
        }
    }

    private fun mutateFolders(
        transform: (List<FolderResponseData>) -> List<FolderResponseData>,
    ) {
        synchronized(catalogProjectionLock) {
            foldersProjectionInitialized = true
            _folders.value = transform(_folders.value)
        }
    }

    private fun mutateUsers(
        transform: (List<UserResponseData>) -> List<UserResponseData>,
    ) {
        synchronized(catalogProjectionLock) {
            usersProjectionInitialized = true
            _users.value = transform(_users.value)
        }
    }

    private fun mutateStreamBindings(
        transform: (
            List<StreamBindingResponseData>,
        ) -> List<StreamBindingResponseData>,
    ) {
        synchronized(catalogProjectionLock) {
            streamBindingsProjectionInitialized = true
            _streamBindings.value = transform(_streamBindings.value)
        }
    }

    fun resetAccountState() {
        synchronized(realtimeControlLock) {
            clearPendingRealtimeReconnectRequests()
            _realtimeConnectionState.value =
                RealtimeConnectionState.PAUSED
        }
        currentUser = null
        jitsiServerUrl = ""
        resetRealtimeCursor()
        _streamTopicMessages.value = emptyMap()
        _conversationPagination.value = emptyMap()
        _messagesPool.value = emptyList()
        _userReactions.value = emptyList()
        synchronized(catalogProjectionLock) {
            _streamTopics.value = emptyMap()
            _streams.value = emptyList()
            foldersProjectionInitialized = false
            usersProjectionInitialized = false
            streamBindingsProjectionInitialized = false
            _folders.value = emptyList()
            _users.value = emptyList()
            _streamBindings.value = emptyList()
        }
        clearExternalProjections()
    }

    /**
     * Restores an encrypted owner-scoped offline snapshot without overwriting
     * newer REST, realtime, or local-outbox state that may already have
     * arrived while disk IO was in progress.
     */
    fun hydrateCachedSnapshot(snapshot: WorkspaceSnapshot) {
        synchronized(catalogProjectionLock) {
            _streams.value = mergeStreams(
                cached = snapshot.streams,
                current = _streams.value,
            )
            _streamTopics.value = _streamTopics.value.let { current ->
                val streamUuids =
                    (_streams.value.map(Stream::uuid) +
                        snapshot.topicsByStream.keys)
                        .toSet()
                buildMap {
                    streamUuids.forEach { streamUuid ->
                        val merged = LinkedHashMap<String, TopicsResponseData>()
                        snapshot.topicsByStream[streamUuid]
                            .orEmpty()
                            .forEach { merged[it.uuid] = it }
                        current[streamUuid].orEmpty().forEach { topic ->
                            val cached = merged[topic.uuid]
                            if (
                                cached == null ||
                                topicUpdatedAt(topic) >= topicUpdatedAt(cached)
                            ) {
                                merged[topic.uuid] = topic
                            }
                        }
                        if (merged.isNotEmpty()) {
                            put(streamUuid, merged.values.toList())
                        }
                    }
                }
            }
        }
        _streamTopicMessages.update { current ->
            buildMap {
                val keys =
                    snapshot.messagesByConversation.keys + current.keys
                keys.forEach { key ->
                    val merged = mergeMessages(
                        existing =
                            snapshot.messagesByConversation[key].orEmpty(),
                        incoming = current[key].orEmpty(),
                    )
                    if (merged.isNotEmpty()) put(key, merged)
                }
            }
        }
        _conversationPagination.update { current ->
            boundedConversationPagination(
                older = snapshot.paginationByConversation,
                newer = current,
            )
        }
        val cachedMessages = snapshot.messagesByConversation
            .values
            .flatten()
        _messagesPool.update { current ->
            mergeMessages(
                existing = cachedMessages,
                incoming = current,
            )
        }
        synchronized(catalogProjectionLock) {
            if (!foldersProjectionInitialized) {
                _folders.value = snapshot.folders
            }
            if (!usersProjectionInitialized) {
                _users.value = snapshot.users
            }
            if (!streamBindingsProjectionInitialized) {
                _streamBindings.value = snapshot.streamBindings
            }
        }
    }

    fun workspaceSnapshot(): WorkspaceSnapshot =
        synchronized(catalogProjectionLock) {
            WorkspaceSnapshot(
                streams = _streams.value,
                topicsByStream = _streamTopics.value,
                messagesByConversation = _streamTopicMessages.value,
                paginationByConversation =
                    _conversationPagination.value,
                folders = _folders.value,
                users = _users.value,
                streamBindings = _streamBindings.value,
            )
        }

    private fun invalidateDerivedStateForExpiredCursor() {
        currentUser = null
        jitsiServerUrl = ""
        resetRealtimeCursor()
        _streamTopicMessages.update { conversations ->
            conversations.mapValues { (_, messages) ->
                messages.filter { it.uuid.startsWith("local-") }
            }.filterValues(List<MessageResponse>::isNotEmpty)
        }
        _conversationPagination.value = emptyMap()
        _messagesPool.value = emptyList()
        _userReactions.value = emptyList()
        synchronized(catalogProjectionLock) {
            _streamTopics.value = emptyMap()
            _streams.value = emptyList()
            foldersProjectionInitialized = false
            usersProjectionInitialized = false
            streamBindingsProjectionInitialized = false
            _folders.value = emptyList()
            _users.value = emptyList()
            _streamBindings.value = emptyList()
        }
        clearExternalProjections()
        _realtimeRecoveryVersion.update { it + 1L }
    }

    private fun clearExternalProjections() {
        synchronized(externalProjectionLock) {
            externalAccountRevisions.clear()
            externalChatRevisions.clear()
            externalOperationRevisions.clear()
            _externalAccounts.value = emptyList()
            _externalChats.value = emptyMap()
            _externalOperations.value = emptyMap()
        }
    }

    internal fun mergeExternalAccountSnapshot(
        response: ExternalAccountResponse,
    ) {
        applyExternalAccountSnapshot(
            response = validateExternalAccountResponse(response).response,
            deleted = false,
        )
    }

    internal fun mergeExternalChatSnapshot(
        response: ExternalChatResponse,
    ) {
        applyExternalChatSnapshot(
            response = validateExternalChatResponse(response),
            deleted = false,
        )
    }

    internal fun mergeExternalOperationSnapshot(
        response: ExternalOperationResponse,
    ) {
        applyExternalOperationSnapshot(
            response = validateExternalOperationResponse(response),
            deleted = false,
        )
    }

    internal fun reconcileExternalAccountSnapshots(
        responses: List<ExternalAccountResponse>,
        baselineRevisions: Map<String, Int>,
    ) {
        val validated = responses.map {
            validateExternalAccountResponse(it).response
        }
        validated.forEach(::mergeExternalAccountSnapshot)
        val authoritativeUuids = validated
            .mapTo(mutableSetOf(), ExternalAccountResponse::uuid)
        _externalAccounts.value
            .filter { current ->
                current.uuid !in authoritativeUuids &&
                    baselineRevisions[current.uuid] == current.revision
            }
            .forEach(::removeExternalAccountSnapshot)
    }

    internal fun reconcileExternalChatSnapshots(
        externalAccountUuid: String,
        responses: List<ExternalChatResponse>,
        baselineRevisions: Map<String, Int>,
    ) {
        val canonicalAccountUuid =
            canonicalExternalIntegrationUuid(externalAccountUuid)
        val validated = responses.map {
            validateExternalChatResponse(
                response = it,
                expectedExternalAccountUuid = canonicalAccountUuid,
            )
        }
        validated.forEach(::mergeExternalChatSnapshot)
        val authoritativeUuids = validated
            .mapTo(mutableSetOf(), ExternalChatResponse::uuid)
        _externalChats.value[canonicalAccountUuid]
            .orEmpty()
            .filter { current ->
                current.uuid !in authoritativeUuids &&
                    baselineRevisions[current.uuid] == current.revision
            }
            .forEach(::removeExternalChatSnapshot)
    }

    internal fun reconcileExternalOperationSnapshots(
        externalAccountUuid: String,
        responses: List<ExternalOperationResponse>,
        baselineRevisions: Map<String, Int>,
    ) {
        val canonicalAccountUuid =
            canonicalExternalIntegrationUuid(externalAccountUuid)
        val validated = responses.map {
            validateExternalOperationResponse(
                response = it,
                expectedExternalAccountUuid = canonicalAccountUuid,
            )
        }
        validated.forEach(::mergeExternalOperationSnapshot)
        val authoritativeUuids = validated
            .mapTo(mutableSetOf(), ExternalOperationResponse::uuid)
        _externalOperations.value[canonicalAccountUuid]
            .orEmpty()
            .filter { current ->
                current.uuid !in authoritativeUuids &&
                    baselineRevisions[current.uuid] == current.revision
            }
            .forEach(::removeExternalOperationSnapshot)
    }

    internal fun removeExternalAccountSnapshot(
        response: ExternalAccountResponse,
    ) {
        applyExternalAccountSnapshot(
            response = validateExternalAccountResponse(response).response,
            deleted = true,
        )
    }

    internal fun removeExternalChatSnapshot(
        response: ExternalChatResponse,
    ) {
        applyExternalChatSnapshot(
            response = validateExternalChatResponse(response),
            deleted = true,
        )
    }

    internal fun removeExternalOperationSnapshot(
        response: ExternalOperationResponse,
    ) {
        applyExternalOperationSnapshot(
            response = validateExternalOperationResponse(response),
            deleted = true,
        )
    }

    private fun applyExternalAccountSnapshot(
        response: ExternalAccountResponse,
        deleted: Boolean,
    ) {
        val projectionStreamsToRemove = mutableSetOf<String>()
        synchronized(externalProjectionLock) {
            if (
                !recordExternalProjectionRevision(
                    revisions = externalAccountRevisions,
                    uuid = response.uuid,
                    revision = response.revision,
                    deleted = deleted,
                )
            ) {
                return
            }
            if (deleted) {
                _externalAccounts.update { accounts ->
                    accounts.filterNot { it.uuid == response.uuid }
                }
                _externalChats.update { chatsByAccount ->
                    chatsByAccount[response.uuid]
                        .orEmpty()
                        .mapNotNullTo(projectionStreamsToRemove) {
                            it.projectionStreamUuid
                        }
                    chatsByAccount - response.uuid
                }
                _externalOperations.update { operationsByAccount ->
                    operationsByAccount - response.uuid
                }
                _streams.value
                    .filter {
                        it.provider
                            ?.accountUuid
                            ?.equals(response.uuid, ignoreCase = true) == true
                    }
                    .mapTo(projectionStreamsToRemove, Stream::uuid)
            } else {
                _externalAccounts.update { accounts ->
                    accounts.filterNot { it.uuid == response.uuid } + response
                }
            }
        }
        projectionStreamsToRemove.forEach(::removeStream)
    }

    private fun applyExternalChatSnapshot(
        response: ExternalChatResponse,
        deleted: Boolean,
    ) {
        var projectionStreamToRemove: String? = null
        synchronized(externalProjectionLock) {
            val accountRevision =
                externalAccountRevisions[response.externalAccountUuid]
            if (accountRevision?.deleted == true) return
            if (
                !recordExternalProjectionRevision(
                    revisions = externalChatRevisions,
                    uuid = response.uuid,
                    revision = response.revision,
                    deleted = deleted,
                )
            ) {
                return
            }
            _externalChats.update { chatsByAccount ->
                val current = chatsByAccount[response.externalAccountUuid]
                    .orEmpty()
                    .filterNot { it.uuid == response.uuid }
                if (deleted) {
                    projectionStreamToRemove = response.projectionStreamUuid
                    if (current.isEmpty()) {
                        chatsByAccount - response.externalAccountUuid
                    } else {
                        chatsByAccount + (
                            response.externalAccountUuid to current
                        )
                    }
                } else {
                    chatsByAccount + (
                        response.externalAccountUuid to (current + response)
                    )
                }
            }
        }
        projectionStreamToRemove?.let(::removeStream)
    }

    private fun applyExternalOperationSnapshot(
        response: ExternalOperationResponse,
        deleted: Boolean,
    ) {
        synchronized(externalProjectionLock) {
            val accountRevision =
                externalAccountRevisions[response.externalAccountUuid]
            if (accountRevision?.deleted == true) return
            if (
                !recordExternalProjectionRevision(
                    revisions = externalOperationRevisions,
                    uuid = response.uuid,
                    revision = response.revision,
                    deleted = deleted,
                )
            ) {
                return
            }
            _externalOperations.update { operationsByAccount ->
                val current =
                    operationsByAccount[response.externalAccountUuid]
                        .orEmpty()
                        .filterNot { it.uuid == response.uuid }
                if (deleted) {
                    if (current.isEmpty()) {
                        operationsByAccount - response.externalAccountUuid
                    } else {
                        operationsByAccount + (
                            response.externalAccountUuid to current
                        )
                    }
                } else {
                    operationsByAccount + (
                        response.externalAccountUuid to (current + response)
                    )
                }
            }
        }
    }

    private fun recordExternalProjectionRevision(
        revisions: MutableMap<String, ExternalProjectionRevision>,
        uuid: String,
        revision: Int,
        deleted: Boolean,
    ): Boolean {
        val previous = revisions[uuid]
        if (previous != null) {
            if (revision < previous.revision) return false
            if (
                revision == previous.revision &&
                previous.deleted &&
                !deleted
            ) {
                return false
            }
        }
        revisions[uuid] = ExternalProjectionRevision(
            revision = revision,
            deleted = deleted,
        )
        return true
    }

    internal fun handleRealtimeConnectionClosed(closeCode: Int?): Boolean {
        if (closeCode != EVENTS_CURSOR_EXPIRED_CLOSE_CODE) return false
        invalidateDerivedStateForExpiredCursor()
        return true
    }


    private val _streamTopicMessages = MutableStateFlow<Map<String, List<MessageResponse>>>(emptyMap())
    val streamTopicMessages: StateFlow<Map<String, List<MessageResponse>>> = _streamTopicMessages.asStateFlow()
    private val _conversationPagination =
        MutableStateFlow<Map<String, ConversationPaginationState>>(emptyMap())
    val conversationPagination:
        StateFlow<Map<String, ConversationPaginationState>> =
            _conversationPagination.asStateFlow()

    fun updateConversationPagination(
        state: ConversationPaginationState,
    ) {
        val key = conversationKey(
            state.streamUuid,
            state.topicUuid,
        )
        _conversationPagination.update { current ->
            boundedConversationPagination(
                older = current,
                newer = mapOf(key to state),
            )
        }
    }

    fun removeConversationPagination(
        streamUuid: String,
        topicUuid: String,
    ) {
        val key = conversationKey(streamUuid, topicUuid)
        _conversationPagination.update { current -> current - key }
    }

    private fun boundedConversationPagination(
        older: Map<String, ConversationPaginationState>,
        newer: Map<String, ConversationPaginationState>,
    ): Map<String, ConversationPaginationState> {
        val merged = LinkedHashMap<String, ConversationPaginationState>()
        sequenceOf(older, newer).forEach { source ->
            source.forEach { (key, state) ->
                merged.remove(key)
                merged[key] = state
            }
        }
        while (merged.size > MAX_CACHED_CONVERSATIONS) {
            merged.remove(merged.keys.first())
        }
        return merged
    }

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

    fun replaceStreamTopicMessages(
        streamUuid: String,
        topicUuid: String,
        messages: List<MessageResponse>,
    ) {
        val messagesWithUser = messages.map { message ->
            message.user = users.value.firstOrNull { it.uuid == message.authorUuid }
            message
        }
        val key = "$streamUuid.$topicUuid"
        _streamTopicMessages.update { current ->
            val incomingUuids = messagesWithUser
                .mapTo(mutableSetOf(), MessageResponse::uuid)
            val retained = current[key]
                .orEmpty()
                .filter {
                    it.uuid.startsWith("local-") || it.uuid in incomingUuids
                }
            current + (key to mergeMessages(retained, messagesWithUser))
        }
    }

    fun markStreamTopicMessagesReadThrough(
        streamUuid: String,
        topicUuid: String,
        boundaryUuid: String,
    ): List<String> {
        val messages = _streamTopicMessages.value["$streamUuid.$topicUuid"]
            .orEmpty()
        val boundary = messages.singleOrNull { it.uuid == boundaryUuid }
            ?: return emptyList()
        val boundaryPosition = repositoryMessagePosition(boundary)
            ?: return emptyList()
        val messageUuids = messages.mapNotNull { message ->
            val position = repositoryMessagePosition(message)
                ?: return@mapNotNull null
            message.uuid.takeIf {
                !message.read &&
                    !message.isOwn &&
                    compareRepositoryMessagePositions(
                        position,
                        boundaryPosition,
                    ) <= 0
            }
        }
        markMessagesRead(messageUuids)
        return messageUuids
    }

    fun markMessagesRead(messageUuids: Collection<String>) {
        val targetUuids = messageUuids.toSet()
        if (targetUuids.isEmpty()) return
        var newlyReadMessages =
            emptyMap<String, Pair<String, String>>()
        _streamTopicMessages.update { current ->
            val changed = mutableMapOf<String, Pair<String, String>>()
            val updated = current.mapValues { (_, messages) ->
                messages.map { message ->
                    if (message.uuid in targetUuids && !message.read) {
                        changed[message.uuid] =
                            message.streamUuid to message.topicUuid
                        message.copy(read = true)
                    } else {
                        message
                    }
                }
            }
            // MutableStateFlow may re-run this transform after a concurrent
            // update. Keep only the rows changed by the successful attempt so
            // duplicate realtime frames cannot decrement badges twice.
            newlyReadMessages = changed
            updated
        }
        _messagesPool.update { current ->
            current.map { message ->
                if (message.uuid in targetUuids && !message.read) {
                    message.copy(read = true)
                } else {
                    message
                }
            }
        }
        newlyReadMessages.values
            .groupingBy { it }
            .eachCount()
            .forEach { (conversation, count) ->
            decrementTopicUnreadProjection(
                streamUuid = conversation.first,
                topicUuid = conversation.second,
                count = count,
            )
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
        updatedMessage.user =
            users.value.firstOrNull { it.uuid == updatedMessage.authorUuid }
        _streamTopicMessages.update { current ->
            val messages = current[key] ?: return@update current
            current + (key to mergeMessages(messages, listOf(updatedMessage)))
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
        mutateStreamTopics { current ->
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
        mutateStreams { current ->
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
                // Workspace exposes read-only read actions: once this client
                // has confirmed a row as read, an in-flight older page must
                // not resurrect it as unread while still being allowed to
                // deliver newer content or reaction fields.
                merged[message.uuid] =
                    if (current?.read == true && !message.read) {
                        message.copy(read = true)
                    } else {
                        message
                    }
            }
        }
        return merged.values.toList()
    }

    private fun messageUpdatedAt(message: MessageResponse): Instant =
        runCatching { OffsetDateTime.parse(message.updatedAt).toInstant() }
            .getOrDefault(Instant.EPOCH)

    private fun mergeStreams(
        cached: List<Stream>,
        current: List<Stream>,
    ): List<Stream> {
        val merged = LinkedHashMap<String, Stream>()
        cached.forEach { stream -> merged[stream.uuid] = stream }
        current.forEach { stream ->
            val restored = merged[stream.uuid]
            if (
                restored == null ||
                streamUpdatedAt(stream) >= streamUpdatedAt(restored)
            ) {
                merged[stream.uuid] = stream
            }
        }
        return merged.values.toList()
    }

    private fun streamUpdatedAt(stream: Stream): Instant =
        runCatching { OffsetDateTime.parse(stream.updatedAt).toInstant() }
            .getOrDefault(Instant.EPOCH)

    private val _streamTopics = MutableStateFlow<Map<String, List<TopicsResponseData>>>(emptyMap())
    val streamTopics: StateFlow<Map<String, List<TopicsResponseData>>> = _streamTopics.asStateFlow()

    fun addStreamTopics(streamUuid: String, topics: List<TopicsResponseData>) {
        mutateStreamTopics { current ->
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
        mutateStreamTopics {
            buildInboxTopics(
                streamUuids = streamUuids,
                topics = topics,
            )
        }
    }

    /**
     * Commits the two authoritative Inbox projections as one compare-and-set.
     * Every stream/topic mutation participates in [catalogProjectionLock], so
     * realtime activity after a REST request began makes this return false
     * instead of being silently overwritten by the older response.
     */
    fun applyInboxCatalogIfUnchanged(
        expected: InboxCatalogReference,
        streams: List<Stream>,
        topics: List<TopicsResponseData>,
    ): Boolean = synchronized(catalogProjectionLock) {
        if (
            _streams.value !== expected.streams ||
            _streamTopics.value !== expected.topics
        ) {
            false
        } else {
            _streams.value = streams
            _streamTopics.value = buildInboxTopics(
                streamUuids = streams.mapTo(mutableSetOf(), Stream::uuid),
                topics = topics,
            )
            true
        }
    }

    fun inboxCatalogReference(): InboxCatalogReference =
        synchronized(catalogProjectionLock) {
            InboxCatalogReference(
                streams = _streams.value,
                topics = _streamTopics.value,
            )
        }

    private fun buildInboxTopics(
        streamUuids: Set<String>,
        topics: List<TopicsResponseData>,
    ): Map<String, List<TopicsResponseData>> = buildMap {
        streamUuids.forEach { put(it, emptyList()) }
        topics
            .filter { it.streamUuid in streamUuids }
            .groupBy(TopicsResponseData::streamUuid)
            .forEach { (streamUuid, streamTopics) ->
                put(streamUuid, streamTopics)
            }
    }

    private fun topicUpdatedAt(topic: TopicsResponseData): Instant =
        runCatching { OffsetDateTime.parse(topic.updatedAt).toInstant() }
            .getOrDefault(Instant.EPOCH)
    fun addTopicToStream(topic: TopicsResponseData) {
        mutateStreamTopics { current ->
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
        mutateStreamTopics { current ->
            val topics =
                current[updatedTopic.streamUuid]
                    ?: return@mutateStreamTopics current
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

            if (updatedTopics == topics) return@mutateStreamTopics current
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
        mutateStreams { current ->
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
        mutateFolders { current ->
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

    private fun decrementTopicUnreadProjection(
        streamUuid: String,
        topicUuid: String,
        count: Int,
    ) {
        if (count <= 0) return
        var appliedDelta = 0
        mutateStreamTopics { current ->
            val topics =
                current[streamUuid] ?: return@mutateStreamTopics current
            val updatedTopics = topics.map { topic ->
                if (topic.uuid == topicUuid) {
                    val updatedCount =
                        (topic.unreadCount - count).coerceAtLeast(0)
                    appliedDelta = updatedCount - topic.unreadCount
                    topic.copy(unreadCount = updatedCount)
                } else {
                    topic
                }
            }
            current + (streamUuid to updatedTopics)
        }
        if (appliedDelta != 0) {
            updateStreamUnreadProjection(streamUuid, appliedDelta)
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
        mutateUsers { current ->
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
        mutateUsers { current ->
            current + newUser
        }
    }

    fun setInitialUsers(newList: List<UserResponseData>) {
        mutateUsers {
            newList
        }
    }

    fun removeUser(userUuid: String) {
        mutateUsers { current ->
            current.filterNot { it.uuid == userUuid }
        }
        mutateStreamBindings { current ->
            current.filterNot { it.userUuid == userUuid }
        }
    }

    private val _streamBindings =
        MutableStateFlow<List<StreamBindingResponseData>>(emptyList())
    val streamBindings: StateFlow<List<StreamBindingResponseData>> =
        _streamBindings.asStateFlow()

    fun setInitialStreamBindings(
        newList: List<StreamBindingResponseData>,
    ) {
        mutateStreamBindings { newList }
    }

    fun replaceStreamBindings(
        streamUuid: String,
        newList: List<StreamBindingResponseData>,
    ) {
        mutateStreamBindings { current ->
            current.filterNot { it.streamUuid == streamUuid } +
                newList.filter { it.streamUuid == streamUuid }
        }
    }

    fun addStreamBindings(
        newBindings: List<StreamBindingResponseData>,
    ) {
        if (newBindings.isEmpty()) return
        val replacementUuids = newBindings.mapTo(mutableSetOf()) { it.uuid }
        mutateStreamBindings { current ->
            current.filterNot { it.uuid in replacementUuids } + newBindings
        }
    }

    fun removeStreamBinding(bindingUuid: String) {
        mutateStreamBindings { current ->
            current.filterNot { it.uuid == bindingUuid }
        }
    }

    private val _streams = MutableStateFlow<List<Stream>>(emptyList())
    val streams: StateFlow<List<Stream>> = _streams.asStateFlow()

    fun updateStream(updatedStream: Stream) {
        val message = messagesPool.value.firstOrNull { it.uuid == updatedStream.lastMessageUuid }
        mutateStreams { current ->
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
        mutateFolders { current ->
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
        mutateStreams { current ->
            current.filterNot { it.uuid == newStream.uuid } + newStream
        }
    }

    fun setInitialStreams(newList: List<Stream>) {
        mutateStreams { newList }
    }

    fun removeStream(streamUuid: String) {
        synchronized(catalogProjectionLock) {
            _streams.value =
                _streams.value.filterNot { it.uuid == streamUuid }
            _streamTopics.value = _streamTopics.value - streamUuid
        }
        _streamTopicMessages.update { current ->
            current.filterKeys { key -> !key.startsWith("$streamUuid.") }
        }
        _conversationPagination.update { current ->
            current.filterKeys { key ->
                !key.startsWith("$streamUuid.")
            }
        }
        mutateStreamBindings { current ->
            current.filterNot { it.streamUuid == streamUuid }
        }
        mutateFolders { current ->
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
        mutateFolders { current ->
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
        mutateFolders { current ->
            current.filterNot { it.uuid == newFolder.uuid } + newFolder
        }
    }

    fun setInitialFolders(newList: List<FolderResponseData>) {
        mutateFolders {
            newList
        }
    }

    fun removeFolder(folderUuid: String) {
        mutateFolders { current ->
            current.filterNot { it.uuid == folderUuid }
        }
    }

    fun removeFolderItem(folderItemUuid: String) {
        mutateFolders { current ->
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
        mutateStreamTopics { current ->
            current.mapValues { (_, topics) ->
                topics.filterNot { it.uuid == topicUuid }
            }
        }
        _streamTopicMessages.update { current ->
            current.filterKeys { key -> !key.endsWith(".$topicUuid") }
        }
        _conversationPagination.update { current ->
            current.filterKeys { key ->
                !key.endsWith(".$topicUuid")
            }
        }
    }

    suspend fun start(ownerKey: String) {
        require(ownerKey.isNotBlank()) {
            "Realtime account owner must not be blank"
        }
        _appForeground
            .collectLatest { foreground ->
                if (foreground) {
                    runForegroundConnectionLoop(ownerKey)
                } else {
                    _realtimeConnectionState.value =
                        RealtimeConnectionState.PAUSED
                    Log.d("WebSocket", "Paused while the app is in background")
                }
            }
    }

    private suspend fun runForegroundConnectionLoop(ownerKey: String) {
        val webSocketClient = client
        if (webSocketClient == null) return

        var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
        var consecutiveExpiredCursors = 0
        try {
            while (currentCoroutineContext().isActive) {
                if (
                    !webSocketClient.userViewModel.repo
                        .isActiveCredentialOwner(ownerKey)
                ) {
                    return
                }
                _realtimeConnectionState.value =
                    RealtimeConnectionState.CONNECTING
                if (
                    !ensureRealtimeCursor(
                        webSocketClient = webSocketClient,
                        ownerKey = ownerKey,
                    )
                ) {
                    if (
                        !webSocketClient.userViewModel.repo
                            .isActiveCredentialOwner(ownerKey)
                    ) {
                        return
                    }
                    Log.d("WebSocket", "Failed to load the initial event cursor")
                    _realtimeConnectionState.value =
                        RealtimeConnectionState.BACKING_OFF
                    awaitRealtimeRetry(retryDelayMillis)
                    retryDelayMillis = nextRealtimeRetryDelay(
                        currentDelayMillis = retryDelayMillis,
                        readyReceived = false,
                        connectedDurationMillis = 0L,
                    )
                    continue
                }

                when (
                    catchUpRealtimeEvents(
                        webSocketClient = webSocketClient,
                        ownerKey = ownerKey,
                    )
                ) {
                    RealtimeCatchUpResult.COMPLETE -> {
                        consecutiveExpiredCursors = 0
                    }

                    RealtimeCatchUpResult.CURSOR_EXPIRED -> {
                        consecutiveExpiredCursors += 1
                        if (consecutiveExpiredCursors > 1) {
                            _realtimeConnectionState.value =
                                RealtimeConnectionState.BACKING_OFF
                            awaitRealtimeRetry(retryDelayMillis)
                            retryDelayMillis = nextRealtimeRetryDelay(
                                currentDelayMillis = retryDelayMillis,
                                readyReceived = false,
                                connectedDurationMillis = 0L,
                            )
                        } else {
                            retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                        }
                        continue
                    }

                    RealtimeCatchUpResult.RETRY -> {
                        if (
                            !webSocketClient.userViewModel.repo
                                .isActiveCredentialOwner(ownerKey)
                        ) {
                            return
                        }
                        _realtimeConnectionState.value =
                            RealtimeConnectionState.BACKING_OFF
                        awaitRealtimeRetry(retryDelayMillis)
                        retryDelayMillis = nextRealtimeRetryDelay(
                            currentDelayMillis = retryDelayMillis,
                            readyReceived = false,
                            connectedDurationMillis = 0L,
                        )
                        continue
                    }
                }

                val connectionResult = try {
                    _realtimeConnectionState.value =
                        RealtimeConnectionState.CONNECTING
                    startWebsocketConnection(
                        webSocketClient = webSocketClient,
                        ownerKey = ownerKey,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    Log.d(
                        "WebSocket",
                        "Connection failed: ${exception::class.simpleName}"
                    )
                    RealtimeConnectionResult()
                }
                if (
                    !webSocketClient.userViewModel.repo
                        .isActiveCredentialOwner(ownerKey)
                ) {
                    return
                }

                if (handleRealtimeConnectionClosed(connectionResult.closeCode)) {
                    Log.d("WebSocket", "Realtime cursor expired; refreshing snapshots")
                    runCatching {
                        cursorStore.clearAccount(ownerKey)
                    }.onFailure {
                        Log.d(
                            "WebSocket",
                            "Failed to clear an expired event cursor",
                        )
                    }
                    retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                }

                if (currentCoroutineContext().isActive) {
                    if (
                        connectionResult.readyReceived &&
                        connectionResult.connectedDurationMillis >=
                            STABLE_CONNECTION_MILLIS
                    ) {
                        retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                    }
                    _realtimeConnectionState.value =
                        RealtimeConnectionState.BACKING_OFF
                    awaitRealtimeRetry(retryDelayMillis)
                    retryDelayMillis = nextRealtimeRetryDelay(
                        currentDelayMillis = retryDelayMillis,
                        readyReceived = connectionResult.readyReceived,
                        connectedDurationMillis =
                            connectionResult.connectedDurationMillis,
                    )
                }
            }
        } finally {
            _realtimeConnectionState.value =
                if (_appForeground.value) {
                    RealtimeConnectionState.BACKING_OFF
                } else {
                    RealtimeConnectionState.PAUSED
                }
        }
    }

    private suspend fun awaitRealtimeRetry(delayMillis: Long) {
        awaitRealtimeRetryOrTimeout(
            requests = manualRealtimeReconnectRequests,
            timeoutMillis = delayMillis,
        )
    }

    private fun clearPendingRealtimeReconnectRequests() {
        while (manualRealtimeReconnectRequests.tryReceive().isSuccess) {
            // A reconnect request is account- and foreground-scoped. Never let
            // an old tap leak into a later account or authentication session.
        }
    }

    private suspend fun ensureRealtimeCursor(
        webSocketClient: WorkspaceAPIClient,
        ownerKey: String,
    ): Boolean {
        if (epochGeneration.isNotBlank()) return true
        if (
            !webSocketClient.userViewModel.repo
                .isActiveCredentialOwner(ownerKey)
        ) {
            return false
        }

        val persistedCursor = try {
            cursorStore.read(ownerKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(
                "WebSocket",
                "Stored event cursor is unreadable; requesting a fresh cursor",
            )
            try {
                cursorStore.clearAccount(ownerKey)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (clearException: Exception) {
                Log.d("WebSocket", "Failed to clear an unreadable event cursor")
                return false
            }
            null
        }
        if (
            !webSocketClient.userViewModel.repo
                .isActiveCredentialOwner(ownerKey)
        ) {
            return false
        }
        if (persistedCursor != null) {
            latestEpoch = persistedCursor.epochVersion
            epochGeneration = persistedCursor.epochGeneration
            Log.d("WebSocket", "Restored the saved event cursor")
            return true
        }

        return when (val response = webSocketClient.performRequest(EpochRequest())) {
            is ApiResult.Success -> {
                if (
                    !webSocketClient.userViewModel.repo
                        .isActiveCredentialOwner(ownerKey)
                ) {
                    return false
                }
                val cursor = runCatching {
                    PersistedRealtimeCursor(
                        epochVersion = response.value.epochVersion,
                        epochGeneration = response.value.epochGeneration,
                    )
                }.getOrElse {
                    Log.d("WebSocket", "Server returned an invalid event cursor")
                    return false
                }
                latestEpoch = cursor.epochVersion
                epochGeneration = cursor.epochGeneration
                if (!persistRealtimeCursor(ownerKey)) {
                    resetRealtimeCursor()
                    return false
                }
                true
            }

            is ApiResult.Error -> false
        }
    }

    private suspend fun catchUpRealtimeEvents(
        webSocketClient: WorkspaceAPIClient,
        ownerKey: String,
    ): RealtimeCatchUpResult {
        repeat(MAX_CATCH_UP_PAGES) {
            if (
                !webSocketClient.userViewModel.repo
                    .isActiveCredentialOwner(ownerKey)
            ) {
                return RealtimeCatchUpResult.RETRY
            }
            val pageStartEpoch = latestEpoch
            when (
                val response = webSocketClient.performRequest(
                    EventsRequest(
                        afterEpochVersion = pageStartEpoch,
                        epochGeneration = epochGeneration,
                    ),
                )
            ) {
                is ApiResult.Error -> {
                    if (
                        !webSocketClient.userViewModel.repo
                            .isActiveCredentialOwner(ownerKey)
                    ) {
                        return RealtimeCatchUpResult.RETRY
                    }
                    if (response.error.httpStatus == HTTP_GONE) {
                        invalidateDerivedStateForExpiredCursor()
                        try {
                            cursorStore.clearAccount(ownerKey)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            Log.d(
                                "WebSocket",
                                "Failed to clear an expired event cursor",
                            )
                            return RealtimeCatchUpResult.RETRY
                        }
                        Log.d(
                            "WebSocket",
                            "Saved event cursor expired; refreshing snapshots",
                        )
                        return RealtimeCatchUpResult.CURSOR_EXPIRED
                    }
                    Log.d("WebSocket", "Failed to catch up realtime events")
                    return RealtimeCatchUpResult.RETRY
                }

                is ApiResult.Success -> {
                    if (
                        !webSocketClient.userViewModel.repo
                            .isActiveCredentialOwner(ownerKey)
                    ) {
                        return RealtimeCatchUpResult.RETRY
                    }
                    val orderedEvents =
                        validateAndOrderRealtimeCatchUpPage(
                            events = response.value,
                            afterEpoch = pageStartEpoch,
                        )
                    if (orderedEvents == null) {
                        Log.d(
                            "WebSocket",
                            "Server returned an invalid event catch-up page",
                        )
                        return RealtimeCatchUpResult.RETRY
                    }
                    for (event in orderedEvents) {
                        if (
                            !webSocketClient.userViewModel.repo
                                .isActiveCredentialOwner(ownerKey)
                        ) {
                            return RealtimeCatchUpResult.RETRY
                        }
                        val previousEpoch = latestEpoch
                        try {
                            processTextFrame(
                                receivedText = event.toString(),
                                ownerKey = ownerKey,
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            Log.d(
                                "WebSocket",
                                "Failed to apply a caught-up event: " +
                                    exception::class.simpleName,
                            )
                            return RealtimeCatchUpResult.RETRY
                        }
                        if (latestEpoch <= previousEpoch) {
                            Log.d(
                                "WebSocket",
                                "Caught-up event did not advance the cursor",
                            )
                            return RealtimeCatchUpResult.RETRY
                        }
                        if (!persistRealtimeCursor(ownerKey)) {
                            return RealtimeCatchUpResult.RETRY
                        }
                    }

                    if (response.metadata.nextPageMarker == null) {
                        return RealtimeCatchUpResult.COMPLETE
                    }
                    if (latestEpoch <= pageStartEpoch) {
                        Log.d(
                            "WebSocket",
                            "Event catch-up pagination made no progress",
                        )
                        return RealtimeCatchUpResult.RETRY
                    }
                }
            }
        }
        Log.d("WebSocket", "Event catch-up exceeded its bounded page limit")
        return RealtimeCatchUpResult.RETRY
    }

    private suspend fun persistRealtimeCursor(ownerKey: String): Boolean {
        val cursor = runCatching {
            PersistedRealtimeCursor(
                epochVersion = latestEpoch,
                epochGeneration = epochGeneration,
            )
        }.getOrElse {
            Log.d("WebSocket", "Refused to persist an invalid event cursor")
            return false
        }
        return try {
            client
                ?.userViewModel
                ?.repo
                ?.withActiveCredentialOwner(ownerKey) {
                    cursorStore.write(ownerKey, cursor)
                    true
                }
                ?: false
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d("WebSocket", "Failed to persist the event cursor")
            false
        }
    }

    private suspend fun startWebsocketConnection(
        webSocketClient: WorkspaceAPIClient,
        ownerKey: String,
    ): RealtimeConnectionResult {
        val session =
            webSocketClient.userViewModel.repo.activeCredentialSnapshot()
        if (session.ownerKey != ownerKey) {
            return RealtimeConnectionResult()
        }
        val accessToken = session.accessToken
        val baseUrl = session.baseUrl
        if (accessToken == null || baseUrl == null) {
            return RealtimeConnectionResult()
        }
        val baseUri = URI(baseUrl)
        var closeCode: Int? = null
        var readyReceived = false
        var connectedAtNanos: Long? = null
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
            connectedAtNanos = System.nanoTime()
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val receivedText = frame.readText()
                        try {
                            if (
                                !webSocketClient.userViewModel.repo
                                    .isActiveCredentialOwner(ownerKey)
                            ) {
                                close()
                                break
                            }
                            val previousEpoch = latestEpoch
                            val frameReady =
                                processTextFrame(
                                    receivedText = receivedText,
                                    ownerKey = ownerKey,
                                )
                            if (frameReady && !readyReceived) {
                                _realtimeConnectionState.value =
                                    RealtimeConnectionState.CONNECTED
                                Log.d("WebSocket", "Ready")
                            }
                            readyReceived =
                                frameReady || readyReceived
                            if (
                                latestEpoch > previousEpoch ||
                                frameReady
                            ) {
                                if (!persistRealtimeCursor(ownerKey)) {
                                    Log.d(
                                        "WebSocket",
                                        "Closing realtime after cursor persistence failed",
                                    )
                                    close()
                                    break
                                }
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
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
                        val reason = frame.readReason()
                        closeCode = reason?.code?.toInt()
                        Log.d(
                            "WebSocket",
                            "Connection closing reason: $reason"
                        )
                    }
                    else -> Log.d("WebSocket", "Received control or ping/pong frame")
                }
            }
            if (closeCode == null) {
                closeCode = closeReason.await()?.code?.toInt()
            }
        }
        val connectedDurationMillis = connectedAtNanos
            ?.let { startedAt ->
                (System.nanoTime() - startedAt)
                    .coerceAtLeast(0L) / NANOS_PER_MILLISECOND
            }
            ?: 0L
        return RealtimeConnectionResult(
            closeCode = closeCode,
            readyReceived = readyReceived,
            connectedDurationMillis = connectedDurationMillis,
        )
    }

    fun processTextFrame(
        receivedText: String,
        ownerKey: String? = null,
    ): Boolean {
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
            return true
        }

        val eventEpoch = jsonObject["epoch_version"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
        if (eventEpoch != null && eventEpoch <= latestEpoch) return false

        val action = jsonObject["action"]?.jsonPrimitive?.contentOrNull
            ?: return false
        val payload = jsonObject["payload"]?.toString() ?: return false
        when (jsonObject["object_type"]?.jsonPrimitive?.contentOrNull) {
            "message" -> didReceiveMessageEvent(payload, action, ownerKey)
            "user" -> didReceiveUserEvent(payload, action)
            "folder" -> didReceiveFolderEvent(payload, action)
            "folder_item" -> didReceiveFolderItemEvent(payload, action)
            "stream_binding" -> didReceiveStreamBindingEvent(payload, action)
            "stream" -> didReceiveStreamEvent(payload, action)
            "topic" -> didReceiveTopicEvent(payload, action)
            "message_reaction" -> didReceiveReactionEvent(payload, action)
            "external_account" -> didReceiveExternalAccountEvent(payload, action)
            "external_chat" -> didReceiveExternalChatEvent(payload, action)
            "external_operation" ->
                didReceiveExternalOperationEvent(payload, action)
            else -> Log.d(
                "WebSocket",
                "Ignored unsupported event type: " +
                    jsonObject["object_type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
        if (eventEpoch != null) {
            latestEpoch = maxOf(latestEpoch, eventEpoch)
        }
        return false
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

    fun didReceiveMessageEvent(
        payload: String,
        action: String,
        ownerKey: String? = null,
    ) {
        when(action) {
            "created" -> {
                val message = json.decodeFromString<MessageResponse>(payload)
                updateMessagesPool(listOf(message))
                addMessageToStreamTopic(message)
                emitMessageProjectionEvent(
                    ownerKey,
                    MessageProjectionEvent.Upsert(message),
                )
            }
            "updated" -> {
                val message = json.decodeFromString<MessageResponse>(payload)
                updateMessagesPool(listOf(message))
                updateMessage(message)
                emitMessageProjectionEvent(
                    ownerKey,
                    MessageProjectionEvent.Upsert(message),
                )
            }
            "read" -> {
                val kind = runCatching {
                    json.parseToJsonElement(payload)
                        .jsonObject["kind"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()
                when (kind) {
                    "message.read" -> {
                        val message =
                            json.decodeFromString<MessageResponse>(payload)
                        markMessagesRead(listOf(message.uuid))
                        updateMessagesPool(listOf(message))
                        updateMessage(message)
                        emitMessageProjectionEvent(
                            ownerKey,
                            MessageProjectionEvent.Upsert(message),
                        )
                    }

                    "messages.read" -> {
                        val event =
                            json.decodeFromString<MessagesReadPayload>(payload)
                        markMessagesRead(event.messageUuids)
                        emitMessageProjectionEvent(
                            ownerKey,
                            MessageProjectionEvent.Read(event.messageUuids),
                        )
                    }

                    else -> Log.w(
                        "WebSocket",
                        "Ignored unsupported message read event kind: $kind",
                    )
                }
            }
            "deleted" -> {
                val messageUuid =
                    json.decodeFromString<DeletedObjectPayload>(payload).uuid
                removeMessageEverywhere(messageUuid)
                emitMessageProjectionEvent(
                    ownerKey,
                    MessageProjectionEvent.Deleted(messageUuid),
                )
            }
        }
    }

    private fun emitMessageProjectionEvent(
        ownerKey: String?,
        event: MessageProjectionEvent,
    ) {
        val validOwnerKey = ownerKey?.takeIf(String::isNotBlank) ?: return
        _messageProjectionEvents.tryEmit(
            OwnedMessageProjectionEvent(
                ownerKey = validOwnerKey,
                sequence = messageProjectionSequence.incrementAndGet(),
                event = event,
            ),
        )
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

    fun didReceiveStreamBindingEvent(payload: String, action: String) {
        when (action) {
            "created" -> {
                val event =
                    json.decodeFromString<StreamBindingsCreatedPayload>(payload)
                if (
                    event.items.any { it.streamUuid != event.uuid } ||
                    event.items.map(StreamBindingResponseData::uuid)
                        .distinct()
                        .size != event.items.size
                ) {
                    logRealtimeWarning(
                        "Ignored malformed stream binding event",
                    )
                    return
                }
                addStreamBindings(event.items)
            }
            "deleted" -> {
                val event =
                    json.decodeFromString<DeletedStreamBindingPayload>(payload)
                removeStreamBinding(event.uuid)
            }
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

    fun didReceiveExternalAccountEvent(payload: String, action: String) {
        val event = decodeExternalSnapshotEvent(
            payload = payload,
            objectType = "external_account",
            action = action,
        ) ?: return
        val response = runCatching {
            validateExternalAccountResponse(
                response = json.decodeFromString<ExternalAccountResponse>(
                    event.snapshot.toString(),
                ),
                expectedUuid = event.uuid,
            ).response
        }.getOrElse { error ->
            logRealtimeWarning(
                message = "Ignored malformed external account event",
                error = error,
            )
            return
        }
        applyExternalAccountSnapshot(
            response = response,
            deleted = action == "deleted",
        )
    }

    fun didReceiveExternalChatEvent(payload: String, action: String) {
        val event = decodeExternalSnapshotEvent(
            payload = payload,
            objectType = "external_chat",
            action = action,
        ) ?: return
        val response = runCatching {
            validateExternalChatResponse(
                response = json.decodeFromString<ExternalChatResponse>(
                    event.snapshot.toString(),
                ),
                expectedUuid = event.uuid,
            )
        }.getOrElse { error ->
            logRealtimeWarning(
                message = "Ignored malformed external chat event",
                error = error,
            )
            return
        }
        applyExternalChatSnapshot(
            response = response,
            deleted = action == "deleted",
        )
    }

    fun didReceiveExternalOperationEvent(payload: String, action: String) {
        val event = decodeExternalSnapshotEvent(
            payload = payload,
            objectType = "external_operation",
            action = action,
        ) ?: return
        val response = runCatching {
            validateExternalOperationResponse(
                response = json.decodeFromString<ExternalOperationResponse>(
                    event.snapshot.toString(),
                ),
                expectedUuid = event.uuid,
            )
        }.getOrElse { error ->
            logRealtimeWarning(
                message = "Ignored malformed external operation event",
                error = error,
            )
            return
        }
        applyExternalOperationSnapshot(
            response = response,
            deleted = action == "deleted",
        )
    }

    private fun decodeExternalSnapshotEvent(
        payload: String,
        objectType: String,
        action: String,
    ): ExternalSnapshotEventPayload? {
        if (action !in EXTERNAL_EVENT_ACTIONS) {
            logRealtimeWarning(
                message = "Ignored unsupported $objectType action: $action",
            )
            return null
        }
        return runCatching {
            val event =
                json.decodeFromString<ExternalSnapshotEventPayload>(payload)
            require(event.kind == "$objectType.$action") {
                "External event kind does not match its envelope"
            }
            event.copy(
                uuid = canonicalExternalIntegrationUuid(event.uuid),
            )
        }.getOrElse { error ->
            logRealtimeWarning(
                message = "Ignored malformed $objectType event envelope",
                error = error,
            )
            null
        }
    }

    private fun logRealtimeWarning(
        message: String,
        error: Throwable? = null,
    ) {
        // android.util.Log is a runtime stub in local JVM tests. The realtime
        // parser must remain independently testable without making logging a
        // functional dependency of poison-event handling.
        runCatching {
            if (error == null) {
                Log.w("WebSocket", message)
            } else {
                Log.w("WebSocket", message, error)
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
        private const val STABLE_CONNECTION_MILLIS = 60_000L
        private const val EVENTS_CURSOR_EXPIRED_CLOSE_CODE = 4_410
        private const val HTTP_GONE = 410
        private const val MAX_CATCH_UP_PAGES = 20
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val EXTERNAL_EVENT_ACTIONS =
            setOf("created", "updated", "deleted")
    }
}

private data class ExternalProjectionRevision(
    val revision: Int,
    val deleted: Boolean,
)

@Serializable
private data class ExternalSnapshotEventPayload(
    val kind: String,
    val uuid: String,
    val snapshot: JsonObject,
)

@Serializable
private data class StreamBindingsCreatedPayload(
    val uuid: String,
    val items: List<StreamBindingResponseData>,
)

@Serializable
private data class DeletedStreamBindingPayload(
    val uuid: String,
)

enum class RealtimeConnectionState {
    PAUSED,
    CONNECTING,
    CONNECTED,
    BACKING_OFF,
}

internal fun shouldAcceptRealtimeReconnect(
    state: RealtimeConnectionState,
    appForeground: Boolean,
): Boolean =
    appForeground && state == RealtimeConnectionState.BACKING_OFF

internal suspend fun awaitRealtimeRetryOrTimeout(
    requests: ReceiveChannel<Unit>,
    timeoutMillis: Long,
): Boolean {
    require(timeoutMillis > 0L) {
        "Realtime retry timeout must be positive"
    }
    return withTimeoutOrNull(timeoutMillis) {
        requests.receive()
        true
    } ?: false
}

private data class RealtimeConnectionResult(
    val closeCode: Int? = null,
    val readyReceived: Boolean = false,
    val connectedDurationMillis: Long = 0L,
)

private enum class RealtimeCatchUpResult {
    COMPLETE,
    CURSOR_EXPIRED,
    RETRY,
}

private fun JsonObject.eventEpochVersion(): Int? =
    this["epoch_version"]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toIntOrNull()

internal fun validateAndOrderRealtimeCatchUpPage(
    events: List<JsonObject>,
    afterEpoch: Int,
): List<JsonObject>? {
    if (afterEpoch < 0) return null
    val ordered = events
        .map { event ->
            val epoch = event.eventEpochVersion()
                ?: return null
            epoch to event
        }
        .sortedBy { (epoch, _) -> epoch }
    var previousEpoch = afterEpoch
    ordered.forEach { (epoch, _) ->
        if (epoch <= previousEpoch) return null
        previousEpoch = epoch
    }
    return ordered.map { (_, event) -> event }
}

internal fun nextRealtimeRetryDelay(
    currentDelayMillis: Long,
    readyReceived: Boolean,
    connectedDurationMillis: Long,
): Long =
    if (
        readyReceived &&
        connectedDurationMillis >= STABLE_REALTIME_CONNECTION_MILLIS
    ) {
        INITIAL_REALTIME_RETRY_DELAY_MILLIS
    } else {
        (currentDelayMillis * 2L)
            .coerceAtMost(MAX_REALTIME_RETRY_DELAY_MILLIS)
    }

private const val INITIAL_REALTIME_RETRY_DELAY_MILLIS = 1_000L
private const val MAX_REALTIME_RETRY_DELAY_MILLIS = 30_000L
private const val STABLE_REALTIME_CONNECTION_MILLIS = 60_000L
private const val MESSAGE_PROJECTION_EVENT_BUFFER_CAPACITY = 64

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
data class MessagesReadPayload(
    @SerialName("message_uuids") val messageUuids: List<String>,
)

private data class RepositoryMessagePosition(
    val createdAt: Instant,
    val uuid: String,
)

private fun repositoryMessagePosition(
    message: MessageResponse,
): RepositoryMessagePosition? {
    val createdAt = runCatching {
        OffsetDateTime.parse(message.createdAt).toInstant()
    }.getOrNull() ?: return null
    val uuid = runCatching {
        UUID.fromString(message.uuid).toString()
    }.getOrNull() ?: return null
    return RepositoryMessagePosition(createdAt = createdAt, uuid = uuid)
}

private fun compareRepositoryMessagePositions(
    left: RepositoryMessagePosition,
    right: RepositoryMessagePosition,
): Int {
    val timeComparison = left.createdAt.compareTo(right.createdAt)
    return if (timeComparison != 0) {
        timeComparison
    } else {
        left.uuid.compareTo(right.uuid)
    }
}

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
