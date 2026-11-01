package com.exordos.workspace.modules.encryptedchat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.data.crypto.*
import java.util.UUID

class EncryptedChatSessionTest {
    private val crypto = WorkspaceChatCrypto()
    private val scope = EncryptedChatScope("https://workspace.example", "project", "reader", 1, "android", "Android", "stream")

    @Test fun `reopened session restores a pending historical generation without a local key or history`() = runBlocking {
        val f = Fixture()
        val request = f.session.requestKey("historical")
        assertNull(f.storage.readChatKey(f.address("historical")))
        f.session.close()
        f.session = f.newSession()
        f.session.load()
        assertEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["historical"])
        f.session.ingest(listOf(request))
        assertEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["historical"])
        assertEquals(request.uuid, f.session.requestKey("historical").uuid)
        assertEquals(1, f.sent.size)
    }

    @Test fun `generation identifier from another stream cannot make the owner issue its key`() = runBlocking {
        val f = Fixture(user = "owner")
        f.storage.writeChatKey(f.address("generation").copy(streamUuid = "another-stream"), crypto.createGroupKey())
        val pair = crypto.createRequestKeyPair()
        val request = f.wire("remote-request", "reader", EncryptedChatPayload("key_request", "generation",
            deviceId = "remote-device", deviceName = "Android", publicKey = WorkspaceChatCrypto.toBase64(pair.publicKey)))
        f.session.ingest(listOf(request))
        expectFailure(EncryptedChatFailure.MISSING_KEY) { f.session.grantKey(request.uuid) }
        assertTrue(f.sent.isEmpty())
    }

    @Test fun `second owner device requests and receives a key from the first owner device`() = runBlocking {
        val first = Fixture(user = "owner", device = "first-owner-device")
        val second = Fixture(user = "owner", device = "second-owner-device")
        val key = crypto.createGroupKey()
        first.storage.writeChatKey(first.address("generation"), key, forSending = true)
        second.session.load()
        assertTrue(second.session.state.value.canManageKeys)
        assertFalse(second.session.state.value.hasCurrentKey)
        val request = second.session.requestKey()
        assertEquals(first.scope.userId, request.authorId)
        assertNotEquals(first.scope.deviceId, request.payload.deviceId)
        first.session.ingest(listOf(request))
        val grant = first.session.grantKey(request.uuid)
        second.session.ingest(listOf(grant))
        assertArrayEquals(key, second.storage.readChatKey(second.address("generation")))
        assertTrue(second.session.state.value.hasCurrentKey)
        assertEquals(EncryptedChatRequestStatus.RECEIVED, second.session.state.value.requestStatus["generation"])
        second.session.removeLocalKey("generation")
        assertNull(second.session.state.value.requestStatus["generation"])
        val replacement = second.session.requestKey()
        assertNotEquals(request.uuid, replacement.uuid)
        first.session.ingest(listOf(replacement))
        second.session.ingest(listOf(first.session.grantKey(replacement.uuid)))
        assertArrayEquals(key, second.storage.readChatKey(second.address("generation")))
        key.fill(0)
    }

    @Test fun `deletion in another panel clears received and pending statuses in the mounted session`() = runBlocking {
        listOf(false, true).forEach { granted ->
            val f = Fixture(user = "owner")
            val request = f.session.requestKey()
            if (granted) f.session.ingest(listOf(f.grant(request, crypto.createGroupKey())))
            val other = f.newSession()
            other.removeLocalKey("generation")
            f.session.load()
            assertFalse(f.session.state.value.hasCurrentKey)
            assertNull(f.session.state.value.requestStatus["generation"])
            assertNotEquals(request.uuid, f.session.requestKey().uuid)
        }
    }

    @Test fun `replacement requested in another panel updates the mounted session without a duplicate send`() = runBlocking {
        val f = Fixture(user = "owner")
        val request = f.session.requestKey()
        f.session.ingest(listOf(f.grant(request, crypto.createGroupKey())))
        val other = f.newSession()
        other.removeLocalKey("generation")
        val replacement = other.requestKey()
        f.session.load()
        assertEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["generation"])
        assertEquals(replacement.uuid, f.session.requestKey().uuid)
        assertEquals(2, f.sent.size)
    }

    @Test fun `history uses its generation and plaintext never replaces wire content`() = runBlocking {
        val f = Fixture()
        val oldKey = crypto.createGroupKey()
        f.storage.writeChatKey(f.address("old"), oldKey, forSending = true)
        f.access = f.access.copy(currentKeyUuid = "new")
        val wire = f.encrypted("old", "**quoted** text and [file](urn:file:file-uuid)", oldKey)
        val original = wire.payload.content
        f.session.load()
        assertEquals("old", f.session.state.value.sendingKeyUuid)
        val decrypted = f.session.decrypt(wire) as EncryptedChatPresentation.Text
        assertEquals("**quoted** text and [file](urn:file:file-uuid)", decrypted.text)
        assertEquals(original, wire.payload.content)
        assertNotEquals(decrypted.text, original)
        assertEquals("old", f.session.encryptText("reply").keyUuid)
        val current = crypto.createGroupKey()
        f.storage.writeChatKey(f.address("new"), current)
        f.session.load()
        assertEquals("new", f.session.encryptText("next").keyUuid)
        assertEquals(decrypted, f.session.decrypt(wire))
    }

    @Test fun `presentation reads never publish pending or perform reconciliation transactions`() = runBlocking {
        val f = Fixture(user = "owner")
        val key = crypto.createGroupKey()
        f.storage.writeChatKey(f.address("generation"), key)
        f.session.load()
        val before = f.session.state.value
        val observed = mutableListOf<EncryptedChatSessionState>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            f.session.state.collect { observed += it }
        }
        val writesBefore = f.storage.keyWrites
        val stagesBefore = f.storage.stageReads
        repeat(10) {
            assertTrue(f.session.decrypt(f.encrypted("generation", "presentation only", key)) is EncryptedChatPresentation.Text)
        }
        val corrupt = f.encrypted("generation", "text", key).let { it.copy(payload = it.payload.copy(content = "invalid-base64")) }
        assertTrue(f.session.decrypt(corrupt) is EncryptedChatPresentation.Locked)
        collector.cancelAndJoin()
        assertEquals(before, f.session.state.value)
        assertEquals(listOf(before), observed)
        assertFalse(observed.any { it.pending })
        assertEquals(writesBefore, f.storage.keyWrites)
        assertEquals(stagesBefore, f.storage.stageReads)
    }

    @Test fun `lost request receipt retries its durable canonical uuid and key pair then suppresses duplicates`() = runBlocking {
        val f = Fixture()
        f.failSend = true
        expectFailure(EncryptedChatFailure.SEND_FAILED) { f.session.requestKey() }
        val first = f.sent.single()
        val persisted = f.storage.listPendingRequests(f.address("generation")).single()
        assertNull(persisted.requestMessageUuid)
        assertEquals(first.second, persisted.localRequestId)
        f.session.load()
        assertNotEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["generation"])
        f.failSend = false
        f.session = f.newSession()
        f.session.load()
        assertNotEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["generation"])
        val response = f.session.requestKey()
        assertEquals(2, f.sent.size)
        assertEquals(first, f.sent.last())
        assertEquals("placement-${first.second}", response.uuid)
        f.session.requestKey()
        assertEquals(2, f.sent.size)
        assertEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["generation"])
    }

    @Test fun `two mounted panels suppress simultaneous requests for one durable address`() = runBlocking {
        val f = Fixture()
        val finish = CompletableDeferred<Unit>()
        f.beforeReceipt = { finish.await() }
        val other = f.newSession()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { f.session.requestKey() }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { other.requestKey() }
        assertEquals(1, f.sent.size)
        finish.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, f.sent.size)
        assertEquals(1, f.storage.listPendingRequests(f.address("generation")).size)
    }

    @Test fun `grant before request binds from own history with exact correlation and imports once`() = runBlocking {
        val f = Fixture()
        f.failSend = true
        expectFailure(EncryptedChatFailure.SEND_FAILED) { f.session.requestKey() }
        val pending = f.storage.listPendingRequests(f.address("generation")).single()
        val request = f.wire("request-placement", "reader", f.sent.single().first)
        val group = crypto.createGroupKey()
        val grant = f.grant(request, group)
        f.session.ingest(listOf(grant))
        assertNull(f.storage.readChatKey(f.address("generation")))
        f.session.ingest(listOf(request))
        assertArrayEquals(group, f.storage.readChatKey(f.address("generation")))
        assertTrue(f.storage.listPendingRequests(f.address("generation")).isEmpty())
        assertNotNull(f.storage.readRequestCompletion(f.address("generation"), pending.localRequestId))
        val revision = f.session.state.value.keyRevision
        f.session.ingest(listOf(request, grant, grant))
        assertEquals(revision, f.session.state.value.keyRevision)
        assertEquals(EncryptedChatRequestStatus.RECEIVED, f.session.state.value.requestStatus["generation"])
    }

    @Test fun `invalid correlated grant does not mark own request received or prevent a valid grant`() = runBlocking {
        val f = Fixture()
        val request = f.session.requestKey()
        val key = crypto.createGroupKey()
        val valid = f.grant(request, key)
        val invalid = valid.copy(uuid = "invalid-grant", payload = valid.payload.copy(content = WorkspaceChatCrypto.toBase64(ByteArray(85))))
        f.session.ingest(listOf(invalid))
        assertNull(f.storage.readChatKey(f.address("generation")))
        assertEquals(EncryptedChatRequestOutcome.PENDING, f.session.state.value.requests.single().outcome)
        assertEquals(EncryptedChatRequestStatus.REQUESTED, f.session.state.value.requestStatus["generation"])
        f.session.ingest(listOf(valid))
        assertArrayEquals(key, f.storage.readChatKey(f.address("generation")))
        assertEquals(EncryptedChatRequestOutcome.GRANTED, f.session.state.value.requests.single().outcome)
    }

    @Test fun `rejection received before request receipt retains rejected status after deferred binding`() = runBlocking {
        val f = Fixture()
        f.failSend = true
        expectFailure(EncryptedChatFailure.SEND_FAILED) { f.session.requestKey() }
        val canonical = f.sent.single().second
        val reject = f.wire("rejection", "owner", EncryptedChatPayload("key_reject", "generation",
            requestMessageUuid = "placement-$canonical", senderDeviceId = "owner-device", recipientDeviceId = f.scope.deviceId))
        f.session.ingest(listOf(reject))
        f.failSend = false
        f.session.requestKey()
        assertEquals(EncryptedChatRequestStatus.REJECTED, f.session.state.value.requestStatus["generation"])
        assertEquals(EncryptedChatRequestOutcome.REJECTED, f.session.state.value.requests.single().outcome)
        assertTrue(f.storage.listPendingRequests(f.address("generation")).isEmpty())
    }

    @Test fun `unauthorized wrong recipient and wrong generation grants do not release a key`() = runBlocking {
        val f = Fixture()
        val request = f.session.requestKey()
        val group = crypto.createGroupKey()
        val grant = f.grant(request, group)
        f.session.ingest(listOf(grant.copy(authorId = "ordinary-member")))
        f.session.ingest(listOf(grant.copy(uuid = "wrong-recipient", payload = grant.payload.copy(recipientDeviceId = "other-device"))))
        f.session.ingest(listOf(grant.copy(uuid = "wrong-generation", payload = grant.payload.copy(keyUuid = "other-generation"))))
        f.session.ingest(listOf(grant.copy(uuid = "wrong-reference", payload = grant.payload.copy(requestMessageUuid = "another-request"))))
        assertNull(f.storage.readChatKey(f.address("generation")))
        assertEquals(1, f.storage.listPendingRequests(f.address("generation")).size)
        f.session.ingest(listOf(grant))
        assertArrayEquals(group, f.storage.readChatKey(f.address("generation")))
    }

    @Test fun `delete immediately removes sending and plaintext and replayed grant cannot restore it`() = runBlocking {
        val f = Fixture()
        val request = f.session.requestKey()
        val key = crypto.createGroupKey()
        val grant = f.grant(request, key)
        f.session.ingest(listOf(grant))
        val message = f.encrypted("generation", "private text", key)
        assertTrue(f.session.decrypt(message) is EncryptedChatPresentation.Text)
        val revision = f.session.state.value.keyRevision
        f.session.removeLocalKey("generation")
        assertTrue(f.session.state.value.keyRevision > revision)
        assertNull(f.session.state.value.sendingKeyUuid)
        assertTrue(f.session.decrypt(message) is EncryptedChatPresentation.Locked)
        f.session.ingest(listOf(grant))
        assertNull(f.storage.readChatKey(f.address("generation")))
        expectFailure(EncryptedChatFailure.MISSING_KEY) { f.session.encryptText("retry") }
    }

    @Test fun `rotation keeps sending old generation until receipt and reuses durable stage on retry`() = runBlocking {
        val f = Fixture(user = "owner")
        val old = crypto.createGroupKey()
        f.storage.writeChatKey(f.address("generation"), old, forSending = true)
        f.session.load()
        f.failSend = true
        expectFailure(EncryptedChatFailure.SEND_FAILED) { f.session.rotate() }
        val stage = f.storage.listStagedCreations(f.keyScope).single()
        assertEquals("generation", f.session.state.value.sendingKeyUuid)
        assertNull(f.storage.readChatKey(f.address(stage.keyUuid)))
        val first = f.sent.single()
        f.failSend = false
        f.session = f.newSession()
        val announcement = f.session.rotate()
        assertEquals(first, f.sent.last())
        assertEquals(stage.keyUuid, announcement?.payload?.keyUuid)
        assertEquals(stage.keyUuid, f.session.state.value.sendingKeyUuid)
        assertNotNull(f.storage.readChatKey(f.address("generation")))
        assertNotNull(f.storage.readChatKey(f.address(stage.keyUuid)))
        assertTrue(f.storage.listStagedCreations(f.keyScope).isEmpty())
    }

    @Test fun `interrupted creation only promotes into its canonical stream despite matching owner and generation`() = runBlocking {
        val f = Fixture(user = "owner")
        val originalStreamId = "00000000-0000-0000-0000-000000000010"
        val stagedKey = crypto.createGroupKey()
        f.storage.writeStagedCreation(f.keyScope, StagedChatCreation(originalStreamId, "generation", stagedKey))

        // Another stream owned by this account can advertise the same public generation identifier.
        f.session.load()
        assertNull(f.storage.readChatKey(f.address("generation")))
        assertFalse(f.session.state.value.hasCurrentKey)
        val retained = f.storage.listStagedCreations(f.keyScope).single()
        assertEquals(originalStreamId, retained.creationId)
        assertArrayEquals(stagedKey, retained.groupKey)

        val originalScope = f.scope.copy(streamId = originalStreamId)
        val originalAccess = f.access.copy(scope = originalScope)
        val originalSession = EncryptedChatSession(
            scope = originalScope,
            storage = f.storage,
            access = { originalAccess },
            transport = EncryptedChatServiceTransport { _, _, _, _ -> error("Creation recovery must not send a request") },
            defaultTopicUuid = { "default-topic" },
            crypto = crypto,
            dispatcher = Dispatchers.Unconfined,
        )
        originalSession.load()

        assertTrue(originalSession.state.value.hasCurrentKey)
        assertEquals("generation", originalSession.state.value.sendingKeyUuid)
        assertArrayEquals(stagedKey, f.storage.readChatKey(ChatKeyAddress(f.keyScope, originalStreamId, "generation")))
        assertTrue(f.storage.listStagedCreations(f.keyScope).isEmpty())
        assertNull(f.storage.readChatKey(f.address("generation")))
    }

    @Test fun `administrator can grant and reject but only stream owner can rotate`() = runBlocking {
        val f = Fixture(user = "administrator")
        f.storage.writeChatKey(f.address("generation"), crypto.createGroupKey())
        val pair = crypto.createRequestKeyPair()
        val request = f.wire("remote-request", "member", EncryptedChatPayload("key_request", "generation",
            deviceId = "remote-device", deviceName = "Phone", publicKey = WorkspaceChatCrypto.toBase64(pair.publicKey)))
        f.session.ingest(listOf(request))
        val grant = f.session.grantKey(request.uuid)
        assertEquals("key_grant", grant.payload.kind)
        assertEquals(request.uuid, grant.payload.requestMessageUuid)
        assertEquals("remote-device", grant.payload.recipientDeviceId)
        val received = crypto.decryptGroupKeyGrant(pair, WorkspaceChatCrypto.fromBase64(grant.payload.content),
            ChatGrantContext("stream", "generation", request.uuid, "android", "remote-device"))
        assertArrayEquals(f.storage.readChatKey(f.address("generation")), received)
        expectFailure(EncryptedChatFailure.FORBIDDEN) { f.session.rotate() }
        val second = request.copy(uuid = "second-request")
        f.session.ingest(listOf(second))
        assertEquals("key_reject", f.session.rejectKey(second.uuid).payload.kind)
    }

    @Test fun `scope change while dispatching prevents old account result from becoming available`() = runBlocking {
        val f = Fixture()
        f.onSend = { f.access = f.access.copy(scope = f.scope.copy(userId = "another-account", runtimeGeneration = 2)) }
        expectFailure(EncryptedChatFailure.STALE) { f.session.requestKey() }
        assertNull(f.storage.readChatKey(f.address("generation")))
        assertEquals(EncryptedChatFailure.STALE, f.session.state.value.error)
        assertFalse(f.session.isCurrent())
    }

    @Test fun `membership revocation removes access despite a retained local key`() = runBlocking {
        val f = Fixture()
        val key = crypto.createGroupKey()
        f.storage.writeChatKey(f.address("generation"), key)
        val encrypted = f.encrypted("generation", "private text", key)
        f.session.load()
        assertTrue(f.session.decrypt(encrypted) is EncryptedChatPresentation.Text)
        f.access = f.access.copy(isMember = false)
        expectFailure(EncryptedChatFailure.STALE) { f.session.decrypt(encrypted) }
        expectFailure(EncryptedChatFailure.STALE) { f.session.encryptText("cannot send") }
        assertFalse(f.session.isCurrent())
    }

    @Test fun `oversized plaintext is rejected before transport encryption`() = runBlocking {
        val f = Fixture()
        f.storage.writeChatKey(f.address("generation"), crypto.createGroupKey())
        expectFailure(EncryptedChatFailure.TOO_LONG) { f.session.send("x".repeat(40_001)) { _, _ -> error("transport called") } }
        assertTrue(f.sent.isEmpty())
    }

    private suspend fun expectFailure(failure: EncryptedChatFailure, action: suspend () -> Any?) {
        try { action(); fail("Expected $failure") }
        catch (error: EncryptedChatException) { assertEquals(failure, error.failure) }
    }

    private inner class Fixture(user: String = "reader", device: String = "android") {
        val scope = this@EncryptedChatSessionTest.scope.copy(userId = user, deviceId = device)
        val keyScope = ChatKeyScope(ChatKeyOwner(scope.server, scope.projectId, scope.userId), scope.deviceId)
        val storage = MemoryChatStorage()
        var access = EncryptedChatAccess(scope, "generation", "owner", mapOf("administrator" to "administrator"))
        var failSend = false
        var onSend: (() -> Unit)? = null
        var beforeReceipt: (suspend () -> Unit)? = null
        val sent = mutableListOf<Pair<EncryptedChatPayload, String>>()
        var session = newSession()
        fun address(key: String) = ChatKeyAddress(keyScope, scope.streamId, key)
        fun wire(id: String, author: String, payload: EncryptedChatPayload) =
            EncryptedChatWireMessage(id, scope.projectId, scope.streamId, author, payload)
        fun newSession() = EncryptedChatSession(scope, storage, { access },
            EncryptedChatServiceTransport { payload, canonical, _, guard ->
                assertTrue(guard())
                sent += payload to canonical
                if (failSend) throw IllegalStateException("offline")
                beforeReceipt?.invoke()
                onSend?.invoke()
                wire("placement-$canonical", scope.userId, payload)
            }, { "default-topic" }, { access = access.copy(currentKeyUuid = it) }, crypto, Dispatchers.Unconfined)
        fun encrypted(keyUuid: String, text: String, key: ByteArray) = wire(UUID.randomUUID().toString(), "owner",
            EncryptedChatPayload("e2ee", keyUuid, WorkspaceChatCrypto.toBase64(crypto.encryptMessage(key,
                text.toByteArray(), ChatMessageContext(scope.streamId, keyUuid)))))
        fun grant(request: EncryptedChatWireMessage, groupKey: ByteArray) = wire(UUID.randomUUID().toString(), "owner",
            EncryptedChatPayload("key_grant", request.payload.keyUuid, WorkspaceChatCrypto.toBase64(crypto.encryptGroupKeyGrant(
                groupKey, WorkspaceChatCrypto.fromBase64(requireNotNull(request.payload.publicKey)),
                ChatGrantContext(scope.streamId, request.payload.keyUuid, request.uuid, "owner-device", scope.deviceId))),
                requestMessageUuid = request.uuid, senderDeviceId = "owner-device", recipientDeviceId = scope.deviceId))
    }
}

