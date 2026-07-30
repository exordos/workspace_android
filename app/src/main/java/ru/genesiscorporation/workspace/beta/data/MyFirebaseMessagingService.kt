package ru.genesiscorporation.workspace.beta.data

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.genesiscorporation.workspace.beta.MainActivity
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.push.PushNavigationRequest
import ru.genesiscorporation.workspace.beta.data.push.PushTokenUpdates

class MyFirebaseMessagingService: FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["kind"]) {
            "remove_notification_message" -> {
                message.data["message_ids"]
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id > 0 } }
                    ?.forEach(::cancelNotification)
            }

            "private_chat_message",
            "group_chat_message",
            "stream_chat_message" -> {
                val navigationRequest =
                    PushNavigationRequest.fromMessageData(message.data) ?: return
                val senderName = message.data["sender_full_name"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return
                val content = message.data["content"] ?: return
                val isStream = navigationRequest.providerChatKey.startsWith("channel:")
                val title = if (isStream) {
                    val streamName = message.data["stream"]
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: return
                    "$streamName → ${navigationRequest.topicName}"
                } else {
                    senderName
                }
                val body = if (isStream) "$senderName: $content" else content
                showNotification(
                    notificationId = navigationRequest.workspaceMessageId,
                    title = title.take(MAX_NOTIFICATION_TITLE_LENGTH),
                    body = body.take(MAX_NOTIFICATION_BODY_LENGTH),
                    navigationRequest = navigationRequest,
                    sound = currentNotificationSound(),
                )
            }

            else -> Unit
        }
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        body: String,
        navigationRequest: PushNavigationRequest,
        sound: WorkspaceNotificationSound,
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                PushNavigationRequest.EXTRA_PROVIDER_CHAT_KEY,
                navigationRequest.providerChatKey,
            )
            navigationRequest.topicName?.let {
                putExtra(PushNavigationRequest.EXTRA_TOPIC_NAME, it)
            }
            putExtra(
                PushNavigationRequest.EXTRA_WORKSPACE_MESSAGE_ID,
                navigationRequest.workspaceMessageId,
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = runCatching {
            WorkspaceNotificationSoundController.ensureChannel(this, sound)
        }.recoverCatching { exception ->
            Log.w(
                TAG,
                "Could not prepare the selected notification channel; using the default",
                exception,
            )
            WorkspaceNotificationSoundController.ensureChannel(
                this,
                WorkspaceNotificationSound.DEFAULT,
            )
        }.onFailure { exception ->
            Log.e(TAG, "Could not prepare a notification channel", exception)
        }.getOrNull() ?: return
        val publicNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("Workspace")
            .setContentText("Новое сообщение")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(notificationId, notification)
    }

    private fun currentNotificationSound(): WorkspaceNotificationSound =
        runCatching {
            runBlocking(Dispatchers.IO) {
                resolveActiveWorkspaceNotificationSound(applicationContext)
            }
        }.onFailure { exception ->
            Log.w(
                TAG,
                "Could not read the active notification sound; using the default",
                exception,
            )
        }.getOrDefault(WorkspaceNotificationSound.DEFAULT)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenUpdates.publish(token)
        Log.d(TAG, "FCM registration token was refreshed")
    }

    private fun cancelNotification(notificationId: Int) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private companion object {
        const val TAG = "WorkspacePush"
        const val MAX_NOTIFICATION_TITLE_LENGTH = 160
        const val MAX_NOTIFICATION_BODY_LENGTH = 1_024
    }
}
