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

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.TokenPair
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/** Seeds authorized sessions on a dedicated debug emulator before a same-certificate release update. */
class LivePushConnectionSeedTest {
    @Test fun seedDedicatedEmulatorBeforeOptimizedUpdate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(Build.HARDWARE in listOf("ranchu", "goldfish"))
        require(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        require(InstrumentationRegistry.getArguments().getString("allowSeed") == "true")
        val app = context.applicationContext as WorkspaceApplication
        val existing = app.serverRepository.serversFlow.first()
        val append = InstrumentationRegistry.getArguments().getString("allowAppend") == "true"
        require(existing.isEmpty() || (append && existing.all { it.id.startsWith("cassi-pr97-") })) {
            "Existing sessions must be preserved"
        }
        val input = File(context.cacheDir, "live-push-connection.json")
        try {
            val fixture = JSONObject(input.readText())
            require(input.delete())
            val base = fixture.getString("base_url").trimEnd('/')
            require(base.startsWith("https://") && URI(base).userInfo == null)
            val config = ServerConfig(fixture.getString("server_id"), base, "", "Push acceptance",
                projectUuid = fixture.getString("project_uuid"))
            require(existing.none { it.id == config.id }) { "A saved session must not be replaced" }
            if (append) require(config.id.startsWith("cassi-pr97-"))
            val work = WorkManager.getInstance(context)
            val previousWork = work.getWorkInfosForUniqueWork("workspace-push-registration")
                .get(10, TimeUnit.SECONDS).map { it.id }.toSet()
            app.serverRepository.addServer(config, TokenPair(fixture.getString("access_token"), fixture.getString("refresh_token")))
            app.serverRepository.setSelectedServerId(config.id)
            val keys = PushKeyStore(context)
            PushRegistrationWorker.schedule(context)
            withTimeout(120_000) {
                while (true) {
                    val succeeded = work.getWorkInfosForUniqueWork("workspace-push-registration")
                        .get(10, TimeUnit.SECONDS).any { it.id !in previousWork && it.state == WorkInfo.State.SUCCEEDED }
                    if (keys.get(config.id) != null && succeeded) break
                    delay(200)
                }
            }
            val registration = requireNotNull(keys.get(config.id))
            assertEquals(fixture.getString("user_uuid"), registration.userUuid)
            assertEquals(fixture.getString("organization_uuid"), registration.organizationUuid)
            assertEquals(config.projectUuid, registration.projectUuid)
            assertFalse(registration.disabled)
            assertTrue(context.getSharedPreferences("secure_tokens", 0).edit().commit())
            println("LIVE_PUSH_SEED device_uuid=${registration.deviceUuid}")
        } catch (failure: Exception) {
            // JSON/parser and HTTP exception messages may contain fixture credentials.
            throw AssertionError("Dedicated fixture setup failed: ${failure.javaClass.simpleName}")
        } finally {
            input.delete()
        }
    }
}
