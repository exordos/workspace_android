package com.exordos.workspace.modules.streaminfo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.ProtectedRequestAuthentication
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.UpdateProtectedStreamRequestData

class ProtectedStreamEditorTest {
    @Test fun `credential replacement during preflight read prevents protected PUT before user projection changes`() = runBlocking {
        val f = Fixture()
        f.context = f.context.copy(authentication = ProtectedRequestAuthentication("synthetic-old-token"))
        f.beforeRead = { f.context = f.context.copy(authentication = ProtectedRequestAuthentication("synthetic-new-token")) }
        f.editor.update("New", "Description")
        assertTrue(f.writes.isEmpty())
        assertTrue(f.commits.isEmpty())
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.editor.state.value.error)
    }

    @Test fun `owner and administrator save confirmed metadata under captured account`() = runBlocking {
        for (role in listOf("owner", "administrator")) {
            val f = Fixture(role)
            f.editor.update("  New name  ", "  New description  ")
            assertEquals(1, f.reads.size)
            assertEquals(1, f.writes.size)
            assertEquals(f.context.server, f.reads.single().server)
            assertEquals(f.context.server, f.writes.single().first.server)
            assertEquals(UpdateProtectedStreamRequestData("New name", "New description"), f.writes.single().second)
            assertEquals("New description", f.commits.single().description)
            assertSame(f.commits.single(), f.editor.details.value)
            assertTrue(f.editor.state.value.saved)
            assertFalse(f.editor.state.value.busy)
            assertNull(f.editor.state.value.error)
        }
    }

    @Test fun `fresh server role prevents update even when cached role still says owner`() = runBlocking {
        val f = Fixture()
        f.readResponse = ApiResult.Success(f.context.stream.copy(role = "member"))
        f.editor.update("New", "Description")
        assertTrue(f.writes.isEmpty())
        assertTrue(f.commits.isEmpty())
        assertEquals(ProtectedStreamUpdateFailure.FORBIDDEN, f.editor.state.value.error)
    }

    @Test fun `details remain readable to a member without granting edit rights`() = runBlocking {
        val f = Fixture("member")
        f.editor.load()
        assertEquals("Original description", f.editor.details.value!!.description)
        assertFalse(f.editor.state.value.saved)
        assertTrue(f.writes.isEmpty())
        f.editor.update("New", "Description")
        assertEquals(ProtectedStreamUpdateFailure.FORBIDDEN, f.editor.state.value.error)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun `wrong server projection cannot become an edit baseline`() = runBlocking {
        val changes: List<(Stream) -> Stream> = listOf(
            { it.copy(uuid = "other-stream") }, { it.copy(projectId = "other-project") },
            { it.copy(userUuid = "other-user") }, { it.copy(encryption = false) },
            { it.copy(isPrivate = true) }, { it.copy(directUserUuid = "other-peer") },
        )
        for (change in changes) {
            val f = Fixture()
            f.readResponse = ApiResult.Success(change(f.context.stream))
            f.editor.update("New", "Description")
            assertEquals(ProtectedStreamUpdateFailure.INVALID_RESPONSE, f.editor.state.value.error)
            assertTrue(f.writes.isEmpty())
            assertTrue(f.commits.isEmpty())
        }
    }

    @Test fun `only exact confirmed edited values are committed`() = runBlocking {
        val f = Fixture()
        f.writeResponse = ApiResult.Success(f.context.stream.copy(name = "Unexpected", description = "Description"))
        f.editor.update("New", "Description")
        assertEquals(1, f.writes.size)
        assertTrue(f.commits.isEmpty())
        assertFalse(f.editor.state.value.saved)
        assertEquals(ProtectedStreamUpdateFailure.INVALID_RESPONSE, f.editor.state.value.error)
    }

    @Test fun `account switch while reading prevents PUT`() = runBlocking {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.beforeRead = { gate.await() }
        val save = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.update("New", "Description") }
        f.context = f.context.copy(userUuid = "another-user")
        f.editor.onContextChanged()
        gate.complete(Unit)
        save.join()
        assertTrue(f.writes.isEmpty())
        assertNull(f.editor.details.value)
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.editor.state.value.error)
    }

    @Test fun `account switch while saving rejects late local commit`() = runBlocking {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.beforeWrite = { gate.await() }
        val save = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.update("New", "Description") }
        f.context = f.context.copy(server = f.context.server.copy(id = "another-server", baseUrl = "https://other.example"))
        f.editor.onContextChanged()
        gate.complete(Unit)
        save.join()
        assertEquals(1, f.writes.size)
        assertEquals("server-a", f.writes.single().first.server.id)
        assertTrue(f.commits.isEmpty())
        assertNull(f.editor.details.value)
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.editor.state.value.error)
    }

    @Test fun `switch away and back cannot revive a suspended save`() = runBlocking {
        val f = Fixture()
        val original = f.context
        val gate = CompletableDeferred<Unit>()
        f.beforeWrite = { gate.await() }
        val save = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.update("New", "Description") }
        f.context = original.copy(userUuid = "another-user")
        f.editor.onContextChanged()
        f.context = original
        f.editor.onContextChanged()
        gate.complete(Unit)
        save.join()
        assertTrue(f.commits.isEmpty())
        assertEquals(ProtectedStreamUpdateFailure.STALE, f.editor.state.value.error)
    }

    @Test fun `double tap performs one metadata PUT and exposes busy state`() = runBlocking {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.beforeWrite = { gate.await() }
        val save = launch(start = CoroutineStart.UNDISPATCHED) { f.editor.update("New", "Description") }
        assertTrue(f.editor.state.value.busy)
        f.editor.update("Other", "Other description")
        assertEquals(1, f.writes.size)
        gate.complete(Unit)
        save.join()
        assertEquals(1, f.commits.size)
        assertFalse(f.editor.state.value.busy)
        assertTrue(f.editor.state.value.saved)
    }

    @Test fun `blank name and server rejection never pretend success`() = runBlocking {
        val f = Fixture()
        f.editor.update("   ", "Description")
        assertTrue(f.reads.isEmpty())
        assertTrue(f.writes.isEmpty())
        assertEquals(ProtectedStreamUpdateFailure.INVALID_NAME, f.editor.state.value.error)
        f.writeResponse = ApiResult.Error(ApiError("Forbidden", "403"))
        f.editor.update("New", "Description")
        assertEquals(ProtectedStreamUpdateFailure.FORBIDDEN, f.editor.state.value.error)
        assertFalse(f.editor.state.value.saved)
        assertFalse(f.editor.state.value.busy)
        assertTrue(f.commits.isEmpty())
    }

    private class Fixture(role: String = "owner") {
        var context = ProtectedStreamEditContext(
            ServerConfig("server-a", "https://workspace.example", "", "Workspace", projectUuid = "project-a"),
            "user-a", stream(role),
        )
        var readResponse: ApiResult<Stream, ApiError>? = null
        var writeResponse: ApiResult<Stream, ApiError>? = null
        var beforeRead: (suspend () -> Unit)? = null
        var beforeWrite: (suspend () -> Unit)? = null
        val reads = mutableListOf<ProtectedStreamEditContext>()
        val writes = mutableListOf<Pair<ProtectedStreamEditContext, UpdateProtectedStreamRequestData>>()
        val commits = mutableListOf<Stream>()
        val editor = ProtectedStreamEditor(
            currentContext = { context },
            readDetails = { captured ->
                reads += captured
                beforeRead?.invoke()
                readResponse ?: ApiResult.Success(captured.stream)
            },
            writeDetails = { captured, body ->
                writes += captured to body
                beforeWrite?.invoke()
                writeResponse ?: ApiResult.Success(captured.stream.copy(name = body.name, description = body.description))
            },
            commit = { _, response -> commits += response },
        )
    }

    companion object {
        private fun stream(role: String) = Stream(
            uuid = "00000000-0000-0000-0000-000000000001", unreadCount = 0,
            activeUnreadCount = 0, passiveUnreadCount = 0, updatedAt = "2026-10-15T12:00:00Z",
            name = "Original", isPrivate = false, color = 0, notificationMode = "all_messages",
            encryption = true, currentEncryptionKeyUuid = "generation-a", ownerUuid = "user-a",
            projectId = "project-a", userUuid = "user-a", role = role, description = "Original description",
        )
    }
}
