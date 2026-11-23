package com.exordos.workspace.data.push

import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.exordos.workspace.ApplicationSwitcher
import com.exordos.workspace.Chat
import com.exordos.workspace.ChatNavigation
import com.exordos.workspace.LocalBottomBarVisible
import com.exordos.workspace.UserState
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.ui.theme.WokspaceTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class PushLinkNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var link: String
    private val pending = mutableStateOf<String?>(null)
    private val mounted = mutableStateOf(false)
    private val handled = AtomicInteger()
    private val pushLookups = AtomicInteger()
    private val owner = UUID.randomUUID().toString()
    private val stream = UUID.randomUUID().toString()
    private val topic = UUID.randomUUID().toString()
    private val message = UUID.randomUUID().toString()
    private val streamPayload =
        """{"uuid":"$stream","name":"Old push target","unread_count":0,"active_unread_count":0,"passive_unread_count":0,"updated_at":"2026-11-01T00:00:00Z","private":false,"color":0,"notification_mode":"all_messages"}"""

    @Before fun prepare() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // A tap on a delivered notification assumes the user already allowed notifications.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        server = LoopbackWorkspaceServer { request ->
            // Keep /me owner verification, without starting unrelated repository bootstrap jobs.
            if (request.path.contains("/message_reactions/"))
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", 503)
            val body = when {
                request.path.endsWith("/me/") ->
                    """{"username":"cassi-fixture","uuid":"$owner","status":"active","avatar":""}"""
                request.path.endsWith("/streams/$stream") -> {
                    pushLookups.incrementAndGet()
                    streamPayload
                }
                request.path.endsWith("/streams/") -> "[$streamPayload]"
                request.path.endsWith("/stream_topics/") ->
                    """[{"uuid":"$topic","stream_uuid":"$stream","name":"Push topic","color":0,"updated_at":"2026-11-01T00:00:00Z","unread_count":0,"active_unread_count":0,"is_done":false,"is_default":true,"notification_mode":"default"}]"""
                request.path.endsWith("/messages/") ->
                    """[{"uuid":"$message","stream_uuid":"$stream","topic_uuid":"$topic","user_uuid":"$owner","author_uuid":"$owner","created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-01T00:00:00Z","payload":{"kind":"markdown","content":"Old push message"},"is_own":false,"read":true,"reactions":{}}]"""
                request.path.endsWith("/server_settings/") ->
                    """{"email_auth_enabled":true,"realm_name":"CASSI fixture","realm_url":"","realm_icon":"","meet_url":""}"""
                request.path.endsWith("/epoch/") -> """{"epoch_version":1,"epoch_generation":"cassi-fixture"}"""
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(body)
        }
        account = WorkspaceTestAccount.create(server.url)
        account.repo.setInitialStreams(listOf(Json.decodeFromString<Stream>(streamPayload).toUio()))
        withTimeout(8_000) { account.repo.loadUserInfo() }
        assertNotNull(confirmedAccountContext(account.api, account.repo))
        val registration = PushKeyStore(context).getOrCreate(account.config.id,
            UUID.randomUUID().toString(), requireNotNull(account.config.projectUuid), owner)
        val route = PushRoute(UUID.randomUUID().toString(), registration.serverId,
            registration.organizationUuid, registration.projectUuid, owner, stream, topic, message, topicUpdate = true)
        PushInbox(context).updateTopic(route, System.currentTimeMillis(), 1, 1, 1, UUID.randomUUID().toString())
        link = "workspace-push/${route.identity}"
        assertEquals(route, WorkspacePushNotifications(context).resolve(link))
    }

    @After fun close() = runBlocking {
        if (mounted.value) {
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
        }
        if (::account.isInitialized) {
            WorkspacePushNotifications(context).cancelServer(account.config.id)
            PushKeyStore(context).remove(account.config.id)
            account.close()
        }
        if (::server.isInitialized) server.close()
    }

    @Test fun coldPushLinkOpensMessage() {
        pending.value = link
        render(chatOnly = false)
        assertLinkedMessageOpens()
    }

    @Test fun warmPushLinkOpensMessage() {
        render(chatOnly = true)
        compose.onNodeWithText("Мессенджер").assertIsDisplayed()
        compose.runOnIdle { pending.value = link }
        assertLinkedMessageOpens()
    }

    @Test fun reopeningChatFromNotificationKeepsComposerAtTheKeyboardEdge() {
        pending.value = link
        render(chatOnly = false)
        assertLinkedMessageOpens()
        openKeyboard()
        assertComposerAtKeyboardEdge()
        compose.runOnIdle { pending.value = link }
        assertLinkedMessageOpens(expectedHandled = 2)
        openKeyboard()
        assertComposerAtKeyboardEdge()
        compose.onNodeWithContentDescription(Chat.title).assertDoesNotExist()
        compose.onNodeWithTag("conversation-back-action").performClick()
        compose.onNodeWithContentDescription(Chat.title).assertIsDisplayed()
    }

    @Test fun messageMenuIsUsableWithKeyboardAfterNotificationNavigation() {
        render(chatOnly = false)
        compose.runOnIdle { pending.value = link }
        assertLinkedMessageOpens()
        val input = openKeyboard()
        input.performTextInput("CASSI draft")

        repeat(3) { attempt ->
            compose.onNodeWithTag("message-bubble-$message", useUnmergedTree = true)
                .assertIsDisplayed().performTouchInput {
                    longClick(Offset(center.x, center.y * 0.4f))
                }
            val select = compose.onNodeWithTag("select-message-action").assertIsDisplayed()
            assertNotNull("Opening the menu dismissed the keyboard", keyboardTop())
            input.assertTextContains("CASSI draft")
            if (attempt == 2) {
                tapOnScreen(select)
                compose.onNodeWithTag("message-selection-bar").assertIsDisplayed()
            } else {
                tapOnScreen(compose.onNodeWithTag("conversation-title", useUnmergedTree = true))
                compose.waitUntil(5_000) { !select.isDisplayed() }
                compose.waitUntil(5_000) { keyboardTop() != null }
            }
        }
    }

    @Test fun systemBackDismissesMessageMenuAndKeepsKeyboardAfterNotificationNavigation() {
        pending.value = link
        render(chatOnly = false)
        assertLinkedMessageOpens()
        val input = openKeyboard()
        input.performTextInput("CASSI draft")
        compose.onNodeWithTag("message-bubble-$message", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput {
                longClick(Offset(center.x, center.y * 0.4f))
            }
        val select = compose.onNodeWithTag("select-message-action").assertIsDisplayed()
        assertNotNull("Opening the menu dismissed the keyboard", keyboardTop())

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        try {
            compose.waitUntil(5_000) {
                !select.isDisplayed() && compose.activity.hasWindowFocus() && keyboardTop() != null
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(
                "Back should close only the menu. menuVisible=${select.isDisplayed()}, keyboardVisible=${keyboardTop() != null}",
                failure,
            )
        }
        input.assertTextContains("CASSI draft")

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { keyboardTop() == null }
        input.assertIsDisplayed().assertTextContains("CASSI draft")
    }

    private fun tapOnScreen(node: SemanticsNodeInteraction) {
        // WindowManager must route this tap between the popup, activity and IME.
        val target = node.fetchSemanticsNode()
        val center = target.positionOnScreen + Offset(target.size.width / 2f, target.size.height / 2f)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, center.x, center.y, 0)
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun openKeyboard() = compose.onNodeWithTag("message-composer-input")
        .assertIsDisplayed().performClick().also {
            compose.waitUntil(10_000) { keyboardTop() != null }
        }

    private fun assertComposerAtKeyboardEdge() {
        val composer = compose.onNodeWithTag("message-composer")
        var gap = Float.NaN
        try {
            compose.waitUntil(5_000) {
                val top = keyboardTop() ?: return@waitUntil false
                gap = top - composer.fetchSemanticsNode().boundsInWindow.bottom
                abs(gap) <= 1f
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Expected the composer to meet the keyboard; last gap was $gap px", failure)
        }
    }

    private fun keyboardTop(): Int? {
        val decor = compose.activity.window.decorView
        var top: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val insets = ViewCompat.getRootWindowInsets(decor)
            if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                top = decor.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
        }
        return top
    }

    private fun render(chatOnly: Boolean) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        mounted.value = true
        compose.setContent {
            WokspaceTheme {
                if (mounted.value) CompositionLocalProvider(UserState provides account.user, LocalBottomBarVisible provides {}) {
                    val clear = { handled.incrementAndGet(); pending.value = null }
                    if (chatOnly) ChatNavigation(account.api, account.events, pending.value, clear)
                    else ApplicationSwitcher(account.api, account.events, pending.value, clear)
                }
            }
        }
    }

    private fun assertLinkedMessageOpens(expectedHandled: Int = 1) {
        compose.waitUntil(8_000) { handled.get() == expectedHandled }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        onView(withText("Old push message")).check(matches(isDisplayed()))
        compose.runOnIdle {
            assertEquals(null, pending.value)
            assertTrue("The valid link must fetch its target", pushLookups.get() > 0)
        }
    }
}
