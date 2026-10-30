package ru.genesiscorporation.workspace.beta.data.push

import android.app.NotificationManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.subtle.X25519
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushPayload
import java.util.Base64

/** Production HPKE receiver, SQLite upgrade, routes and Android NotificationManager. */
class EncryptedTopicPushTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val keys = PushKeyStore(context)
    private val fixture = Json.parseToJsonElement(instrumentation.context.assets.open("topic_push_events.json").bufferedReader().readText()).jsonObject
    private val payloads = fixture.getValue("payloads").jsonObject
    private val first = Json.decodeFromJsonElement<WorkspacePushPayload>(payloads.getValue("first"))
    private val privateKey = fixture.getValue("recipient_private_key_hex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val registration = PushRegistration("cassi-topic-update-test", first.organizationUuid, first.projectUuid, first.userUuid,
        first.deviceUuid, first.content.keyUuid, encoder.encodeToString(X25519.publicFromPrivate(privateKey)), encoder.encodeToString(privateKey))
    private val notifications = WorkspacePushNotifications(context)
    private val route = PushRoute.from(first, registration.serverId)

    @Before fun prepare() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        cleanup()
        keys.save(registration)
    }
    @After fun finish() { cleanup(); privateKey.fill(0) }
    private fun cleanup() { notifications.cancelServer(registration.serverId); keys.remove(registration.serverId) }
    private fun receive(name: String) = notifications.receive(payloads.getValue(name).toString())
    private fun card() = manager.activeNotifications.firstOrNull { it.tag == WorkspacePushNotifications.messageTag(route.identity) }
    private fun await(predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (!predicate() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        assertTrue(predicate())
    }

    @Test fun oneCardTracksTheFirstUnreadAndZeroClosesIt() {
        assertTrue(receive("first"))
        await { card()?.notification?.number == 2 }
        assertEquals("Разработка · Мобильные пуши", card()!!.notification.extras.getString("android.title"))
        assertEquals("Антон: Первое непрочитанное сообщение", card()!!.notification.extras.getCharSequence("android.text").toString())
        assertEquals(first.messageUuid, notifications.resolve("workspace-push/${route.identity}")!!.messageUuid)
        assertEquals(1, manager.activeNotifications.count { it.tag == WorkspacePushNotifications.messageTag(route.identity) })

        assertTrue(receive("second"))
        await { card()?.notification?.number == 1 }
        val second = Json.decodeFromJsonElement<WorkspacePushPayload>(payloads.getValue("second"))
        assertEquals(second.messageUuid, notifications.resolve("workspace-push/${route.identity}")!!.messageUuid)
        assertTrue(receive("first"))
        assertEquals(second.messageUuid, notifications.resolve("workspace-push/${route.identity}")!!.messageUuid)
        assertTrue(receive("second"))
        assertTrue(receive("zero"))
        await { card() == null }
        assertNull(notifications.resolve("workspace-push/${route.identity}"))
        assertTrue(receive("first"))
        WorkspacePushNotifications(context).restore()
        await { card() == null }
    }

    @Test fun zeroWithoutAMessageTargetClosesTheTopic() {
        assertTrue(receive("first"))
        await { card() != null }
        assertTrue(receive("empty"))
        await { card() == null }
        assertNull(notifications.resolve("workspace-push/${route.identity}"))
        assertTrue(receive("first"))
        await { card() == null }
    }

    @Test fun positiveCountWithoutAMessageTargetIsRejected() {
        assertTrue(receive("first"))
        assertFalse(receive("invalid_positive_no_target"))
        await { card()?.notification?.number == 2 }
        assertEquals(first.messageUuid, notifications.resolve("workspace-push/${route.identity}")!!.messageUuid)
    }

    @Test fun oldSwipeCannotDismissANewerCounterSnapshot() {
        assertTrue(receive("first"))
        assertTrue(receive("second"))
        notifications.dismiss(route.identity, null, 100, "00000000-0000-0000-0000-00000000000b")
        await { card()?.notification?.number == 1 }
        notifications.dismiss(route.identity, null, 200, "00000000-0000-0000-0000-00000000000b")
        await { card() == null }
        assertTrue(receive("newer"))
        await { card()?.notification?.number == 3 }
    }
    @Test fun websocketAllReadPreventsADelayedPushFromRevivingTheCard() {
        for (alreadyPosted in listOf(false, true)) {
            cleanup(); keys.save(registration)
            if (alreadyPosted) assertTrue(receive("first"))
            notifications.topicUpdated(registration.serverId, registration.userUuid,
                route.streamUuid, route.topicUuid, 0, 300, "00000000-0000-0000-0000-00000000000b")
            assertTrue(receive("first"))
            WorkspacePushNotifications(context).restore()
            await { card() == null }
            assertNull(notifications.resolve("workspace-push/${route.identity}"))
            assertTrue(receive("newer"))
            await { card()?.notification?.number == 3 }
        }
    }

    @Test fun aRetiredWebsocketGenerationCannotReviveTheCard() {
        assertTrue(receive("first"))
        notifications.topicUpdated(registration.serverId, registration.userUuid,
            route.streamUuid, route.topicUuid, 0, 1, "00000000-0000-0000-0000-00000000000c")
        assertTrue(receive("newer"))
        await { card() == null }
    }

    @Test fun anOldHttpReconciliationCannotCloseANewerPushSnapshot() {
        val inbox = PushInbox(context)
        assertTrue(receive("first"))
        val captured = inbox.pending(registration, 0).single()
        assertTrue(receive("second"))
        notifications.reconciledTopics(registration, listOf(captured))
        await { card()?.notification?.number == 1 }
        notifications.reconciledTopics(registration, inbox.pending(registration, 0))
        await { card() == null }
    }

    @Test fun anOldSwipeFromAnotherGenerationCannotDismissANewCard() {
        assertTrue(receive("first"))
        val staleIntent = requireNotNull(card()).notification.deleteIntent
        PushInbox(context).updateTopic(route.copy(eventUuid = java.util.UUID.randomUUID().toString()),
            System.currentTimeMillis(), 3, 3, 1, "00000000-0000-0000-0000-00000000000c")
        notifications.restore()
        await { card()?.notification?.number == 3 }
        staleIntent.send()
        Thread.sleep(300)
        assertEquals(3, card()?.notification?.number)
    }

    @Test fun closedTopicReceiptsExpireOneDeliveryWindowAfterClosure() {
        val inbox = PushInbox(context)
        val now = System.currentTimeMillis()
        val generation = "00000000-0000-0000-0000-00000000000b"
        val old = now - 2 * PushInbox.DELIVERY_WINDOW_MILLIS
        val withoutRoute = route.copy(topicUuid = "00000000-0000-0000-0000-000000000021")
        val zero = route.copy(topicUuid = "00000000-0000-0000-0000-000000000022")
        val active = route.copy(topicUuid = "00000000-0000-0000-0000-000000000023")
        val dismissed = route.copy(topicUuid = "00000000-0000-0000-0000-000000000024")
        inbox.updateTopic(route, old, 1, 1, 1, generation)
        inbox.closeTopic(registration, route.streamUuid, route.topicUuid, 2, generation)
        inbox.closeTopic(registration, withoutRoute.streamUuid, withoutRoute.topicUuid, 2, generation)
        inbox.updateTopic(zero, now, 0, 0, 2, generation)
        inbox.updateTopic(active, old, 1, 1, 1, generation)
        inbox.updateTopic(dismissed, old, 1, 1, 1, generation)
        inbox.dismiss(dismissed.identity, 1, generation)
        inbox.pruneTransientDecisions(now)
        for (entry in listOf(route, withoutRoute, zero, active, dismissed)) assertTrue(inbox.hasTopic(entry.identity))
        assertNull(inbox.updateTopic(route, now, 1, 1, 1, generation))
        // Age only this fixture's rows; do not advance pruning for real accounts.
        SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path,
            null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE push_receipts SET created_at=? WHERE server_id=? AND state='closed' AND topic_update=1",
                arrayOf(now - PushInbox.DELIVERY_WINDOW_MILLIS - 1, registration.serverId))
        }
        inbox.pruneTransientDecisions(now)
        for (entry in listOf(route, withoutRoute, zero)) assertFalse(inbox.hasTopic(entry.identity))
        for (entry in listOf(active, dismissed)) assertTrue(inbox.hasTopic(entry.identity))
    }

}
