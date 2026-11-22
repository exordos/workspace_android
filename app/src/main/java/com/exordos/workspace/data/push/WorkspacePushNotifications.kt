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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.MainActivity
import com.exordos.workspace.R
import com.exordos.workspace.data.crypto.WorkspacePushCrypto
import com.exordos.workspace.data.crypto.WorkspacePushPayload
import java.util.Base64
import java.util.UUID

@Serializable
internal data class PushPreview(val kind: String, val sender_name: String = "Workspace", val text: String = "Новое сообщение",
    val stream_name: String = "", val topic_name: String = "",
    val unread_count: Int = 0, val active_unread_count: Int = 0, val passive_unread_count: Int = 0,
    val epoch_version: Long = 0, val epoch_generation: String = "")

/** Render entirely offline. Durable receipts contain routes and decisions, never decrypted previews. */
internal class WorkspacePushNotifications(private val context: Context, private val now: () -> Long = WorkspaceDateTime::nowEpochMillis) {
    private val keys = PushKeyStore(context)
    private val inbox = PushInbox(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("workspace_push_receipts", Context.MODE_PRIVATE)

    init { synchronized(lock) { removeLegacyReceipts() } }

    fun receive(encoded: String): Boolean = synchronized(lock) {
        inbox.pruneTransientDecisions(now())
        if (encoded.toByteArray().size > 4096) return false
        val payload = runCatching { json.decodeFromString<WorkspacePushPayload>(encoded) }.getOrNull() ?: return false
        if (payload.kind != WorkspacePushCrypto.TOPIC_UPDATE) return false
        val registrations = keys.all().filter {
            !it.disabled && it.deviceUuid == payload.deviceUuid && it.keyUuid == payload.content.keyUuid &&
                it.organizationUuid == payload.organizationUuid && it.projectUuid == payload.projectUuid && it.userUuid == payload.userUuid
        }
        var accepted = false
        registrations.forEach { registration ->
            val route = runCatching { PushRoute.from(payload, registration.serverId) }.getOrNull() ?: return@forEach
            val plaintext = runCatching {
                val decoder = Base64.getUrlDecoder()
                val privateKey = decoder.decode(registration.privateKey)
                try { WorkspacePushCrypto.decrypt(privateKey, decoder.decode(registration.publicKey), payload) }
                finally { privateKey.fill(0) }
            }.getOrNull() ?: return@forEach
            try {
                if (keys.get(registration.serverId) != registration) return@forEach
                val preview = runCatching { json.decodeFromString<PushPreview>(plaintext.decodeToString()) }.getOrNull() ?: return@forEach
                if ((preview.active_unread_count > 0 && payload.messageUuid == null) ||
                    preview.kind != "topic" || preview.unread_count < 0 || preview.active_unread_count < 0 ||
                    preview.passive_unread_count < 0 || preview.active_unread_count.toLong() + preview.passive_unread_count != preview.unread_count.toLong() ||
                    preview.epoch_version <= 0 || runCatching { UUID.fromString(preview.epoch_generation).toString() }.getOrNull() != preview.epoch_generation)
                    return@forEach
                val alert = inbox.updateTopic(route, now(), preview.unread_count, preview.active_unread_count,
                    preview.epoch_version, preview.epoch_generation)
                if (alert != null) {
                    if (inbox.resolve(route.identity) != null) previews[route.identity] = preview else previews.remove(route.identity)
                    render(if (alert) route.identity else null)
                }
                accepted = true
            } finally { plaintext.fill(0) }
        }
        accepted
    }

    fun resolve(deepLink: String?): PushRoute? = synchronized(lock) {
        val id = deepLink?.takeIf { it.startsWith("workspace-push/") }?.removePrefix("workspace-push/") ?: return null
        val route = inbox.resolve(id) ?: return null
        route.takeIf(::valid)
    }

    fun restore() = synchronized(lock) { render() }

    fun reconciledTopics(registration: PushRegistration, closed: List<PushReceipt>) = synchronized(lock) {
        if (keys.get(registration.serverId) != registration) return
        closed.forEach(inbox::closeTopicSnapshot)
        render()
    }

    fun retireRegistration(registration: PushRegistration): Boolean = synchronized(lock) {
        if (keys.get(registration.serverId) != registration) return false
        keys.remove(registration.serverId)
        cancelServer(registration.serverId)
        true
    }

    fun cancelServer(serverId: String) = synchronized(lock) {
        inbox.removeServer(serverId)
        visible.entries.removeAll { it.value.first == serverId }
        render()
    }

    fun topicUpdated(serverId: String, userUuid: String?, streamUuid: String, topicUuid: String,
        activeUnreadCount: Int, epochVersion: Long, epochGeneration: String) = synchronized(lock) {
        if (activeUnreadCount != 0 || epochVersion <= 0 ||
            runCatching { UUID.fromString(epochGeneration).toString() }.getOrNull() != epochGeneration) return
        val registration = keys.get(serverId)?.takeIf { !it.disabled && it.userUuid == userUuid } ?: return
        inbox.closeTopic(registration, streamUuid, topicUuid, epochVersion, epochGeneration)
        render()
    }

    fun visibleMessages(owner: Any, serverId: String, projectUuid: String?, userUuid: String?, messageUuids: Set<String>) {
        val registration = keys.get(serverId)?.takeIf { !it.disabled && it.projectUuid == projectUuid && it.userUuid == userUuid }
        val identities = if (registration == null) emptySet() else messageUuids.map {
            PushRoute.messageIdentity(serverId, registration.organizationUuid, registration.projectUuid, registration.userUuid, it)
        }.toSet()
        visibility(owner, serverId, identities)
    }

    fun visibility(owner: Any, serverId: String, identities: Set<String>) = synchronized(lock) {
        if (identities.isEmpty()) visible.remove(owner) else visible[owner] = serverId to identities
        render()
    }

    fun dismiss(identity: String, snapshot: Long, generation: String) = synchronized(lock) {
        inbox.dismiss(identity, snapshot, generation)
        render()
    }

    fun topicLimit(): Int = preferences.getInt("groups_limit", 5).coerceIn(1, 5)
    fun setTopicLimit(topics: Int) = synchronized(lock) {
        require(topics in 1..5)
        check(preferences.edit().putInt("groups_limit", topics).commit())
        render()
    }

    private fun valid(route: PushRoute): Boolean = keys.get(route.serverId)?.let {
        !it.disabled && it.organizationUuid == route.organizationUuid && it.projectUuid == route.projectUuid && it.userUuid == route.userUuid
    } == true

    private fun contentIntent(route: PushRoute): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            identifier = "workspace-push:${route.identity}"
            putExtra("deeplink", "workspace-push/${route.identity}")
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun deleteIntent(identity: String, snapshot: Long, generation: String): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, PushDismissReceiver::class.java).apply {
            identifier = "workspace-push-dismiss:$identity:$generation:$snapshot"
            putExtra("identity", identity)
            putExtra("snapshot", snapshot)
            putExtra("epoch_generation", generation)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun builder() = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.icon)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.icon)
            .setContentTitle("Workspace").setContentText(context.getString(R.string.push_new_message)).build())
        // Android 16 replaces zero with a three-day deadline and invokes deleteIntent on timeout.
        // Use the representable "indefinite" duration, with headroom for elapsedRealtime addition.
        // This is an OS compatibility sentinel, not an unread-message retention policy.
        .setTimeoutAfter(Long.MAX_VALUE / 2)
        .setAutoCancel(false).setOnlyAlertOnce(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

    private fun render(alertIdentity: String? = null) {
        inbox.pruneTransientDecisions(now())
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        val shown = mutableSetOf<String>()
        val hiddenVisible = visible.values.flatMap { it.second }.toSet()
        inbox.activeTopics(topicLimit()).forEach { receipt ->
            val route = receipt.route
            if (!valid(route) || PushRoute.messageIdentity(route.serverId, route.organizationUuid,
                    route.projectUuid, route.userUuid, route.messageUuid) in hiddenVisible) return@forEach
            val preview = previews[route.identity]
            val count = context.resources.getQuantityString(R.plurals.push_unread_count, receipt.unreadCount, receipt.unreadCount)
            val title = preview?.let { "${it.stream_name} · ${it.topic_name}" } ?: "Workspace"
            val message = preview?.let { "${it.sender_name}: ${it.text}" } ?: context.getString(R.string.push_new_message)
            val notification = builder().setContentTitle(title).setContentText(message).setSubText(count)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message).setSummaryText(count))
                .setNumber(receipt.unreadCount).setWhen(now()).setShowWhen(false)
                .setOnlyAlertOnce(route.identity != alertIdentity).setSilent(route.identity != alertIdentity)
                .setGroupAlertBehavior(if (route.identity == alertIdentity) NotificationCompat.GROUP_ALERT_CHILDREN else NotificationCompat.GROUP_ALERT_SUMMARY)
                .setContentIntent(contentIntent(route))
                .setDeleteIntent(deleteIntent(route.identity, receipt.epochVersion, receipt.epochGeneration)).build()
            shown += messageTag(route.identity)
            manager.notify(messageTag(route.identity), 0, notification)
        }
        manager.activeNotifications.filter { (it.tag?.startsWith(MESSAGE_PREFIX) == true || it.tag?.startsWith(GROUP_PREFIX) == true) && it.tag !in shown }
            .forEach { manager.cancel(it.tag, it.id) }
        previews.keys.retainAll(shown.map { it.removePrefix(MESSAGE_PREFIX) }.toSet())
    }

    private fun removeLegacyReceipts() {
        if (preferences.getBoolean("legacy_receipts_removed", false)) return
        inbox.removeLegacyReceipts()
        val editor = preferences.edit()
        for ((key, value) in preferences.all) {
            if (key.startsWith("route:") && value is String) {
                val route = runCatching { json.decodeFromString<PushRoute>(value) }.getOrNull()
                if (route != null && !route.topicUpdate) {
                    val tag = listOf(route.organizationUuid, route.projectUuid, route.userUuid, route.messageUuid).joinToString(":")
                    manager.cancel(tag, 0)
                }
            }
            if (key.startsWith("route:") || key.startsWith("event:") || key.startsWith("cancel:") ||
                key.startsWith("legacy:") || key == "route_migration_complete" || key == "messages_limit") editor.remove(key)
        }
        check(editor.putBoolean("legacy_receipts_removed", true).commit())
    }

    companion object {
        const val CHANNEL = "workspace_messages"
        private const val MESSAGE_PREFIX = "workspace:message:"
        private const val GROUP_PREFIX = "workspace:group:"
        internal fun messageTag(identity: String) = MESSAGE_PREFIX + identity
        private val lock = Any()
        private val previews = mutableMapOf<String, PushPreview>()
        private val visible = mutableMapOf<Any, Pair<String, Set<String>>>()
    }
}
