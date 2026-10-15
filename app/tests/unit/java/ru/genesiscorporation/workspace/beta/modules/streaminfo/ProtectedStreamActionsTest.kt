package ru.genesiscorporation.workspace.beta.modules.streaminfo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.ProtectedRequestAuthentication
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatException
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatFailure

class ProtectedStreamActionsTest {
    @Test fun `delete commits only after the acknowledged DELETE becomes unreadable`() = runBlocking {
        val f = DeleteFixture()
        f.afterDelete = { f.absent = true }
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(2, f.reads)
        assertEquals(1, f.removals)
        assertTrue(f.editor.deleteState.value.deleted)
        assertFalse(f.editor.deleteState.value.busy)
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(2, f.reads)
    }

    @Test fun `lost DELETE ACK verifies repeatedly without repeating the deletion`() = runBlocking {
        val f = DeleteFixture()
        f.deleteResult = ApiResult.Error(ApiError("lost", "REQUEST_FAILED"))
        f.verificationError = ApiError("Read outcome unknown", "503")
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertTrue(f.editor.deleteState.value.verificationRequired)
        assertFalse(f.editor.deleteState.value.deleted)
        assertEquals(0, f.removals)
        f.editor.clearDeleteStatus()
        assertTrue(f.editor.deleteState.value.verificationRequired)
        f.editor.delete()
        assertEquals(1, f.deletes)
        f.verificationError = null
        f.absent = true
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(1, f.removals)
        assertTrue(f.editor.deleteState.value.deleted)
    }

    @Test fun `authoritative existing stream makes a failed deletion retryable with a new confirmation`() = runBlocking {
        val f = DeleteFixture()
        f.deleteResult = ApiResult.Error(ApiError("not applied", "REQUEST_FAILED"))
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(ProtectedStreamDeleteOutcome.STILL_PRESENT, f.editor.deleteState.value.outcome)
        assertFalse(f.editor.deleteState.value.verificationRequired)
        assertFalse(f.editor.deleteState.value.deleted)
        f.editor.clearDeleteStatus()
        assertEquals(ProtectedStreamDeleteOutcome.STILL_PRESENT, f.editor.deleteState.value.outcome)
        f.deleteResult = ApiResult.Success("")
        f.afterDelete = { f.absent = true }
        f.editor.delete()
        assertEquals(2, f.deletes)
        assertEquals(4, f.reads)
        assertEquals(1, f.removals)
        assertTrue(f.editor.deleteState.value.deleted)
    }

    @Test fun `retry after verified non-deletion still requires current owner permission`() = runBlocking {
        val f = DeleteFixture()
        f.editor.delete()
        assertEquals(ProtectedStreamDeleteOutcome.STILL_PRESENT, f.editor.deleteState.value.outcome)
        f.context = f.context.copy(stream = f.context.stream.copy(role = "member"))
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(ProtectedStreamUpdateFailure.FORBIDDEN, f.editor.deleteState.value.error)
        assertEquals(0, f.removals)
    }

