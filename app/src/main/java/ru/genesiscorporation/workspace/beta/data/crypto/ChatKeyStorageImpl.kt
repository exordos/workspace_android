package ru.genesiscorporation.workspace.beta.data.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

internal interface ChatSecretRecords {
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
}

/** One authenticated encrypted owner document makes grant completion and rotation promotion atomic. */
internal class ChatKeyStorageImpl(private val records: ChatSecretRecords) : ChatKeyStorage {
    override fun purgeOwner(owner: ChatKeyOwner): Boolean = mutate(owner) { state ->
        // Keep only public retired device IDs. A delayed old writer must not recreate a secret
        // after logout, including after another store instance is opened or the account logs in.
        state.identity?.let { state.retiredDevices += it.deviceId }
        state.retiredDevices += state.keys.values.map { it.deviceId }
        state.retiredDevices += state.creations.values.map { it.deviceId }
        val addresses = state.requests.values.map { it.addressId } + state.completions.values.map { it.addressId } +
            state.issuedResponses.keys + state.creationReceipts.keys
        addresses.forEach { state.retiredDevices += Json.decodeFromString(ListSerializer(String.serializer()), it).first() }
        state.wipeSecrets()
        state.identity = null
        state.keys.clear()
        state.requests.clear()
        state.completions.clear()
        state.issuedResponses.clear()
        state.creations.clear()
        state.creationReceipts.clear()
        true
    }

    override fun ensureDeviceIdentity(owner: ChatKeyOwner, deviceName: String): ChatDeviceIdentity =
        mutate(owner) { state ->
            require(deviceName.isNotBlank())
            (state.identity ?: DeviceRecord(UUID.randomUUID().toString(), deviceName)
                .also { state.identity = it }).let { ChatDeviceIdentity(it.deviceId, it.deviceName) }
        }

    override fun readDeviceIdentity(owner: ChatKeyOwner): ChatDeviceIdentity? = read(owner) { state ->
        state.identity?.let { ChatDeviceIdentity(it.deviceId, it.deviceName) }
    }

    override fun readChatKey(address: ChatKeyAddress): ByteArray? = read(address.scope.owner) {
        it.keys[address.keyId()]?.groupKey?.copyOf()
    }

    override fun readChatKeyDeletionRevision(address: ChatKeyAddress): Long = read(address.scope.owner) {
        it.keys[address.keyId()]?.deletionRevision ?: 0
    }

    override fun listChatKeys(scope: ChatKeyScope, streamUuid: String): List<StoredChatKeyMetadata> =
        read(scope.owner) { state ->
            state.keys.values.filter { it.matches(scope, streamUuid) && it.groupKey != null }
                .map { StoredChatKeyMetadata(it.keyUuid, it.receivedAt) }
                .sortedWith(compareByDescending<StoredChatKeyMetadata> { it.receivedAt }.thenBy { it.keyUuid })
        }

    override fun readLastAvailableChatKey(scope: ChatKeyScope, streamUuid: String): Pair<String, ByteArray>? =
        read(scope.owner) { state ->
            state.keys.values.filter { it.matches(scope, streamUuid) && it.groupKey != null }
                .maxWithOrNull(compareBy<KeyRecord> { it.sendOrder }.thenBy { it.receivedAt })
                ?.let { it.keyUuid to it.groupKey!!.copyOf() }
        }

