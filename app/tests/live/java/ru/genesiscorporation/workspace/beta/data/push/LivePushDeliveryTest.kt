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

import android.app.Notification
import android.app.NotificationManager
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.WorkspaceApplication
import java.util.UUID
import java.io.File

/** Observes real external FCM delivery. Never invokes the receipt handler or injects a payload. */
class LivePushDeliveryTest {
    @Test fun externallyDeliveredNotificationOpensOrCancelsTheSavedChat() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WorkspaceApplication
        val eventUuid = requireNotNull(arguments.getString("eventUuid"))
        require(UUID.fromString(eventUuid).toString() == eventUuid)
        val route = withTimeout(30_000) {
            var receipt = livePushRoute(context, eventUuid, arguments.getString("serverId"))
            while (receipt == null) {
                delay(100)
                receipt = livePushRoute(context, eventUuid, arguments.getString("serverId"))
            }
            receipt
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        fun active() = manager.activeNotifications.singleOrNull { it.tag == WorkspacePushNotifications.messageTag(route.identity) }?.notification
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (SystemClock.uptimeMillis() < deadline) {
                if (condition()) return
                SystemClock.sleep(50)
            }
            assertTrue(message, condition())
        }
        if (arguments.getString("action") == "cancel") {
            await("Authenticated FCM cancellation was not applied") {
                active() == null && livePushClosed(context, route.identity)
            }
            return@runBlocking
        }
        val preview = requireNotNull(arguments.getString("expectedPreview"))
        val sender = requireNotNull(arguments.getString("expectedSender"))
        await("Expected FCM notification was not displayed") { active() != null }
        val notification = requireNotNull(active())
        assertEquals(sender, notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(preview, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNotNull(notification.contentIntent)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(notification.contentIntent.isImmutable)
        val action = arguments.getString("action")
        if (action != "open") return@runBlocking

        assertFalse("Unlock the acceptance device before navigation", context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        await("System notification shade did not close") {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != "com.android.systemui"
        }
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        instrumentation.startActivitySync(intent)
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= 34) {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }
        notification.contentIntent.send(context, 0, null, null, null, null, options.toBundle())
        val message = requireNotNull(arguments.getString("expectedMessageText"))
        await("The notification did not open its actual chat message") {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.packageName?.toString() == context.packageName &&
                root.findAccessibilityNodeInfosByText(message).any { it.text?.toString()?.contains(message) == true }
        }
        assertEquals(route.serverId, app.serverRepository.selectedServerIdFlow.first())
        val server = requireNotNull(app.serverRepository.getServer(route.serverId))
        assertEquals(route.projectUuid, server.projectUuid)
        assertEquals(requireNotNull(arguments.getString("workspaceBaseUrl")).trimEnd('/'), server.baseUrl.trimEnd('/'))
        if (arguments.getString("screenshot") == "true") {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(context.cacheDir, "live-push-chat.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
        }
    }
}
