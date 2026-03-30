package ru.genesiscorporation.workspace.beta.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService: FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // handle message
    }

    override fun onNewToken(token: String) {
        // handle token refresh
    }
}