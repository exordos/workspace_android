package ru.genesiscorporation.workspace.beta.data

import android.Manifest
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.RemoteMessage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.MainActivity

/** Synthetic callbacks exercise Android's real notification and PendingIntent registry. */
@RunWith(AndroidJUnit4::class)
class FirebaseNotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private lateinit var service: MyFirebaseMessagingService
    private val ownedIds = mutableSetOf<String>()

    @Before fun prepare() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        service = MyFirebaseMessagingService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        ownedIds += "0"
    }

    @After fun cleanup() {
        ownedIds.forEach { id -> manager.cancel(tag(id), id.toIntOrNull() ?: 0) }
    }

    @Test fun malformedPrivateIdsDoNotCrashOrReplaceAnotherNotification() {
        malformedCreation("private_chat_message")
    }

    @Test fun malformedStreamIdsDoNotCrashOrReplaceAnotherNotification() {
        malformedCreation("stream_chat_message")
    }

    private fun malformedCreation(kind: String) {
        post("71301")
        for (id in listOf(null, "", " ", "not-a-number", "-1", "+1", "1.5", "١٢")) {
            service.onMessageReceived(message(id, kind = kind))
        }
        assertNull(active("0"))
        assertEquals("Synthetic first", notification("71301").extras.getString(Notification.EXTRA_TITLE))
        post("71302") // A bad callback cannot poison the next delivery.
        notification("71302")
    }

    @Test fun malformedRemovalIdsDoNotCrash() {
        for (id in listOf(null, "", "not-a-number", "-1", "+1", "1.5")) remove(id)
        post("71303")
        notification("71303")
    }

    @Test fun mixedRemovalListCancelsOnlyItsValidEntries() {
        post("71311")
        post("71312")
        post("71313")
        notification("71313")
        remove(" 71311 , broken, , -1, 71312, 71311 ")
        await { active("71311") == null && active("71312") == null }
        notification("71313")
    }

    @Test fun producerNumericIdsSurviveIntAndLongOverflowAndCancelIndependently() {
        // Bouncer serializes non-negative Python integers without an upper bound.
        val ids = listOf("0", "2147483647", "2147483648", "9223372036854775808")
        ids.forEach { post(it); notification(it) }
        post("0002147483648", "updated")
        await { active("2147483648")?.extras?.getString(Notification.EXTRA_TITLE) == "Synthetic updated" }
        assertEquals(1, manager.activeNotifications.count { it.tag == tag("2147483648") })
        remove("broken, 2147483648, 9223372036854775808")
        await { active("2147483648") == null && active("9223372036854775808") == null }
        notification("0")
        notification("2147483647")
    }

    @Test fun notificationsKeepIndependentImmutableIntentsAndUpdateOnlyTheSameId() {
        post("71321", "first")
        val first = notification("71321").contentIntent
        post("71322", "second", "stream_chat_message")
        val second = notification("71322").contentIntent
        assertNotEquals(first, second)
        if (Build.VERSION.SDK_INT >= 31) { assertTrue(first.isImmutable); assertTrue(second.isImmutable) }
        post("71321", "updated")
        assertEquals(first, notification("71321").contentIntent)
        assertEquals(second, notification("71322").contentIntent)
        remove("71321")
        await { active("71321") == null }
        assertEquals(second, notification("71322").contentIntent)
    }

    @Test fun olderAndNewerNotificationsDeliverTheirOwnTargetsToMainActivity() {
        post("71331", "first")
        val older = notification("71331").contentIntent
        post("71332", "second", "stream_chat_message")
        val newer = notification("71332").contentIntent
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        try {
            fun open(pending: android.app.PendingIntent, expected: String) {
                val options = ActivityOptions.makeBasic().apply {
                    if (Build.VERSION.SDK_INT >= 34) {
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                }
                pending.send(context, 0, null, null, null, null, options.toBundle())
                var actual: String? = null
                await({ "Expected deeplink $expected, received $actual" }) {
                    instrumentation.runOnMainSync { actual = activity.intent.getStringExtra("deeplink") }
                    actual == expected
                }
            }
            open(older, "dialog/first")
            open(newer, "stream/synthetic-stream/synthetic-topic")
            post("71331", "updated")
            notification("71331")
            open(older, "dialog/updated")
            open(newer, "stream/synthetic-stream/synthetic-topic")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun post(id: String, sender: String = "first", kind: String = "private_chat_message") {
        ownedIds += id.trimStart('0').ifEmpty { "0" }
        service.onMessageReceived(message(id, sender, kind))
    }

    private fun message(id: String?, sender: String = "first", kind: String = "private_chat_message") =
        RemoteMessage.Builder("synthetic-recipient").setData(buildMap {
            put("kind", kind)
            if (id != null) put("workspace_message_id", id)
            put("sender_id", sender)
            put("sender_full_name", "Synthetic $sender")
            put("content", "Synthetic content")
            put("stream", "synthetic-stream")
            put("topic", "synthetic-topic")
        }).build()

    private fun remove(ids: String?) = service.onMessageReceived(
        RemoteMessage.Builder("synthetic-recipient").setData(buildMap {
            put("kind", "remove_notification_message")
            if (ids != null) put("message_ids", ids)
        }).build()
    )

    private fun tag(id: String): String? = if (id.toIntOrNull() != null) null else "workspace-message:$id"
    private fun active(id: String) = manager.activeNotifications.firstOrNull {
        it.id == (id.toIntOrNull() ?: 0) && it.tag == tag(id)
    }?.notification
    private fun notification(id: String): Notification {
        await { active(id) != null }
        return requireNotNull(active(id))
    }
    private fun await(description: () -> String = { "Notification state did not converge" }, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(25)
        }
        assertTrue(description(), condition())
    }
}