    @Test fun `cancelled deletion stays verification only until its outcome is established`() = runBlocking {
        val f = DeleteFixture()
        f.afterDelete = { CompletableDeferred<Unit>().await() }
        val deletion = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.delete() }
        deletion.cancelAndJoin()
        assertTrue(f.editor.deleteState.value.verificationRequired)
        assertFalse(f.editor.deleteState.value.busy)
        f.verificationError = ApiError("Unknown read", "503")
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertTrue(f.editor.deleteState.value.verificationRequired)
    }

    @Test fun `thrown DELETE transport failure still performs immediate read verification`() = runBlocking {
        val f = DeleteFixture()
        f.afterDelete = { f.absent = true; throw IllegalStateException("lost ACK") }
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(2, f.reads)
        assertTrue(f.editor.deleteState.value.deleted)
    }

    @Test fun `member cannot delete and a forbidden DELETE never removes local metadata`() = runBlocking {
        val member = DeleteFixture("member")
        member.editor.delete()
        assertEquals(0, member.deletes)
        assertEquals(ProtectedStreamUpdateFailure.FORBIDDEN, member.editor.deleteState.value.error)
        val owner = DeleteFixture()
        owner.deleteResult = ApiResult.Error(ApiError("Forbidden", "403"))
        owner.editor.delete()
        assertEquals(1, owner.deletes)
        assertEquals(1, owner.reads)
        assertEquals(0, owner.removals)
        assertFalse(owner.editor.deleteState.value.verificationRequired)
        assertFalse(owner.editor.deleteState.value.deleted)
    }

    @Test fun `late DELETE result cannot remove another selected account stream`() = runBlocking {
        val f = DeleteFixture()
        val gate = CompletableDeferred<Unit>()
        f.afterDelete = { gate.await(); f.absent = true }
        val delete = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.delete() }
        f.context = f.context.copy(userUuid = "other-user")
        f.editor.onContextChanged()
        gate.complete(Unit)
        delete.join()
        assertEquals(0, f.removals)
        assertFalse(f.editor.deleteState.value.deleted)
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.editor.deleteState.value.error)
    }

    @Test fun `realtime removal before DELETE acknowledgement still verifies the captured account and navigates`() = runBlocking {
        val f = DeleteFixture()
        val gate = CompletableDeferred<Unit>()
        f.afterDelete = { gate.await() }
        val deletion = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.delete() }
        assertEquals(1, f.deletes)
        f.targetPresent = false
        f.absent = true
        f.editor.onContextChanged()
        gate.complete(Unit)
        deletion.join()
        assertEquals(2, f.reads)
        assertEquals(1, f.removals)
        assertTrue(f.editor.deleteState.value.deleted)
        assertNull(f.editor.deleteState.value.error)
        f.editor.onContextChanged()
        assertTrue(f.editor.deleteState.value.deleted)
        f.editor.delete()
        assertEquals(1, f.deletes)
    }

    @Test fun `replacement authentication after realtime removal cannot verify or commit a delayed DELETE`() = runBlocking {
        val f = DeleteFixture()
        val gate = CompletableDeferred<Unit>()
        f.afterDelete = { gate.await() }
        val deletion = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.delete() }
        f.targetPresent = false
        f.absent = true
        f.context = f.context.copy(authentication = ProtectedRequestAuthentication("replacement-fixture"))
        f.editor.onContextChanged()
        gate.complete(Unit)
        deletion.join()
        assertEquals(1, f.deletes)
        assertEquals(1, f.reads)
        assertEquals(0, f.removals)
        assertFalse(f.editor.deleteState.value.deleted)
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.editor.deleteState.value.error)
        f.editor.delete()
        assertEquals(1, f.deletes)
        assertEquals(1, f.reads)
    }

    @Test fun `call returns room only after encrypted link receipt under the captured account`() = runBlocking {
        val f = CallFixture()
        assertEquals("cassi-fixture-room", f.starter.createCall())
        assertEquals(listOf("https://meet.example/cassi-fixture-room"), f.links)
        assertEquals(1, f.contexts.size)
        assertEquals(f.context.owner(), f.contexts.single().owner())
        assertFalse(f.starter.state.value.busy)
        assertNull(f.starter.state.value.error)
    }

    @Test fun `plaintext call acknowledgement cannot launch a protected call`() = runBlocking {
        val f = CallFixture()
        f.response = receipt().copy(payload = MessageResponsePayload("markdown", "https://meet.example/room"))
        assertNull(f.starter.createCall())
        assertEquals(ProtectedStreamUpdateFailure.INVALID_RESPONSE, f.starter.state.value.error)
    }

    @Test fun `missing encryption key cannot launch a conference`() = runBlocking {
        val f = CallFixture()
        f.beforeReceipt = { throw EncryptedChatException(EncryptedChatFailure.MISSING_KEY) }
        assertNull(f.starter.createCall())
        assertFalse(f.starter.state.value.busy)
        assertNotNull(f.starter.state.value.error)
    }

    @Test fun `call receipt after switch away and back cannot launch the old conference`() = runBlocking {
        val f = CallFixture()
        val original = f.context
        val gate = CompletableDeferred<Unit>()
        f.beforeReceipt = { gate.await() }
        var room: String? = "not-called"
        val call = launch(start = CoroutineStart.UNDISPATCHED) { room = f.starter.createCall() }
        f.context = original.copy(userUuid = "other")
        f.starter.onContextChanged()
        f.context = original
        f.starter.onContextChanged()
        gate.complete(Unit)
        call.join()
        assertNull(room)
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.starter.state.value.error)
    }

    @Test fun `double tap while announcing a call sends one encrypted link`() = runBlocking {
        val f = CallFixture()
        val gate = CompletableDeferred<Unit>()
        f.beforeReceipt = { gate.await() }
        val call = launch(start = CoroutineStart.UNDISPATCHED) { assertNotNull(f.starter.createCall()) }
        assertTrue(f.starter.state.value.busy)
        assertNull(f.starter.createCall())
        assertEquals(1, f.links.size)
        gate.complete(Unit)
        call.join()
        assertFalse(f.starter.state.value.busy)
    }

    @Test fun `same owner with replacement authentication cannot launch after encrypted call receipt`() = runBlocking {
        val f = CallFixture()
        f.context = f.context.copy(authentication = ProtectedRequestAuthentication("original-call-fixture"))
        val originalOwner = f.context.owner()
        val gate = CompletableDeferred<Unit>()
        f.beforeReceipt = { gate.await() }
        var room: String? = "not-called"
        val call = launch(start = CoroutineStart.UNDISPATCHED) { room = f.starter.createCall() }
        f.context = f.context.copy(authentication = ProtectedRequestAuthentication("replacement-call-fixture"))
        assertEquals(originalOwner, f.context.owner())
        f.starter.onContextChanged()
        gate.complete(Unit)
        call.join()
        assertNull(room)
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.starter.state.value.error)
        assertFalse(f.starter.state.value.busy)
    }

    private class DeleteFixture(role: String = "owner") {
        var context = editContext(role).copy(authentication = ProtectedRequestAuthentication("original-fixture"))
        var targetPresent = true
        var reads = 0
        var deletes = 0
        var removals = 0
        var absent = false
        var verificationError: ApiError? = null
        var deleteResult: ApiResult<String, ApiError> = ApiResult.Success("")
        var afterDelete: (suspend () -> Unit)? = null
        val editor = ProtectedStreamEditor(
            currentContext = { context.takeIf { targetPresent } },
            readDetails = { captured ->
                reads++
                if (reads > 1 && verificationError != null) ApiResult.Error(requireNotNull(verificationError))
                else if (absent) ApiResult.Error(ApiError("Not found", "404")) else ApiResult.Success(captured.stream)
            },
            writeDetails = { _, _ -> error("Not a metadata edit") },
            commit = { _, _ -> error("Not a metadata edit") },
            deleteDetails = { captured ->
                assertEquals("server-a", captured.server.id)
                deletes++
                afterDelete?.invoke()
                deleteResult
            },
            commitDelete = { captured -> assertEquals("user-a", captured.userUuid); removals++ },
            currentDeletionAccount = { captured -> context.owner() == captured.owner() && context.server == captured.server &&
                captured.authentication?.matches(context.authentication) == true },
        )
    }

    private class CallFixture {
        var context = editContext()
        var response = receipt()
        var beforeReceipt: (suspend () -> Unit)? = null
        val links = mutableListOf<String>()
        val contexts = mutableListOf<ProtectedStreamEditContext>()
        val starter = ProtectedCallStarter(
            currentContext = { context }, serverUrl = { "https://meet.example" },
            topicUuid = TOPIC, createRoom = { "cassi-fixture-room" },
            sendEncrypted = { captured, text, _ ->
                contexts += captured
                links += text
                beforeReceipt?.invoke()
                response
            },
        )
    }

    companion object {
        private const val STREAM = "00000000-0000-0000-0000-000000000001"
        private const val TOPIC = "00000000-0000-0000-0000-000000000002"
        private fun editContext(role: String = "owner") = ProtectedStreamEditContext(
            ServerConfig("server-a", "https://workspace.example", "", "Workspace", projectUuid = "project-a"), "user-a",
            Stream(STREAM, 0, 0, 0, "2026-10-15T12:00:00Z", "Protected", false, 0,
                notificationMode = "all_messages", encryption = true, currentEncryptionKeyUuid = "generation",
                ownerUuid = "user-a", projectId = "project-a", userUuid = "user-a", role = role),
        )
        private fun receipt() = MessageResponse("message-a", "2026-10-15T12:00:00Z", "2026-10-15T12:00:00Z",
            STREAM, TOPIC, "user-a", "user-a", MessageResponsePayload("e2ee", "opaque-ciphertext", keyUuid = "generation"),
            true, emptyMap(), true, projectId = "project-a")
    }
}
