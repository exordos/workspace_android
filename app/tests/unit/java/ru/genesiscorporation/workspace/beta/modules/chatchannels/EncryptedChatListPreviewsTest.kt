package ru.genesiscorporation.workspace.beta.modules.chatchannels

import kotlinx.coroutines.*
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatScope
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.ConfirmedAccountContext
import ru.genesiscorporation.workspace.beta.data.VerifiedOwnerAuthentication
import ru.genesiscorporation.workspace.beta.data.crypto.*
import ru.genesiscorporation.workspace.beta.data.remote.ProtectedRequestAuthentication
import ru.genesiscorporation.workspace.beta.data.remote.dto.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class EncryptedChatListPreviewsTest {
    private val crypto = WorkspaceChatCrypto()

    @Test fun `exact historical generation decrypts locally without changing wire or writing storage`() = runBlocking {
        val f = Fixture()
        val before = f.records.writes
        val wire = f.message.payload.copy()
        val projector = f.projector()
        projector.refresh()
        assertEquals("private **preview**", projector.preview("stream")?.text)
        assertEquals("historical", projector.preview("stream")?.keyUuid)
        assertEquals(wire, f.message.payload)
        assertEquals(before, f.records.writes)
        f.store.removeChatKey(f.address)
        f.store.writeChatKey(f.address.copy(keyUuid = "current"), f.key)
        projector.invalidate()
        projector.refresh()
        assertTrue(projector.previews.value.isEmpty()) // The current key must never stand in for a historical generation.
    }

    @Test fun `only matching visible storage changes and own logout invalidate previews`() = runBlocking {
        val f = Fixture()
        var decryptions = 0
        val projector = f.projector { key, envelope, context ->
            decryptions++
            crypto.decryptMessage(key, envelope, context)
        }
        projector.refresh()
        val published = projector.previews.value
        val own = EncryptedChatScope(f.owner.serverUrl, f.owner.projectUuid, f.owner.userUuid, 99,
            f.device.deviceId, "Another panel", "stream")
        for (unrelated in listOf(own.copy(server = "https://other.example"), own.copy(projectId = "other"),
            own.copy(userId = "other"), own.copy(deviceId = "other"), own.copy(streamId = "invisible"))) {
            if (projector.affectedBy(unrelated)) { projector.invalidate(); projector.refresh() }
            assertSame(published, projector.previews.value)
        }
        val other = Fixture(serverUrl = "https://other.example")
        requireNotNull(other.snapshot).account.revoke()
        assertFalse(projector.accountChanged())
        assertSame(published, projector.previews.value)
        assertEquals(1, decryptions)
        assertTrue(projector.affectedBy(own)) // runtime generation and device label are deliberately ignored.
        f.snapshot = f.snapshot!!.copy(rows = emptyList())
        assertFalse(projector.affectedBy(own))
        requireNotNull(f.snapshot).account.revoke()
        f.snapshot = null
        assertTrue(projector.accountChanged())
    }

    @Test fun `missing identity or revoked membership never decrypts or creates local credentials`() = runBlocking {
        val f = Fixture(identity = false)
        val before = f.records.writes
        val projector = f.projector { _, _, _ -> error("Must not decrypt") }
        projector.refresh()
        assertTrue(projector.previews.value.isEmpty())
        assertEquals(before, f.records.writes)
        assertNull(f.store.readDeviceIdentity(f.owner))
        val member = Fixture()
        val own = member.projector()
        own.refresh()
        member.snapshot = member.snapshot?.copy(rows = member.snapshot!!.rows.map { it.copy(authorized = false) })
        assertNull(own.preview("stream")) // Getter also rejects a stale membership before the observer executes.
        own.invalidate()
        own.refresh()
        assertTrue(own.previews.value.isEmpty())
    }

    @Test fun `previous account notification is ignored while new identity read is suspended`() = runBlocking {
        val a = Fixture()
        val b = Fixture(serverUrl = "https://other.example") // Both accounts display the same stream UUID.
        val snapshot = AtomicReference(a.snapshot)
        val readingIdentity = CompletableDeferred<Unit>()
        val releaseIdentity = CountDownLatch(1)
        val heldStorage = object : ChatKeyStorage by b.store {
            override fun readDeviceIdentity(owner: ChatKeyOwner): ChatDeviceIdentity? {
                readingIdentity.complete(Unit)
                check(releaseIdentity.await(10, TimeUnit.SECONDS))
                return b.store.readDeviceIdentity(owner)
            }
        }
        val projector = EncryptedChatListPreviewProjector({ snapshot.get() }, {
            if (snapshot.get()?.account?.owner == a.owner) a.store else heldStorage
        })
        val oldChange = EncryptedChatScope(a.owner.serverUrl, a.owner.projectUuid, a.owner.userUuid, 0,
            a.device.deviceId, a.device.deviceName, "stream")
        projector.refresh()
        assertTrue(projector.affectedBy(oldChange))
        snapshot.set(b.snapshot)
        projector.invalidate()
        val refresh = async { projector.refresh() }
        try {
            withTimeout(10_000) { readingIdentity.await() }
            assertFalse(projector.affectedBy(oldChange))
            assertTrue(refresh.isActive)
            assertTrue(projector.previews.value.isEmpty())
        } finally {
            releaseIdentity.countDown()
            refresh.join()
        }
        assertEquals("private **preview**", projector.preview("stream")?.text)
        assertTrue(projector.affectedBy(oldChange.copy(server = b.owner.serverUrl, deviceId = b.device.deviceId)))
        assertFalse(projector.affectedBy(oldChange))
    }

    @Test fun `credential replacement blocks stale user but verified same-user refresh restores preview`() = runBlocking {
        val f = Fixture()
        val projector = f.projector()
        projector.refresh()
        assertNotNull(projector.preview("stream"))
        val nextToken = "synthetic-refreshed-token"
        assertNull(confirmed(f.server, "owner", nextToken, f.proof))
        f.snapshot = null // VM rejects new credentials until existing /me produces matching evidence.
        projector.invalidate()
        projector.refresh()
        assertTrue(projector.previews.value.isEmpty())
        val renewed = f.proof.copy(authentication = ProtectedRequestAuthentication(nextToken), revision = 2)
        f.snapshot = EncryptedPreviewSnapshot(requireNotNull(confirmed(f.server, "owner", nextToken, renewed)), listOf(f.source))
        projector.refresh()
        assertEquals("private **preview**", projector.preview("stream")?.text)
        assertNull(confirmed(f.server, "another-owner", nextToken, renewed))
        assertNull(confirmed(f.server.copy(baseUrl = "https://other.example"), "owner", nextToken, renewed))
    }

    @Test fun `old decrypt finishing after account invalidation cannot republish plaintext`() = runBlocking {
        val f = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val projector = f.projector { key, envelope, context ->
            val plaintext = crypto.decryptMessage(key, envelope, context)
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            plaintext
        }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { projector.refresh() }
        started.await()
        f.snapshot = null
        projector.invalidate()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertTrue(projector.previews.value.isEmpty())
    }

    @Test fun `deleting key while decrypt is suspended prevents publication even before key notification`() = runBlocking {
        val f = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val projector = f.projector { key, envelope, context ->
            val plaintext = crypto.decryptMessage(key, envelope, context)
            started.complete(Unit)
            release.await()
            plaintext
        }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { projector.refresh() }
        started.await()
        f.store.removeChatKey(f.address)
        release.complete(Unit)
        job.join()
        assertTrue(projector.previews.value.isEmpty())
    }

    @Test fun `service last message selects newest loaded regular message and edited wire replaces old ciphertext`() = runBlocking {
        val f = Fixture()
        val service = f.message.copy(uuid = "grant", createdAt = "2026-10-15T12:01:00Z", payload = MessageResponsePayload("key_grant", keyUuid = "current"))
        val stream = f.stream.copy(lastMessageUuid = service.uuid, lastMessage = service)
        val old = f.message.copy(uuid = "older", createdAt = "2026-10-15T11:59:00Z")
        assertEquals(f.message.uuid, latestLoadedEncryptedPreviewMessage(stream, listOf(old, f.message), emptyList())?.uuid)
        assertNull(latestLoadedEncryptedPreviewMessage(stream, emptyList(), emptyList()))
        assertTrue(requireNotNull(encryptedPreviewSource(stream, f.message, "owner", "project", null)).authorized)
        val edit = f.message.copy(updatedAt = "2026-10-15T12:02:00Z", payload = f.message.payload.copy(content = "replaced"))
        assertEquals("replaced", latestLoadedEncryptedPreviewMessage(f.stream, listOf(f.message), listOf(edit))?.payload?.content)
        val projector = f.projector()
        projector.refresh()
        f.snapshot = f.snapshot!!.copy(rows = listOf(f.source.copy(ciphertext = "replaced")))
        assertNull(projector.preview("stream"))
        projector.invalidate()
        projector.refresh()
        assertTrue(projector.previews.value.isEmpty())
    }

    @Test fun `public projection without own membership and cross-owner or project wire fail closed`() {
        val f = Fixture()
        for (stream in listOf(f.stream.copy(role = null), f.stream.copy(userUuid = "other"), f.stream.copy(projectId = "other")))
            assertFalse(requireNotNull(encryptedPreviewSource(stream, f.message, "owner", "project", null)).authorized)
        assertFalse(requireNotNull(encryptedPreviewSource(f.stream, f.message, "owner", "project", emptyList())).authorized)
        assertFalse(requireNotNull(encryptedPreviewSource(f.stream, f.message.copy(userUuid = "other"), "owner", "project", null)).authorized)
    }

    private inner class Fixture(identity: Boolean = true, serverUrl: String = "https://workspace.example") {
        val server = ServerConfig("server", serverUrl, "", "", projectUuid = "project")
        val owner = ChatKeyOwner(server.baseUrl, "project", "owner")
        val records = Records()
        val store: ChatKeyStorage = ChatKeyStorageImpl(records)
        val device = if (identity) store.ensureDeviceIdentity(owner) else ChatDeviceIdentity("unused", "unused")
        val address = ChatKeyAddress(ChatKeyScope(owner, device.deviceId), "stream", "historical")
        val key = crypto.createGroupKey()
        val message = MessageResponse("message", "2026-10-15T12:00:00Z", "2026-10-15T12:00:00Z", "stream", "topic", "owner", "author",
            MessageResponsePayload("e2ee", WorkspaceChatCrypto.toBase64(crypto.encryptMessage(key, "private **preview**".toByteArray(),
                ChatMessageContext("stream", "historical"))), "historical"), false, emptyMap(), true, projectId = "project")
        val stream = Stream("stream", 0, 0, 0, "2026-10-15T12:00:00Z", "Protected", false, 0,
            lastMessageUuid = message.uuid, notificationMode = "all_messages", lastMessage = message,
            encryption = true, currentEncryptionKeyUuid = "current", projectId = "project", userUuid = "owner", role = "member")
        val source = requireNotNull(encryptedPreviewSource(stream, message, "owner", "project", null))
        val proof = VerifiedOwnerAuthentication(server.id, server.baseUrl, "owner", ProtectedRequestAuthentication("synthetic-token"), 1, server.copy())
        var snapshot: EncryptedPreviewSnapshot? = EncryptedPreviewSnapshot(requireNotNull(confirmed(server, "owner", "synthetic-token", proof)), listOf(source))
        init { if (identity) store.writeChatKey(address, key) }
        fun projector(decrypt: suspend (ByteArray, ByteArray, ChatMessageContext) -> ByteArray = { group, envelope, context -> crypto.decryptMessage(group, envelope, context) }) =
            EncryptedChatListPreviewProjector({ snapshot }, { store }, decrypt, Dispatchers.Unconfined)
    }

    private fun confirmed(server: ServerConfig, user: String, token: String, proof: VerifiedOwnerAuthentication?) =
        ConfirmedAccountContext.from(server, server.id, server.id, user, token, proof)

    private class Records : ChatSecretRecords {
        var writes = 0
        private val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String): Boolean { writes++; values[key] = value; return true }
    }
}
