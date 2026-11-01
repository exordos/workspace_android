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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.TokenPair
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real application logout must handle a slow DELETE and still bound an unresponsive peer. */
class PushLogoutNetworkTest {
    @Test fun stalledUnregisterCannotKeepCredentialsOrPushKeyAfterLogout() = checkLogout(25_000)

    @Test fun slowSuccessfulDeleteFinishesBeforeCredentialsAreRemoved() = checkLogout(6_100, 6_000)

    private fun checkLogout(responseDelayMillis: Long, minimumWaitMillis: Long = 0) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val repository = app.serverRepository
        val selected = repository.selectedServerIdFlow.first()
        val schedule = repository.onPushRegistrationNeeded
        val release = CountDownLatch(1)
        val request = AtomicReference<LoopbackWorkspaceServer.Request>()
        val id = "cassi-push-logout-${UUID.randomUUID()}"
        val keys = PushKeyStore(context)
        repository.onPushRegistrationNeeded = null
        // Measure the DELETE timeout, not unrelated startup registration's network/mutex wait.
        WorkManager.getInstance(context).cancelUniqueWork("workspace-push-registration").result.get(10, TimeUnit.SECONDS)
        repository.pushRegistrationMutex.withLock { }
        LoopbackWorkspaceServer {
            request.set(it)
            release.await(responseDelayMillis, TimeUnit.MILLISECONDS)
            LoopbackWorkspaceServer.Response("{}")
        }.use { server ->
            try {
                repository.addServer(ServerConfig(id, server.url, "", "Push logout fixture", projectUuid = UUID.randomUUID().toString()),
                    TokenPair("synthetic-logout-access", "synthetic-logout-refresh"))
                val registration = keys.getOrCreate(id, UUID.randomUUID().toString(),
                    requireNotNull(repository.getServer(id)?.projectUuid), UUID.randomUUID().toString())
                val started = System.nanoTime()
                repository.removeServer(id)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                assertTrue("Logout waited for a stalled network response: ${elapsedMs}ms", elapsedMs < 18_000)
                assertTrue("Logout abandoned an online DELETE before its response: ${elapsedMs}ms", elapsedMs >= minimumWaitMillis)
                assertEquals("DELETE", request.get()?.method)
                assertTrue(request.get()?.path.orEmpty().endsWith("/push_devices/${registration.deviceUuid}"))
                assertNull(repository.tokensFor(id))
                assertNull(repository.getServer(id))
                assertNull(keys.get(id))
            } finally {
                release.countDown()
                repository.removeServer(id)
                keys.remove(id)
                repository.setSelectedServerId(selected)
                repository.onPushRegistrationNeeded = schedule
                schedule?.invoke()
            }
        }
    }
}
