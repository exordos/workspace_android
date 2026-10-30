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

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.UserViewModelFactory
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.OwnUserRequest
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/** Verifies actual saved-owner authentication before a live notification/navigation test. */
class LivePushOwnerSmokeTest {
    @Test fun savedAcceptanceRegistrationReconcilesCurrentToken() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val base = requireNotNull(InstrumentationRegistry.getArguments().getString("workspaceBaseUrl")).trimEnd('/')
        val serverId = InstrumentationRegistry.getArguments().getString("serverId")
        val server = app.serverRepository.serversFlow.first().single {
            it.baseUrl.trimEnd('/') == base && (serverId == null || it.id == serverId)
        }
        val before = requireNotNull(PushKeyStore(context).get(server.id))
        require(!before.disabled && !server.needsToRelogin)
        val work = WorkManager.getInstance(context)
        val previous = work.getWorkInfosForUniqueWork("workspace-push-registration")
            .get(10, TimeUnit.SECONDS).map { it.id }.toSet()
        PushRegistrationWorker.schedule(context)
        withTimeout(180_000) {
            while (work.getWorkInfosForUniqueWork("workspace-push-registration").get(10, TimeUnit.SECONDS)
                    .none { it.id !in previous && it.state == WorkInfo.State.SUCCEEDED }) delay(200)
        }
        assertEquals("Reconciliation changed the saved identity", before, PushKeyStore(context).get(server.id))
        val token = Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        println("LIVE_PUSH_REGISTRATION device_uuid=${before.deviceUuid} key_uuid=${before.keyUuid} token_sha256=$fingerprint")
    }

    @Test fun savedReceiptRestoresItsAccountAndOpensActualChat() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val app = context.applicationContext as WorkspaceApplication
        val event = requireNotNull(arguments.getString("eventUuid"))
        val route = requireNotNull(livePushRoute(context, event, arguments.getString("serverId")))
        val expected = requireNotNull(arguments.getString("expectedMessageText"))
        if (arguments.getString("coldOtherAccount") == "true") {
            // Reproduce an initial repository-map race: the selected connection
            // exists before the notification's target repository is created.
            val other = app.serverRepository.serversFlow.first().first { it.id != route.serverId }
            app.serverRepository.setSelectedServerId(other.id)
            delay(500)
            app.eventsRepositoryStore.clear()
            app.eventsRepositoryStore.getOrCreate(other)
        }
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .putExtra("deeplink", "workspace-push/${route.identity}")
        instrumentation.startActivitySync(intent)
        val deadline = SystemClock.uptimeMillis() + 30_000
        fun visible(): Boolean {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
            return root.packageName?.toString() == context.packageName &&
                root.findAccessibilityNodeInfosByText(expected).any { it.text?.toString()?.contains(expected) == true }
        }
        while (!visible() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        val repo = app.eventsRepositoryStore.get(route.serverId)
        println("LIVE_PUSH_NAV selected_target=${app.serverRepository.selectedServerIdFlow.first() == route.serverId}" +
            " owner_proof_present=${repo?.verifiedOwnerAuthentication?.value != null}" +
            " repository_owner_matches=${repo?.currentUser?.value?.uuid == route.userUuid}")
        assertTrue("Authenticated receipt did not restore its actual chat", visible())
        assertEquals(route.serverId, app.serverRepository.selectedServerIdFlow.first())
    }

    @Test fun selectedAcceptanceSessionRefreshKeepsItsPushRegistration() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("allowSessionRefresh") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val base = requireNotNull(arguments.getString("workspaceBaseUrl")).trimEnd('/')
        val serverId = arguments.getString("serverId") ?: arguments.getString("eventUuid")?.let {
            requireNotNull(livePushRoute(context, it, null)).serverId
        }
        val keys = PushKeyStore(context)
        val server = app.serverRepository.serversFlow.first().single {
            val key = keys.get(it.id)
            it.baseUrl.trimEnd('/') == base && (serverId == null || it.id == serverId) &&
                key != null && key.userUuid == arguments.getString("userUuid") &&
                key.organizationUuid == arguments.getString("organizationUuid")
        }
        val before = requireNotNull(keys.get(server.id))
        app.serverRepository.setNeedsToRelogin(true, server.id)
        assertTrue("The recovery fixture must require sign-in before refresh",
            requireNotNull(app.serverRepository.getServer(server.id)).needsToRelogin)
        val store = ViewModelStore()
        try {
            val state = withContext(Dispatchers.Main) {
                ViewModelProvider(store, UserViewModelFactory(app.serverRepository))[UserViewModel::class.java]
            }
            val client = WorkspaceAPIClient(app.workspaceApiClient.client).apply { attachUserViewModel(state) }
            assertTrue("The real IAM session could not be renewed",
                withTimeout(20_000) { client.refreshToken(server.id) } is ApiResult.Success)
            val own = withTimeout(20_000) { client.performRequest(OwnUserRequest(), server.id) }
            assertTrue("The renewed credentials cannot authenticate", own is ApiResult.Success)
            assertEquals(before.userUuid, (own as ApiResult.Success).value.uuid)
            assertFalse(requireNotNull(app.serverRepository.getServer(server.id)).needsToRelogin)
            assertEquals("Session refresh changed the push key", before, keys.get(server.id))
            println("LIVE_PUSH_SESSION_REFRESH owner_matches=true registration_unchanged=true")
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun savedAcceptanceOwnerCanAuthenticate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as WorkspaceApplication
        val base = requireNotNull(InstrumentationRegistry.getArguments().getString("workspaceBaseUrl")).trimEnd('/')
        val serverId = InstrumentationRegistry.getArguments().getString("serverId")
        val server = app.serverRepository.serversFlow.first().single {
            it.baseUrl.trimEnd('/') == base && (serverId == null || it.id == serverId)
        }
        val registration = requireNotNull(PushKeyStore(context).get(server.id))
        assertFalse("Saved acceptance connection requires sign-in", server.needsToRelogin)
        val store = ViewModelStore()
        val state = ViewModelProvider(store, UserViewModelFactory(app.serverRepository))[UserViewModel::class.java]
        try {
            val client = WorkspaceAPIClient(app.workspaceApiClient.client).apply { attachUserViewModel(state) }
            val response = withTimeout(20_000) { client.performRequest(OwnUserRequest(), server.id, server) }
            assertTrue("Saved acceptance credentials cannot authenticate", response is ApiResult.Success)
            assertEquals(registration.userUuid, (response as ApiResult.Success).value.uuid)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
