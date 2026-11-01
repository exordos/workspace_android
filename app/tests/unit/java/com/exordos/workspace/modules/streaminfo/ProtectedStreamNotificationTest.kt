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
import com.exordos.workspace.data.remote.HTTPMethod
import com.exordos.workspace.data.remote.dto.Stream

class ProtectedStreamNotificationTest {
    @Test fun `owner administrator moderator and member update their own preference under captured configuration`() = runBlocking {
        for (role in listOf("owner", "administrator", "moderator", "member")) {
            val f = Fixture(role)
            assertTrue(f.updater.update("mentions_only"))
            assertEquals(listOf(f.original to "mentions_only"), f.writes)
            assertEquals(listOf("mentions_only"), f.applied)
        }
    }

    @Test fun `switch before first notification action cannot rebind old VM to new account`() = runBlocking {
        val f = Fixture()
        f.context = f.original.copy(userUuid = "other-account")
        f.updater.onContextChanged()
        assertFalse(f.updater.update("muted"))
        assertTrue(f.writes.isEmpty())
        assertTrue(f.applied.isEmpty())
    }

    @Test fun `late result after identity switches away and back cannot apply`() = runBlocking {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.beforeReceipt = { gate.await() }
        val action = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(f.updater.update("muted")) }
        f.context = null
        f.updater.onContextChanged()
        f.context = f.original
        f.updater.onContextChanged()
        gate.complete(Unit)
        action.join()
        assertEquals(1, f.writes.size)
        assertTrue(f.applied.isEmpty())
        assertFalse(f.updater.update("all_messages"))
        assertEquals(1, f.writes.size)
    }

    @Test fun `changed server configuration is rejected before HTTP even without observer delivery`() = runBlocking {
        val f = Fixture()
        f.context = f.original.copy(server = f.original.server.copy(baseUrl = "https://other.example"))
        assertFalse(f.updater.update("muted"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun `replacement credentials cannot be used while current user projection is still stale`() = runBlocking {
        val f = Fixture()
        f.authentication = "synthetic-new-account-token"
        // The old repository can still contain the old user and stream until the next identity event.
        assertFalse(f.updater.update("muted"))
        assertTrue(f.writes.isEmpty())
        f.updater.onContextChanged()
        f.authentication = "synthetic-original-token"
        f.updater.onContextChanged()
        assertFalse(f.updater.update("all_messages"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun `protected request pins Authorization and disables API client credential refresh`() {
        val request = protectedNotificationRequest("stream", "muted", "synthetic-captured-token")
        assertFalse(request.requiresApiKey)
        assertEquals(mapOf("Authorization" to "Bearer synthetic-captured-token"), request.additionalHeaders)
        assertEquals(HTTPMethod.POST, request.method)
        assertEquals("/api/workspace/v1/messenger/streams/stream/actions/notifications/invoke", request.url)
        assertEquals("muted", request.data.notificationMode)
    }

    @Test fun `failed request does not apply preference and duplicate concurrent tap makes one request`() = runBlocking {
        val f = Fixture()
        f.result = ApiResult.Error(ApiError("Unavailable", "503"))
        assertFalse(f.updater.update("muted"))
        assertTrue(f.applied.isEmpty())
        val gate = CompletableDeferred<Unit>()
        f.beforeReceipt = { gate.await() }
        f.result = ApiResult.Success(Unit)
        val action = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(f.updater.update("all_messages")) }
        assertFalse(f.updater.update("mentions_only"))
        gate.complete(Unit)
        action.join()
        assertEquals(2, f.writes.size)
        assertEquals(listOf("all_messages"), f.applied)
    }

    @Test fun `stream arriving after mount binds only under original owner configuration and credentials`() = runBlocking {
        val f = Fixture(startMissing = true)
        f.context = f.original
        f.updater.onContextChanged()
        assertTrue(f.updater.update("muted"))
        f.context = null
        f.updater.onContextChanged()
        f.context = f.original
        assertFalse(f.updater.update("all_messages"))
        assertEquals(1, f.writes.size)
        val other = Fixture(startMissing = true)
        other.context = other.original.copy(userUuid = "other-user")
        assertFalse(other.updater.update("muted"))
        assertTrue(other.writes.isEmpty())
    }

    private class Fixture(role: String = "member", startMissing: Boolean = false) {
        val original = ProtectedStreamEditContext(ServerConfig("server", "https://workspace.example", "", "", false,
            projectUuid = "project"), "user", Stream(uuid = "stream", unreadCount = 0, activeUnreadCount = 0,
            passiveUnreadCount = 0, updatedAt = "", isArchived = false, notificationMode = "all_messages",
            isPrivate = false, name = "Protected", color = 0, role = role,
            projectId = "project", userUuid = "user", encryption = true, currentEncryptionKeyUuid = "generation"))
        var context: ProtectedStreamEditContext? = original.takeUnless { startMissing }
        var authentication = "synthetic-original-token"
        val writes = mutableListOf<Pair<ProtectedStreamEditContext, String>>()
        val applied = mutableListOf<String>()
        var beforeReceipt: (suspend () -> Unit)? = null
        var result: ApiResult<Unit, ApiError> = ApiResult.Success(Unit)
        val updater = ProtectedStreamNotificationUpdater({ context }, { authentication }, { captured, mode, token ->
            assertEquals("synthetic-original-token", token)
            writes += captured to mode
            beforeReceipt?.invoke()
            result
        }, { _, mode -> applied += mode }, initialOwner = original.owner(), initialConfiguration = original.server)
    }
}
