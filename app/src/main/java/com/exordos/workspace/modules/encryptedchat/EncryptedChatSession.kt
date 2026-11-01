package com.exordos.workspace.modules.encryptedchat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.data.crypto.ChatCryptoException
import com.exordos.workspace.data.crypto.ChatGrantContext
import com.exordos.workspace.data.crypto.ChatKeyAddress
import com.exordos.workspace.data.crypto.ChatKeyOwner
import com.exordos.workspace.data.crypto.ChatKeyRotation
import com.exordos.workspace.data.crypto.ChatKeyScope
import com.exordos.workspace.data.crypto.ChatKeyStorage
import com.exordos.workspace.data.crypto.ChatKeyStorageException
import com.exordos.workspace.data.crypto.ChatMessageContext
import com.exordos.workspace.data.crypto.PendingChatKeyRequest
import com.exordos.workspace.data.crypto.IssuedChatKeyResponse
import com.exordos.workspace.data.crypto.StagedChatCreation
import com.exordos.workspace.data.crypto.WorkspaceChatCrypto
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One account/runtime/device/stream session. Every operation rechecks the live scope after
 * suspension. The repository and its cache receive only wire payloads; plaintext is returned
 * to presentation callers and is never retained by this object.
 */
class EncryptedChatSession(
    val scope: EncryptedChatScope,
    private val storage: ChatKeyStorage,
    private val access: () -> EncryptedChatAccess?,
    private val transport: EncryptedChatServiceTransport,
    private val defaultTopicUuid: () -> String?,
    private val onCurrentKeyConfirmed: (String) -> Unit = {},
    private val crypto: WorkspaceChatCrypto = WorkspaceChatCrypto(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val keyScope = ChatKeyScope(ChatKeyOwner(scope.server, scope.projectId, scope.userId), scope.deviceId)
    private val mutex = Mutex()
    @Volatile private var closed = false
    private var revision = 0L
    private var sendingKeyUuid: String? = null
    private var recoveredRotationKeyUuid: String? = null
    private val requests = linkedMapOf<String, EncryptedChatWireMessage>()
    private val responses = linkedMapOf<String, EncryptedChatWireMessage>()
    private val processedResponses = hashSetOf<String>()
    private val requestStatuses = mutableMapOf<String, EncryptedChatRequestStatus>()
    private val mutableState = MutableStateFlow(EncryptedChatSessionState())
    val state: StateFlow<EncryptedChatSessionState> = mutableState.asStateFlow()

    fun isCurrent(): Boolean {
        val current = access()
        return !closed && current != null && current.scope == scope && current.isMember &&
            current.encrypted && current.currentKeyUuid.isNotBlank()
    }

    /** Must also be called at logout/project change and before abandoning a mounted dialog. */
    fun close() {
        closed = true
        // Ciphertext records can remain until this instance is collected; clearing maps here
        // would race an operation running on IO. Every such operation observes closed.
        sendingKeyUuid = null
        mutableState.value = EncryptedChatSessionState(keyRevision = ++revision, error = EncryptedChatFailure.STALE)
    }

    private fun fail(reason: EncryptedChatFailure): Nothing = throw EncryptedChatException(reason)
    private fun current(): EncryptedChatAccess {
        val snapshot = access()
        if (closed || snapshot == null || snapshot.scope != scope || !snapshot.isMember ||
            !snapshot.encrypted || snapshot.currentKeyUuid.isBlank()) fail(EncryptedChatFailure.STALE)
        return snapshot
    }
    private fun address(keyUuid: String) = ChatKeyAddress(keyScope, scope.streamId, keyUuid)
    private fun issuerAllowed(author: String) = isCurrent() && current().canIssue(author)
    private fun canManage() = issuerAllowed(scope.userId)
    private fun requireManage() { if (!canManage()) fail(EncryptedChatFailure.FORBIDDEN) }
    private fun requireOwner() { if (current().ownerUuid != scope.userId) fail(EncryptedChatFailure.FORBIDDEN) }
    private fun validMessage(message: EncryptedChatWireMessage): Boolean =
        message.projectId == scope.projectId && message.streamId == scope.streamId &&
            message.uuid.isNotBlank() && message.authorId.isNotBlank() && message.payload.keyUuid.isNotBlank()

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            if (!isCurrent()) {
                mutableState.value = EncryptedChatSessionState(keyRevision = revision, error = EncryptedChatFailure.STALE)
                fail(EncryptedChatFailure.STALE)
            }
            mutableState.value = mutableState.value.copy(pending = true, error = null)
            try {
                reconcile()
                val result = block()
                current()
                publish()
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = when (error) {
                    is EncryptedChatException -> error.failure
                    is ChatKeyStorageException -> EncryptedChatFailure.STORAGE_FAILED
                    is ChatCryptoException -> EncryptedChatFailure.INVALID_CIPHERTEXT
                    else -> EncryptedChatFailure.UNAVAILABLE
                }
                if (isCurrent()) mutableState.value = mutableState.value.copy(pending = false, error = reason)
                else mutableState.value = EncryptedChatSessionState(keyRevision = revision, error = EncryptedChatFailure.STALE)
                if (error is EncryptedChatException) throw error
                throw EncryptedChatException(reason)
            } finally {
                mutableState.value = mutableState.value.copy(pending = false)
            }
        }
    }

    private fun reconcile() {
        val current = current()
        // A visible current generation confirms an interrupted creation/rotation, never an older stage.
        if (current.ownerUuid == scope.userId) {
            storage.listStagedCreations(keyScope).firstOrNull {
                it.keyUuid == current.currentKeyUuid &&
                    (it.rotation?.streamUuid ?: it.creationId) == scope.streamId
            }?.let {
                requireOwner()
                if (!storage.finalizeCreation(keyScope, it.creationId, scope.streamId)) fail(EncryptedChatFailure.STORAGE_FAILED)
                if (it.rotation != null) recoveredRotationKeyUuid = it.keyUuid
                revision++
            }
        }
        val currentKey = storage.readChatKey(address(current.currentKeyUuid))
        if (currentKey != null) {
            try {
                if (sendingKeyUuid != current.currentKeyUuid &&
                    !storage.writeChatKey(address(current.currentKeyUuid), currentKey, forSending = true, onlyIfPresent = true))
                    fail(EncryptedChatFailure.STORAGE_FAILED)
                sendingKeyUuid = current.currentKeyUuid
            } finally { currentKey.fill(0) }
        } else {
            val previous = storage.readLastAvailableChatKey(keyScope, scope.streamId)
            sendingKeyUuid = previous?.first
            previous?.second?.fill(0)
        }
        val localGenerations = storage.listChatKeys(keyScope, scope.streamId).map { it.keyUuid }
        val generations = (localGenerations + current.currentKeyUuid + requestStatuses.keys +
            storage.listPendingRequestKeyUuids(keyScope, scope.streamId)).distinct()
        generations.forEach { keyUuid ->
            val pending = storage.listPendingRequests(address(keyUuid))
            val delivered = pending.any { it.requestMessageUuid != null && it.requestMessageUuid != it.localRequestId }
            if (delivered)
                requestStatuses[keyUuid] = EncryptedChatRequestStatus.REQUESTED
            else if ((!delivered && requestStatuses[keyUuid] == EncryptedChatRequestStatus.REQUESTED) ||
                (keyUuid !in localGenerations && requestStatuses[keyUuid] == EncryptedChatRequestStatus.RECEIVED))
                requestStatuses.remove(keyUuid)
        }
    }

    private fun requestOutcomes(): List<EncryptedChatKeyRequest> = requests.values.map { request ->
        val response = responses.values.firstOrNull { correlates(request, it) }
        val ownDevice = request.authorId == scope.userId && request.payload.deviceId == scope.deviceId
        val completed = if (ownDevice) storage.readRequestCompletionByMessageUuid(address(request.payload.keyUuid), request.uuid) else null
        EncryptedChatKeyRequest(request, when (if (ownDevice) completed?.outcome else response?.payload?.kind) {
            "granted" -> EncryptedChatRequestOutcome.GRANTED
            "rejected" -> EncryptedChatRequestOutcome.REJECTED
            "key_grant" -> EncryptedChatRequestOutcome.GRANTED
            "key_reject" -> EncryptedChatRequestOutcome.REJECTED
            else -> EncryptedChatRequestOutcome.PENDING
        })
    }

    private fun publish() {
        val current = current()
        val hasCurrent = storage.readChatKey(address(current.currentKeyUuid))?.let { it.fill(0); true } ?: false
        mutableState.value = EncryptedChatSessionState(
            currentKeyUuid = current.currentKeyUuid,
            sendingKeyUuid = sendingKeyUuid,
            hasCurrentKey = hasCurrent,
            pending = false,
            canManageKeys = canManage(),
            canRotate = current.ownerUuid == scope.userId,
            requestStatus = requestStatuses.toMap(),
            requests = requestOutcomes(),
            localKeys = storage.listChatKeys(keyScope, scope.streamId).map { EncryptedChatLocalKey(it.keyUuid, it.receivedAt, true) },
            keyRevision = revision,
            deviceId = scope.deviceId,
        )
    }

    suspend fun load() = operation {
        // Reconsider grants previously received before the participant roles were hydrated.
        responses.values.toList().forEach { receiveResponse(it) }
        reconcile()
    }

    suspend fun listLocalKeys(): List<EncryptedChatLocalKey> = operation {
        storage.listChatKeys(keyScope, scope.streamId).map { EncryptedChatLocalKey(it.keyUuid, it.receivedAt, true) }
    }

    suspend fun removeLocalKey(keyUuid: String) = operation {
        current()
        if (!storage.removeChatKey(address(keyUuid))) fail(EncryptedChatFailure.STORAGE_FAILED)
        requestStatuses.remove(keyUuid)
        revision++
        reconcile()
    }

    /** Repeated history/realtime ingestion is safe. Unknown generations create no stored metadata. */
    suspend fun ingest(messages: List<EncryptedChatWireMessage>) = operation {
        messages.filter(::validMessage).forEach { message ->
            when (message.payload.kind) {
                "key_request" -> receiveRequest(message)
                "key_grant", "key_reject" -> {
                    responses[message.uuid] = message
                    receiveResponse(message)
                }
                "key_announced" -> if (issuerAllowed(message.authorId)) {
                    // Catalog current_encryption_key_uuid, rather than history order, selects sending key.
                    responses.values.toList().forEach { receiveResponse(it) }
                }
            }
        }
        reconcile()
    }

    private fun receiveRequest(message: EncryptedChatWireMessage) {
        val payload = message.payload
        if (payload.deviceId.isNullOrBlank() || payload.publicKey.isNullOrBlank()) return
        val publicKey = runCatching { WorkspaceChatCrypto.fromBase64(payload.publicKey) }.getOrNull() ?: return
        if (publicKey.size != 32) return
        if (message.authorId == scope.userId && payload.deviceId == scope.deviceId) {
            val matching = storage.listPendingRequests(address(payload.keyUuid)).filter {
                MessageDigest.isEqual(it.keyPair.publicKey, publicKey)
            }
            val bound = matching.filter { it.requestMessageUuid == message.uuid }
            val candidates = if (bound.isNotEmpty()) bound else matching.filter {
                it.requestMessageUuid == null || it.requestMessageUuid == it.localRequestId
            }
            if (candidates.size > 1) return
            candidates.singleOrNull()?.let { pending ->
                current()
                if (pending.requestMessageUuid != message.uuid &&
                    !storage.bindPendingRequest(address(payload.keyUuid), pending.localRequestId, message.uuid))
                    fail(EncryptedChatFailure.STORAGE_FAILED)
            }
        }
        requests[message.uuid] = message
        responses.values.filter { it.payload.requestMessageUuid == message.uuid }.toList().forEach { receiveResponse(it) }
    }

    private fun correlates(request: EncryptedChatWireMessage, response: EncryptedChatWireMessage): Boolean =
        validMessage(request) && validMessage(response) && issuerAllowed(response.authorId) &&
            response.payload.kind in setOf("key_grant", "key_reject") &&
            request.uuid == response.payload.requestMessageUuid &&
            request.payload.keyUuid == response.payload.keyUuid &&
            request.payload.deviceId == response.payload.recipientDeviceId

    private fun receiveResponse(message: EncryptedChatWireMessage) {
        if (message.uuid in processedResponses || !issuerAllowed(message.authorId)) return
        val payload = message.payload
        if (payload.recipientDeviceId != scope.deviceId || payload.senderDeviceId.isNullOrBlank() ||
            payload.requestMessageUuid.isNullOrBlank()) return
        val keyAddress = address(payload.keyUuid)
        if (storage.readRequestCompletionByMessageUuid(keyAddress, payload.requestMessageUuid) != null) {
            processedResponses += message.uuid
            return
        }
        val pending = storage.listPendingRequests(keyAddress).firstOrNull {
            it.requestMessageUuid == payload.requestMessageUuid
        } ?: return // Response-before-request stays in responses and is retried on binding.
        val request = requests[payload.requestMessageUuid]
        if (request != null && (!correlates(request, message) || request.authorId != scope.userId ||
                !MessageDigest.isEqual(WorkspaceChatCrypto.fromBase64(requireNotNull(request.payload.publicKey)), pending.keyPair.publicKey))) return
        if (payload.kind == "key_reject") {
            current()
            if (!storage.completeReject(keyAddress, pending.localRequestId, payload.requestMessageUuid, message.uuid))
                fail(EncryptedChatFailure.STORAGE_FAILED)
            requestStatuses[payload.keyUuid] = EncryptedChatRequestStatus.REJECTED
        } else {
            val deletionRevision = storage.readChatKeyDeletionRevision(keyAddress)
            val groupKey = try {
                crypto.decryptGroupKeyGrant(pending.keyPair, WorkspaceChatCrypto.fromBase64(payload.content), grantContext(payload))
            } catch (_: Exception) { return }
            try {
                current()
                if (!issuerAllowed(message.authorId)) return
                if (pending.recoveryEnvelope != null && pending.previousGroupKey != null &&
                    !MessageDigest.isEqual(groupKey, pending.previousGroupKey)) {
                    try {
                        crypto.decryptMessage(groupKey, pending.recoveryEnvelope, ChatMessageContext(scope.streamId, payload.keyUuid)).fill(0)
                    } catch (_: ChatCryptoException) {
                        if (!storage.completeReject(keyAddress, pending.localRequestId, payload.requestMessageUuid, message.uuid))
                            fail(EncryptedChatFailure.STORAGE_FAILED)
                        requestStatuses[payload.keyUuid] = EncryptedChatRequestStatus.REJECTED
                        processedResponses += message.uuid
                        return
                    }
                }
                if (!storage.completeGrant(keyAddress, pending.localRequestId, payload.requestMessageUuid, message.uuid,
                        groupKey, expectedDeletionRevision = deletionRevision, replaceExistingKey = pending.recoveryEnvelope != null))
                    fail(EncryptedChatFailure.STORAGE_FAILED)
                requestStatuses[payload.keyUuid] = EncryptedChatRequestStatus.RECEIVED
                revision++
            } finally { groupKey.fill(0) }
        }
        processedResponses += message.uuid
    }

    suspend fun requestKey(
        keyUuid: String? = null,
        recoveryMessage: EncryptedChatWireMessage? = null,
        allowUsableKey: Boolean = false,
    ): EncryptedChatWireMessage = operation {
      requestLocks.getOrPut(address(keyUuid ?: current().currentKeyUuid)) { Mutex() }.withLock {
        val generation = keyUuid ?: current().currentKeyUuid
        if (generation.isBlank()) fail(EncryptedChatFailure.INVALID_MESSAGE)
        val keyAddress = address(generation)
        val deletionRevision = storage.readChatKeyDeletionRevision(keyAddress)
        var recoveryEnvelope: ByteArray? = null
        var previousGroupKey: ByteArray? = null
        if (recoveryMessage != null) {
            if (!validMessage(recoveryMessage) || recoveryMessage.payload.kind != "e2ee" || recoveryMessage.payload.keyUuid != generation)
                fail(EncryptedChatFailure.INVALID_MESSAGE)
            val key = storage.readChatKey(keyAddress)
            if (key != null) {
                try {
                    val envelope = WorkspaceChatCrypto.fromBase64(recoveryMessage.payload.content)
                    val decrypted = runCatching { crypto.decryptMessage(key, envelope, ChatMessageContext(scope.streamId, generation)) }
                    decrypted.getOrNull()?.fill(0)
                    if (decrypted.isSuccess && !allowUsableKey) fail(EncryptedChatFailure.INVALID_MESSAGE)
                    if (decrypted.isFailure) { recoveryEnvelope = envelope; previousGroupKey = key.copyOf() }
                } finally { key.fill(0) }
            }
        }
        try {
            val existing = storage.listPendingRequests(keyAddress).filter {
                recoveryEnvelope == null || (it.recoveryEnvelope?.contentEquals(recoveryEnvelope) == true &&
                    it.previousGroupKey?.contentEquals(previousGroupKey) == true)
            }.minWithOrNull(compareBy<PendingChatKeyRequest> { it.createdAt }.thenBy { it.localRequestId })
            val pending = existing ?: PendingChatKeyRequest(UUID.randomUUID().toString(), null,
                crypto.createRequestKeyPair(), WorkspaceDateTime.nowEpochMillis(), recoveryEnvelope, previousGroupKey).also {
                current()
                if (deletionRevision != storage.readChatKeyDeletionRevision(keyAddress)) fail(EncryptedChatFailure.MISSING_KEY)
                if (!storage.writePendingRequest(keyAddress, it)) fail(EncryptedChatFailure.STORAGE_FAILED)
            }
            val payload = EncryptedChatPayload("key_request", generation, deviceId = scope.deviceId,
                deviceName = scope.deviceName, publicKey = WorkspaceChatCrypto.toBase64(pending.keyPair.publicKey))
            if (pending.requestMessageUuid != null && pending.requestMessageUuid != pending.localRequestId) {
                requestStatuses[generation] = EncryptedChatRequestStatus.REQUESTED
                return@withLock requests[pending.requestMessageUuid] ?: EncryptedChatWireMessage(pending.requestMessageUuid,
                    scope.projectId, scope.streamId, scope.userId, payload)
            }
            val result = dispatch(payload, pending.localRequestId)
            current()
            if (deletionRevision != storage.readChatKeyDeletionRevision(keyAddress)) fail(EncryptedChatFailure.MISSING_KEY)
            if (!storage.bindPendingRequest(keyAddress, pending.localRequestId, result.uuid)) fail(EncryptedChatFailure.STORAGE_FAILED)
            receiveRequest(result)
            if (requestStatuses[generation] !in setOf(EncryptedChatRequestStatus.RECEIVED, EncryptedChatRequestStatus.REJECTED))
                requestStatuses[generation] = EncryptedChatRequestStatus.REQUESTED
            result
        } finally { previousGroupKey?.fill(0) }
      }
    }

    private suspend fun dispatch(payload: EncryptedChatPayload, canonicalUuid: String = UUID.randomUUID().toString(),
                                 rotateKey: Boolean = false, permission: () -> Boolean = { isCurrent() }): EncryptedChatWireMessage {
        val guard = { isCurrent() && permission() }
        if (!guard()) fail(EncryptedChatFailure.FORBIDDEN)
        val result = try { transport.send(payload, canonicalUuid, rotateKey, guard) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail(EncryptedChatFailure.SEND_FAILED) }
        current()
        if (!guard()) fail(EncryptedChatFailure.FORBIDDEN)
        if (!validMessage(result) || result.authorId != scope.userId || result.payload != payload)
            fail(EncryptedChatFailure.INVALID_MESSAGE)
        return result
    }

    private fun pendingRequest(uuid: String): EncryptedChatWireMessage =
        requestOutcomes().firstOrNull { it.message.uuid == uuid && it.outcome == EncryptedChatRequestOutcome.PENDING }?.message
            ?: fail(EncryptedChatFailure.INVALID_MESSAGE)

    private fun grantContext(payload: EncryptedChatPayload) = ChatGrantContext(scope.streamId, payload.keyUuid,
        requireNotNull(payload.requestMessageUuid), requireNotNull(payload.senderDeviceId), requireNotNull(payload.recipientDeviceId))

    private fun IssuedChatKeyResponse.payload() = EncryptedChatPayload(kind, keyUuid, content,
        requestMessageUuid = requestMessageUuid, senderDeviceId = scope.deviceId, recipientDeviceId = recipientDeviceId)

    private fun validateIssuedResponse(response: IssuedChatKeyResponse, request: EncryptedChatWireMessage, kind: String) {
        if (response.kind != kind || response.keyUuid != request.payload.keyUuid ||
            response.requestMessageUuid != request.uuid || response.requestAuthorUuid != request.authorId ||
            response.recipientDeviceId != request.payload.deviceId || response.requestPublicKey != request.payload.publicKey)
            fail(EncryptedChatFailure.INVALID_MESSAGE)
    }

    /** A grant/rejection is one immutable transaction, including an uncertain network attempt. */
    private suspend fun issueResponse(uuid: String, kind: String): EncryptedChatWireMessage =
        issuerLocks.getOrPut(IssuerAddress(keyScope, scope.streamId, uuid)) { Mutex() }.withLock {
            requireManage()
            val request = requests[uuid] ?: fail(EncryptedChatFailure.INVALID_MESSAGE)
            val keyAddress = address(request.payload.keyUuid)
            var issued = storage.readIssuedResponse(keyAddress, uuid)
            if (issued == null) {
                pendingRequest(uuid)
                val deletionRevision = if (kind == "key_grant") storage.readChatKeyDeletionRevision(keyAddress) else null
                val content = if (kind == "key_grant") {
                    val key = storage.readChatKey(keyAddress) ?: fail(EncryptedChatFailure.MISSING_KEY)
                    try {
                        WorkspaceChatCrypto.toBase64(crypto.encryptGroupKeyGrant(key,
                            WorkspaceChatCrypto.fromBase64(requireNotNull(request.payload.publicKey)),
                            ChatGrantContext(scope.streamId, request.payload.keyUuid, request.uuid,
                                scope.deviceId, requireNotNull(request.payload.deviceId))))
                    } finally { key.fill(0) }
                } else ""
                current()
                issued = storage.stageIssuedResponse(keyAddress, IssuedChatKeyResponse(request.payload.keyUuid,
                    uuid, request.authorId, requireNotNull(request.payload.deviceId), requireNotNull(request.payload.publicKey),
                    UUID.randomUUID().toString(), kind, content, deletionRevision = deletionRevision))
                    ?: fail(EncryptedChatFailure.MISSING_KEY)
            }
            val staged = requireNotNull(issued)
            validateIssuedResponse(staged, request, kind)
            val payload = staged.payload()
            fun confirmed(messageUuid: String) = EncryptedChatWireMessage(messageUuid, scope.projectId, scope.streamId, scope.userId, payload)
                .also { responses[it.uuid] = it }
            staged.responseMessageUuid?.let { return@withLock confirmed(it) }

            // History can settle a lost acknowledgement before the user retries the action.
            val observed = responses.values.firstOrNull { correlates(request, it) }
            if (observed != null) {
                if (observed.authorId != scope.userId || observed.payload != payload) fail(EncryptedChatFailure.INVALID_MESSAGE)
                current()
                if (!storage.confirmIssuedResponse(keyAddress, uuid, staged.canonicalUuid, observed.uuid))
                    fail(EncryptedChatFailure.STORAGE_FAILED)
                return@withLock confirmed(observed.uuid)
            }
            val allowed = { canManage() && (staged.deletionRevision == null ||
                staged.deletionRevision == storage.readChatKeyDeletionRevision(keyAddress)) }
            if (!allowed()) fail(EncryptedChatFailure.MISSING_KEY)
            dispatch(payload, staged.canonicalUuid, permission = allowed).also {
                current()
                if (!storage.confirmIssuedResponse(keyAddress, uuid, staged.canonicalUuid, it.uuid))
                    fail(EncryptedChatFailure.STORAGE_FAILED)
                responses[it.uuid] = it
            }
        }

    private suspend fun grant(uuid: String): EncryptedChatWireMessage = issueResponse(uuid, "key_grant")

    suspend fun grantKey(requestMessageUuid: String) = operation { grant(requestMessageUuid) }

    suspend fun rejectKey(requestMessageUuid: String) = operation { issueResponse(requestMessageUuid, "key_reject") }

    suspend fun grantAllKeys(): List<EncryptedChatGrantOutcome> = operation {
        requireManage()
        requestOutcomes().filter { it.outcome == EncryptedChatRequestOutcome.PENDING }.map {
            try { grant(it.message.uuid); EncryptedChatGrantOutcome(it.message.uuid) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                current()
                EncryptedChatGrantOutcome(it.message.uuid, (error as? EncryptedChatException)?.failure ?: EncryptedChatFailure.UNAVAILABLE)
            }
        }
    }

    /** Failed dispatch retains the same staged key and canonical UUID, and sending stays on the old key. */
    suspend fun rotate(): EncryptedChatWireMessage? = operation {
        requireOwner()
        val previous = current().currentKeyUuid
        if (recoveredRotationKeyUuid == previous) { recoveredRotationKeyUuid = null; return@operation null }
        val stage = storage.listStagedCreations(keyScope).firstOrNull {
            it.rotation?.streamUuid == scope.streamId && it.rotation.previousKeyUuid == previous
        } ?: StagedChatCreation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), crypto.createGroupKey(),
            ChatKeyRotation(scope.streamId, previous, defaultTopicUuid() ?: fail(EncryptedChatFailure.INVALID_MESSAGE))).also {
            requireOwner()
            if (!storage.writeStagedCreation(keyScope, it)) fail(EncryptedChatFailure.STORAGE_FAILED)
        }
        try {
            val allowed = { isCurrent() && current().ownerUuid == scope.userId && current().currentKeyUuid in setOf(previous, stage.keyUuid) }
            val result = dispatch(EncryptedChatPayload("key_announced", stage.keyUuid), stage.creationId, true, allowed)
            if (!allowed()) fail(EncryptedChatFailure.STALE)
            // A verified receipt proves this exact announcement was accepted even before a stream event.
            if (current().currentKeyUuid == previous) onCurrentKeyConfirmed(stage.keyUuid)
            if (current().currentKeyUuid != stage.keyUuid) fail(EncryptedChatFailure.STALE)
            requireOwner()
            if (!storage.finalizeCreation(keyScope, stage.creationId, scope.streamId)) fail(EncryptedChatFailure.STORAGE_FAILED)
            revision++
            reconcile()
            result
        } finally { stage.groupKey.fill(0) }
    }

    /** Sending and editing share the ordinary text/Markdown representation before encryption. */
    suspend fun <T> send(text: String, dispatch: suspend (EncryptedChatPayload, () -> Boolean) -> T): T = operation {
        if (text.length > MAX_PLAINTEXT_LENGTH) fail(EncryptedChatFailure.TOO_LONG)
        val generation = sendingKeyUuid ?: fail(EncryptedChatFailure.MISSING_KEY)
        val expectedCurrent = current().currentKeyUuid
        val keyAddress = address(generation)
        val deletionRevision = storage.readChatKeyDeletionRevision(keyAddress)
        val key = storage.readChatKey(keyAddress) ?: fail(EncryptedChatFailure.MISSING_KEY)
        val plaintext = text.toByteArray(Charsets.UTF_8)
        val payload = try {
            EncryptedChatPayload("e2ee", generation,
                WorkspaceChatCrypto.toBase64(crypto.encryptMessage(key, plaintext, ChatMessageContext(scope.streamId, generation))))
        } finally { key.fill(0); plaintext.fill(0) }
        val guard = { isCurrent() && current().currentKeyUuid == expectedCurrent &&
            deletionRevision == storage.readChatKeyDeletionRevision(keyAddress) }
        if (!guard()) fail(EncryptedChatFailure.STALE)
        val result = try { dispatch(payload, guard) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail(EncryptedChatFailure.SEND_FAILED) }
        if (!guard()) fail(EncryptedChatFailure.STALE)
        result
    }

    /** Convenience for transport adapters; prefer send() to preserve the guard through dispatch. */
    suspend fun encryptText(text: String): EncryptedChatPayload = send(text) { payload, guard ->
        if (!guard()) fail(EncryptedChatFailure.STALE)
        payload
    }

    /** Presentation reads never reconcile generations, write storage, or toggle the action spinner. */
    suspend fun decrypt(message: EncryptedChatWireMessage): EncryptedChatPresentation = withContext(dispatcher) {
        mutex.withLock {
            try {
                current()
                if (!validMessage(message)) fail(EncryptedChatFailure.INVALID_MESSAGE)
                val payload = message.payload
                if (payload.kind != "e2ee") return@withLock EncryptedChatPresentation.Service(payload)
                val keyAddress = address(payload.keyUuid)
                val deletionRevision = storage.readChatKeyDeletionRevision(keyAddress)
                val key = storage.readChatKey(keyAddress)
                    ?: return@withLock EncryptedChatPresentation.Locked(payload.keyUuid)
                try {
                    current()
                    val plaintext = try {
                        crypto.decryptMessage(key, WorkspaceChatCrypto.fromBase64(payload.content),
                            ChatMessageContext(scope.streamId, payload.keyUuid))
                    } catch (_: Exception) {
                        current()
                        val removed = deletionRevision != storage.readChatKeyDeletionRevision(keyAddress)
                        return@withLock EncryptedChatPresentation.Locked(payload.keyUuid, invalidCiphertext = !removed)
                    }
                    try {
                        current()
                        if (deletionRevision != storage.readChatKeyDeletionRevision(keyAddress))
                            return@withLock EncryptedChatPresentation.Locked(payload.keyUuid)
                        EncryptedChatPresentation.Text(plaintext.toString(Charsets.UTF_8), payload.keyUuid)
                    } finally { plaintext.fill(0) }
                } finally { key.fill(0) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = when (error) {
                    is EncryptedChatException -> error.failure
                    is ChatKeyStorageException -> EncryptedChatFailure.STORAGE_FAILED
                    else -> EncryptedChatFailure.UNAVAILABLE
                }
                if (isCurrent()) mutableState.value = mutableState.value.copy(error = reason)
                else mutableState.value = EncryptedChatSessionState(keyRevision = revision, error = EncryptedChatFailure.STALE)
                if (error is EncryptedChatException) throw error
                throw EncryptedChatException(reason)
            }
        }
    }

    companion object {
        const val MAX_PLAINTEXT_LENGTH = 40_000
        // A dialog and its info panel can own separate sessions for the same durable address.
        private val requestLocks = ConcurrentHashMap<ChatKeyAddress, Mutex>()
        private data class IssuerAddress(val scope: ChatKeyScope, val streamUuid: String, val requestUuid: String)
        private val issuerLocks = ConcurrentHashMap<IssuerAddress, Mutex>()
    }
}
