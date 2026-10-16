package ru.genesiscorporation.workspace.beta.data

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FirebaseTokenLoggingTest {
    @Test
    fun tokenRefreshDoesNotWriteTheRegistrationTokenToLogcat() {
        val previous = FCMTokenHolder.token
        val token = "cassi-synthetic-token-${UUID.randomUUID()}"
        try {
            MyFirebaseMessagingService().onNewToken(token)
            val output = serviceLogs()
            assertFalse("FCM registration tokens must not enter logcat", output.contains(token))
        } finally {
            FCMTokenHolder.token = previous
        }
    }

    @Test
    fun notificationPayloadDoesNotLogSenderOrBody() {
        val sender = "cassi-synthetic-sender-${UUID.randomUUID()}"
        val body = "cassi-synthetic-body-${UUID.randomUUID()}"
        val message = RemoteMessage(Bundle().apply {
            putString("from", sender)
            putString("gcm.n.e", "1")
            putString("gcm.n.body", body)
        })
        assertEquals(sender, message.from)
        assertEquals(body, requireNotNull(message.notification).body)

        MyFirebaseMessagingService().onMessageReceived(message)

        val output = serviceLogs()
        assertFalse("FCM sender must not enter logcat", sender in output)
        assertFalse("Notification body must not enter logcat", body in output)
    }

    @Test
    fun privateChatPayloadIsDisplayedWithoutLoggingItsContents() = checkDataPayload("private_chat_message")

    @Test
    fun streamChatPayloadIsDisplayedWithoutLoggingItsContents() = checkDataPayload("stream_chat_message")

    private fun checkDataPayload(kind: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // As with GrantPermissionRule, permission persists for this dedicated test installation.
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val id = UUID.randomUUID().hashCode().ushr(1)
        val marker = "cassi-synthetic-push-${UUID.randomUUID()}"
        val sender = "$marker-sender"
        val content = "$marker-content"
        val stream = "$marker-stream"
        val topic = "$marker-topic"
        val message = RemoteMessage.Builder("synthetic-recipient").setData(mapOf(
            "kind" to kind, "sender_id" to "synthetic-user", "sender_full_name" to sender,
            "content" to content, "stream" to stream, "topic" to topic,
            "workspace_message_id" to id.toString(),
        )).build()
        val service = MyFirebaseMessagingService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            service.onMessageReceived(message)
            val deadline = SystemClock.uptimeMillis() + 5_000
            var notification = manager.activeNotifications.singleOrNull { it.id == id }?.notification
            while (notification == null && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(25)
                notification = manager.activeNotifications.singleOrNull { it.id == id }?.notification
            }
            assertNotNull("Expected the synthetic chat notification", notification)
            val expectedTitle = if (kind == "private_chat_message") sender else "$stream -> $topic"
            val expectedBody = if (kind == "private_chat_message") content else "$sender: $content"
            assertEquals(expectedTitle, notification!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(expectedBody, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertFalse("Chat payload must not enter logcat", marker in serviceLogs())
        } finally {
            manager.cancel(id)
        }
    }

    @Test
    fun logCaptureIncludesOtherTagsFromThisAppProcess() {
        val marker = "cassi-synthetic-log-${UUID.randomUUID()}"
        Log.d("cassi-privacy-fixture", marker)
        assertTrue("Privacy checks must capture every app log tag", marker in serviceLogs())
    }

    private fun serviceLogs(): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("logcat -d -v raw --pid=${android.os.Process.myPid()}")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
