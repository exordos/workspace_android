/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.genesiscorporation.workspace.beta.data.push

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.WorkspaceApplication
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.TokenPair
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class PushAccountAndRecoveryTest {
    @Test fun explicitLoginAndProjectChangeRetireTheOldDeviceBeforeReplacingCredentials() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val repository = app.serverRepository
        val selected = repository.selectedServerIdFlow.first()
        val schedule = repository.onPushRegistrationNeeded
        val requests = CopyOnWriteArrayList<LoopbackWorkspaceServer.Request>()
        val id = "cassi-push-owner-${UUID.randomUUID()}"
        val project = UUID.randomUUID().toString()
        val keys = PushKeyStore(context)
        repository.onPushRegistrationNeeded = null
        LoopbackWorkspaceServer { requests += it; LoopbackWorkspaceServer.Response("{}") }.use { server ->
            try {
                repository.addServer(ServerConfig(id, server.url, "", "Push owner fixture", projectUuid = project),
                    TokenPair("cassi-old-access", "cassi-old-refresh"))
                val old = keys.getOrCreate(id, UUID.randomUUID().toString(), project, UUID.randomUUID().toString())
                repository.saveTokensAndClearRelogin(id, TokenPair("cassi-new-access", "cassi-new-refresh"))
                assertTrue(requests.any { it.method == "DELETE" && it.path.endsWith("/push_devices/${old.deviceUuid}") &&
                    it.headers["authorization"] == "Bearer cassi-old-access" })
                assertNull(keys.get(id))
                assertEquals("cassi-new-access", repository.tokensFor(id)?.accessToken)
                val next = keys.getOrCreate(id, old.organizationUuid, project, old.userUuid)
                requests.clear()
                repository.updateServerProjectUuid(id, UUID.randomUUID().toString())
                assertTrue(requests.any { it.method == "DELETE" && it.path.endsWith("/push_devices/${next.deviceUuid}") })
                assertNull(keys.get(id))
            } finally {
                repository.removeServer(id)
                repository.setSelectedServerId(selected)
                repository.onPushRegistrationNeeded = schedule
            }
        }
    }

    @Test fun automaticRefreshKeepsRegistrationAndExistingNotificationHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val repository = app.serverRepository
        val selected = repository.selectedServerIdFlow.first()
        val schedule = repository.onPushRegistrationNeeded
        val id = "cassi-push-refresh-${UUID.randomUUID()}"
        val keys = PushKeyStore(context)
        repository.onPushRegistrationNeeded = null
        try {
            val project = UUID.randomUUID().toString()
            repository.addServer(ServerConfig(id, "http://127.0.0.1:1", "", "Refresh fixture", projectUuid = project),
                TokenPair("cassi-refresh-access", "cassi-refresh-token"))
            val registration = keys.getOrCreate(id, UUID.randomUUID().toString(), project, UUID.randomUUID().toString())
            assertTrue(repository.saveRefreshedTokensIfCurrent(id, "cassi-refresh-token", TokenPair("cassi-refreshed-access", "cassi-refreshed-token")))
            assertEquals(registration, keys.get(id))
        } finally {
            keys.remove(id) // This fixture has no remote device; do not wait on a nonexistent HTTP listener.
            repository.removeServer(id)
            repository.setSelectedServerId(selected)
            repository.onPushRegistrationNeeded = schedule
        }
    }

    @Test fun existingMessageApiRepairsLostReadAndDeleteUpdatesWithoutClosingUnreadMessages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val repository = app.serverRepository
        val selected = repository.selectedServerIdFlow.first()
        val schedule = repository.onPushRegistrationNeeded
        val id = "cassi-push-recovery-${UUID.randomUUID()}"
        val owner = UUID.randomUUID().toString()
        val project = UUID.randomUUID().toString()
        val stream = UUID.randomUUID().toString()
        val topic = UUID.randomUUID().toString()
        val read = UUID.randomUUID().toString()
        val deleted = UUID.randomUUID().toString()
        val unread = UUID.randomUUID().toString()
        val requests = CopyOnWriteArrayList<LoopbackWorkspaceServer.Request>()
        fun message(uuid: String, isRead: Boolean) = """{"uuid":"$uuid","updated_at":"2026-10-01T00:00:00Z","created_at":"2026-10-01T00:00:00Z","stream_uuid":"$stream","topic_uuid":"$topic","user_uuid":"$owner","author_uuid":"$owner","payload":{"kind":"markdown","content":"Synthetic recovery fixture"},"is_own":false,"reactions":{},"read":$isRead,"project_id":"$project"}"""
        repository.onPushRegistrationNeeded = null
        LoopbackWorkspaceServer { request ->
            requests += request
            when {
                request.path.endsWith("/messenger/me/") -> LoopbackWorkspaceServer.Response("""{"username":"cassi-fixture","uuid":"$owner","status":"active","avatar":""}""")
                request.path.endsWith("/messenger/messages/") -> LoopbackWorkspaceServer.Response("[${message(read, true)},${message(unread, false)}]")
                else -> LoopbackWorkspaceServer.Response("{}")
            }
        }.use { server ->
            try {
                repository.addServer(ServerConfig(id, server.url, "", "Recovery fixture", projectUuid = project),
                    TokenPair("cassi-recovery-access", "cassi-recovery-refresh"))
                val registration = PushKeyStore(context).getOrCreate(id, UUID.randomUUID().toString(), project, owner)
                val inbox = PushInbox(context)
                fun route(uuid: String) = PushRoute(UUID.randomUUID().toString(), id, registration.organizationUuid, project, owner, stream, topic, uuid)
                val routes = listOf(route(read), route(deleted), route(unread))
                routes.forEach { inbox.accept(it, System.currentTimeMillis() - 15 * 24 * 3600_000L) }
                PushReconciliationWorker.schedule(context)
                val deadline = android.os.SystemClock.elapsedRealtime() + 25_000
                while ((inbox.resolve(routes[0].identity) != null || inbox.resolve(routes[1].identity) != null) && android.os.SystemClock.elapsedRealtime() < deadline) delay(50)
                assertNull(inbox.resolve(routes[0].identity)); assertNull(inbox.resolve(routes[1].identity))
                assertNotNull(inbox.resolve(routes[2].identity))
                assertTrue(requests.any { it.path.endsWith("/messenger/me/") })
                assertTrue(requests.any { it.path.endsWith("/messenger/messages/") })
                assertFalse(inbox.accept(routes[0], System.currentTimeMillis()))
            } finally {
                repository.removeServer(id)
                repository.setSelectedServerId(selected)
                repository.onPushRegistrationNeeded = schedule
            }
        }
    }
}
