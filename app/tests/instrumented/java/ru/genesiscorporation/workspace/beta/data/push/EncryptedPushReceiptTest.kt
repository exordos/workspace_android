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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import kotlinx.serialization.json.*
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushPayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import android.database.sqlite.SQLiteDatabase
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Real Android storage, PendingIntents and NotificationManager; Firebase delivery is separate. */
class EncryptedPushReceiptTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val keys = PushKeyStore(context)
    private val inbox = PushInbox(context)
    private val fixture = Json.parseToJsonElement(instrumentation.context.assets.open("mobile_push_event.json").bufferedReader().readText()).jsonObject
    private val payload = Json.decodeFromJsonElement<WorkspacePushPayload>(fixture.getValue("payload"))
    private val privateKey = fixture.getValue("recipient_private_key_hex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val registration = PushRegistration("cassi-pr97-receipt", payload.organizationUuid, payload.projectUuid, payload.userUuid,
        payload.deviceUuid, payload.content.keyUuid, encoder.encodeToString(com.google.crypto.tink.subtle.X25519.publicFromPrivate(privateKey)), encoder.encodeToString(privateKey))
    private val secondary = registration.copy(serverId = "cassi-pr97-second-connection")
    private val route = PushRoute.from(payload, registration.serverId)
    private val notifications = WorkspacePushNotifications(context)
    private val create = fixture.getValue("payload").toString()

    @Before fun prepare() {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        cleanup()
        keys.save(registration)
        notifications.setLimits(5, 5)
    }
    @After fun finish() { cleanup(); privateKey.fill(0) }
    private fun cleanup() {
        listOf(registration.serverId, secondary.serverId).forEach { notifications.cancelServer(it); keys.remove(it) }
        notifications.visibility(this, registration.serverId, emptySet())
    }
    private fun child(route: PushRoute = this.route) = manager.activeNotifications.firstOrNull { it.tag == WorkspacePushNotifications.messageTag(route.identity) }
    private fun waitUntil(check: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 5000
        while (!check() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(20)
        assertTrue(check())
    }
    private fun shown(route: PushRoute = this.route): android.service.notification.StatusBarNotification {
        waitUntil { child(route) != null }
        return requireNotNull(child(route))
    }
    private fun assertPresent(route: PushRoute = this.route) { shown(route) }
    private fun assertSilent(route: PushRoute = this.route) {
        waitUntil { child(route)?.notification?.groupAlertBehavior == Notification.GROUP_ALERT_SUMMARY }
    }
    private fun assertAbsent(route: PushRoute = this.route) { waitUntil { child(route) == null } }
    private fun another(topic: String = route.topicUuid, message: String = UUID.randomUUID().toString()) =
        route.copy(eventUuid = UUID.randomUUID().toString(), topicUuid = topic, messageUuid = message)

    private fun readSnapshot(message: String, created: String) = Json.decodeFromString<MessageResponse>(
        """{"uuid":"$message","updated_at":"$created","created_at":"$created","stream_uuid":"${route.streamUuid}","topic_uuid":"${route.topicUuid}","user_uuid":"${route.userUuid}","author_uuid":"${route.userUuid}","payload":{"kind":"markdown","content":"Synthetic read history"},"is_own":false,"reactions":{},"read":true,"project_id":"${route.projectUuid}"}""")

    private fun storedRows(): Int = SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path,
        null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM push_receipts WHERE server_id=?", arrayOf(registration.serverId)).use {
            check(it.moveToFirst()); it.getInt(0)
        }
    }

    @Test fun loadingOldReadHistoryDoesNotCreateRowsForMessagesWithoutPushes() {
        inbox.accept(route, 1)
        val old = Instant.now().minusSeconds(15 * 86400L).toString()
        val messages = List(2000) { readSnapshot(UUID.randomUUID().toString(), old) } + readSnapshot(route.messageUuid, old)
        notifications.knownMessages(registration.serverId, registration.userUuid, messages)
        assertEquals("Loaded history must only close the already received old push", 1, storedRows())
        assertNull(inbox.resolve(route.identity))
        assertFalse(inbox.accept(route, 2))
    }

    @Test fun aRecentReadSnapshotBeforeThePushStillPreventsLateDisplay() {
        notifications.knownMessages(registration.serverId, registration.userUuid,
            listOf(readSnapshot(route.messageUuid, Instant.now().toString())))
        assertTrue(notifications.receive(create))
        assertAbsent()
        assertNull(notifications.resolve("workspace-push/${route.identity}"))
    }

    @Test fun onlyExpiredDecisionsWithoutReceiptsArePruned() {
        val unknown = UUID.randomUUID().toString()
        notifications.readMessages(registration.serverId, registration.userUuid, listOf(unknown))
        val old = System.currentTimeMillis() - 15 * 24 * 3600_000L
        val closed = another(); val dismissed = another()
        inbox.accept(route, old); inbox.accept(closed, old); inbox.accept(dismissed, old)
        inbox.close(closed); inbox.dismiss(dismissed.identity)
        SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE push_receipts SET created_at=? WHERE server_id=? AND message_uuid=?",
                arrayOf(System.currentTimeMillis() - 2 * 3600_000L, registration.serverId, unknown))
        }
        notifications.restore()
        assertEquals("Only the transient no-receipt decision should expire", 3, storedRows())
        assertPresent(route)
        assertEquals(route, notifications.resolve("workspace-push/${route.identity}"))
        assertFalse(inbox.accept(closed, old)); assertFalse(inbox.accept(dismissed, old))
    }

    @Test fun authenticatedOfflineReceiptKeepsItsRouteAndRejectsTamperedCancellation() {
        assertTrue(notifications.receive(create))
        val posted = shown()
        assertEquals("Alice", posted.notification.extras.getString("android.title"))
        assertEquals("Привет из Workspace", posted.notification.extras.getCharSequence("android.text").toString())
        assertTrue("No application deadline; Android may supply its own default", posted.notification.timeoutAfter == 0L || posted.notification.timeoutAfter >= 3 * 24 * 3600_000L)
        assertEquals(0, posted.notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(route, notifications.resolve("workspace-push/${route.identity}"))
        assertFalse(notifications.receive(Json.encodeToString(payload.copy(kind = "notification_message_cancel"))))
        assertPresent()
        assertTrue(notifications.receive(create))
        assertEquals(1, manager.activeNotifications.count { it.tag == posted.tag })
        assertFalse("A duplicate receipt must not be inserted again", inbox.accept(route, System.currentTimeMillis()))
        // Android can retain the original group behavior while coalescing an update.
        // The existing card must always suppress another alert when it is updated.
        assertTrue(shown().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notifications.receive(fixture.getValue("cancel_payload").toString()))
        assertAbsent()
        assertTrue(notifications.receive(create))
        assertAbsent()
        assertNull(notifications.resolve("workspace-push/${route.identity}"))
    }

    @Test fun unreadRoutesRemainUsableAfterTwoWeeksAndRestoreSilently() {
        val old = System.currentTimeMillis() - 15 * 24 * 3600_000L
        assertTrue(inbox.accept(route, old))
        WorkspacePushNotifications(context).restore()
        val posted = shown()
        assertEquals(old, posted.notification.extras.getLong("workspace.message_created_at"))
        assertTrue("No application deadline; Android may supply its own default", posted.notification.timeoutAfter == 0L || posted.notification.timeoutAfter >= 3 * 24 * 3600_000L)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, posted.notification.groupAlertBehavior)
        assertEquals(route, WorkspacePushNotifications(context).resolve("workspace-push/${route.identity}"))
        // An unsuccessful cold-start attempt must leave the receipt and Android notification available.
        posted.notification.contentIntent.send()
        instrumentation.waitForIdleSync()
        assertPresent()
        assertEquals(route, notifications.resolve("workspace-push/${route.identity}"))
        notifications.reconciled(registration, listOf(route.messageUuid))
        assertAbsent()
    }

    @Test fun identicalAccountsInTwoSavedConnectionsOwnIndependentAlertsAndRoutes() {
        keys.save(secondary)
        assertTrue(notifications.receive(create))
        val secondRoute = PushRoute.from(payload, secondary.serverId)
        assertNotEquals(route.identity, secondRoute.identity)
        assertPresent(route); assertPresent(secondRoute)
        assertEquals(secondary.serverId, notifications.resolve("workspace-push/${secondRoute.identity}")?.serverId)
        notifications.cancelServer(registration.serverId)
        keys.remove(registration.serverId)
        assertAbsent(route); assertPresent(secondRoute)
        assertTrue(notifications.receive(create))
        assertPresent(secondRoute)
    }

    @Test fun aSingleSwipeIsDurableAndDoesNotMarkOtherMessagesRead() {
        assertTrue(notifications.receive(create))
        shown().notification.deleteIntent.send()
        waitUntil { child() == null }
        WorkspacePushNotifications(context).restore()
        assertTrue(notifications.receive(create))
        assertAbsent()
        assertNull(notifications.resolve("workspace-push/${route.identity}"))
        val fresh = another()
        inbox.accept(fresh, System.currentTimeMillis())
        notifications.restore()
        assertPresent(fresh)
        assertAbsent()
    }

    @Test fun aGroupSwipeUsesItsSnapshotAndLeavesLaterArrivals() {
        val older = another()
        inbox.accept(route, 1); inbox.accept(older, 2)
        notifications.restore()
        waitUntil { manager.activeNotifications.any { it.tag == WorkspacePushNotifications.groupTag(route.conversationIdentity) } }
        val summary = manager.activeNotifications.single { it.tag == WorkspacePushNotifications.groupTag(route.conversationIdentity) }
        val newArrival = another()
        inbox.accept(newArrival, 3)
        notifications.restore()
        summary.notification.deleteIntent.send()
        waitUntil { child(route) == null && child(older) == null }
        assertPresent(newArrival)
        notifications.restore()
        assertPresent(newArrival)
        assertFalse(inbox.accept(route.copy(eventUuid = UUID.randomUUID().toString()), 4))
        assertAbsent(route)
    }

    @Test fun overflowReturnsOlderUnreadMessagesSilentlyInsteadOfDismissingThem() {
        notifications.setLimits(1, 2)
        val middle = another(); val newest = another(); val otherConversation = another(topic = UUID.randomUUID().toString())
        inbox.accept(route, 1); inbox.accept(middle, 2); inbox.accept(newest, 3); inbox.accept(otherConversation, 0)
        notifications.restore()
        assertAbsent(route); assertPresent(middle); assertPresent(newest); assertAbsent(otherConversation)
        assertEquals(route, notifications.resolve("workspace-push/${route.identity}"))
        notifications.reconciled(registration, listOf(newest.messageUuid))
        assertPresent(route); assertPresent(middle); assertAbsent(newest)
        assertSilent(route)
        notifications.reconciled(registration, listOf(route.messageUuid, middle.messageUuid))
        assertPresent(otherConversation)
        assertSilent(otherConversation)
    }

    @Test fun readOrDeleteBeforeCreationAndOldCancellationCannotBeReversedByLateDelivery() {
        notifications.removedMessage(registration.serverId, registration.userUuid, route.messageUuid)
        assertTrue(notifications.receive(create))
        assertAbsent()
        notifications.restore()
        assertAbsent()
        val fresh = another()
        inbox.accept(fresh, 1)
        notifications.reconciled(registration, listOf(fresh.messageUuid))
        assertFalse(inbox.accept(fresh.copy(eventUuid = UUID.randomUUID().toString()), 2))
        notifications.restore()
        assertAbsent(fresh)
    }

    @Test fun onlyActuallyVisibleMessagesAreHiddenAndTheyReturnSilentlyAfterLeaving() {
        val elsewhere = another()
        notifications.visibility(this, registration.serverId, setOf(route.identity))
        assertTrue(notifications.receive(create))
        assertAbsent()
        inbox.accept(elsewhere, System.currentTimeMillis())
        notifications.restore()
        assertPresent(elsewhere)
        notifications.visibility(this, registration.serverId, emptySet())
        assertPresent()
        assertEquals(Notification.GROUP_ALERT_SUMMARY, shown().notification.groupAlertBehavior)
    }
}
