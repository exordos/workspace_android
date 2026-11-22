package com.exordos.workspace.data.push

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.exordos.workspace.R
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.MyFirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PushConfigurationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("DEPRECATION")
    @Test fun firebasePushIsEnabledWithoutEnablingAnalytics() {
        assertTrue(context.resources.getBoolean(R.bool.push_enabled))
        val manager = context.packageManager
        val service = manager.getServiceInfo(ComponentName(context, MyFirebaseMessagingService::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS)
        val receiver = manager.getReceiverInfo(ComponentName(context,
            "com.google.firebase.iid.FirebaseInstanceIdReceiver"), PackageManager.MATCH_DISABLED_COMPONENTS)
        val dismiss = manager.getReceiverInfo(ComponentName(context, PushDismissReceiver::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS)
        assertTrue(service.enabled)
        assertTrue(receiver.enabled)
        assertTrue(dismiss.enabled)
        val metadata = manager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertTrue(metadata.containsKey("firebase_messaging_auto_init_enabled"))
        assertTrue(metadata.getBoolean("firebase_messaging_auto_init_enabled"))
        assertTrue(metadata.containsKey("firebase_analytics_collection_enabled"))
        assertFalse(metadata.getBoolean("firebase_analytics_collection_enabled"))
        assertTrue(metadata.containsKey("firebase_messaging_notification_delegation_enabled"))
        assertTrue(metadata.getBoolean("firebase_messaging_notification_delegation_enabled"))
        assertTrue(FirebaseMessaging.getInstance().isAutoInitEnabled)
    }

    @Test fun applicationAttachesPushCallbacksAndSchedulesPeriodicReconciliation() {
        val app = context.applicationContext as WorkspaceApplication
        assertNotNull(app.serverRepository.onPushRegistrationNeeded)
        assertNotNull(app.serverRepository.unregisterPush)
        val work = WorkManager.getInstance(context)
        val requests = work.getWorkInfosForUniqueWork("workspace-push-status-periodic").get(10, TimeUnit.SECONDS)
        assertTrue("Periodic push work was not scheduled", requests.any { !it.state.isFinished })
    }
}
