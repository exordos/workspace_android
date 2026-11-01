package com.exordos.workspace.data.push

import android.content.ComponentName
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.exordos.workspace.R
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.MyFirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PushDisabledTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("DEPRECATION")
    @Test fun firebaseEntryPointsAndTokenAutoInitializationAreDisabled() {
        assertFalse(context.resources.getBoolean(R.bool.push_enabled))
        val manager = context.packageManager
        val service = manager.getServiceInfo(ComponentName(context, MyFirebaseMessagingService::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS)
        val receiver = manager.getReceiverInfo(ComponentName(context,
            "com.google.firebase.iid.FirebaseInstanceIdReceiver"), PackageManager.MATCH_DISABLED_COMPONENTS)
        val dismiss = manager.getReceiverInfo(ComponentName(context, PushDismissReceiver::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS)
        assertFalse(service.enabled)
        assertFalse(receiver.enabled)
        assertFalse(dismiss.enabled)
        val metadata = manager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertTrue(metadata.containsKey("firebase_messaging_auto_init_enabled"))
        assertFalse(metadata.getBoolean("firebase_messaging_auto_init_enabled"))
        assertTrue(metadata.containsKey("firebase_analytics_collection_enabled"))
        assertFalse(metadata.getBoolean("firebase_analytics_collection_enabled"))
        assertTrue(metadata.containsKey("firebase_messaging_notification_delegation_enabled"))
        assertFalse(metadata.getBoolean("firebase_messaging_notification_delegation_enabled"))
        assertFalse(FirebaseMessaging.getInstance().isAutoInitEnabled)
        assertNull(context.getSystemService(NotificationManager::class.java).notificationDelegate)
    }

    @Test fun applicationDoesNotAttachPushCallbacksOrSchedulePeriodicWork() {
        val app = context.applicationContext as WorkspaceApplication
        assertNull(app.serverRepository.onPushRegistrationNeeded)
        assertNull(app.serverRepository.unregisterPush)
        val work = WorkManager.getInstance(context)
        val requests = work.getWorkInfosForUniqueWork("workspace-push-status-periodic").get(10, TimeUnit.SECONDS)
        assertTrue("Unexpected periodic push work", requests.all { it.state.isFinished })
    }
}
