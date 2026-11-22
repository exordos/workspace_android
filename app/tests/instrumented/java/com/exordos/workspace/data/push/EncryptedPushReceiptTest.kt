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

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.exordos.workspace.R
import com.exordos.workspace.data.crypto.WorkspacePushPayload
import com.google.crypto.tink.subtle.X25519
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.UUID

class EncryptedPushReceiptTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val inbox = PushInbox(context)
    private val keys = PushKeyStore(context)
    private val preferences = context.getSharedPreferences("workspace_push_receipts", 0)
    private val fixture = Json.parseToJsonElement(instrumentation.context.assets.open("mobile_push_event.json").bufferedReader().readText()).jsonObject
    private val payload = Json.decodeFromJsonElement<WorkspacePushPayload>(fixture.getValue("payload"))
    private val privateKey = fixture.getValue("recipient_private_key_hex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val registration = PushRegistration("cassi-legacy-upgrade", payload.organizationUuid, payload.projectUuid,
        payload.userUuid, payload.deviceUuid, payload.content.keyUuid, encoder.encodeToString(X25519.publicFromPrivate(privateKey)),
        encoder.encodeToString(privateKey))
    private val secondary = registration.copy(serverId = "cassi-topic-second-connection")
    private val legacy = PushRoute(payload.uuid, registration.serverId, registration.organizationUuid, registration.projectUuid,
        registration.userUuid, payload.streamUuid, payload.topicUuid, requireNotNull(payload.messageUuid))
    private val generation = "00000000-0000-0000-0000-00000000000b"
    private val notifications = WorkspacePushNotifications(context)
    private val previousLimit = notifications.topicLimit()
    private val legacyTag = listOf(legacy.organizationUuid, legacy.projectUuid, legacy.userUuid, legacy.messageUuid).joinToString(":")

    @Before fun prepare() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        keys.save(registration)
        notifications.setTopicLimit(5)
    }

    @After fun cleanup() {
        notifications.visibility(this, registration.serverId, emptySet())
        notifications.cancelServer(registration.serverId)
        notifications.cancelServer(secondary.serverId)
        keys.remove(registration.serverId)
        keys.remove(secondary.serverId)
        manager.cancel(legacyTag, 0)
        notifications.setTopicLimit(previousLimit)
        privateKey.fill(0)
    }

    @Test fun legacyCreateAndCancelAreIgnoredWithoutAlteringTheTopicCard() {
        val topic = legacy.copy(topicUpdate = true)
        addTopic(topic)
        notifications.restore()
        await { shown(topic) }
        for (name in listOf("payload", "cancel_payload")) {
            assertFalse(notifications.receive(fixture.getValue(name).toString()))
        }
        assertEquals(topic, notifications.resolve("workspace-push/${topic.identity}"))
        assertNull(notifications.resolve("workspace-push/${legacy.identity}"))
        assertEquals(0, legacyRowCount())
        assertTrue(shown(topic))
    }

    @Test fun upgradeRemovesLegacyRoutesAndCardsButRestoresTheTopicCard() {
        val topic = legacy.copy(topicUpdate = true)
        addTopic(topic)
        val legacyJson = Json.encodeToString(legacy)
        SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path,
            null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insertOrThrow("push_receipts", null, ContentValues().apply {
                put("identity", legacy.identity); put("conversation", legacy.conversationIdentity)
                put("server_id", legacy.serverId); put("organization_uuid", legacy.organizationUuid)
                put("project_uuid", legacy.projectUuid); put("user_uuid", legacy.userUuid)
                put("message_uuid", legacy.messageUuid); put("route", legacyJson)
                put("created_at", System.currentTimeMillis()); put("state", "unread")
            })
        }
        val obsoleteRoute = "route:${legacy.eventUuid}"
        val obsoleteAlias = "legacy:${legacy.eventUuid}"
        val legacyGroup = "workspace:group:${legacy.conversationIdentity}"
        preferences.edit().remove("legacy_receipts_removed").putString(obsoleteRoute, legacyJson)
            .putString(obsoleteAlias, legacy.identity).putInt("messages_limit", 3).commit()
        val oldCard = Notification.Builder(context, WorkspacePushNotifications.CHANNEL).setSmallIcon(R.drawable.icon)
            .setContentTitle("Old message").build()
        manager.notify(legacyTag, 0, oldCard)
        manager.notify(legacyGroup, 0, oldCard)
        manager.notify(WorkspacePushNotifications.messageTag(legacy.identity), 0, oldCard)
        await { shown(legacy) }
        assertEquals(1, legacyRowCount())

        val upgraded = WorkspacePushNotifications(context)
        upgraded.restore()
        await { shown(topic) && !shown(legacy) && manager.activeNotifications.none { it.tag == legacyTag || it.tag == legacyGroup } }
        assertEquals(0, legacyRowCount())
        assertFalse(preferences.contains(obsoleteRoute))
        assertFalse(preferences.contains(obsoleteAlias))
        assertFalse(preferences.contains("messages_limit"))
        assertEquals(topic, upgraded.resolve("workspace-push/${topic.identity}"))
        assertNull(upgraded.resolve("workspace-push/${legacy.eventUuid}"))
    }

    @Test fun visibleTargetHidesOnlyItsOwnSavedConnectionUntilTheChatCloses() {
        val topic = legacy.copy(topicUpdate = true)
        val other = topic.copy(serverId = secondary.serverId)
        keys.save(secondary)
        addTopic(topic)
        addTopic(other)
        notifications.restore()
        await { shown(topic) && shown(other) }
        notifications.visibleMessages(this, topic.serverId, topic.projectUuid, topic.userUuid, setOf(topic.messageUuid))
        await { !shown(topic) && shown(other) }
        notifications.visibility(this, topic.serverId, emptySet())
        await { shown(topic) && shown(other) }
    }

    @Test fun exceedingTheTopicLimitKeepsTheOlderTopicAvailableForRestoration() {
        val older = legacy.copy(topicUpdate = true)
        val newer = older.copy(topicUuid = UUID.randomUUID().toString())
        val now = System.currentTimeMillis()
        notifications.setTopicLimit(1)
        addTopic(older, now - 1_000)
        addTopic(newer, now)
        notifications.restore()
        await { shown(newer) && !shown(older) }
        notifications.dismiss(newer.identity, 1, generation)
        await { shown(older) && !shown(newer) }
    }

    private fun addTopic(route: PushRoute, createdAt: Long = System.currentTimeMillis()) {
        assertEquals(true, inbox.updateTopic(route, createdAt, 1, 1, 1, generation))
    }

    private fun shown(route: PushRoute): Boolean = manager.activeNotifications.any {
        it.tag == WorkspacePushNotifications.messageTag(route.identity)
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue(predicate())
    }

    private fun legacyRowCount(): Int = SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path,
        null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM push_receipts WHERE server_id=? AND topic_update=0", arrayOf(registration.serverId)).use {
            check(it.moveToFirst()); it.getInt(0)
        }
    }
}