    override fun writeChatKey(address: ChatKeyAddress, groupKey: ByteArray, forSending: Boolean,
                              expectedDeletionRevision: Long?, onlyIfPresent: Boolean): Boolean =
        mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate false
            validKey(groupKey)
            val previous = state.keys[address.keyId()]
            if (!revisionMatches(previous, expectedDeletionRevision) || (onlyIfPresent && previous?.groupKey == null)) {
                return@mutate false
            }
            compatible(previous?.groupKey, groupKey)
            val nextOrder = if (forSending) state.keys.values.filter { it.matches(address.scope, address.streamUuid) }
                .maxOfOrNull { it.sendOrder }?.plus(1) ?: 1 else previous?.sendOrder ?: 0
            state.keys[address.keyId()] = keyRecord(address, previous, groupKey, nextOrder)
            true
        }

    override fun removeChatKey(address: ChatKeyAddress): Boolean = mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate false
        val previous = state.keys[address.keyId()]
        state.keys[address.keyId()] = KeyRecord(address.scope.deviceId, address.streamUuid,
            address.keyUuid, null, previous?.receivedAt ?: 0, previous?.sendOrder ?: 0,
            (previous?.deletionRevision ?: 0) + 1)
        // Deleting a local generation also destroys old pending private keys, preventing late resurrection.
        state.requests.entries.removeAll { it.value.addressId == address.keyId() }
        true
    }

    override fun listPendingRequests(address: ChatKeyAddress): List<PendingChatKeyRequest> = read(address.scope.owner) {
        it.requests.values.filter { record -> record.addressId == address.keyId() }.map(RequestRecord::toDomain)
            .sortedBy { request -> request.createdAt }
    }

    override fun writePendingRequest(address: ChatKeyAddress, request: PendingChatKeyRequest): Boolean =
        mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate false
            validKey(request.keyPair.publicKey)
            validKey(request.keyPair.privateKey)
            require(request.localRequestId.isNotBlank() && request.createdAt >= 0 &&
                request.requestMessageUuid?.isBlank() != true)
            require((request.recoveryEnvelope == null) == (request.previousGroupKey == null))
            request.previousGroupKey?.let(::validKey)
            val id = address.requestId(request.localRequestId)
            if (state.completions.containsKey(id)) return@mutate false
            val previous = state.requests[id]
            if (previous != null && (!equal(previous.publicKey, request.keyPair.publicKey) ||
                    !equal(previous.privateKey, request.keyPair.privateKey) ||
                    (previous.requestMessageUuid != null && previous.requestMessageUuid != request.requestMessageUuid))) conflict()
            state.requests[id] = RequestRecord(address.keyId(), request.localRequestId,
                previous?.requestMessageUuid ?: request.requestMessageUuid, request.keyPair.publicKey.copyOf(),
                request.keyPair.privateKey.copyOf(), request.createdAt, request.recoveryEnvelope?.copyOf(),
                request.previousGroupKey?.copyOf(), state.keys[address.keyId()]?.deletionRevision ?: 0)
            true
        }

    override fun bindPendingRequest(address: ChatKeyAddress, localRequestId: String,
                                    requestMessageUuid: String): Boolean = mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate false
        require(requestMessageUuid.isNotBlank())
        val id = address.requestId(localRequestId)
        val pending = state.requests[id] ?: return@mutate false
        if (pending.requestMessageUuid != null && pending.requestMessageUuid != requestMessageUuid) conflict()
        state.requests[id] = pending.copy(requestMessageUuid = requestMessageUuid)
        true
    }

    override fun readRequestCompletion(address: ChatKeyAddress, localRequestId: String): ChatKeyRequestCompletion? =
        read(address.scope.owner) { it.completions[address.requestId(localRequestId)]?.toDomain() }

    override fun readRequestCompletionByMessageUuid(address: ChatKeyAddress,
                                                    requestMessageUuid: String): ChatKeyRequestCompletion? =
        read(address.scope.owner) { state ->
            state.completions.values.firstOrNull { it.addressId == address.keyId() &&
                it.requestMessageUuid == requestMessageUuid }?.toDomain()
        }

    override fun completeGrant(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String,
                               responseMessageUuid: String, groupKey: ByteArray,
                               expectedDeletionRevision: Long?, replaceExistingKey: Boolean): Boolean =
        complete(address, localRequestId, requestMessageUuid, responseMessageUuid, "granted", groupKey,
            expectedDeletionRevision, replaceExistingKey)

    override fun completeReject(address: ChatKeyAddress, localRequestId: String,
                                requestMessageUuid: String, responseMessageUuid: String): Boolean =
        complete(address, localRequestId, requestMessageUuid, responseMessageUuid, "rejected", null, null, false)

    override fun readIssuedResponse(address: ChatKeyAddress, requestMessageUuid: String): IssuedChatKeyResponse? =
        read(address.scope.owner) { it.issuedResponses[address.issuedResponseId(requestMessageUuid)] }

    override fun stageIssuedResponse(address: ChatKeyAddress, response: IssuedChatKeyResponse): IssuedChatKeyResponse? =
        mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate null
            require(response.keyUuid == address.keyUuid && response.requestMessageUuid.isNotBlank() &&
                response.requestAuthorUuid.isNotBlank() && response.recipientDeviceId.isNotBlank() &&
                response.requestPublicKey.isNotBlank() && response.canonicalUuid.isNotBlank() &&
                response.responseMessageUuid == null && response.kind in setOf("key_grant", "key_reject"))
            require(if (response.kind == "key_grant") response.content.isNotBlank() && response.deletionRevision != null
                else response.content.isEmpty() && response.deletionRevision == null)
            val id = address.issuedResponseId(response.requestMessageUuid)
            state.issuedResponses[id]?.let { return@mutate it }
            if (!revisionMatches(state.keys[address.keyId()], response.deletionRevision)) return@mutate null
            state.issuedResponses[id] = response
            response
        }

    override fun confirmIssuedResponse(address: ChatKeyAddress, requestMessageUuid: String,
                                       canonicalUuid: String, responseMessageUuid: String): Boolean =
        mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate false
            require(responseMessageUuid.isNotBlank())
            val id = address.issuedResponseId(requestMessageUuid)
            val staged = state.issuedResponses[id] ?: return@mutate false
            if (staged.keyUuid != address.keyUuid || staged.canonicalUuid != canonicalUuid ||
                (staged.responseMessageUuid != null && staged.responseMessageUuid != responseMessageUuid)) return@mutate false
            state.issuedResponses[id] = staged.copy(responseMessageUuid = responseMessageUuid)
            true
        }

    private fun complete(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String,
                         responseMessageUuid: String, outcome: String, groupKey: ByteArray?,
                         expectedDeletionRevision: Long?, replaceExistingKey: Boolean): Boolean =
        mutate(address.scope.owner) { state ->
            if (address.scope.deviceId in state.retiredDevices) return@mutate false
            require(requestMessageUuid.isNotBlank() && responseMessageUuid.isNotBlank())
            groupKey?.let(::validKey)
            val id = address.requestId(localRequestId)
            val receipt = state.completions[id]
            if (receipt != null) return@mutate receipt.requestMessageUuid == requestMessageUuid &&
                receipt.responseMessageUuid == responseMessageUuid && receipt.outcome == outcome
            val pending = state.requests[id] ?: return@mutate false
            if (pending.requestMessageUuid != requestMessageUuid) conflict()
            val previous = state.keys[address.keyId()]
            if (!revisionMatches(previous, expectedDeletionRevision) ||
                (previous?.deletionRevision ?: 0) != pending.deletionRevision) return@mutate false
            if (groupKey != null) {
                if (replaceExistingKey) {
                    val recoveryKey = pending.previousGroupKey ?: conflict()
                    if (pending.recoveryEnvelope == null || previous?.groupKey == null ||
                        (!equal(previous.groupKey, recoveryKey) && !equal(previous.groupKey, groupKey))) conflict()
                } else compatible(previous?.groupKey, groupKey)
                state.keys[address.keyId()] = keyRecord(address, previous, groupKey, previous?.sendOrder ?: 0)
            }
            state.completions[id] = CompletionRecord(address.keyId(), localRequestId,
                requestMessageUuid, responseMessageUuid, outcome)
            state.requests.remove(id)
            true
        }

    override fun listStagedCreations(scope: ChatKeyScope): List<StagedChatCreation> = read(scope.owner) { state ->
        validScope(scope)
        state.creations.values.filter { it.deviceId == scope.deviceId }.map(CreationRecord::toDomain)
    }

    override fun writeStagedCreation(scope: ChatKeyScope, creation: StagedChatCreation): Boolean =
        mutate(scope.owner) { state ->
            if (scope.deviceId in state.retiredDevices) return@mutate false
            validKey(creation.groupKey)
            require(creation.keyUuid.isNotBlank())
            creation.rotation?.let { require(it.streamUuid.isNotBlank() && it.previousKeyUuid.isNotBlank() && it.topicUuid.isNotBlank()) }
            val id = scope.creationId(creation.creationId)
            if (state.creationReceipts.containsKey(id)) return@mutate false
            val previous = state.creations[id]
            if (previous != null && (previous.keyUuid != creation.keyUuid ||
                    previous.rotation?.toDomain() != creation.rotation)) conflict()
            compatible(previous?.groupKey, creation.groupKey)
            state.creations[id] = CreationRecord(scope.deviceId, creation.creationId, creation.keyUuid,
                creation.groupKey.copyOf(), creation.rotation?.let {
                    RotationRecord(it.streamUuid, it.previousKeyUuid, it.topicUuid)
                })
            true
        }

    override fun finalizeCreation(scope: ChatKeyScope, creationId: String, streamUuid: String): Boolean =
        mutate(scope.owner) { state ->
            if (scope.deviceId in state.retiredDevices) return@mutate false
            require(streamUuid.isNotBlank())
            val id = scope.creationId(creationId)
            val receipt = state.creationReceipts[id]
            if (receipt != null) return@mutate receipt.streamUuid == streamUuid
            val creation = state.creations[id] ?: return@mutate false
            if (creation.rotation != null && creation.rotation.streamUuid != streamUuid) conflict()
            val address = ChatKeyAddress(scope, streamUuid, creation.keyUuid)
            val previous = state.keys[address.keyId()]
            // An API response or retry arriving after local deletion cannot restore that generation.
            if (previous?.groupKey == null && (previous?.deletionRevision ?: 0) > 0) return@mutate false
            compatible(previous?.groupKey, creation.groupKey)
            val nextOrder = state.keys.values.filter { it.matches(scope, streamUuid) }
                .maxOfOrNull { it.sendOrder }?.plus(1) ?: 1
            state.keys[address.keyId()] = keyRecord(address, previous, creation.groupKey, nextOrder)
            state.creations.remove(id)
            state.creationReceipts[id] = CreationReceipt(streamUuid, creation.keyUuid)
            true
        }

    override fun discardStagedCreation(scope: ChatKeyScope, creationId: String): Boolean =
        mutate(scope.owner) { scope.deviceId !in it.retiredDevices && it.creations.remove(scope.creationId(creationId)) != null }

    private fun <T> read(owner: ChatKeyOwner, operation: (OwnerSecrets) -> T): T = synchronized(lock) {
        val state = load(owner)
        try { operation(state) } finally { state.wipeSecrets() }
    }

    private fun <T> mutate(owner: ChatKeyOwner, operation: (OwnerSecrets) -> T): T = synchronized(lock) {
        val state = load(owner)
        try {
            val result = operation(state)
            val encoded = Json.encodeToString(OwnerSecrets.serializer(), state)
            if (!records.write(owner.storageId(), encoded)) throw ChatKeyStorageException("unavailable")
            result
        } finally { state.wipeSecrets() }
    }

    private fun OwnerSecrets.wipeSecrets() {
        keys.values.forEach { it.groupKey?.fill(0) }
        requests.values.forEach {
            it.privateKey.fill(0)
            it.previousGroupKey?.fill(0)
        }
        creations.values.forEach { it.groupKey.fill(0) }
    }

    private fun load(owner: ChatKeyOwner): OwnerSecrets {
        val encoded = records.read(owner.storageId()) ?: return OwnerSecrets()
        return try {
            Json.decodeFromString(OwnerSecrets.serializer(), encoded).also { state ->
                if (state.schema != 1) throw ChatKeyStorageException("invalid-record")
                state.keys.values.forEach { it.groupKey?.let(::validKey) }
            }
        } catch (_: Exception) { throw ChatKeyStorageException("invalid-record") }
    }

    private fun keyRecord(address: ChatKeyAddress, previous: KeyRecord?, groupKey: ByteArray,
                          sendOrder: Long): KeyRecord = KeyRecord(address.scope.deviceId,
        address.streamUuid, address.keyUuid, groupKey.copyOf(),
        if (previous?.groupKey != null) previous.receivedAt else System.currentTimeMillis(),
        sendOrder, previous?.deletionRevision ?: 0)

    private fun KeyRecord.matches(scope: ChatKeyScope, stream: String): Boolean {
        validScope(scope)
        require(stream.isNotBlank())
        return deviceId == scope.deviceId && streamUuid == stream
    }

    private fun revisionMatches(previous: KeyRecord?, revision: Long?): Boolean =
        revision == null || (previous?.deletionRevision ?: 0) == revision

    private fun ChatKeyOwner.storageId(): String = listKey(listOf(serverUrl.trimEnd('/'), projectUuid, userUuid))
    private fun ChatKeyAddress.keyId(): String {
        validScope(scope)
        require(streamUuid.isNotBlank() && keyUuid.isNotBlank())
        return listKey(listOf(scope.deviceId, streamUuid, keyUuid))
    }
    private fun ChatKeyAddress.requestId(localId: String): String {
        require(localId.isNotBlank())
        return listKey(listOf(keyId(), localId))
    }
    private fun ChatKeyAddress.issuedResponseId(requestMessageUuid: String): String {
        validScope(scope)
        require(streamUuid.isNotBlank() && requestMessageUuid.isNotBlank())
        // A mutable request cannot obtain a second decision by changing its generation.
        return listKey(listOf(scope.deviceId, streamUuid, requestMessageUuid))
    }
    private fun ChatKeyScope.creationId(creationId: String): String {
        validScope(this)
        require(creationId.isNotBlank())
        return listKey(listOf(deviceId, creationId))
    }
    private fun listKey(values: List<String>): String = Json.encodeToString(ListSerializer(String.serializer()), values)
    private fun validScope(scope: ChatKeyScope) = require(scope.deviceId.isNotBlank())
    private fun validKey(key: ByteArray) { if (key.size != 32) throw ChatKeyStorageException("invalid-record") }
    private fun equal(left: ByteArray, right: ByteArray) = MessageDigest.isEqual(left, right)
    private fun compatible(previous: ByteArray?, incoming: ByteArray) {
        if (previous != null && !equal(previous, incoming)) conflict()
    }
    private fun conflict(): Nothing = throw ChatKeyStorageException("conflict")

    private companion object { val lock = Any() }
}

