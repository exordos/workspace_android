package ru.genesiscorporation.workspace.beta.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ru.genesiscorporation.workspace.beta.MainActivity
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.push.PushTokenUpdates

class MyFirebaseMessagingService: FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "From: ${message.from}")

        // Check if message contains a data payload.


        if (message.data.isNotEmpty() && message.data["kind"] != null) {
            val dataKind = "${message.data["kind"]}"
            when(dataKind) {
                "remove_notification_message" -> {
                    val messageIds = message.data["message_ids"]
                    if (messageIds != null) {
                        val messageIdsList: List<Int> = messageIds.split(',')
                            .map { it.trim().toInt() }
                        for (messageId in messageIdsList) {
                            cancelNotification(messageId)
                        }
                    }
                }
                "private_chat_message" -> {
                    if (message.data["sender_full_name"] != null && message.data["content"] != null) {
                        var deepLink: String? = null
                        val userId = message.data["sender_id"]
                        if (userId != null) {
                            deepLink = "dialog/${userId}"
                        }

                        showNotification(message.data["workspace_message_id"]?.toInt() ?: 0, "${message.data["sender_full_name"]}", "${
                            message.data["content"]}", deepLink)
                        Log.d(TAG, "Message data payload: ${message.data}")
                    }
                }
                "stream_chat_message" -> {
                    if (message.data["sender_full_name"] != null && message.data["content"] != null && message.data["stream"] != null && message.data["topic"] != null) {
                        var deepLink: String? = "stream/${message.data["stream"]}/${message.data["topic"]}"
                        val title = "${message.data["stream"]} -> ${message.data["topic"]}"
                        val body = "${message.data["sender_full_name"]}: ${message.data["content"]}"
                        showNotification(
                            message.data["workspace_message_id"]?.toInt() ?: 0,
                            title,
                            body,
                            deepLink
                        )
                        Log.d(TAG, "Message data payload: ${message.data}")
                    }
                }
                else -> {

                }
            }
        }

        // Check if message contains a notification payload.
        message.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }

    @SuppressLint("ServiceCast")
    private fun showNotification(
        notificationId: Int,
        title: String,
        body: String,
        deepLink: String?
    ) {
        val channelId = "fcm_default_channel"
        // Intent opened when user taps notification
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deeplink", deepLink)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Required on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "General notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.icon) // must exist
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(notificationId, notification)
    }
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenUpdates.publish(token)
        Log.d(TAG, "FCM registration token was refreshed")
    }

    fun cancelNotification(notificationId: Int) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
