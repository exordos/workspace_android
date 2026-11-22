package com.exordos.workspace.modules.chatdialog

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exordos.workspace.LocalBottomBarVisible
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.ui.EnhancedMarkdown
import com.exordos.workspace.ui.theme.WokspaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native selection and the real clipboard, including the surrounding chat gestures. */
@RunWith(AndroidJUnit4::class)
class MessageTextSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var chat: ChatDialogViewModel

    @After fun cleanup() = runBlocking {
        if (::chat.isInitialized) withContext(Dispatchers.Main) {
            chat.onChatClosed()
            chat.encryptionRuntime.close()
            chat.viewModelScope.cancel()
        }
        if (::account.isInitialized) account.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun incomingMessageLongPressCopiesOnlyTheSelectedWord() = runBlocking {
        showChat(own = false)
        selectAndCopy("Начало выделение конец", "выделение")
    }

    @Test fun ownFormattedMessageCopiesRenderedTextWithoutMarkdownMarkers() = runBlocking {
        showChat(own = true)
        selectAndCopy("Начало выделение конец", "выделение")
    }

    @Test fun selectAllCopiesTheWholeRenderedMessage() = runBlocking {
        showChat(own = true)
        compose.runOnIdle { clipboard.clearPrimaryClip() }
        onView(body("Начало выделение конец")).perform(touchWord("выделение", Tap.LONG))
        clickTextAction(android.R.string.selectAll)
        clickTextAction(android.R.string.copy)
        compose.runOnIdle {
            assertEquals("Начало выделение конец", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
        }
    }

    @Test fun snapshotQuoteInConversationSupportsFragmentCopy() = runBlocking {
        showChat(own = false, content = "> **Original author**:\n> Quoted fragment end\n\nReply")
        selectAndCopy("Quoted fragment end", "fragment")
    }

    @Test fun quoteBodyAndHeaderSupportNativeSelection() {
        showMarkdown("""
            Before
            @_**First Автор** [Topic](https://example.invalid):
            ```quote
            Цитата фрагмент конец
            ```
            After
        """.trimIndent())
        selectAndCopy("Цитата фрагмент конец", "фрагмент")
        selectAndCopy("Автор", "Автор")
    }

    @Test fun linkStillOpensItsOriginalDestination() {
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
        }, ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            showMarkdown("Before [website](https://example.invalid/selection) after")
            onView(body("Before website after")).perform(touchWord("website", Tap.SINGLE))
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun previewTextKeepsItsParentNavigationTap() {
        var opened = 0
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.safeDrawingPadding().clickable { opened++ }) {
                    EnhancedMarkdown("Mention preview", style = TextStyle(fontSize = 18.sp),
                        navController = null, viewModel = null)
                }
            }
        }
        onView(body("Mention preview")).perform(touchWord("preview", Tap.SINGLE))
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun bubbleMenuAndMessageSelectionStillWork() = runBlocking {
        showChat(own = true)
        compose.onNodeWithTag("message-bubble-$MESSAGE", useUnmergedTree = true).performTouchInput {
            longClick(Offset(20f, 12f))
        }
        compose.onNodeWithTag("select-message-action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("message-selection-bar").assertIsDisplayed()
        // The selection overlay must consume a body tap rather than focus its TextView.
        compose.onNodeWithTag("select-message-$MESSAGE").performClick()
        compose.onNodeWithTag("message-selection-bar").assertDoesNotExist()
    }

    private fun selectAndCopy(text: String, word: String) {
        compose.runOnIdle { clipboard.clearPrimaryClip() }
        onView(body(text)).check { view, error ->
            if (error != null) throw error
            assertTrue("Native text selection must be enabled", (view as TextView).isTextSelectable)
        }.perform(touchWord(word, Tap.LONG)).check { view, error ->
            if (error != null) throw error
            val textView = view as TextView
            val start = minOf(textView.selectionStart, textView.selectionEnd)
            val end = maxOf(textView.selectionStart, textView.selectionEnd)
            assertTrue("Long press must select a nonempty fragment", start >= 0 && end > start)
            assertEquals(word, textView.text.subSequence(start, end).toString())
        }
        clickTextAction(android.R.string.copy)
        compose.runOnIdle {
            assertEquals(word, clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
        }
    }

    /** Android 16 renders the floating text toolbar outside the app's Espresso roots. */
    private fun clickTextAction(labelId: Int) {
        val automation = instrumentation.uiAutomation
        val service = automation.serviceInfo
        val flags = service.flags
        service.flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = service
        try {
            val label = context.getString(labelId)
            // Select all can rebuild the toolbar between finding a visible label and clicking it.
            compose.waitUntil(conditionDescription = "Native $label action accepts a click", timeoutMillis = 5_000) {
                automation.windows.asSequence().mapNotNull { it.root }
                    .flatMap { it.findAccessibilityNodeInfosByText(label).asSequence() }
                    .filter { it.text?.toString() == label && it.isVisibleToUser }
                    .mapNotNull { action -> generateSequence(action) { it.parent }.firstOrNull { it.isClickable } }
                    .any { it.isEnabled && it.isVisibleToUser && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            }
            instrumentation.waitForIdleSync()
        } finally {
            service.flags = flags
            automation.serviceInfo = service
        }
    }

    private fun touchWord(word: String, tap: Tap) = GeneralClickAction(tap, { view ->
        val textView = view as TextView
        val offset = textView.text.toString().indexOf(word) + word.length / 2
        val layout = textView.layout
        val line = layout.getLineForOffset(offset)
        val location = IntArray(2).also(textView::getLocationOnScreen)
        floatArrayOf(
            location[0] + textView.totalPaddingLeft + layout.getPrimaryHorizontal(offset) - textView.scrollX,
            location[1] + textView.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f - textView.scrollY,
        )
    }, Press.FINGER)

    private fun body(text: String): Matcher<View> = allOf(isDisplayed(), object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) { description.appendText("Markdown TextView containing $text") }
        override fun matchesSafely(view: View) = view is TextView && view.text.toString().contains(text)
    })

    private fun showMarkdown(markdown: String) {
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.safeDrawingPadding()) {
                    EnhancedMarkdown(markdown, style = TextStyle(fontSize = 18.sp), navController = null,
                        viewModel = null, isTextSelectable = true)
                }
            }
        }
    }

    private suspend fun showChat(own: Boolean, content: String = "Начало **выделение** конец") {
        server = LoopbackWorkspaceServer { LoopbackWorkspaceServer.Response("[]") }
        account = WorkspaceTestAccount.create(server.url)
        val owner = UserResponseData(username = "cassi", uuid = OWNER, status = "active", avatar = "")
        val author = if (own) owner else owner.copy(uuid = AUTHOR, username = "Other")
        account.repo.updateCurrentUser(owner)
        account.repo.setInitialUsers(listOf(owner, author).distinctBy { it.uuid })
        account.repo.setInitialStreams(listOf(Stream(STREAM, 0, 0, 0, TIME,
            "CASSI selection", false, 0, defaultTopicUuid = TOPIC, notificationMode = "all_messages")))
        account.repo.addStreamTopicMessages(STREAM, TOPIC, listOf(MessageResponse(MESSAGE, TIME, TIME,
            STREAM, TOPIC, author.uuid, author.uuid,
            MessageResponsePayload("markdown", content), own, emptyMap(), true)))
        chat = withContext(Dispatchers.Main) {
            ChatDialogViewModel(account.api, account.user, "CASSI selection", STREAM, "Topic", TOPIC,
                false, account.events, null, AttachmentStorage(context, account.api))
        }
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    ChatDialogScreen(chat, rememberNavController())
                }
            }
        }
        compose.onNodeWithTag("chat-message-$MESSAGE").assertIsDisplayed()
    }

    companion object {
        private const val OWNER = "11111111-1111-4111-8111-111111111111"
        private const val AUTHOR = "22222222-2222-4222-8222-222222222222"
        private const val STREAM = "33333333-3333-4333-8333-333333333333"
        private const val TOPIC = "44444444-4444-4444-8444-444444444444"
        private const val MESSAGE = "55555555-5555-4555-8555-555555555555"
        private const val TIME = "2026-11-13T10:00:00Z"
    }
}
