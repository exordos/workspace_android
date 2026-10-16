package ru.genesiscorporation.workspace.beta.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Opt in with -PfirebaseLiveSmoke=true on a dedicated, correctly configured installation. */
@RunWith(AndroidJUnit4::class)
class FirebaseRegistrationSmokeTest {
    @Test
    fun configuredAppObtainsARegistrationToken() {
        val token = Tasks.await(FirebaseMessaging.getInstance().token, 90, TimeUnit.SECONDS)
        assertTrue("FCM did not return a registration token", token.isNotBlank())
    }
}
