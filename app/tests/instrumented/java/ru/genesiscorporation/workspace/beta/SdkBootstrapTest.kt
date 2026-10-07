package ru.genesiscorporation.workspace.beta

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.ViewGroup
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.jitsi.meet.sdk.BroadcastEvent
import org.jitsi.meet.sdk.BroadcastIntentHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.facebook.react.bridge.ReactContext
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/** Debug SDK regression. The optimized release uses a framework-only driver
 * so that obfuscated app dependencies do not invalidate the AndroidX runner. */
@RunWith(AndroidJUnit4::class)
class SdkBootstrapTest {
    @Test
    fun jitsiLoadsItsJavaScriptBundleAndNativeBridgeWithoutARealConference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(launch)
        val options = JitsiMeetConferenceOptions.Builder()
            .setServerURL(URL("https://cassi-release-smoke.invalid"))
            .setRoom("cassi-local-smoke")
            .setAudioMuted(true).setVideoMuted(true)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .build()
        val closed = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { closed.countDown() }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        broadcasts.registerReceiver(receiver, IntentFilter(BroadcastEvent.Type.READY_TO_CLOSE.action))
        var mounted = false
        try {
            instrumentation.runOnMainSync { JitsiMeetActivity.launch(activity, options) }
            // The SDK's own holder is kept by its vendor rules. Locate the
            // context by its retained bridge type rather than R8-private names.
            val holder = Class.forName("org.jitsi.meet.sdk.ReactInstanceManagerHolder")
            val getter = holder.getDeclaredMethod("getReactInstanceManager").apply { isAccessible = true }
            var ready: ReactContext? = null
            val deadline = System.currentTimeMillis() + 30_000
            while ((ready == null || !mounted) && System.currentTimeMillis() < deadline) {
                val manager = getter.invoke(null)
                if (manager != null) {
                    for (field in manager.javaClass.declaredFields) {
                        if (ReactContext::class.java.isAssignableFrom(field.type)) {
                            field.isAccessible = true
                            val candidate = field.get(manager) as? ReactContext
                            if (candidate?.hasActiveCatalystInstance() == true &&
                                candidate.catalystInstance.hasRunJSBundle()) ready = candidate
                        }
                    }
                }
                instrumentation.runOnMainSync {
                    val sdkActivity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<JitsiMeetActivity>().firstOrNull()
                    if (sdkActivity != null) {
                        val viewGetter = JitsiMeetActivity::class.java.getDeclaredMethod("getJitsiView")
                            .apply { isAccessible = true }
                        val view = viewGetter.invoke(sdkActivity) as ViewGroup
                        mounted = (view.getChildAt(0) as? ViewGroup)?.childCount?.let { it > 0 } == true
                    }
                }
                if (ready == null || !mounted) Thread.sleep(200)
            }
            assertNotNull("Jitsi's optimized JavaScript/native bridge must bootstrap", ready)
            assertTrue(requireNotNull(ready).catalystInstance.hasRunJSBundle())
            assertTrue("The SDK must mount its first UI before teardown", mounted)
        } finally {
            // Closing before the first JS commit can leave queued native UI
            // work targeting an already disposed root. Use the SDK close event.
            broadcasts.sendBroadcast(BroadcastIntentHelper.buildHangUpIntent())
            closed.await(3, TimeUnit.SECONDS)
            broadcasts.unregisterReceiver(receiver)
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<JitsiMeetActivity>().forEach { it.finish() }
                activity.finish()
            }
            // An intentionally unreachable host may never create a conference,
            // so READY_TO_CLOSE is not guaranteed. Mounted-root and JS checks
            // above remain mandatory; finish handles this abort-only path.
        }
    }
}
