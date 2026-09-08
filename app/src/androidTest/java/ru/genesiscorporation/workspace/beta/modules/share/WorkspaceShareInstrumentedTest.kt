package ru.genesiscorporation.workspace.beta.modules.share

import android.content.ContextWrapper
import android.content.Intent
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
import androidx.lifecycle.Lifecycle
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
        val link = requireNotNull(
            workspaceStreamShareLink(
                "https://workspace.example.com",
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222"
            )
        )
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                ActionButtonsRow(shareEnabled = true, onShare = { share("Shared stream", link) })
            }
        }
        val activity = compose.activity
        compose.onNodeWithContentDescription("Поделиться стримом").performClick()
        assertShareIntent(link)
        cancelShare(activity)
        compose.onNodeWithContentDescription("Поделиться стримом").assertExists()
    }

    @Test
    fun contactActionsKeepTheirCallbacksAndOpenNativeShare() {
        val link = requireNotNull(
            workspaceUserShareLink(
                "https://workspace.example.com",
                "33333333-3333-4333-8333-333333333333"
            )
        )
        var messageClicks = 0
        var callClicks = 0
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.padding(16.dp)) {
                    ChatUserActionButtonsRow(
                        shareEnabled = true,
                        onMessage = { messageClicks++ },
                        onCall = { callClicks++ },
                        onShare = { share("Shared contact", link) }
                    )
                }
            }
        }
        compose.onNodeWithText("Написать").performClick()
        compose.onNodeWithText("Позвонить").performClick()
        assertEquals(1, messageClicks)
        assertEquals(1, callClicks)
        val activity = compose.activity
        compose.onNodeWithText("Поделиться").performClick()
        assertShareIntent(link)
        cancelShare(activity)
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

    private fun cancelShare(activity: ComponentActivity) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(timeoutMillis = 5_000) {
            activity.lifecycle.currentState != Lifecycle.State.RESUMED
        }
        repeat(3) {
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            val resumed = runCatching {
                compose.waitUntil(timeoutMillis = 2_000) {
                    activity.lifecycle.currentState == Lifecycle.State.RESUMED
                }
            }.isSuccess
            if (resumed) {
                compose.waitForIdle()
                return
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            activity.lifecycle.currentState == Lifecycle.State.RESUMED
        }
    }
}
