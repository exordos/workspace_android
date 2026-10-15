package ru.genesiscorporation.workspace.beta.data.crypto

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ChatKeyStorageTest {
    private val records = MemoryRecords()
    private val store: ChatKeyStorage = ChatKeyStorageImpl(records)
    private val owner = ChatKeyOwner("https://workspace.example.test", "project-a", "user-a")
    private val scope = ChatKeyScope(owner, "device-a")
    private val address = ChatKeyAddress(scope, "chat-a", "generation-a")
    private val key = ByteArray(32) { it.toByte() }

    @Test fun explicitLogoutPurgesEverySecretAndReceiptOnlyForItsOwnerAndFencesLateWriters() {
        val identity = store.ensureDeviceIdentity(owner)
        val others = listOf(owner.copy(userUuid = "other-owner"), owner.copy(projectUuid = "other-project"),
            owner.copy(serverUrl = "https://other.example.test")).map { address.copy(scope = scope.copy(owner = it)) }
        store.writeChatKey(address, key)
        others.forEach { store.writeChatKey(it, key) }
        store.writePendingRequest(address, pending("completed"))
        assertTrue(store.completeGrant(address, "completed", "request-completed", "grant-completed", key))
        store.writePendingRequest(address, pending("pending", byteArrayOf(1, 2), key))
        store.stageIssuedResponse(address, issuedResponse("key_grant", "canonical", "opaque-grant", 0))
        store.writeStagedCreation(scope, StagedChatCreation("completed-create", "completed-generation", key))
        store.finalizeCreation(scope, "completed-create", "completed-stream")
        store.writeStagedCreation(scope, StagedChatCreation("pending-create", "pending-generation", key))

        assertTrue(store.purgeOwner(owner))
        val reloaded = ChatKeyStorageImpl(records)
        assertNull(reloaded.readDeviceIdentity(owner))
        assertNull(reloaded.readChatKey(address))
        assertTrue(reloaded.listPendingRequests(address).isEmpty())
        assertNull(reloaded.readRequestCompletion(address, "completed"))
        assertNull(reloaded.readIssuedResponse(address, "request-a"))
        assertTrue(reloaded.listStagedCreations(scope).isEmpty())
        others.forEach { assertArrayEquals(key, reloaded.readChatKey(it)) }

        // Old IO work can resume after purge and after a new login creates a new identity.
        val newIdentity = reloaded.ensureDeviceIdentity(owner)
        assertNotEquals(identity.deviceId, newIdentity.deviceId)
        assertFalse(reloaded.writeChatKey(address, key))
        assertFalse(reloaded.writePendingRequest(address, pending("late")))
        assertFalse(reloaded.writeStagedCreation(scope, StagedChatCreation("late-create", "late-generation", key)))
        assertFalse(reloaded.finalizeCreation(scope, "completed-create", "completed-stream"))
        assertNull(reloaded.stageIssuedResponse(address, issuedResponse("key_reject", "late-canonical")))
        assertTrue(reloaded.writeChatKey(address.copy(scope = ChatKeyScope(owner, newIdentity.deviceId)), key))
        assertFalse(reloaded.writeChatKey(address.copy(scope = ChatKeyScope(owner, identity.deviceId)), key))
    }

    @Test fun failedLogoutPurgeKeepsPreviouslyCommittedSecretsAndReportsFailure() {
        store.writeChatKey(address, key)
        records.rejectWrites = true
        try { store.purgeOwner(owner); fail("Expected durable purge failure") }
        catch (error: ChatKeyStorageException) { assertEquals("unavailable", error.reason) }
        records.rejectWrites = false
        assertArrayEquals(key, ChatKeyStorageImpl(records).readChatKey(address))
    }

    @Test fun deviceIdentityIsDurableAndConcurrentCreationHasSingleWinner() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val identities = executor.invokeAll((1..12).map { Callable {
                ChatKeyStorageImpl(records).ensureDeviceIdentity(owner)
            } }).map { it.get() }
            assertEquals(1, identities.map { it.deviceId }.toSet().size)
            assertEquals(identities.first(), ChatKeyStorageImpl(records).readDeviceIdentity(owner))
            assertNotEquals(identities.first().deviceId, store.ensureDeviceIdentity(owner.copy(userUuid = "other")).deviceId)
        } finally { executor.shutdownNow() }
    }

    @Test fun isolatesEveryOwnerDeviceStreamAndGenerationAndCopiesSecrets() {
        store.writeChatKey(address, key)
        val wrongAddresses = listOf(address.copy(scope = scope.copy(owner = owner.copy(serverUrl = "https://other.example.test"))),
            address.copy(scope = scope.copy(owner = owner.copy(projectUuid = "other"))),
            address.copy(scope = scope.copy(owner = owner.copy(userUuid = "other"))),
            address.copy(scope = scope.copy(deviceId = "other")), address.copy(streamUuid = "other"), address.copy(keyUuid = "other"))
        wrongAddresses.forEach { assertNull(store.readChatKey(it)) }
        val reloaded = ChatKeyStorageImpl(records)
        assertArrayEquals(key, reloaded.readChatKey(address))
        reloaded.readChatKey(address)!!.fill(0)
        assertArrayEquals(key, store.readChatKey(address))
        assertEquals(listOf("generation-a"), store.listChatKeys(scope, "chat-a").map { it.keyUuid })
    }

    @Test fun historicalGrantsNeverOverrideSelectedSendingGeneration() {
        store.writeChatKey(address, key, forSending = true)
        val historical = address.copy(keyUuid = "historical")
        store.writeChatKey(historical, ByteArray(32) { 8 })
        assertEquals(address.keyUuid, store.readLastAvailableChatKey(scope, address.streamUuid)!!.first)
        assertArrayEquals(key, store.readChatKey(address))
        expectConflict { store.writeChatKey(address, ByteArray(32) { 9 }) }
    }

    @Test fun completesGrantAtomicallyAndPersistsIdempotentReceipt() {
        val pair = WorkspaceChatCrypto().createRequestKeyPair()
        store.writePendingRequest(address, PendingChatKeyRequest("local-a", null, pair, 100))
        assertTrue(store.bindPendingRequest(address, "local-a", "request-a"))
        val reloaded = ChatKeyStorageImpl(records)
        assertArrayEquals(pair.privateKey, reloaded.listPendingRequests(address).single().keyPair.privateKey)
        assertTrue(reloaded.completeGrant(address, "local-a", "request-a", "response-a", key, 0))
        assertArrayEquals(key, store.readChatKey(address))
        assertTrue(store.listPendingRequests(address).isEmpty())
        assertEquals("granted", store.readRequestCompletionByMessageUuid(address, "request-a")!!.outcome)
        assertTrue(store.completeGrant(address, "local-a", "request-a", "response-a", key, 0))
        assertFalse(store.completeReject(address, "local-a", "request-a", "response-other"))
    }

    @Test fun rejectsMismatchedBindingsAndImportsOnlyAnExplicitlyRequestedKey() {
        val pair = WorkspaceChatCrypto().createRequestKeyPair()
        assertFalse(store.completeGrant(address, "missing", "request", "response", key))
        store.writePendingRequest(address, PendingChatKeyRequest("local", "bound", pair, 100))
        expectConflict { store.bindPendingRequest(address, "local", "different") }
        expectConflict { store.completeGrant(address, "local", "different", "response", key) }
        assertNull(store.readChatKey(address))
        assertTrue(store.completeReject(address, "local", "bound", "rejection"))
        assertNull(store.readChatKey(address))
        assertTrue(store.listPendingRequests(address).isEmpty())
        assertEquals("rejected", store.readRequestCompletion(address, "local")!!.outcome)
    }

    @Test fun localDeletionPurgesRequestsAndRejectsLateImports() {
        store.writeChatKey(address, key, true)
        store.writePendingRequest(address, pending("old"))
        val before = store.readChatKeyDeletionRevision(address)
        assertTrue(store.removeChatKey(address))
        assertEquals(before + 1, store.readChatKeyDeletionRevision(address))
        assertNull(store.readChatKey(address))
        assertTrue(store.listPendingRequests(address).isEmpty())
        assertFalse(store.completeGrant(address, "old", "request-old", "response-old", key, before))
        assertFalse(store.writeChatKey(address, key, expectedDeletionRevision = before))
        assertFalse(store.writeChatKey(address, key, onlyIfPresent = true))
        store.writePendingRequest(address, pending("fresh"))
        assertTrue(store.completeGrant(address, "fresh", "request-fresh", "response-fresh", key, before + 1))
        assertArrayEquals(key, store.readChatKey(address))
    }

    @Test fun repairRequiresUnchangedOriginalKeyAndExplicitRecoveryProbe() {
        store.writeChatKey(address, key)
        val corrected = ByteArray(32) { 9 }
        val pending = pending("repair", ByteArray(48), key)
        store.writePendingRequest(address, pending)
        expectConflict { store.completeGrant(address, "repair", "request-repair", "response", corrected) }
        assertTrue(store.completeGrant(address, "repair", "request-repair", "response", corrected, replaceExistingKey = true))
        assertArrayEquals(corrected, store.readChatKey(address))
    }

    @Test fun rotationStagesAcrossReloadAndPromotionRetainsHistoricalKeys() {
        store.writeChatKey(address, key, true)
        val rotationKey = ByteArray(32) { 7 }
        val stage = StagedChatCreation("rotate", "generation-b", rotationKey,
            ChatKeyRotation(address.streamUuid, address.keyUuid, "topic-a"))
        assertTrue(store.writeStagedCreation(scope, stage))
        val reloaded = ChatKeyStorageImpl(records)
        val pending = reloaded.listStagedCreations(scope).single()
        assertArrayEquals(rotationKey, pending.groupKey)
        assertEquals(stage.rotation, pending.rotation)
        expectConflict { reloaded.finalizeCreation(scope, "rotate", "other-chat") }
        assertTrue(reloaded.finalizeCreation(scope, "rotate", address.streamUuid))
        assertTrue(store.finalizeCreation(scope, "rotate", address.streamUuid))
        assertFalse(store.finalizeCreation(scope, "rotate", "different"))
        assertTrue(store.listStagedCreations(scope).isEmpty())
        assertEquals("generation-b", store.readLastAvailableChatKey(scope, address.streamUuid)!!.first)
        assertArrayEquals(key, store.readChatKey(address))
        assertEquals(2, store.listChatKeys(scope, address.streamUuid).size)
    }

    @Test fun storageFailureNeverReportsSuccessfulImportOrLosesPendingSecret() {
        store.writePendingRequest(address, pending("a"))
        records.rejectWrites = true
        try { store.completeGrant(address, "a", "request-a", "response", key); fail("Expected storage failure") }
        catch (error: ChatKeyStorageException) { assertEquals("unavailable", error.reason) }
        records.rejectWrites = false
        assertNull(store.readChatKey(address))
        assertEquals(1, store.listPendingRequests(address).size)
        assertNull(store.readRequestCompletion(address, "a"))
    }

    @Test fun delayedCreationResponseCannotResurrectLocallyDeletedGeneration() {
        store.writeStagedCreation(scope, StagedChatCreation("new-chat", address.keyUuid, key))
        store.removeChatKey(address)
        assertFalse(store.finalizeCreation(scope, "new-chat", address.streamUuid))
        assertNull(store.readChatKey(address))
    }

    @Test fun issuerResponseFirstWriterWinsAcrossReloadAndCannotBeReboundToAnotherReceipt() {
        val grant = issuedResponse("key_grant", "canonical-grant", "sealed-group-key", 0)
        assertEquals(grant, store.stageIssuedResponse(address, grant))
        val reloaded = ChatKeyStorageImpl(records)
        val rejection = issuedResponse("key_reject", "canonical-rejection")
        assertEquals(grant, reloaded.stageIssuedResponse(address, rejection))
        // The request UUID cannot obtain another decision by advertising another generation.
        assertEquals(grant, reloaded.readIssuedResponse(address.copy(keyUuid = "other"), "request-a"))
        assertFalse(reloaded.confirmIssuedResponse(address, "request-a", "wrong-canonical", "placement"))
        assertTrue(reloaded.confirmIssuedResponse(address, "request-a", grant.canonicalUuid, "placement"))
        assertTrue(store.confirmIssuedResponse(address, "request-a", grant.canonicalUuid, "placement"))
        assertFalse(store.confirmIssuedResponse(address, "request-a", grant.canonicalUuid, "another-placement"))
        assertEquals(grant.copy(responseMessageUuid = "placement"), store.readIssuedResponse(address, "request-a"))
        assertNull(store.readIssuedResponse(address.copy(scope = scope.copy(deviceId = "other-device")), "request-a"))
    }

    @Test fun issuerCannotStageGrantUsingDeletedKeyRevisionAndWriteFailureDoesNotPublishDecision() {
        store.removeChatKey(address)
        assertNull(store.stageIssuedResponse(address, issuedResponse("key_grant", "canonical", "sealed", 0)))
        records.rejectWrites = true
        try { store.stageIssuedResponse(address, issuedResponse("key_reject", "canonical")); fail("Expected storage failure") }
        catch (error: ChatKeyStorageException) { assertEquals("unavailable", error.reason) }
        records.rejectWrites = false
        assertNull(store.readIssuedResponse(address, "request-a"))
    }

    private fun issuedResponse(kind: String, canonical: String, content: String = "", revision: Long? = null) =
        IssuedChatKeyResponse(address.keyUuid, "request-a", "recipient-user", "recipient-device", "public-key", canonical,
            kind, content, deletionRevision = revision)

    private fun pending(id: String, envelope: ByteArray? = null, previous: ByteArray? = null) =
        PendingChatKeyRequest(id, "request-$id", WorkspaceChatCrypto().createRequestKeyPair(), 100, envelope, previous)
    private fun expectConflict(block: () -> Any) {
        try { block(); fail("Expected storage conflict") }
        catch (error: ChatKeyStorageException) { assertEquals("conflict", error.reason) }
    }

    private class MemoryRecords : ChatSecretRecords {
        private val data = mutableMapOf<String, String>()
        var rejectWrites = false
        override fun read(key: String): String? = data[key]
        override fun write(key: String, value: String): Boolean {
            if (rejectWrites) return false
            data[key] = value
            return true
        }
    }
}