/** Transactional test double. Secrets are copied so callers can erase temporary buffers. */
private class MemoryChatStorage : ChatKeyStorage {
    var keyWrites = 0
    var stageReads = 0
    private val keys = linkedMapOf<ChatKeyAddress, ByteArray>()
    private val revisions = mutableMapOf<ChatKeyAddress, Long>()
    private val pending = mutableMapOf<ChatKeyAddress, MutableMap<String, PendingChatKeyRequest>>()
    private val completions = mutableMapOf<ChatKeyAddress, MutableMap<String, ChatKeyRequestCompletion>>()
    private val stages = mutableMapOf<ChatKeyScope, MutableMap<String, StagedChatCreation>>()
    private val devices = mutableMapOf<ChatKeyOwner, ChatDeviceIdentity>()
    private val issued = mutableMapOf<Triple<ChatKeyScope, String, String>, IssuedChatKeyResponse>()
    private val last = mutableMapOf<Pair<ChatKeyScope, String>, String>()
    override fun purgeOwner(owner: ChatKeyOwner): Boolean {
        devices.remove(owner)
        keys.keys.removeAll { it.scope.owner == owner }
        pending.keys.removeAll { it.scope.owner == owner }
        completions.keys.removeAll { it.scope.owner == owner }
        revisions.keys.removeAll { it.scope.owner == owner }
        stages.keys.removeAll { it.owner == owner }
        issued.keys.removeAll { it.first.owner == owner }
        last.keys.removeAll { it.first.owner == owner }
        return true
    }
    override fun ensureDeviceIdentity(owner: ChatKeyOwner, deviceName: String) =
        devices.getOrPut(owner) { ChatDeviceIdentity(UUID.randomUUID().toString(), deviceName) }
    override fun readDeviceIdentity(owner: ChatKeyOwner) = devices[owner]
    override fun readChatKey(address: ChatKeyAddress) = keys[address]?.copyOf()
    override fun readChatKeyDeletionRevision(address: ChatKeyAddress) = revisions[address] ?: 0
    override fun listChatKeys(scope: ChatKeyScope, streamUuid: String) = keys.keys.filter {
        it.scope == scope && it.streamUuid == streamUuid
    }.map { StoredChatKeyMetadata(it.keyUuid, 1) }
    override fun readLastAvailableChatKey(scope: ChatKeyScope, streamUuid: String): Pair<String, ByteArray>? {
        val candidates = keys.keys.filter { it.scope == scope && it.streamUuid == streamUuid }
        val address = candidates.firstOrNull { it.keyUuid == last[scope to streamUuid] } ?: candidates.lastOrNull() ?: return null
        return address.keyUuid to requireNotNull(readChatKey(address))
    }
    override fun writeChatKey(address: ChatKeyAddress, groupKey: ByteArray, forSending: Boolean,
                              expectedDeletionRevision: Long?, onlyIfPresent: Boolean): Boolean {
        if (expectedDeletionRevision != null && expectedDeletionRevision != readChatKeyDeletionRevision(address)) return false
        if (onlyIfPresent && address !in keys) return false
        keyWrites++
        keys[address] = groupKey.copyOf()
        if (forSending) last[address.scope to address.streamUuid] = address.keyUuid
        return true
    }
    override fun removeChatKey(address: ChatKeyAddress): Boolean {
        keys.remove(address)?.fill(0)
        pending.remove(address)
        revisions[address] = readChatKeyDeletionRevision(address) + 1
        return true
    }
    override fun listPendingRequestKeyUuids(scope: ChatKeyScope, streamUuid: String) = pending.keys.filter {
        it.scope == scope && it.streamUuid == streamUuid && pending[it]?.isNotEmpty() == true
    }.map { it.keyUuid }.distinct().sorted()
    override fun listPendingRequests(address: ChatKeyAddress) = pending[address]?.values?.map(::copyPending) ?: emptyList()
    override fun writePendingRequest(address: ChatKeyAddress, request: PendingChatKeyRequest): Boolean {
        pending.getOrPut(address) { linkedMapOf() }[request.localRequestId] = copyPending(request)
        return true
    }
    override fun bindPendingRequest(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String): Boolean {
        val old = pending[address]?.get(localRequestId) ?: return false
        pending[address]!![localRequestId] = PendingChatKeyRequest(old.localRequestId, requestMessageUuid,
            old.keyPair, old.createdAt, old.recoveryEnvelope, old.previousGroupKey)
        return true
    }
    override fun readRequestCompletion(address: ChatKeyAddress, localRequestId: String) = completions[address]?.get(localRequestId)
    override fun readRequestCompletionByMessageUuid(address: ChatKeyAddress, requestMessageUuid: String) =
        completions[address]?.values?.firstOrNull { it.requestMessageUuid == requestMessageUuid }
    override fun completeGrant(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String,
                               responseMessageUuid: String, groupKey: ByteArray, expectedDeletionRevision: Long?, replaceExistingKey: Boolean): Boolean {
        if (pending[address]?.get(localRequestId)?.requestMessageUuid != requestMessageUuid) return false
        if (expectedDeletionRevision != null && readChatKeyDeletionRevision(address) != expectedDeletionRevision) return false
        if (address !in keys || replaceExistingKey) keys[address] = groupKey.copyOf()
        return complete(address, localRequestId, requestMessageUuid, responseMessageUuid, "granted")
    }
    override fun completeReject(address: ChatKeyAddress, localRequestId: String, requestMessageUuid: String, responseMessageUuid: String) =
        complete(address, localRequestId, requestMessageUuid, responseMessageUuid, "rejected")
    override fun readIssuedResponse(address: ChatKeyAddress, requestMessageUuid: String) =
        issued[Triple(address.scope, address.streamUuid, requestMessageUuid)]
    override fun stageIssuedResponse(address: ChatKeyAddress, response: IssuedChatKeyResponse): IssuedChatKeyResponse? {
        val id = Triple(address.scope, address.streamUuid, response.requestMessageUuid)
        issued[id]?.let { return it }
        if (response.deletionRevision != null && response.deletionRevision != readChatKeyDeletionRevision(address)) return null
        issued[id] = response
        return response
    }
    override fun confirmIssuedResponse(address: ChatKeyAddress, requestMessageUuid: String, canonicalUuid: String, responseMessageUuid: String): Boolean {
        val id = Triple(address.scope, address.streamUuid, requestMessageUuid)
        val response = issued[id] ?: return false
        if (response.keyUuid != address.keyUuid || response.canonicalUuid != canonicalUuid ||
            (response.responseMessageUuid != null && response.responseMessageUuid != responseMessageUuid)) return false
        issued[id] = response.copy(responseMessageUuid = responseMessageUuid)
        return true
    }
    private fun complete(address: ChatKeyAddress, local: String, request: String, response: String, outcome: String): Boolean {
        if (pending[address]?.get(local)?.requestMessageUuid != request) return false
        pending[address]?.remove(local)
        completions.getOrPut(address) { mutableMapOf() }[local] = ChatKeyRequestCompletion(local, request, response, outcome)
        return true
    }
    override fun listStagedCreations(scope: ChatKeyScope): List<StagedChatCreation> {
        stageReads++
        return stages[scope]?.values?.map(::copyStage) ?: emptyList()
    }
    override fun writeStagedCreation(scope: ChatKeyScope, creation: StagedChatCreation): Boolean {
        stages.getOrPut(scope) { linkedMapOf() }[creation.creationId] = copyStage(creation)
        return true
    }
    override fun finalizeCreation(scope: ChatKeyScope, creationId: String, streamUuid: String): Boolean {
        val stage = stages[scope]?.remove(creationId) ?: return false
        keys[ChatKeyAddress(scope, streamUuid, stage.keyUuid)] = stage.groupKey.copyOf()
        return true
    }
    override fun discardStagedCreation(scope: ChatKeyScope, creationId: String) = stages[scope]?.remove(creationId) != null
    private fun copyPending(p: PendingChatKeyRequest) = PendingChatKeyRequest(p.localRequestId, p.requestMessageUuid,
        ChatRequestKeyPair(p.keyPair.publicKey, p.keyPair.privateKey), p.createdAt, p.recoveryEnvelope?.copyOf(), p.previousGroupKey?.copyOf())
    private fun copyStage(s: StagedChatCreation) = StagedChatCreation(s.creationId, s.keyUuid, s.groupKey.copyOf(), s.rotation)
}
