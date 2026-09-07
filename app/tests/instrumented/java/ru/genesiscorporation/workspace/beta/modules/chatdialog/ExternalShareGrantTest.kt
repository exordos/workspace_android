package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.sharefixture.ExternalShareReceiverActivity
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExternalShareGrantTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeChooserTransfersTwoReadOnlyAttachmentsToAnotherApplicationUid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val receipt = AtomicReference<Intent?>(null)
        val action = "${targetContext.packageName}.cassi.SHARE_RECEIPT.${UUID.randomUUID()}"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { receipt.set(intent) }
        }
        ContextCompat.registerReceiver(targetContext, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val callback = PendingIntent.getBroadcast(targetContext, 0, Intent(action).setPackage(targetContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val folder = File(targetContext.cacheDir, "message-shares/cassi-grant-${UUID.randomUUID()}").apply { check(mkdirs()) }
        lateinit var activityContext: Context
        compose.setContent { activityContext = LocalContext.current }
        try {
            val files = listOf("first.txt" to "CASSI first attachment", "second.txt" to "CASSI second attachment")
            val uris = ArrayList(files.map { (name, text) ->
                val file = File(folder, name).apply { writeText(text) }
                FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.message-share-files", file)
            })
            val share = buildExternalShareIntent("CASSI: two selected messages", uris, "text/plain")
            val externalTestTarget = Intent(share).apply {
                component = ComponentName(instrumentation.context.packageName, ExternalShareReceiverActivity::class.java.name)
                putExtra(ExternalShareReceiverActivity.CALLBACK, callback)
            }
            val chooser = Intent.createChooser(share, "Переслать").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(externalTestTarget))
            }
            compose.runOnIdle { activityContext.startActivity(chooser) }
            val deadline = System.currentTimeMillis() + 10_000
            var clicked = false
            while (!clicked && System.currentTimeMillis() < deadline) {
                val nodes = instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("CASSI share receiver").orEmpty()
                for (node in nodes) {
                    if (node.text?.toString() != "CASSI share receiver") continue
                    var target: AccessibilityNodeInfo? = node
                    while (target != null && !target.isClickable) target = target.parent
                    if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { clicked = true; break }
                }
                if (!clicked) Thread.sleep(100)
            }
            assertTrue("The native chooser must expose the external fixture target", clicked)
            compose.waitUntil(10_000) { receipt.get() != null }
            val actual = requireNotNull(receipt.get())
            assertNull(actual.getStringExtra("failure"))
            assertNotEquals("Receiver must run in the separate test APK UID", Process.myUid(), actual.getIntExtra("receiver_uid", -1))
            assertEquals(Intent.ACTION_SEND_MULTIPLE, actual.getStringExtra("action"))
            assertEquals("CASSI: two selected messages", actual.getStringExtra("text"))
            assertEquals("text/plain", actual.getStringExtra("mime_type"))
            assertEquals(files.map { it.second }, actual.getStringArrayListExtra("contents"))
            assertFalse("The grant must not allow writes", actual.getBooleanExtra("can_write", true))
        } finally {
            if (receipt.get() == null) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            targetContext.unregisterReceiver(receiver)
            callback.cancel()
            folder.deleteRecursively()
        }
    }
}
