package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.crypto.*

/** Uses the production serialized owner document, including reopening the storage after a lost receipt. */
class EncryptedChatIssuerResponseTest {
    private val crypto = WorkspaceChatCrypto()

    @Test fun `grant retry after lost ack and recovery reuses canonical and HPKE envelope across restart`() = runBlocking {
        val f = Fixture()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = true
        failure(EncryptedChatFailure.SEND_FAILED) { f.session.grantKey(f.request.uuid) }
        val first = f.attempts.single()
        assertEquals(1, f.serverMessages.size) // POST committed, but neither acknowledgement nor recovery was available.
        f.restart()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = false
        val receipt = f.session.grantKey(f.request.uuid)
        assertEquals(listOf(first, first), f.attempts)
        assertEquals(1, f.serverMessages.size)
        assertEquals(first.first, receipt.payload)
        val received = crypto.decryptGroupKeyGrant(f.pair, WorkspaceChatCrypto.fromBase64(receipt.payload.content),
            ChatGrantContext("stream", "generation", f.request.uuid, "issuer-device", "recipient-device"))
        try { assertArrayEquals(f.groupKey, received) } finally { received.fill(0) }
        f.restart()
        f.session.ingest(listOf(f.request))
        assertEquals(receipt, f.session.grantKey(f.request.uuid))
        assertEquals(2, f.attempts.size) // A durable confirmed receipt needs no third POST.
    }

    @Test fun `rejection retry persists one canonical decision and confirmed receipt across restart`() = runBlocking {
        val f = Fixture()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = true
        failure(EncryptedChatFailure.SEND_FAILED) { f.session.rejectKey(f.request.uuid) }
        val first = f.attempts.single()
        f.restart()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = false
        val receipt = f.session.rejectKey(f.request.uuid)
        assertEquals(listOf(first, first), f.attempts)
        assertEquals("key_reject", receipt.payload.kind)
        assertEquals(1, f.serverMessages.size)
        f.restart()
        f.session.ingest(listOf(f.request))
        assertEquals(receipt, f.session.rejectKey(f.request.uuid))
        assertEquals(2, f.attempts.size)
    }

    @Test fun `ambiguous first decision cannot be changed between grant and rejection`() = runBlocking {
        for (firstGrant in listOf(true, false)) {
            val f = Fixture()
            f.session.ingest(listOf(f.request))
            f.loseReceipt = true
            failure(EncryptedChatFailure.SEND_FAILED) {
                if (firstGrant) f.session.grantKey(f.request.uuid) else f.session.rejectKey(f.request.uuid)
            }
            f.restart()
            f.session.ingest(listOf(f.request))
            f.loseReceipt = false
            failure(EncryptedChatFailure.INVALID_MESSAGE) {
                if (firstGrant) f.session.rejectKey(f.request.uuid) else f.session.grantKey(f.request.uuid)
            }
            assertEquals(1, f.attempts.size)
            if (firstGrant) f.session.grantKey(f.request.uuid) else f.session.rejectKey(f.request.uuid)
            assertEquals(1, f.serverMessages.size)
        }
    }

