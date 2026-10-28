package ru.genesiscorporation.workspace.beta.data

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.WorkspaceApplication

/** Local instrumentation must initialize the SDK without uploading diagnostics. */
@RunWith(AndroidJUnit4::class)
class CrashlyticsConfigurationTest {
    @Test
    fun debugBuildInitializesCrashlyticsWithCollectionDisabled() {
        val application = ApplicationProvider.getApplicationContext<WorkspaceApplication>()
        val metadata = application.packageManager.getApplicationInfo(
            application.packageName, PackageManager.GET_META_DATA,
        ).metaData
        assertFalse(metadata.getBoolean("firebase_crashlytics_collection_enabled", true))
        assertNotNull(FirebaseApp.getInstance().get(FirebaseCrashlytics::class.java))
        assertFalse(FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled)
    }
}
