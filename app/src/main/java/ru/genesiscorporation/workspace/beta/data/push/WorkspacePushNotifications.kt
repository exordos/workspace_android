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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.MainActivity
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushCrypto
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushPayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import java.time.Instant
import java.util.Base64

@Serializable
internal data class PushPreview(val kind: String, val sender_name: String = "Workspace", val text: String = "Новое сообщение",
    val created_at: String? = null)

internal data class PushDisplayLimits(val conversations: Int = 5, val messages: Int = 5)

/** Render entirely offline. Durable receipts contain routes and decisions, never decrypted previews. */
internal class WorkspacePushNotifications(private val context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val keys = PushKeyStore(context)
    private val inbox = PushInbox(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("workspace_push_receipts", Context.MODE_PRIVATE)

    init { synchronized(lock) { migrateLegacyRoutes() } }

    fun receive(encoded: String): Boolean = synchronized(lock) {
        inbox.pruneTransientDecisions(now())
        if (encoded.toByteArray().size > 4096) return false
        val payload = runCatching { json.decodeFromString<WorkspacePushPayload>(encoded) }.getOrNull() ?: return false
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
                if (payload.kind == WorkspacePushCrypto.MESSAGE_CANCELLED) {
                    inbox.close(route)
                    previews.remove(route.identity)
                    render()
                    accepted = true
                    return@forEach
                }
                val preview = json.decodeFromString<PushPreview>(plaintext.decodeToString())
                if (preview.kind != "message") return@forEach
                // Preserve pre-upgrade cancellations as well as new database tombstones.
                if (preferences.contains("cancel:${legacyIdentity(route)}")) inbox.close(route)
                val createdAt = preview.created_at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    ?.takeIf { it > 0 } ?: now()
                val isNew = inbox.accept(route, createdAt)
                if (inbox.resolve(route.identity) != null) previews[route.identity] = preview
                render(if (isNew) route.identity else null)
                accepted = true
            } finally { plaintext.fill(0) }
        }
        accepted
    }

    fun resolve(deepLink: String?): PushRoute? = synchronized(lock) {
        val id = deepLink?.takeIf { it.startsWith("workspace-push/") }?.removePrefix("workspace-push/") ?: return null
        val route = inbox.resolve(id) ?: preferences.getString("legacy:$id", null)?.let(inbox::resolve) ?: return null
        route.takeIf(::valid)
    }

    fun restore() = synchronized(lock) { render() }

    fun reconciled(registration: PushRegistration, closedIds: List<String>) = synchronized(lock) {
        if (keys.get(registration.serverId) != registration) return
        inbox.closeMessages(registration, closedIds, rememberUnknown = false)
        render()
    }


    fun retireRegistration(registration: PushRegistration): Boolean = synchronized(lock) {
        if (keys.get(registration.serverId) != registration) return false
        keys.remove(registration.serverId)
        cancelServer(registration.serverId)
        true
    }

    fun cancelServer(serverId: String) = synchronized(lock) {
        val legacy = preferences.all.filter { (key, value) -> key.startsWith("legacy:") && value is String && inbox.belongsTo(serverId, value) }
        if (legacy.isNotEmpty()) check(preferences.edit().also { editor -> legacy.keys.forEach(editor::remove) }.commit())
        inbox.removeServer(serverId)
        visible.entries.removeAll { it.value.first == serverId }
        render()
    }

    fun knownMessages(serverId: String, userUuid: String?, messages: List<MessageResponse>) = synchronized(lock) {
        val registration = keys.get(serverId)?.takeIf { !it.disabled && it.userUuid == userUuid } ?: return
        val cutoff = now() - PushInbox.DELIVERY_WINDOW_MILLIS
        val (recent, history) = messages.filter {
            it.read && (it.projectId == null || it.projectId == registration.projectUuid)
        }.partition { runCatching { Instant.parse(it.createdAt).toEpochMilli() >= cutoff }.getOrDefault(false) }
        // Old loaded history can close an existing alert, but cannot have an undelivered new push.
        inbox.closeMessages(registration, history.map { it.uuid }, rememberUnknown = false)
        inbox.closeMessages(registration, recent.map { it.uuid })
        render()
    }

    fun removedMessage(serverId: String, userUuid: String?, messageUuid: String) = synchronized(lock) {
        val registration = keys.get(serverId)?.takeIf { !it.disabled && it.userUuid == userUuid } ?: return
        inbox.closeMessages(registration, listOf(messageUuid))
        render()
    }

    fun readMessages(serverId: String, userUuid: String?, messageUuids: List<String>) = synchronized(lock) {
        val registration = keys.get(serverId)?.takeIf { !it.disabled && it.userUuid == userUuid } ?: return
        inbox.closeMessages(registration, messageUuids)
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

    fun dismiss(identity: String?, conversation: String?, snapshot: Long) = synchronized(lock) {
        if (identity != null) inbox.dismiss(identity)
        if (conversation != null && snapshot > 0) inbox.dismissConversation(conversation, snapshot)
        render()
    }

    fun limits(): PushDisplayLimits = PushDisplayLimits(preferences.getInt("groups_limit", 5), preferences.getInt("messages_limit", 5))
    fun setLimits(conversations: Int, messages: Int) = synchronized(lock) {
        require(conversations in 1..5 && messages in 1..10)
        check(preferences.edit().putInt("groups_limit", conversations).putInt("messages_limit", messages).commit())
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

    private fun deleteIntent(id: String? = null, group: String? = null, snapshot: Long = 0): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, PushDismissReceiver::class.java).apply {
            identifier = "workspace-push-dismiss:${id ?: group}:$snapshot"
            putExtra("identity", id); putExtra("conversation", group); putExtra("snapshot", snapshot)
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
        val limits = limits()
        val shown = mutableSetOf<String>()
        val hiddenVisible = visible.values.flatMap { it.second }.toSet()
        var budget = DISPLAY_BUDGET
        inbox.conversations(limits.conversations.coerceIn(1, 5)).forEach { group ->
            if (budget < 2) return@forEach
            val messages = inbox.messages(group.identity, limits.messages.coerceIn(1, 10) + hiddenVisible.size)
                .filter { it.route.identity !in hiddenVisible && valid(it.route) }.take(minOf(limits.messages, budget - 1))
            if (messages.isEmpty()) return@forEach
            val groupKey = "workspace:${group.identity}"
            messages.forEach { receipt ->
                val route = receipt.route
                val preview = previews[route.identity]
                val notification = builder().setContentTitle(preview?.sender_name ?: "Workspace")
                    .setContentText(preview?.text ?: context.getString(R.string.push_new_message))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(preview?.text ?: context.getString(R.string.push_new_message)))
                    // Android 16 rejects newly posted notifications with an old `when`.
                    // Keep message chronology in sortKey; posting time is only the system lifecycle time.
                    .setGroup(groupKey).setWhen(now()).setShowWhen(false)
                    .addExtras(android.os.Bundle().apply { putLong("workspace.message_created_at", receipt.createdAt) })
                    .setSortKey("%019d:%s".format(Long.MAX_VALUE - receipt.createdAt, route.identity))
                    .setSilent(route.identity != alertIdentity)
                    .setGroupAlertBehavior(if (route.identity == alertIdentity)
                        NotificationCompat.GROUP_ALERT_CHILDREN else NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setContentIntent(contentIntent(route))
                    .setDeleteIntent(deleteIntent(id = route.identity)).build()
                shown += messageTag(route.identity)
                manager.notify(messageTag(route.identity), 0, notification)
                budget--
            }
            val summaryTag = groupTag(group.identity)
            val summary = builder().setContentTitle("Workspace")
                .setContentText(context.resources.getQuantityString(R.plurals.push_unread_count, group.count, group.count))
                .setNumber(group.count).setGroup(groupKey).setGroupSummary(true).setSilent(true)
                .setContentIntent(contentIntent(messages.first().route))
                .setDeleteIntent(deleteIntent(group = group.identity, snapshot = group.snapshot)).build()
            shown += summaryTag
            manager.notify(summaryTag, 0, summary)
            budget--
        }
        manager.activeNotifications.filter { (it.tag?.startsWith(MESSAGE_PREFIX) == true || it.tag?.startsWith(GROUP_PREFIX) == true) && it.tag !in shown }
            .forEach { manager.cancel(it.tag, it.id) }
        previews.keys.retainAll(shown.map { it.removePrefix(MESSAGE_PREFIX) }.toSet())
    }

    private fun migrateLegacyRoutes() {
        if (preferences.getBoolean("route_migration_complete", false)) return
        val editor = preferences.edit()
        preferences.all.filterKeys { it.startsWith("route:") }.forEach { (key, value) ->
            val route = runCatching { json.decodeFromString<PushRoute>(value as String) }.getOrNull()
            if (route != null && valid(route)) {
                inbox.accept(route, preferences.getLong("event:${route.eventUuid}", now()))
                if (preferences.contains("cancel:${legacyIdentity(route)}")) inbox.close(route)
                editor.putString("legacy:${route.eventUuid}", route.identity)
                manager.cancel(legacyIdentity(route), 0)
            }
            editor.remove(key)
        }
        preferences.all.keys.filter { it.startsWith("event:") }.forEach(editor::remove)
        check(editor.putBoolean("route_migration_complete", true).commit())
    }

    companion object {
        const val CHANNEL = "workspace_messages"
        private const val DISPLAY_BUDGET = 40
        private const val MESSAGE_PREFIX = "workspace:message:"
        private const val GROUP_PREFIX = "workspace:group:"
        internal fun messageTag(identity: String) = MESSAGE_PREFIX + identity
        internal fun groupTag(identity: String) = GROUP_PREFIX + identity
        private fun legacyIdentity(route: PushRoute) = listOf(route.organizationUuid, route.projectUuid, route.userUuid, route.messageUuid).joinToString(":")
        private val lock = Any()
        private val previews = mutableMapOf<String, PushPreview>()
        private val visible = mutableMapOf<Any, Pair<String, Set<String>>>()
    }
}
