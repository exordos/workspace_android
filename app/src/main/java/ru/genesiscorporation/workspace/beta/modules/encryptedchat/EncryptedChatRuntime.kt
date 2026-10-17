package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.ConfirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.confirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.crypto.EncryptedChatKeyStore
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.EncryptedEditMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.EncryptedSendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Android binding for one mounted stream. Repositories and disk caches retain opaque payloads. */
class EncryptedChatRuntime(
    context: Context,
    private val client: WorkspaceAPIClient,
    private val repo: EventsRepository,
    private val coroutineScope: CoroutineScope,
    private val streamId: String,
    private val topicId: String,
) {
    private val appContext = context.applicationContext
    private val user = client.requireUserViewModel()
    private val serverId = repo.serverId
    private val streamRevision = repo.streamRemovalRevision(streamId)
    private val initMutex = Mutex()
    private val refreshMutex = Mutex()
    private val bindingLock = Any()
    private val mutableState = MutableStateFlow(EncryptedChatSessionState())
    val state: StateFlow<EncryptedChatSessionState> = mutableState.asStateFlow()
    private val mutablePresentations = MutableStateFlow<Map<String, EncryptedChatPresentation>>(emptyMap())
    val presentations: StateFlow<Map<String, EncryptedChatPresentation>> = mutablePresentations.asStateFlow()
    @Volatile private var closed = false
    @Volatile private var account: ConfirmedAccountContext? = null
    private var mountedAccount: ConfirmedAccountContext? = null
    @Volatile private var protectedStreamKnown = repo.isKnownProtectedStream(streamId)
    @Volatile private var boundGeneration: Long? = null
    private var ownerUuid: String? = null
    @Volatile private var session: EncryptedChatSession? = null
    private var sessionStateJob: Job? = null
    private var defaultTopic: String? = null
    private var rolesLoaded = false
    private var rawSignature: List<Pair<String, MessageResponsePayload>>? = null
    private var presentationRevision = -1L
    private class PreparedSend(val topicUuid: String, val textHash: ByteArray, val payload: EncryptedChatPayload)
    private val preparedSends = ConcurrentHashMap<String, PreparedSend>()
    private val observer: Job = coroutineScope.launch {
        merge(
            repo.streams.map { Unit }, repo.currentUser.map { Unit },
            repo.streamBindings.map { Unit }, repo.messagesPool.map { Unit },
            repo.streamTopicMessages.map { Unit }, user.selectedServer.map { Unit },
            user.accessToken.map { Unit }, repo.verifiedOwnerAuthentication.map { Unit },
            ConfirmedAccountContext.invalidations.map { Unit },
        ).collect {
            if (closed || !isProtectedRuntime()) return@collect
            invalidateIfStale()
            if (repo.streams.value.firstOrNull { it.uuid == streamId }?.encryption != true) return@collect
            try { refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { report(error) }
        }
    }
    private val keyObserver: Job = coroutineScope.launch {
        keyChanges.collect { change ->
            val currentSession = session ?: return@collect
            if (change.source !== this@EncryptedChatRuntime && encryptedChatStorageScopeMatches(change.scope, currentSession.scope) && !closed) {
                mutablePresentations.value = emptyMap()
                presentationRevision = -1
                try { refresh(force = true) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { report(error) }
            }
        }
    }

    // Revocation must not wait behind history/network refresh work in the main observer.
    private val identityObserver: Job = coroutineScope.launch {
        merge(user.selectedServer.map { Unit }, user.selectedServerId.map { Unit }, user.accessToken.map { Unit },
            repo.currentUser.map { Unit }, repo.verifiedOwnerAuthentication.map { Unit },
            ConfirmedAccountContext.invalidations.map { Unit }, repo.streams.map { Unit }, repo.streamBindings.map { Unit }).collect {
            if (!closed && isProtectedRuntime()) invalidateIfStale()
        }
    }

    private fun isProtectedRuntime(): Boolean {
        if (repo.streams.value.any { it.uuid == streamId && it.encryption }) protectedStreamKnown = true
        return protectedStreamKnown || mountedAccount != null
    }

    private fun boundCurrent(expected: ConfirmedAccountContext? = account): Boolean =
        !closed && repo.streamRemovalRevision(streamId) == streamRevision &&
            expected != null && expected.matches(account) && expected.isCurrent(client, repo)

    private fun <T : Any> withCurrentStream(block: () -> T): T =
        repo.withStreamRevision(streamId, streamRevision) { requireBound(); block() }
            ?: throw EncryptedChatException(EncryptedChatFailure.STALE)

    private fun requireBound(expected: ConfirmedAccountContext? = account) {
        if (!boundCurrent(expected)) throw EncryptedChatException(EncryptedChatFailure.STALE)
    }

    private inline fun <T> withBinding(expected: ConfirmedAccountContext, block: () -> T): T = synchronized(bindingLock) {
        if (closed || account !== expected) throw EncryptedChatException(EncryptedChatFailure.STALE)
        block()
    }

    private fun invalidateIfStale(): Boolean {
        val observedAccount = account
        val observedSession = session
        // Live checks take repository/token locks; never hold bindingLock around them.
        if (boundCurrent(observedAccount) && observedSession?.isCurrent() != false) return false
        val previous = synchronized(bindingLock) {
            // An initial/delayed observer must not revoke a binding installed after its snapshot.
            if (account === observedAccount && session === observedSession) invalidate() else null
        }
        previous?.close()
        return true
    }

    /** Called with bindingLock held. Close the detached session outside the lock. */
    private fun invalidate(): EncryptedChatSession? {
        val previous = session
        session = null
        account = null
        boundGeneration = null
        sessionStateJob?.cancel()
        sessionStateJob = null
        mutablePresentations.value = emptyMap()
        preparedSends.clear()
        rawSignature = null
        presentationRevision = -1
        rolesLoaded = false
        mutableState.value = EncryptedChatSessionState(error = EncryptedChatFailure.STALE)
        return previous
    }

    fun close() {
        val previous = synchronized(bindingLock) { closed = true; invalidate() }
        previous?.close()
        observer.cancel(); keyObserver.cancel(); identityObserver.cancel()
    }
    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }

    private fun report(error: Exception) {
        val reason = (error as? EncryptedChatException)?.failure ?: EncryptedChatFailure.UNAVAILABLE
        if (invalidateIfStale()) return
        mutableState.value = mutableState.value.copy(pending = false, error = reason)
    }

    private fun currentAccess(scope: EncryptedChatScope): EncryptedChatAccess? {
        if (!boundCurrent() || scope.runtimeGeneration != boundGeneration) return null
        val stream = repo.streams.value.firstOrNull { it.uuid == streamId } ?: return null
        if (!stream.encryption || stream.currentEncryptionKeyUuid.isNullOrBlank() ||
            (stream.projectId != null && stream.projectId != scope.projectId) ||
            (stream.userUuid != null && stream.userUuid != scope.userId)) return null
        val bindings = repo.streamBindings.value[streamId].orEmpty().filter { it.streamUuid == streamId }
        val roles = bindings.associate { it.userUuid to it.role }.toMutableMap()
        if (!rolesLoaded && stream.role != null) roles[scope.userId] = stream.role
        val owner = stream.ownerUuid ?: bindings.firstOrNull { it.role == "owner" }?.userUuid ?: ""
        return EncryptedChatAccess(scope, requireNotNull(stream.currentEncryptionKeyUuid), owner, roles,
            isMember = owner == scope.userId || !rolesLoaded || bindings.any { it.userUuid == scope.userId })
    }

    suspend fun readySession(): EncryptedChatSession = initMutex.withLock {
        session?.let { requireBound(); return@withLock it }
        if (closed || repo.streams.value.none { it.uuid == streamId && it.encryption }) throw EncryptedChatException(EncryptedChatFailure.STALE)
        val confirmed = confirmedAccountContext(client, repo)
            ?: throw EncryptedChatException(EncryptedChatFailure.STALE)
        synchronized(bindingLock) {
            if (closed || mountedAccount?.sameOwner(confirmed) == false) throw EncryptedChatException(EncryptedChatFailure.STALE)
            mountedAccount = mountedAccount ?: confirmed
            account = confirmed
            ownerUuid = confirmed.userUuid
        }
        val captured = confirmed.serverConfig()
        requireBound(confirmed)
        val keyStore = withContext(Dispatchers.IO) { EncryptedChatKeyStore(appContext) }
        requireBound(confirmed)
        val device = withContext(Dispatchers.IO) {
            requireBound(confirmed)
            keyStore.ensureDeviceIdentity(confirmed.owner, "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        }
        requireBound(confirmed)
        val boundScope = EncryptedChatScope(confirmed.baseUrl, confirmed.projectUuid, confirmed.userUuid,
            generations.incrementAndGet(), device.deviceId, device.deviceName, streamId)
        withBinding(confirmed) { boundGeneration = boundScope.runtimeGeneration }
        // Fetch participant roles explicitly. Stream role alone cannot authorize another issuer.
        repo.beginStreamBindingsRequest(streamId).use { request ->
            when (val response = client.performRequest(confirmed.pin(StreamBindingsRequest(streamId)), serverId, captured)) {
                is ApiResult.Success -> {
                    requireBound(confirmed)
                    if (!repo.applyStreamBindingsSnapshot(request, response.value)) throw EncryptedChatException(EncryptedChatFailure.STALE)
                    rolesLoaded = true
                }
                is ApiResult.Error -> throw EncryptedChatException(EncryptedChatFailure.UNAVAILABLE)
            }
        }
        requireBound(confirmed)
        defaultTopic = repo.streams.value.firstOrNull { it.uuid == streamId }?.defaultTopicUuid?.takeIf { it.isNotBlank() }
            ?: repo.streamTopics.value[streamId]?.firstOrNull { it.isDefault }?.uuid
        if (defaultTopic == null) {
            val topicScope = repo.captureTopicCacheScope()
            when (val response = client.performRequest(confirmed.pin(TopicsRequest(listOf(streamId))), serverId, captured)) {
                is ApiResult.Success -> {
                    requireBound(confirmed)
                    val topics = response.value.filter { it.streamUuid == streamId }
                    if (!repo.addStreamTopics(streamId, topics, topicScope)) throw EncryptedChatException(EncryptedChatFailure.STALE)
                    defaultTopic = topics.firstOrNull { it.isDefault }?.uuid
                }
                is ApiResult.Error -> throw EncryptedChatException(EncryptedChatFailure.UNAVAILABLE)
            }
        }
        if (defaultTopic == null) throw EncryptedChatException(EncryptedChatFailure.INVALID_MESSAGE)
        val created = EncryptedChatSession(boundScope, keyStore, { currentAccess(boundScope) },
            EncryptedChatServiceTransport { payload, canonical, _, guard ->
                postPayload(payload, requireNotNull(defaultTopic), canonical, guard).asEncryptedChatWire(boundScope.projectId)
            }, { defaultTopic }, { keyUuid ->
                requireBound(confirmed)
                repo.streams.value.firstOrNull { it.uuid == streamId }?.let { stream ->
                    repo.updateStream(stream.copy(currentEncryptionKeyUuid = keyUuid))
                }
            })
        created.load()
        requireBound(confirmed)
        val stateJob = withBinding(confirmed) {
            session = created
            coroutineScope.launch(start = CoroutineStart.LAZY) {
                var knownKeyRevision = created.state.value.keyRevision
                created.state.collect { updated ->
                    if (session !== created) return@collect
                    if (!boundCurrent(confirmed)) { invalidateIfStale(); return@collect }
                    mutableState.value = updated
                    if (updated.keyRevision != knownKeyRevision) {
                        knownKeyRevision = updated.keyRevision
                        mutablePresentations.value = emptyMap()
                        presentationRevision = -1
                        keyChanges.emit(KeyChange(this@EncryptedChatRuntime, created.scope))
                        coroutineScope.launch { try { refresh(force = true) } catch (error: Exception) { if (error is CancellationException) throw error; report(error) } }
                    }
                }
            }.also { sessionStateJob = it }
        }
        stateJob.start()
        // Service messages can live in the default topic while the user is viewing another topic.
        when (val history = client.performRequest(confirmed.pin(MessagesRequest(streamId, null)), serverId, captured)) {
            is ApiResult.Success -> { requireBound(confirmed); repo.updateMessagesPool(history.value.filter { it.streamUuid == streamId }) }
            is ApiResult.Error -> { mutableState.value = mutableState.value.copy(error = EncryptedChatFailure.UNAVAILABLE) }
        }
        created
    }

    private fun wireMessages(): List<MessageResponse> = latestEncryptedChatWireMessages(
        repo.messagesPool.value, repo.streamTopicMessages.value.values.flatten(), streamId)

    internal suspend fun refresh(force: Boolean = false) = refreshMutex.withLock {
        val active = readySession()
        requireBound()
        val messages = wireMessages()
        val signature = messages.map { it.uuid to it.payload.copy() }
        val revision = active.state.value.keyRevision
        if (!force && rawSignature == signature && presentationRevision == revision) {
            active.load() // Role changes can make previously deferred grants verifiable.
            return@withLock
        }
        active.ingest(messages.map { it.asEncryptedChatWire(active.scope.projectId) })
        val projected = linkedMapOf<String, EncryptedChatPresentation>()
        for (message in messages) {
            requireBound()
            projected[message.uuid] = active.decrypt(message.asEncryptedChatWire(active.scope.projectId))
        }
        withCurrentStream {
            if (active === session) {
                mutablePresentations.value = projected
                rawSignature = signature
                presentationRevision = active.state.value.keyRevision
                mutableState.value = active.state.value
            }
        }
    }

    private suspend fun postPayload(payload: EncryptedChatPayload, targetTopic: String, canonical: String,
                                    guard: () -> Boolean): MessageResponse {
        val confirmed = account ?: throw EncryptedChatException(EncryptedChatFailure.STALE)
        val authenticatedGuard = { guard() && boundCurrent(confirmed) }
        requireBound()
        if (!authenticatedGuard()) throw EncryptedChatException(EncryptedChatFailure.STALE)
        val captured = confirmed.serverConfig()
        val expectedUuid = encryptedMessagePlacementUuid(targetTopic, canonical)
        val response = try {
            client.performRequest(confirmed.pin(EncryptedSendMessageRequest(streamId, targetTopic, payload.asMessageResponsePayload(), canonical)), serverId, captured)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        requireBound()
        if (!authenticatedGuard()) throw EncryptedChatException(EncryptedChatFailure.STALE)
        val raw = when (response) {
            is ApiResult.Success -> response.value
            else -> {
                // An uncertain POST is reconciled at its deterministic public placement UUID.
                when (val recovery = client.performRequest(confirmed.pin(MessagesByIdsRequest(listOf(expectedUuid))), serverId, captured)) {
                    is ApiResult.Success -> recovery.value.singleOrNull { it.uuid == expectedUuid }
                    is ApiResult.Error -> null
                } ?: throw EncryptedChatException(EncryptedChatFailure.SEND_FAILED)
            }
        }
        requireBound()
        if (!authenticatedGuard() || raw.uuid != expectedUuid || raw.streamUuid != streamId || raw.topicUuid != targetTopic ||
            raw.authorUuid != ownerUuid || (raw.projectId != null && raw.projectId != confirmed.projectUuid) ||
            raw.payload != payload.asMessageResponsePayload())
            throw EncryptedChatException(EncryptedChatFailure.INVALID_MESSAGE)
        repo.reconcileSentMessage(canonical, raw)
        return raw
    }

    private suspend fun <T> action(block: suspend (EncryptedChatSession) -> T): T {
        try {
            val result = block(readySession())
            // A presentation refresh failure must not turn a verified successful write into a retry.
            try { refresh(force = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { report(error) }
            return result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { report(error); throw error }
    }

    suspend fun sendText(text: String, topicId: String = this.topicId, canonicalUuid: String? = null): MessageResponse = action { active ->
        val canonical = canonicalUuid ?: UUID.randomUUID().toString()
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val textHash = try { MessageDigest.getInstance("SHA-256").digest(textBytes) } finally { textBytes.fill(0) }
        active.send(text) { payload, guard ->
            val existing = preparedSends[canonical]
            val prepared = if (existing != null) {
                if (existing.topicUuid != topicId || !MessageDigest.isEqual(existing.textHash, textHash) ||
                    existing.payload.keyUuid != payload.keyUuid)
                    throw EncryptedChatException(EncryptedChatFailure.INVALID_MESSAGE)
                existing
            } else PreparedSend(topicId, textHash, payload).also { preparedSends[canonical] = it }
            postPayload(prepared.payload, topicId, canonical, guard).also { preparedSends.remove(canonical) }
        }
    }

    suspend fun editText(uuid: String, text: String): MessageResponse = action { active ->
        val original = wireMessages().firstOrNull { it.uuid == uuid && it.authorUuid == ownerUuid && it.payload.kind == "e2ee" }
            ?: throw EncryptedChatException(EncryptedChatFailure.FORBIDDEN)
        active.send(text) { payload, guard ->
            val confirmed = account ?: throw EncryptedChatException(EncryptedChatFailure.STALE)
            val authenticatedGuard = { guard() && boundCurrent(confirmed) }
            requireBound()
            if (!authenticatedGuard()) throw EncryptedChatException(EncryptedChatFailure.STALE)
            val response = client.performRequest(confirmed.pin(EncryptedEditMessageRequest(uuid, payload.asMessageResponsePayload())), serverId, confirmed.serverConfig())
            requireBound()
            if (!authenticatedGuard()) throw EncryptedChatException(EncryptedChatFailure.STALE)
            val raw = when (response) {
                is ApiResult.Success -> response.value
                is ApiResult.Error -> throw EncryptedChatException(EncryptedChatFailure.SEND_FAILED)
            }
            if (raw.uuid != uuid || raw.streamUuid != streamId || raw.topicUuid != original.topicUuid ||
                raw.authorUuid != ownerUuid || (raw.projectId != null && raw.projectId != confirmed.projectUuid) ||
            raw.payload != payload.asMessageResponsePayload())
                throw EncryptedChatException(EncryptedChatFailure.INVALID_MESSAGE)
            repo.updateMessage(raw)
            raw
        }
    }

    suspend fun presentation(message: MessageResponse): EncryptedChatPresentation {
        lateinit var expectedAccount: ConfirmedAccountContext
        lateinit var expectedSession: EncryptedChatSession
        val projected = action { active ->
            expectedAccount = account ?: throw EncryptedChatException(EncryptedChatFailure.STALE)
            expectedSession = active
            active.decrypt(message.asEncryptedChatWire(active.scope.projectId))
        }
        // Unlike a verified write receipt, plaintext cannot survive a failed post-action refresh.
        return withCurrentStream {
            requireBound(expectedAccount)
            if (session !== expectedSession) throw EncryptedChatException(EncryptedChatFailure.STALE)
            projected
        }
    }
    suspend fun requestKey(keyUuid: String? = null, recoveryMessage: MessageResponse? = null) = action { active ->
        active.requestKey(keyUuid, recoveryMessage?.asEncryptedChatWire(active.scope.projectId))
    }
    suspend fun grantKey(requestMessageUuid: String) = action { it.grantKey(requestMessageUuid) }
    suspend fun rejectKey(requestMessageUuid: String) = action { it.rejectKey(requestMessageUuid) }
    suspend fun grantAllKeys() = action { it.grantAllKeys() }
    suspend fun rotate() = action { it.rotate() }
    suspend fun removeLocalKey(keyUuid: String) = action { active ->
        mutablePresentations.value = emptyMap()
        active.removeLocalKey(keyUuid)
    }

    private data class KeyChange(val source: EncryptedChatRuntime, val scope: EncryptedChatScope)
    companion object {
        private val generations = AtomicLong()
        private val keyChanges = MutableSharedFlow<KeyChange>(extraBufferCapacity = 32)
        internal val localKeyChanges: Flow<EncryptedChatScope> get() = keyChanges.map { it.scope }
    }
}

fun MessageResponse.asEncryptedChatWire(projectId: String) = EncryptedChatWireMessage(uuid, this.projectId ?: projectId, streamUuid, authorUuid,
    EncryptedChatPayload(payload.kind, payload.keyUuid.orEmpty(), payload.content, payload.deviceId, payload.deviceName,
        payload.publicKey, payload.requestMessageUuid, payload.senderDeviceId, payload.recipientDeviceId))

fun EncryptedChatPayload.asMessageResponsePayload() = MessageResponsePayload(kind, content, keyUuid, deviceId,
    deviceName, publicKey, requestMessageUuid, senderDeviceId, recipientDeviceId)

/** Backend v2_store uses UUIDv5(topic UUID, canonical UUID's standard string). */
internal fun encryptedMessagePlacementUuid(topicUuid: String, canonicalUuid: String): String {
    val namespace = UUID.fromString(topicUuid)
    val canonical = UUID.fromString(canonicalUuid).toString()
    val bytes = ByteBuffer.allocate(16).putLong(namespace.mostSignificantBits).putLong(namespace.leastSignificantBits).array()
    val digest = MessageDigest.getInstance("SHA-1").digest(bytes + canonical.toByteArray(Charsets.UTF_8))
    digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
    digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
    val buffer = ByteBuffer.wrap(digest)
    return UUID(buffer.long, buffer.long).toString()
}

/** Runtime generations differ between panels, but their durable secret address is identical. */
internal fun encryptedChatStorageScopeMatches(left: EncryptedChatScope, right: EncryptedChatScope): Boolean =
    left.server == right.server && left.projectId == right.projectId && left.userId == right.userId &&
        left.deviceId == right.deviceId && left.streamId == right.streamId

/** Pool and open-conversation snapshots may arrive in either order; edits must beat old ciphertext. */
internal fun latestEncryptedChatWireMessages(pool: List<MessageResponse>, conversations: List<MessageResponse>,
                                             streamId: String): List<MessageResponse> {
    val latest = linkedMapOf<String, MessageResponse>()
    for (message in pool + conversations) {
        if (message.streamUuid != streamId || message.payload.kind == "markdown") continue
        val previous = latest[message.uuid]
        val updated = runCatching { Instant.parse(message.updatedAt) }.getOrDefault(Instant.MIN)
        val previousUpdated = previous?.let { runCatching { Instant.parse(it.updatedAt) }.getOrDefault(Instant.MIN) }
        // Open conversation wins ties because it receives edits directly from the repository.
        if (previousUpdated == null || updated >= previousUpdated) latest[message.uuid] = message
    }
    return latest.values.toList()
}
