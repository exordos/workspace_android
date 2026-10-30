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

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.ServerConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Destructive only to a dedicated emulator's FCM token, with explicit opt-in. */
internal fun selectTokenRefreshServer(servers: List<ServerConfig>, expectedServer: String): ServerConfig {
    return servers.single { it.id == expectedServer }
}

class LivePushTokenRefreshTest {
    @Test fun tokenRefreshSelectsItsFixtureAmongMultipleSavedConnections() {
        val other = ServerConfig("cassi-pr97-other", "https://other.invalid", "", "Other")
        val target = ServerConfig("cassi-pr97-target", "https://target.invalid", "", "Target")
        assertEquals(target, selectTokenRefreshServer(listOf(other, target), target.id))
    }

    @Test fun sdkTokenReplacementRetainsTheDeviceAndEncryptionKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        require(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("allowTokenRefresh") == "true")
        val app = context.applicationContext as WorkspaceApplication
        val expectedServer = arguments.getString("serverId") ?: "cassi-optimized-push-acceptance"
        val server = selectTokenRefreshServer(app.serverRepository.serversFlow.first(), expectedServer)
        require(!server.needsToRelogin)
        require(server.id == "cassi-optimized-push-acceptance" || server.id.startsWith("cassi-pr97-"))
        val keys = PushKeyStore(context)
        val old = requireNotNull(keys.get(server.id))
        require(!old.disabled)
        val work = WorkManager.getInstance(context)
        val previousWork = work.getWorkInfosForUniqueWork("workspace-push-registration")
            .get(10, TimeUnit.SECONDS).map { it.id }.toSet()
        val firebase = FirebaseMessaging.getInstance()
        val previous = Tasks.await(firebase.token, 30, TimeUnit.SECONDS)
        Tasks.await(firebase.deleteToken(), 30, TimeUnit.SECONDS)
        val replacement = Tasks.await(firebase.token, 30, TimeUnit.SECONDS)
        assertTrue("FCM did not replace its token", replacement.isNotBlank() && replacement != previous)
        // The real SDK callback must schedule reconciliation; do not invoke it from this test.
        withTimeout(120_000) {
            while (!work.getWorkInfosForUniqueWork("workspace-push-registration")
                    .get(10, TimeUnit.SECONDS).any { it.id !in previousWork && it.state == WorkInfo.State.SUCCEEDED }) {
                delay(200)
            }
        }
        assertEquals("Token replacement changed the push identity", old, keys.get(server.id))
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(replacement.toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(context.cacheDir, "live-push-token-fingerprint.txt").writeText(fingerprint)
        println("LIVE_PUSH_TOKEN_REFRESH device_uuid=${old.deviceUuid}")
    }
}
