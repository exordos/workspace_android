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

package com.exordos.workspace.data.push

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.WorkspaceApplication
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Explicit opt-in real logout/re-registration of one selected acceptance connection. */
class LivePushRegistrationTest {
    @Test fun selectedAcceptanceConnectionRemovesAndReplacesItsDeviceKey() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("allowAccountRemoval") == "true")
        val base = requireNotNull(arguments.getString("workspaceBaseUrl")).trimEnd('/')
        require(base.startsWith("https://") && URI(base).userInfo == null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val repository = app.serverRepository
        val servers = repository.serversFlow.first()
        val selected = repository.selectedServerIdFlow.first()
        val server = servers.single { it.baseUrl.trimEnd('/') == base }
        val tokens = requireNotNull(repository.tokensFor(server.id))
        val keys = PushKeyStore(context)
        val old = requireNotNull(keys.get(server.id))
        require(old.organizationUuid == arguments.getString("organizationUuid"))
        require(old.projectUuid == arguments.getString("projectUuid"))
        require(old.userUuid == arguments.getString("userUuid") && !old.disabled)
        val deleteStatus = AtomicInteger(0)
        // Observe the real application client: local key removal alone cannot prove unregister.
        app.workspaceApiClient.client.plugin(HttpSend).intercept { request ->
            execute(request).also { call ->
                if (request.method.value == "DELETE" && request.url.build().encodedPath.endsWith("/push_devices/${old.deviceUuid}")) {
                    deleteStatus.set(call.response.status.value)
                }
            }
        }
        val otherKeys = keys.all().filter { it.serverId != server.id }
        val work = WorkManager.getInstance(context)
        try {
            withTimeout(25_000) { repository.removeServer(server.id) }
            assertEquals("The backend did not acknowledge device deletion", 204, deleteStatus.get())
            assertNull(repository.getServer(server.id))
            assertNull(repository.tokensFor(server.id))
            assertNull(keys.get(server.id))
            assertTrue("Another connection's key changed", otherKeys.all { keys.get(it.serverId) == it })
            repository.addServer(server, tokens)
            repository.setSelectedServerId(selected)
            val previousWork = work.getWorkInfosForUniqueWork("workspace-push-registration")
                .get(10, TimeUnit.SECONDS).map { it.id }.toSet()
            PushRegistrationWorker.schedule(context)
            withTimeout(90_000) {
                while (true) {
                    val current = keys.get(server.id)
                    val succeeded = work.getWorkInfosForUniqueWork("workspace-push-registration")
                        .get(10, TimeUnit.SECONDS).any { it.id !in previousWork && it.state == WorkInfo.State.SUCCEEDED }
                    if (current != null && !current.disabled && current.deviceUuid != old.deviceUuid && succeeded) break
                    delay(200)
                }
            }
            val replacement = requireNotNull(keys.get(server.id))
            assertNotEquals(old.keyUuid, replacement.keyUuid)
            assertTrue("Other saved connections changed", repository.serversFlow.first().toSet() == servers.toSet())
            assertTrue("Another connection's key changed", otherKeys.all { keys.get(it.serverId) == it })
            println("LIVE_PUSH_LIFECYCLE old_device_uuid=${old.deviceUuid} new_device_uuid=${replacement.deviceUuid}")
        } finally {
            withContext(NonCancellable) {
                if (repository.getServer(server.id) == null) repository.addServer(server, tokens)
                repository.setSelectedServerId(selected)
                PushRegistrationWorker.schedule(context)
            }
        }
    }
}