@Serializable
private data class OwnerSecrets(
    val schema: Int = 1,
    var identity: DeviceRecord? = null,
    val retiredDevices: MutableSet<String> = mutableSetOf(),
    val keys: MutableMap<String, KeyRecord> = mutableMapOf(),
    val requests: MutableMap<String, RequestRecord> = mutableMapOf(),
    val completions: MutableMap<String, CompletionRecord> = mutableMapOf(),
    val issuedResponses: MutableMap<String, IssuedChatKeyResponse> = mutableMapOf(),
    val creations: MutableMap<String, CreationRecord> = mutableMapOf(),
    val creationReceipts: MutableMap<String, CreationReceipt> = mutableMapOf(),
)

@Serializable
private data class DeviceRecord(val deviceId: String, val deviceName: String)
@Serializable
private data class KeyRecord(val deviceId: String, val streamUuid: String, val keyUuid: String,
                             val groupKey: ByteArray?, val receivedAt: Long, val sendOrder: Long,
                             val deletionRevision: Long)
@Serializable
private data class RequestRecord(val addressId: String, val localRequestId: String,
                                 val requestMessageUuid: String?, val publicKey: ByteArray,
                                 val privateKey: ByteArray, val createdAt: Long,
                                 val recoveryEnvelope: ByteArray?, val previousGroupKey: ByteArray?,
                                 val deletionRevision: Long) {
    fun toDomain() = PendingChatKeyRequest(localRequestId, requestMessageUuid,
        ChatRequestKeyPair(publicKey, privateKey), createdAt, recoveryEnvelope?.copyOf(), previousGroupKey?.copyOf())
}
@Serializable
private data class CompletionRecord(val addressId: String, val localRequestId: String,
                                    val requestMessageUuid: String, val responseMessageUuid: String,
                                    val outcome: String) {
    fun toDomain() = ChatKeyRequestCompletion(localRequestId, requestMessageUuid, responseMessageUuid, outcome)
}
@Serializable
private data class RotationRecord(val streamUuid: String, val previousKeyUuid: String, val topicUuid: String) {
    fun toDomain() = ChatKeyRotation(streamUuid, previousKeyUuid, topicUuid)
}
@Serializable
private data class CreationRecord(val deviceId: String, val creationId: String, val keyUuid: String,
                                  val groupKey: ByteArray, val rotation: RotationRecord?) {
    fun toDomain() = StagedChatCreation(creationId, keyUuid, groupKey.copyOf(), rotation?.toDomain())
}
@Serializable
private data class CreationReceipt(val streamUuid: String, val keyUuid: String)