    @Test fun `two mounted issuer panels dispatch one response and opposite concurrent decision loses`() = runBlocking {
        val f = Fixture()
        val other = f.newSession()
        f.session.ingest(listOf(f.request))
        other.ingest(listOf(f.request))
        val release = CompletableDeferred<Unit>()
        f.beforeReceipt = { release.await() }
        val first = launch(start = CoroutineStart.UNDISPATCHED) { f.session.grantKey(f.request.uuid) }
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            failure(EncryptedChatFailure.INVALID_MESSAGE) { other.rejectKey(f.request.uuid) }
            other.grantKey(f.request.uuid)
        }
        assertEquals(1, f.attempts.size)
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, f.attempts.size)
        assertEquals(1, f.serverMessages.size)
    }

    @Test fun `history settles uncertain own response without another POST`() = runBlocking {
        val f = Fixture()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = true
        failure(EncryptedChatFailure.SEND_FAILED) { f.session.grantKey(f.request.uuid) }
        val actual = f.serverMessages.values.single()
        f.restart()
        f.session.ingest(listOf(f.request, actual))
        assertEquals(actual, f.session.grantKey(f.request.uuid))
        assertEquals(1, f.attempts.size)
        assertEquals(actual.uuid, f.store.readIssuedResponse(f.address, f.request.uuid)?.responseMessageUuid)
    }

    @Test fun `edited request cannot reuse its UUID to change generation recipient or public key`() = runBlocking {
        val f = Fixture()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = true
        failure(EncryptedChatFailure.SEND_FAILED) { f.session.grantKey(f.request.uuid) }
        val otherPublic = WorkspaceChatCrypto.toBase64(crypto.createRequestKeyPair().publicKey)
        for (payload in listOf(f.request.payload.copy(keyUuid = "other-generation"),
            f.request.payload.copy(deviceId = "other-device"), f.request.payload.copy(publicKey = otherPublic))) {
            f.restart()
            f.session.ingest(listOf(f.request.copy(payload = payload)))
            failure(EncryptedChatFailure.INVALID_MESSAGE) { f.session.grantKey(f.request.uuid) }
        }
        assertEquals(1, f.attempts.size)
    }

    @Test fun `deleting and reimporting local key cannot redispatch an uncertain grant`() = runBlocking {
        val f = Fixture()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = true
        failure(EncryptedChatFailure.SEND_FAILED) { f.session.grantKey(f.request.uuid) }
        f.store.removeChatKey(f.address)
        f.store.writeChatKey(f.address, f.groupKey)
        f.restart()
        f.session.ingest(listOf(f.request))
        f.loseReceipt = false
        failure(EncryptedChatFailure.MISSING_KEY) { f.session.grantKey(f.request.uuid) }
        assertEquals(1, f.attempts.size)
    }

    private suspend fun failure(expected: EncryptedChatFailure, block: suspend () -> Any?) {
        try { block(); fail("Expected $expected") }
        catch (error: EncryptedChatException) { assertEquals(expected, error.failure) }
    }

    private inner class Fixture {
        val scope = EncryptedChatScope("https://workspace.example", "project", "owner", 1,
            "issuer-device", "Android", "stream")
        val keyScope = ChatKeyScope(ChatKeyOwner(scope.server, scope.projectId, scope.userId), scope.deviceId)
        val address = ChatKeyAddress(keyScope, scope.streamId, "generation")
        private val records = object : ChatSecretRecords {
            val values = mutableMapOf<String, String>()
            override fun read(key: String) = values[key]
            override fun write(key: String, value: String): Boolean { values[key] = value; return true }
        }
        var store: ChatKeyStorage = ChatKeyStorageImpl(records)
        val groupKey = crypto.createGroupKey()
        val pair = crypto.createRequestKeyPair()
        val request = EncryptedChatWireMessage("request-placement", scope.projectId, scope.streamId, "member",
            EncryptedChatPayload("key_request", "generation", deviceId = "recipient-device",
                publicKey = WorkspaceChatCrypto.toBase64(pair.publicKey)))
        var loseReceipt = false
        var beforeReceipt: (suspend () -> Unit)? = null
        val attempts = mutableListOf<Pair<EncryptedChatPayload, String>>()
        val serverMessages = linkedMapOf<String, EncryptedChatWireMessage>()
        var session = newSession()
        init { store.writeChatKey(address, groupKey) }
        fun newSession() = EncryptedChatSession(scope, store,
            { EncryptedChatAccess(scope, "generation", "owner", emptyMap()) },
            EncryptedChatServiceTransport { payload, canonical, _, guard ->
                assertTrue(guard())
                attempts += payload to canonical
                val receipt = serverMessages.getOrPut(canonical) {
                    EncryptedChatWireMessage("placement-$canonical", scope.projectId, scope.streamId, scope.userId, payload)
                }
                assertEquals(receipt.payload, payload) // The backend rejects a changed canonical message.
                beforeReceipt?.invoke()
                if (loseReceipt) throw IllegalStateException("POST acknowledgement and recovery unavailable")
                receipt
            }, { "default-topic" }, crypto = crypto, dispatcher = Dispatchers.Unconfined)
        fun restart() {
            session.close()
            store = ChatKeyStorageImpl(records)
            session = newSession()
        }
    }
}
