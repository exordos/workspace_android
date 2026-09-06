package ru.genesiscorporation.workspace.beta.modules.share

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserActionButtonsRow
import ru.genesiscorporation.workspace.beta.modules.streaminfo.ActionButtonsRow
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class WorkspaceShareInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var launchedIntent: Intent? = null

    private fun share(title: String, link: String) {
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) {
                launchedIntent = intent
                super.startActivity(intent)
            }
        }
        shareWorkspaceLink(context, title, link)
    }

    @Test
    fun streamActionOpensNativeShareAndCanBeCancelled() {
        val link = "https://workspace.example.com/project/11111111-1111-4111-8111-111111111111/stream/22222222-2222-4222-8222-222222222222"
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                ActionButtonsRow(shareEnabled = true, onShare = { share("cassi stream", link) })
            }
        }
        saveScreenshot("stream-actions")
        compose.onNodeWithContentDescription("Поделиться стримом").performClick()
        assertShareIntent(link)
        cancelShare()
        compose.onNodeWithContentDescription("Поделиться стримом").assertExists()
    }

    @Test
    fun contactActionsKeepTheirCallbacksAndOpenNativeShare() {
        val link = "https://workspace.example.com/#user/33333333-3333-4333-8333-333333333333"
        var messageClicks = 0
        var callClicks = 0
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    ChatUserActionButtonsRow(
                        shareEnabled = true,
                        onMessage = { messageClicks++ },
                        onCall = { callClicks++ },
                        onShare = { share("cassi contact", link) }
                    )
                }
            }
        }
        saveScreenshot("contact-actions")
        compose.onNodeWithText("Написать").performClick()
        compose.onNodeWithText("Позвонить").performClick()
        assertEquals(1, messageClicks)
        assertEquals(1, callClicks)
        compose.onNodeWithText("Поделиться").performClick()
        assertShareIntent(link)
        cancelShare()
        compose.onNodeWithText("Поделиться").assertExists()
    }

    @Test
    fun unavailableLinksDisableBothShareActions() {
        compose.setContent {
            WokspaceTheme(dynamicColor = false) {
                Column {
                    ActionButtonsRow(shareEnabled = false, onShare = {})
                    ChatUserActionButtonsRow(false, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Поделиться стримом").assertIsNotEnabled()
        compose.onNodeWithText("Поделиться").assertIsNotEnabled()
    }

    @Suppress("DEPRECATION")
    private fun assertShareIntent(link: String) {
        val chooser = launchedIntent
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val send = chooser?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("text/plain", send?.type)
        assertEquals(link, send?.getStringExtra(Intent.EXTRA_TEXT))
    }

    private fun cancelShare() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(timeoutMillis = 5_000) {
            automation.rootInActiveWindow?.packageName?.toString() in
                setOf("android", "com.android.intentresolver")
        }
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.activity.lifecycle.currentState == Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "cassi-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
