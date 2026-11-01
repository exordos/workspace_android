package com.exordos.workspace.modules.encryptedchat

import android.graphics.Bitmap
import android.widget.TextView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule as createQueuedAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.LocalBottomBarVisible
import com.exordos.workspace.R
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.SecureTokenStore
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.ServerRepository
import com.exordos.workspace.data.TokenPair
import com.exordos.workspace.data.crypto.ChatKeyAddress
import com.exordos.workspace.data.crypto.ChatKeyOwner
import com.exordos.workspace.data.crypto.ChatKeyScope
import com.exordos.workspace.data.crypto.ChatMessageContext
import com.exordos.workspace.data.crypto.EncryptedChatKeyStore
import com.exordos.workspace.data.crypto.WorkspaceChatCrypto
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.chatdialog.AttachmentStorage
import com.exordos.workspace.modules.chatdialog.ChatDialogScreen
import com.exordos.workspace.modules.chatdialog.ChatDialogViewModel
import com.exordos.workspace.ui.theme.DarkWorkspaceColorsPalette
import com.exordos.workspace.ui.theme.LightWorkspaceColorsPalette
import com.exordos.workspace.ui.theme.WokspaceTheme
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real authenticated Android runtime, storage, HTTP and chat screen; all data stays on loopback. */
@RunWith(AndroidJUnit4::class)
@OptIn(DelicateCoilApi::class)
class EncryptedConversationUiTest {
    // Use queued test effect scheduling. The legacy immediate harness remains
    // opt-in for reproducing the observed native/Compose layout race.
    @get:Rule val compose = if (
        InstrumentationRegistry.getArguments().getString("cassiImmediateCompose") == "true"
    ) createAndroidComposeRule<ComponentActivity>() else createQueuedAndroidComposeRule<ComponentActivity>()
    private lateinit var fixture: EncryptedConversationServer
    private lateinit var directory: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var preferences: ServerRepository
    private lateinit var events: EventsRepositoryStore
    private lateinit var user: UserViewModel
    private lateinit var http: HttpClient
    private lateinit var imageHttp: HttpClient
    private lateinit var previousImageLoader: ImageLoader
    private lateinit var fixtureImageLoader: ImageLoader
    private lateinit var chat: ChatDialogViewModel
    private lateinit var serverId: String
    private lateinit var address: ChatKeyAddress

    @Before fun setup() = runBlocking {
        android.util.Log.i("CassiEncryptedUiTest", "composeHarness=" +
            if (InstrumentationRegistry.getArguments().getString("cassiImmediateCompose") == "true") "immediate-v1" else "queued-v2")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previousImageLoader = SingletonImageLoader.get(context)
        // This loopback peer is HTTP. The production default transport correctly
        // rejects cleartext; use an explicit test-only CIO transport for the fixture.
        imageHttp = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000 }
        }
        fixtureImageLoader = ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .components { add(KtorNetworkFetcherFactory(httpClient = { imageHttp })) }
            .build()
        SingletonImageLoader.setUnsafe(fixtureImageLoader)
        fixture = EncryptedConversationServer()
        directory = File(context.cacheDir, "cassi-encrypted-conversation-${UUID.randomUUID()}").apply { mkdirs() }
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { File(directory, "fixture.preferences_pb") }
        http = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000 }
            // Ktor's production json() also writes defaults. Exercise that wire
            // policy so nullable fields cannot silently pass a more permissive peer.
            install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
        }
        val api = WorkspaceAPIClient(http)
        val tokens = SecureTokenStore(context)
        events = EventsRepositoryStore(tokens, api)
        preferences = ServerRepository(dataStore, tokens, events, storeScope)
        serverId = "cassi-encrypted-conversation-${UUID.randomUUID()}"
        val config = ServerConfig(serverId, fixture.url, "", "CASSI test", projectUuid = PROJECT)
        preferences.addServer(config, TokenPair("synthetic-access", "synthetic-refresh"))
        user = withContext(Dispatchers.Main) { UserViewModel(preferences) }
        api.attachUserViewModel(user)
        withTimeout(5_000) {
            combine(user.selectedServerId, user.selectedServer, user.accessToken) { id, selected, access ->
                id == serverId && selected == config && access == "synthetic-access"
            }.first { it }
        }
        val keys = EncryptedChatKeyStore(context)
        val owner = ChatKeyOwner(fixture.url, PROJECT, OWNER)
        val identity = keys.ensureDeviceIdentity(owner)
        address = ChatKeyAddress(ChatKeyScope(owner, identity.deviceId), STREAM, CURRENT)
        assertTrue(keys.writeChatKey(address, fixture.groupKey, forSending = true))
        val repo = events.getOrCreate(config)
        repo.updateCurrentUser(fixture.owner)
        repo.loadUserInfo()
        assertTrue(repo.verifiedOwnerAuthentication.value != null)
        repo.setInitialUsers(listOf(fixture.owner, fixture.participant))
        repo.setInitialStreams(listOf(fixture.stream))
        repo.addStreamBindings(STREAM, fixture.bindings)
        repo.addStreamTopicMessages(STREAM, TOPIC, fixture.messages.toList())
        chat = withContext(Dispatchers.Main) {
            ChatDialogViewModel(api, user, "Команда продукта", STREAM, "Общий чат", TOPIC,
                false, events, null, AttachmentStorage(context, api))
        }
    }

    @After fun cleanup() = runBlocking {
        if (::chat.isInitialized) withContext(Dispatchers.Main) { chat.onChatClosed(); chat.encryptionRuntime.close(); chat.viewModelScope.cancel() }
        if (::previousImageLoader.isInitialized) SingletonImageLoader.setUnsafe(previousImageLoader)
        if (::fixtureImageLoader.isInitialized) fixtureImageLoader.shutdown()
        if (::imageHttp.isInitialized) imageHttp.close()
        if (::address.isInitialized) EncryptedChatKeyStore(InstrumentationRegistry.getInstrumentation().targetContext).removeChatKey(address)
        if (::preferences.isInitialized) preferences.removeServer(serverId)
        if (::events.isInitialized) events.clear()
        if (::user.isInitialized) user.viewModelScope.cancel()
        if (::storeScope.isInitialized) storeScope.cancel()
        if (::http.isInitialized) http.close()
        if (::fixture.isInitialized) fixture.close()
        if (::directory.isInitialized) directory.deleteRecursively()
        Unit
    }

    private fun showConversation(fontScale: Float? = null, darkTheme: () -> Boolean = { true }) {
        // Match MainActivity and WorkspaceApp. The default test Activity can pan its
        // entire window above the screen instead of dispatching the production IME insets.
        compose.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            WokspaceTheme(darkTheme = darkTheme(), dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
                    LocalBottomBarVisible provides {},
                ) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    ChatDialogScreen(chat, rememberNavController())
                    }
                }
            }
        }
    }

    private suspend fun useLongTopicTitle() {
        val title = "Очень длинное название темы для обсуждения интерфейса и доступности рабочего чата"
        val previous = chat
        chat = withContext(Dispatchers.Main) {
            previous.onChatClosed()
            previous.encryptionRuntime.close()
            previous.viewModelScope.cancel()
            ChatDialogViewModel(previous.client, user, "Команда продукта", STREAM, title, TOPIC,
                false, events, null, previous.storage)
        }
    }

    /** Measures actual rendered foreground/background pixels, including the enclosing surface. */
    private fun renderedContrast(tag: String): Double {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val counts = HashMap<Int, Int>()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y].toArgb()
            counts[color] = (counts[color] ?: 0) + 1
        }
        val background = counts.maxBy { it.value }.key
        assertEquals("The captured surface must be opaque", 255, Color.alpha(background))
        return counts.keys.maxOf { ColorUtils.calculateContrast(it, background) }
    }

    @Test fun protectedHeaderUsesThemeLockBeforeTitle() = runBlocking {
        useLongTopicTitle()
        var dark by mutableStateOf(false)
        showConversation { dark }
        compose.waitUntil(10_000) { chat.encryptedChatState.value.sendingKeyUuid != null }
        listOf(false, true).forEach { theme ->
            compose.runOnIdle { dark = theme }
            val lock = compose.onNodeWithTag("conversation-protected-lock", useUnmergedTree = true).assertIsDisplayed()
            val title = compose.onNodeWithTag("conversation-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("The protected lock must precede the title", lock.fetchSemanticsNode().boundsInRoot.right <= title.left)
            compose.onNodeWithTag("conversation-protected-label", useUnmergedTree = true).assertDoesNotExist()
            val pixels = lock.captureToImage().toPixelMap()
            val accent = (if (theme) DarkWorkspaceColorsPalette else LightWorkspaceColorsPalette).protectedChatHeaderAccent.toArgb()
            var accentPixels = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                if (pixels[x, y].toArgb() == accent) accentPixels++
            }
            assertTrue("The visible lock must use the active theme accent", accentPixels > 8)
            assertTrue("The protected lock must remain readable", renderedContrast("conversation-protected-lock") >= 3.0)
        }
    }

    @Test fun composerAttachmentStaysNearLeadingEdgeWithKeyboard() = runBlocking {
        showConversation { false }
        compose.waitUntil(10_000) { chat.encryptedChatState.value.sendingKeyUuid != null }
        val density = compose.activity.resources.displayMetrics.density
        val failures = mutableListOf<String>()
        fun checkAttachment() {
            val composer = compose.onNodeWithTag("message-composer").fetchSemanticsNode().boundsInRoot
            val attach = compose.onNodeWithTag("message-composer-attach").assertIsDisplayed().fetchSemanticsNode()
            val inset = (attach.boundsInRoot.left - composer.left) / density
            if (inset > 4.5f) failures += "Attachment control starts $inset dp from the leading edge (>4)"
            assertTrue("The attachment target must remain at least 48 dp wide", attach.touchBoundsInRoot.width >= 48 * density - 1)
            assertTrue("The attachment target must remain at least 48 dp high", attach.touchBoundsInRoot.height >= 48 * density - 1)
        }
        checkAttachment()
        compose.onNodeWithTag("message-composer-input").performClick().performTextInput("Файл")
        compose.waitUntil(10_000) {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                visible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
        checkAttachment()
        capture("attachment-leading-keyboard")
        compose.onNodeWithTag("message-composer-attach").performClick()
        compose.onNodeWithText("Add file").assertIsDisplayed()
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test fun conversationChromeKeepsReadableTextAndActionsAcrossThemeChanges() = runBlocking {
        useLongTopicTitle()
        var dark by mutableStateOf(false)
        showConversation { dark }
        compose.waitUntil(10_000) { chat.encryptedChatState.value.sendingKeyUuid != null }
        val failures = mutableListOf<String>()
        listOf(false, true).forEach { theme ->
            compose.runOnIdle { dark = theme }
            compose.waitForIdle()
            capture(if (theme) "chrome-dark" else "chrome-light")
            listOf("conversation-title", "conversation-subtitle").forEach {
                val contrast = renderedContrast(it)
                if (contrast < 4.5) failures += "$it in dark=$theme has contrast $contrast < 4.5"
            }
            listOf("conversation-back-action", "conversation-call-action", "conversation-more-action", "message-composer-attach", "message-composer-send").forEach {
                compose.onNodeWithTag(it).assertIsEnabled()
                val contrast = renderedContrast(it)
                if (contrast < 3.0) failures += "$it in dark=$theme has contrast $contrast < 3.0"
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test fun conversationHeaderDoesNotClipTextAtLargeAccessibilityFontScale() = runBlocking {
        useLongTopicTitle()
        showConversation(fontScale = 2f)
        compose.waitUntil(10_000) { chat.encryptedChatState.value.sendingKeyUuid != null }
        capture("chrome-large-text")
        val header = compose.onNodeWithTag("conversation-header").fetchSemanticsNode().boundsInRoot
        assertTrue("The header must grow to fit large text", header.height > 60 * compose.activity.resources.displayMetrics.density)
        listOf("conversation-title", "conversation-subtitle").forEach { tag ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("$tag must stay on one line", 1, layouts.single().lineCount)
            assertTrue("$tag is vertically clipped at 200% font scale",
                node.fetchSemanticsNode().boundsInRoot.height + 1 >= layouts.single().getLineBottom(0))
        }
    }

    @Test fun conversationChromeStaysCompactWithLongTitleAndRealKeyboard() = runBlocking {
        useLongTopicTitle()
        showConversation { false }
        compose.waitUntil(10_000) { chat.encryptedChatState.value.sendingKeyUuid != null }
        val density = compose.activity.resources.displayMetrics.density
        val failures = mutableListOf<String>()
        fun checkChrome() {
            val header = compose.onNodeWithTag("conversation-header").fetchSemanticsNode().boundsInRoot
            val composer = compose.onNodeWithTag("message-composer").fetchSemanticsNode().boundsInRoot
            if (header.height > 64 * density + 1) failures += "Header is ${header.height / density} dp (>64)"
            if (composer.height > 64 * density + 1) failures += "Single-line composer is ${composer.height / density} dp (>64)"
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("conversation-title", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            listOf("conversation-back-action", "conversation-call-action", "message-composer-attach", "message-composer-send").forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
                if (bounds.width < 48 * density - 1 || bounds.height < 48 * density - 1)
                    failures += "$tag has a touch target smaller than 48 dp"
            }
        }
        checkChrome()
        compose.onNodeWithTag("message-composer-input").performClick().performTextInput("Готово")
        compose.waitUntil(10_000) {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                visible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
        checkChrome()
        capture("chrome-light-keyboard")
        clickComposerSendAfterImeLayout("Send")
        compose.waitUntil(10_000) { fixture.sentPlaintexts.contains("Готово") }
        compose.waitUntil(10_000) { chat.mentions.text.isEmpty() }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
        compose.onNodeWithTag("message-composer-input").performTextInput("Первая строка\nВторая строка")
        compose.onNodeWithTag("message-composer-attach").assertIsDisplayed()
        capture("chrome-light-multiline-keyboard")
        clickComposerSendAfterImeLayout("Send")
        compose.waitUntil(10_000) { fixture.sentPlaintexts.contains("Первая строка\nВторая строка") }
    }

    @Test fun decryptedMessageBodiesRemainReadableAcrossLiveThemeChanges() {
        var darkTheme by mutableStateOf(false)
        showConversation(darkTheme = { darkTheme })
        val bodies = listOf("Доступ к защищённому чату получен.", "Макеты готовы, можно обсуждать.")
        listOf(false, true, false).forEachIndexed { index, dark ->
            compose.runOnIdle { darkTheme = dark }
            bodies.forEach(::waitForText)
            capture("protected-body-$index-${if (dark) "dark" else "light"}")
            bodies.forEachIndexed { bodyIndex, text ->
                val bounds = visibleNativeTextBounds(text)
                var foreground = 0
                onView(allOf(withText(text), isCompletelyDisplayed())).check { view, failure ->
                    if (failure != null) throw failure
                    foreground = (view as TextView).currentTextColor
                }
                val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                try {
                    val pixels = IntArray(bounds.width() * bounds.height())
                    screenshot.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                    val background = pixels.asSequence().groupingBy { it }.eachCount().maxBy { it.value }.key
                    // Inspect the actual AndroidView glyphs and the bubble visible behind it.
                    // Palette-only assertions miss a stale native view after a theme change.
                    fun near(a: Int, b: Int) = kotlin.math.abs(Color.red(a) - Color.red(b)) <= 2 &&
                        kotlin.math.abs(Color.green(a) - Color.green(b)) <= 2 &&
                        kotlin.math.abs(Color.blue(a) - Color.blue(b)) <= 2
                    assertTrue("The body foreground must be rendered", pixels.count { near(it, foreground) } > 20)
                    val contrast = ColorUtils.calculateContrast(foreground, background)
                    android.util.Log.i("CassiProtectedPalette", "dark=$dark own=${bodyIndex == 0} foreground=${Integer.toHexString(foreground)} background=${Integer.toHexString(background)} contrast=$contrast")
                    assertTrue("Rendered protected body contrast must be >= 4.5:1 (dark=$dark, actual=$contrast)", contrast >= 4.5)
                    val palette = if (dark) DarkWorkspaceColorsPalette else LightWorkspaceColorsPalette
                    assertEquals(palette.textHeaders.toArgb(), foreground)
                    assertBubblePalette(fixture.messages[bodyIndex + 1], dark)
                } finally { screenshot.recycle() }
            }
        }
    }

    @Test fun lockedMessageBubblesFollowThePaletteAcrossLiveThemeChanges() = runBlocking {
        chat.encryptionRuntime.readySession()
        chat.encryptionRuntime.removeLocalKey(CURRENT)
        var darkTheme by mutableStateOf(false)
        showConversation(darkTheme = { darkTheme })
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("encrypted-message-placeholder"), useUnmergedTree = true)
                .fetchSemanticsNodes().size == fixture.messages.size
        }
        listOf(false, true, false).forEachIndexed { index, dark ->
            compose.runOnIdle { darkTheme = dark }
            fixture.messages.forEach { assertBubblePalette(it, dark) }
            capture("protected-locked-$index-${if (dark) "dark" else "light"}")
        }
    }

    private fun assertBubblePalette(message: MessageResponse, dark: Boolean) {
        val palette = if (dark) DarkWorkspaceColorsPalette else LightWorkspaceColorsPalette
        val image = compose.onNodeWithTag("message-bubble-${message.uuid}", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        val background = if (message.isOwn) palette.messageOwnBackground else palette.messageBackground
        assertEquals("The bubble background must follow the selected theme", background.toArgb(), image[image.width / 2, 1].toArgb())
        val author = if (message.isOwn) palette.textHeaders else palette.indicatorPurple
        fun colorCount(pixels: PixelMap, color: androidx.compose.ui.graphics.Color): Int {
            val expected = color.toArgb()
            return (0 until pixels.height).sumOf { y -> (0 until pixels.width).count { x ->
                val actual = pixels[x, y].toArgb()
                kotlin.math.abs(Color.red(actual) - Color.red(expected)) <= 2 &&
                    kotlin.math.abs(Color.green(actual) - Color.green(expected)) <= 2 &&
                    kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) <= 2
            } }
        }
        val authorName = (if (message.isOwn) fixture.owner else fixture.participant).displayableName()
        val authorNode = compose.onNode(hasText(authorName) and
            hasAnyAncestor(hasTestTag("message-bubble-${message.uuid}")), useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        authorNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val renderedAuthor = layouts.single().layoutInput.style.color
        assertEquals("The author must use its theme color", author, renderedAuthor)
        assertTrue("The author's own glyphs must render its theme color",
            colorCount(authorNode.captureToImage().toPixelMap(), author) > 20)
        if (message.isOwn) {
            val authorContrast = ColorUtils.calculateContrast(renderedAuthor.toArgb(), background.toArgb())
            assertTrue("The rendered own author needs >= 4.5:1 contrast (actual=$authorContrast)", authorContrast >= 4.5)
        }
        val labelFailures = mutableListOf<String>()
        val time = Instant.parse(message.createdAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        listOf(time, "# ${chat.topicName ?: chat.chatTitle}").forEach { text ->
            val label = compose.onNode(hasText(text) and
                hasAnyAncestor(hasTestTag("message-bubble-${message.uuid}")), useUnmergedTree = true)
                .assertIsDisplayed()
            val labelLayouts = mutableListOf<TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
            val foreground = labelLayouts.single().layoutInput.style.color
            assertTrue("The label's glyphs must render its actual foreground",
                colorCount(label.captureToImage().toPixelMap(), foreground) > 8)
            val contrast = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
            if (contrast < 4.5) labelFailures += "$text in dark=$dark own=${message.isOwn}: $contrast < 4.5"
        }
        assertTrue("Rendered protected labels need >= 4.5:1 contrast: ${labelFailures.joinToString()}", labelFailures.isEmpty())
        assertTrue("The protected-topic marker must use the theme accent", colorCount(image, palette.protectedChatHeaderAccent) > 10)
        val markerContrast = ColorUtils.calculateContrast(palette.protectedChatHeaderAccent.toArgb(), background.toArgb())
        assertTrue("The rendered protected marker needs >= 3:1 contrast (actual=$markerContrast)", markerContrast >= 3.0)
    }

    @Test fun missingKeyShowsBlurUntilTheRealConversationCanDecryptAgain() = runBlocking {
        val text = "Макеты готовы, можно обсуждать."
        val placeholder = hasTestTag("encrypted-message-placeholder") and
            hasAnyAncestor(hasTestTag("chat-message-${fixture.messages[2].uuid}"))
        chat.encryptionRuntime.readySession()
        chat.encryptionRuntime.removeLocalKey(CURRENT)
        showConversation()
        compose.waitUntil(10_000) {
            compose.onAllNodes(placeholder, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(placeholder, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.encrypted_chat_locked)).assertDoesNotExist()
        onView(withText(text)).check(doesNotExist())
        // A protected body belongs to the ordinary message shell, as in web.
        // Checking only the blur missed centered, anonymous placeholders with no bubble or time.
        val bubbles = fixture.messages.take(4).map { message ->
            val row = hasTestTag("chat-message-${message.uuid}")
            val author = if (message.isOwn) fixture.owner else fixture.participant
            compose.onNode(hasText(author.displayableName()) and hasAnyAncestor(row), useUnmergedTree = true)
                .assertIsDisplayed()
            val time = Instant.parse(message.createdAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            compose.onNode(hasText(time) and hasAnyAncestor(row), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("message-bubble-${message.uuid}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        assertTrue("Incoming bubbles must stay left of outgoing bubbles", bubbles[2].left < bubbles[1].left)
        assertTrue("Outgoing bubbles must stay right of incoming bubbles", bubbles[1].right > bubbles[2].right)
        capture("revision-locked-conversation-bubbles")
        openMessageActions(fixture.messages[2].uuid)
        compose.onNodeWithTag("request-message-key-action").assertIsDisplayed()
        compose.onNodeWithText("Ответить").assertDoesNotExist()
        compose.onNodeWithText("Изменить").assertDoesNotExist()
        compose.onNodeWithTag("forward-message-action").assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()

        assertTrue(EncryptedChatKeyStore(InstrumentationRegistry.getInstrumentation().targetContext)
            .writeChatKey(address, fixture.groupKey, forSending = true))
        chat.encryptionRuntime.refresh(force = true)
        waitForText(text)
        compose.onAllNodes(placeholder, useUnmergedTree = true).assertCountEquals(0)
        capture("revision-unlocked-conversation-bubbles")
    }

    @Test fun fullConversationRetainsComposerAttachmentsReplyEditSelectionAndReactions() = runBlocking {
        showConversation()
        try {
        compose.waitUntil(15_000) { chat.encryptedChatState.value.hasCurrentKey && chat.encryptedPresentations.value.size >= 4 }
        waitForText("Макеты готовы, можно обсуждать.")
        capture("07-full-encrypted-chat")
        compose.onNodeWithContentDescription("Attach file").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Добавим ответы и реакции")
        clickComposerSendAfterImeLayout("Send")
        compose.waitUntil(10_000) { fixture.sentPlaintexts.contains("Добавим ответы и реакции") }
        closeSoftKeyboard()
        waitForText("Добавим ответы и реакции")
        val editableMessageUuid = fixture.messages.last().uuid
        openMessageActions(editableMessageUuid)
        compose.onNodeWithText("Изменить").assertIsDisplayed()
        capture("07b-encrypted-message-actions")
        compose.onNodeWithText("Изменить").performClick()
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Ответы и реакции работают")
        // Native message actions can leave the editor focused with its IME hidden.
        closeSoftKeyboard()
        clickComposerSendAfterImeLayout("Send edit")
        compose.waitUntil(10_000) { fixture.editedPlaintexts.contains("Ответы и реакции работают") }
        closeSoftKeyboard()
        waitForText("Ответы и реакции работают")
        openMessageActions(editableMessageUuid)
        compose.onNodeWithText("Ответить").performClick()
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Согласна")
        clickComposerSendAfterImeLayout("Send")
        compose.waitUntil(10_000) { fixture.sentPlaintexts.any { "urn:quote:" in it && "Согласна" in it } }
        compose.waitUntil(10_000) { chat.mentions.text.isEmpty() && chat.quotedMessages.value.isEmpty() }

        // Drive the same picker callbacks with local files; the native picker itself is outside chat logic.
        val document = File(directory, "План.txt").apply { writeText("Synthetic document") }
        withContext(Dispatchers.Main) { chat.addUri(Uri.fromFile(document), document.name, "file") }
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) { fixture.sentPlaintexts.any { "urn:file:" in it } }
        compose.waitUntil(10_000) { chat.uriList.value.isEmpty() }
        val photo = File(directory, "Макет.png").apply { writeBytes(fixture.image) }
        withContext(Dispatchers.Main) { chat.addUri(Uri.fromFile(photo), photo.name, "image") }
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) { fixture.sentPlaintexts.any { "urn:image:" in it } }
        compose.waitUntil(10_000) { chat.uriList.value.isEmpty() }
        closeSoftKeyboard()
        val expectedImageHash = sha256(fixture.image)
        compose.waitUntil(15_000) {
            fixture.imageDownloads.any { it.uuid == fixture.uploadedImageUuid && it.authMatchesFixture && it.sha256 == expectedImageHash }
        }
        captureRenderedFixtureImage("08-encrypted-chat-attachments")

        val latest = chat.streamTopicMessages.value["$STREAM.$TOPIC"].orEmpty().last { it.originalEncryptedPayload != null }
        openMessageActions(latest.uuid)
        compose.onNodeWithTag("select-message-action").performClick()
        compose.onNodeWithTag("message-selection-bar").assertIsDisplayed()
        compose.onNodeWithTag("forward-selected-messages").assertIsDisplayed()
        capture("09-encrypted-chat-selection")
        compose.onNodeWithTag("cancel-message-selection").performClick()
        capture("09b-encrypted-chat-selection-cancelled")
        openMessageActions(latest.uuid)
        compose.onNodeWithText("👍").performClick()
        compose.waitUntil(5_000) { fixture.reactions.isNotEmpty() }

        // A real history gesture must suspend following, including while an
        // incoming encrypted DTO first projects as locked and then decrypts.
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { !chat.shouldScrollToBottom }
        compose.waitForIdle()
        val historyText = "Макеты готовы, можно обсуждать."
        waitForText(historyText)
        val historyBounds = visibleNativeTextBounds(historyText)
        val duringHistoryText = "Входящее сообщение во время просмотра истории"
        val duringHistory = fixture.incomingEncryptedMessage(duringHistoryText)
        withContext(Dispatchers.Main) { chat.repo.addMessageToStreamTopic(duringHistory) }
        compose.waitUntil(10_000) {
            (chat.encryptedPresentations.value[duringHistory.uuid] as? EncryptedChatPresentation.Text)?.text == duringHistoryText
        }
        compose.waitForIdle()
        assertEquals(historyBounds, visibleNativeTextBounds(historyText))
        onView(withText(duringHistoryText)).check { view, _ ->
            if (view != null) assertFalse("New incoming message forced history to the bottom", isDisplayed().matches(view))
        }
        assertFalse(chat.shouldScrollToBottom)
        capture("10-manual-history-preserved")

        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.waitUntil(5_000) { chat.shouldScrollToBottom }
        waitForText(duringHistoryText)
        val afterReturnText = "Новое сообщение после возврата вниз"
        val afterReturn = fixture.incomingEncryptedMessage(afterReturnText)
        withContext(Dispatchers.Main) { chat.repo.addMessageToStreamTopic(afterReturn) }
        waitForText(afterReturnText)
        assertTrue(chat.shouldScrollToBottom)
        capture("11-follow-after-return")

        assertTrue(fixture.uploads >= 2)
        assertTrue(fixture.sentPayloads.all { it.kind == "e2ee" })
        assertTrue(fixture.rejectedPayloadKinds.isEmpty())
        assertTrue(fixture.transportFailureTypes.isEmpty())
        assertFalse(fixture.sentBodies.any { "Ответы и реакции работают" in it || "Согласна" in it || "urn:file:" in it || "urn:image:" in it })
        assertTrue(chat.repo.streamTopicMessages.value["$STREAM.$TOPIC"].orEmpty().all { it.payload.kind != "markdown" })
        val postedBeforeMissingSnapshot = fixture.sentBodies.size
        withContext(Dispatchers.Main) {
            chat.repo.setInitialStreams(emptyList())
            chat.sendTextMessage("should never plaintext")
            assertEquals(R.string.chat_send_unavailable, chat.actionError.value?.resourceId)
        }
        assertEquals(postedBeforeMissingSnapshot, fixture.sentBodies.size)
        chat.repo.setInitialStreams(listOf(fixture.stream))
        } catch (failure: Throwable) {
            runCatching { diagnostics("full-chat-failure") }
            throw failure
        }
    }

    @Test fun cancellingSelectionFollowsLatestMessageButPreservesManualHistory() = runBlocking {
        val history = (1..14).map { index ->
            fixture.incomingEncryptedMessage(("Сообщение $index: " + "Длинная строка меняет переносы при выделении. ".repeat(4)).trimEnd())
        }
        withContext(Dispatchers.Main) { chat.repo.addStreamTopicMessages(STREAM, TOPIC, fixture.messages.toList()) }
        showConversation()
        compose.waitUntil(10_000) { chat.encryptedPresentations.value[history.last().uuid] is EncryptedChatPresentation.Text }
        val latestText = (chat.encryptedPresentations.value.getValue(history.last().uuid) as EncryptedChatPresentation.Text).text
        waitForText(latestText)
        val photo = File(directory, "Selection.png").apply { writeBytes(fixture.image) }
        withContext(Dispatchers.Main) { chat.addUri(Uri.fromFile(photo), photo.name, "image") }
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) { fixture.sentPlaintexts.any { "urn:image:" in it } && chat.uriList.value.isEmpty() }
        compose.waitUntil(10_000) {
            chat.streamTopicMessages.value["$STREAM.$TOPIC"].orEmpty().lastOrNull()?.let {
                it.originalEncryptedPayload != null && "urn:image:" in it.payload.content
            } == true
        }
        val messages = chat.streamTopicMessages.value["$STREAM.$TOPIC"].orEmpty()
        val latest = messages.last()
        // Establish the bottom precondition once, before exercising cancellation.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(messages.lastIndex)
        captureRenderedFixtureImage("selection-regression-before")
        repeat(2) {
            compose.onNodeWithTag("message-bubble-${latest.uuid}", useUnmergedTree = true).assertIsDisplayed()
            openMessageActions(latest.uuid)
            compose.onNodeWithTag("select-message-action").performClick()
            compose.onNodeWithTag("cancel-message-selection").performClick()
            // Observe the result without issuing a scroll or reopening the keyboard.
            compose.waitUntil(5_000) {
                compose.onNodeWithTag("message-bubble-${latest.uuid}", useUnmergedTree = true).isDisplayed()
            }
            compose.onNodeWithTag("message-bubble-${latest.uuid}", useUnmergedTree = true).assertIsDisplayed()
            captureRenderedFixtureImage("selection-regression-cancelled-$it")
            assertTrue(chat.shouldScrollToBottom)
        }
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { !chat.shouldScrollToBottom }
        val anchor = history.dropLast(1).filter { message ->
            compose.onNodeWithTag("message-bubble-${message.uuid}", useUnmergedTree = true).isDisplayed()
        }.dropLast(1).last()
        assertFalse(compose.onNodeWithTag("message-bubble-${latest.uuid}", useUnmergedTree = true).isDisplayed())
        openMessageActions(anchor.uuid)
        compose.onNodeWithTag("select-message-action").performClick()
        compose.onNodeWithTag("cancel-message-selection").performClick()
        compose.onNodeWithTag("message-bubble-${anchor.uuid}", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(chat.shouldScrollToBottom)
        assertFalse(compose.onNodeWithTag("message-bubble-${latest.uuid}", useUnmergedTree = true).isDisplayed())
    }

    @Test fun disconnectedImageResponseDoesNotPoisonSubsequentDownloads() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val imageUuid = UUID.randomUUID().toString()
        val path = "/api/workspace/v1/messenger/files/$imageUuid/actions/download/"
        val origin = URI(fixture.url)
        fixture.beforeImageResponse = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        try {
            Socket(origin.host, origin.port).use { client ->
                client.getOutputStream().apply {
                    write("GET $path HTTP/1.1\r\nHost: ${origin.host}\r\nConnection: close\r\n\r\n".toByteArray())
                    flush()
                }
                assertTrue("The image GET reached its response boundary", entered.await(5, TimeUnit.SECONDS))
                client.setSoLinger(true, 0) // Close the peer with RST while its response is held.
            }
            release.countDown()
            compose.waitUntil(5_000) { fixture.disconnectedImageDownloads.isNotEmpty() || fixture.transportFailureTypes.isNotEmpty() }
            assertEquals(listOf(imageUuid), fixture.disconnectedImageDownloads.map { it.first })
            assertTrue(fixture.disconnectedImageDownloads.single().second.isNotBlank())
            assertTrue(fixture.imageDownloads.isEmpty())
            assertTrue(fixture.transportFailureTypes.isEmpty())
            fixture.beforeImageResponse = null
            val downloaded = fixtureImageLoader.execute(ImageRequest.Builder(
                InstrumentationRegistry.getInstrumentation().targetContext,
            ).data(fixture.url + path).build())
            assertTrue("A fresh Coil/CIO image request must decode after the disconnect", downloaded is SuccessResult)
            compose.waitUntil(5_000) { fixture.imageDownloads.size == 1 }
            assertEquals(imageUuid, fixture.imageDownloads.single().uuid)
            assertEquals(sha256(fixture.image), fixture.imageDownloads.single().sha256)
            assertTrue(fixture.transportFailureTypes.isEmpty())
        } finally { release.countDown(); fixture.beforeImageResponse = null }
    }

    @Test fun rejectedRequestSurvivesReopeningTheConversationAndCanBeRetried() = runBlocking {
        try {
            chat.encryptionRuntime.readySession()
            chat.encryptionRuntime.removeLocalKey(CURRENT)
            val request = chat.encryptionRuntime.requestKey(CURRENT)
            val rejection = fixture.messages.single { it.uuid == request.uuid }.copy(
                uuid = UUID.randomUUID().toString(), createdAt = "2026-10-15T10:00:12Z",
                payload = MessageResponsePayload("key_reject", keyUuid = CURRENT,
                    requestMessageUuid = request.uuid, senderDeviceId = "synthetic-owner-device",
                    recipientDeviceId = request.payload.deviceId),
            )
            fixture.messages += rejection
            chat.repo.addStreamTopicMessages(STREAM, TOPIC, fixture.messages.toList())
            chat.encryptionRuntime.refresh(force = true)
            assertTrue(chat.encryptedChatState.value.requestStatus[CURRENT] == EncryptedChatRequestStatus.REJECTED)
            val previous = chat
            chat = withContext(Dispatchers.Main) {
                previous.onChatClosed()
                previous.encryptionRuntime.close()
                previous.viewModelScope.cancel()
                ChatDialogViewModel(previous.client, user, "Команда продукта", STREAM, "Общий чат", TOPIC,
                    false, events, null, previous.storage)
            }
            compose.setContent {
                WokspaceTheme(darkTheme = true, dynamicColor = false) {
                    CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                        ChatDialogScreen(chat, rememberNavController())
                    }
                }
            }
            compose.waitUntil(10_000) {
                chat.encryptedChatState.value.requests.any { it.message.uuid == request.uuid && it.outcome == EncryptedChatRequestOutcome.REJECTED }
            }
            assertTrue(chat.encryptedChatState.value.requestStatus[CURRENT] == null)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val rejectedText = context.getString(R.string.encrypted_chat_rejected_prefix)
            // Target the actual annotated link's accessibility action, not its paragraph.
            // Coordinate taps can miss while the reopened conversation is still mounting.
            val retryLink = compose.onNode(
                hasAnyAncestor(hasTestTag("encrypted-request-key-$CURRENT")) and hasClickAction(),
                useUnmergedTree = true,
            )
            compose.waitUntil(5_000) {
                compose.onNodeWithText(rejectedText, substring = true).isDisplayed() && retryLink.isDisplayed()
            }
            compose.onNodeWithText(rejectedText, substring = true).assertIsDisplayed()
            assertEquals(1, compose.encryptedInlineLinkCount("encrypted-request-key-$CURRENT"))
            val retryReply = CountDownLatch(1)
            fixture.nextMessageResponseGate = retryReply
            try {
                retryLink.performClick()
                compose.waitUntil(5_000) { fixture.sentPayloads.count { it.kind == "key_request" } == 2 }
                // Receipt by the server is not acknowledgement by the runtime or its UI.
                compose.onNodeWithText(context.getString(R.string.encrypted_chat_requested)).assertDoesNotExist()
            } finally { retryReply.countDown() }
            compose.waitUntil(5_000) {
                chat.encryptedChatState.value.let { it.requestStatus[CURRENT] == EncryptedChatRequestStatus.REQUESTED && !it.pending } &&
                    compose.onNodeWithText(context.getString(R.string.encrypted_chat_requested)).isDisplayed()
            }
            compose.onNodeWithText(context.getString(R.string.encrypted_chat_requested)).assertIsDisplayed()
            compose.onNodeWithText(rejectedText, substring = true).assertDoesNotExist()
        } catch (error: Throwable) {
            runCatching { diagnostics("retry-request-failure") }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    @Test fun protectedHeaderCallIsDisabledWhenNoSendingGenerationIsAvailable() = runBlocking {
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    ChatDialogScreen(chat, rememberNavController())
                }
            }
        }
        compose.waitUntil(10_000) { chat.encryptedChatState.value.sendingKeyUuid != null }
        compose.onNodeWithContentDescription("Call").assertIsEnabled()
        chat.encryptionRuntime.removeLocalKey(CURRENT)
        compose.waitUntil(5_000) { chat.encryptedChatState.value.sendingKeyUuid == null }
        compose.onNodeWithContentDescription("Call").assertIsNotEnabled().performClick()
        compose.runOnIdle {
            assertFalse(chat.protectedCallState.value.busy)
            assertTrue(fixture.sentBodies.isEmpty())
        }
    }

    private fun openMessageActions(messageUuid: String) {
        // Inject through Compose so its gesture clock advances, and target the
        // bubble header rather than the interop TextView's native event handler.
        compose.onNodeWithTag("message-bubble-$messageUuid", useUnmergedTree = true)
            .assertIsDisplayed().performTouchInput {
                longClick(Offset(center.x, center.y * 0.4f))
            }
    }

    private fun clickComposerSendAfterImeLayout(description: String) {
        // Native IME animation can move the button after Compose has become idle.
        // Sending is valid with either a shown or dismissed keyboard; require the
        // native window, IME state and button coordinates to settle before one touch.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val decor = compose.activity.window.decorView
        var previous: androidx.compose.ui.geometry.Rect? = null
        var previousWindow: Rect? = null
        var previousIme: Boolean? = null
        var stableSince = 0L
        var windowFocused = false
        var imeVisible = false
        var reachable = false
        val visibleWindow = Rect()
        try {
            compose.waitUntil(5_000) {
                instrumentation.runOnMainSync {
                    windowFocused = decor.hasWindowFocus()
                    imeVisible = ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    decor.getWindowVisibleDisplayFrame(visibleWindow)
                }
                val bounds = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInWindow
                val now = SystemClock.uptimeMillis()
                reachable = bounds.left >= visibleWindow.left && bounds.right <= visibleWindow.right &&
                    bounds.top >= visibleWindow.top && bounds.bottom <= visibleWindow.bottom
                if (!windowFocused || !reachable || bounds != previous ||
                    visibleWindow != previousWindow || imeVisible != previousIme) {
                    previous = bounds
                    previousWindow = Rect(visibleWindow)
                    previousIme = imeVisible
                    stableSince = now
                    false
                } else now - stableSince >= 200
            }
        } catch (failure: Throwable) {
            failure.addSuppressed(AssertionError("Native composer layout: windowFocused=$windowFocused; " +
                "imeVisible=$imeVisible; reachable=$reachable; button=$previous; visibleWindow=$visibleWindow"))
            throw failure
        }
        compose.onNodeWithContentDescription(description).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun waitForText(text: String) {
        // MarkdownText renders a native TextView, outside the Compose semantics tree.
        val deadline = SystemClock.uptimeMillis() + 10_000
        var lastFailure: Throwable? = null
        do {
            try {
                onView(allOf(withText(text), isCompletelyDisplayed())).check(matches(isCompletelyDisplayed()))
                return
            } catch (failure: Throwable) { lastFailure = failure }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Actual native message text was not displayed: $text", lastFailure)
    }

    private fun visibleNativeTextBounds(text: String): Rect {
        val bounds = Rect()
        onView(allOf(withText(text), isCompletelyDisplayed())).check { view, failure ->
            if (failure != null) throw failure
            assertTrue(requireNotNull(view).getGlobalVisibleRect(bounds))
        }
        return bounds
    }

    private fun diagnostics(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "encrypted-chat-screenshots").apply { mkdirs() }
        val hierarchy = buildString {
            val encryption = chat.encryptedChatState.value
            appendLine("hasCurrentKey=${encryption.hasCurrentKey}; hasSendingKey=${encryption.sendingKeyUuid != null}; pending=${encryption.pending}; presentationCount=${chat.encryptedPresentations.value.size}; error=${encryption.error}")
            appendLine("requestStatus=${encryption.requestStatus}; requestOutcomes=${encryption.requests.map { it.outcome }}")
            appendLine("composerLength=${chat.mentions.text.length}; postedBodyCount=${fixture.sentBodies.size}; acceptedPayloadCount=${fixture.sentPayloads.size}; rejectedPayloadKinds=${fixture.rejectedPayloadKinds}; actionError=${chat.actionError.value?.resourceId}")
            appendLine("fixtureTransportFailureTypes=${fixture.transportFailureTypes}; disconnectedImages=${fixture.disconnectedImageDownloads.size}")
            appendLine("hostSoftInputMode=${compose.activity.window.attributes.softInputMode}")
            appendLine("shouldScrollToBottom=${chat.shouldScrollToBottom}")
            appendLine("uploadedImageUuid=${fixture.uploadedImageUuid}; imageDownloads=${fixture.imageDownloads}")
            fun dump(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || depth > 40) return
                val bounds = Rect().also(node::getBoundsInScreen)
                appendLine("${" ".repeat(depth)}${node.className}: text=${node.text}; description=${node.contentDescription}; visible=${node.isVisibleToUser}; bounds=$bounds")
                for (index in 0 until node.childCount) dump(node.getChild(index), depth + 1)
            }
            dump(instrumentation.uiAutomation.rootInActiveWindow, 0)
        }
        File(output, "$name-hierarchy.txt").writeText(hierarchy)
        capture(name)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "encrypted-chat-screenshots").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun captureRenderedFixtureImage(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "encrypted-chat-screenshots").apply { mkdirs() }
        // The fixture photo contains a solid orange field. A 120x32 rectangle
        // cannot be satisfied by the much smaller lock icons or orange text.
        compose.waitUntil(15_000) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
            try {
                val rendered = containsFixtureImageRectangle(screenshot)
                if (rendered) {
                    File(output, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(output, "$name-verification.txt").writeText(
                        "renderedOrangeRectangle=true\nimageSha256=${sha256(fixture.image)}\nimageDownloads=${fixture.imageDownloads}\n",
                    )
                }
                rendered
            } finally { screenshot.recycle() }
        }
    }

    private fun containsFixtureImageRectangle(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        var consecutiveRows = 0
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            var run = 0
            var hasLongOrangeRun = false
            row.forEach { pixel ->
                // Display-P3 screenshot conversion can round the fixture's sRGB channels by one byte.
                run = if (Color.red(pixel) in 253..255 && Color.green(pixel) in 127..131 &&
                    Color.blue(pixel) in 43..47) run + 1 else 0
                if (run >= 120) hasLongOrangeRun = true
            }
            consecutiveRows = if (hasLongOrangeRun) consecutiveRows + 1 else 0
            if (consecutiveRows >= 32) return true
        }
        return false
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        internal const val OWNER = "11111111-1111-4111-8111-111111111111"
        internal const val PARTICIPANT = "22222222-2222-4222-8222-222222222222"
        internal const val STREAM = "33333333-3333-4333-8333-333333333333"
        internal const val TOPIC = "44444444-4444-4444-8444-444444444444"
        internal const val CURRENT = "55555555-5555-4555-8555-555555555555"
        internal const val PROJECT = "66666666-6666-4666-8666-666666666666"
    }
}

internal data class EncryptedFixtureImageDownload(val uuid: String, val authMatchesFixture: Boolean, val sha256: String)

internal class EncryptedConversationServer : Closeable {
    private val socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private val workers = Executors.newCachedThreadPool()
    private val crypto = WorkspaceChatCrypto()
    val groupKey = crypto.createGroupKey()
    val url = "http://127.0.0.1:${socket.localPort}"
    val owner = UserResponseData(firstName = "Касандра", lastName = "Волкова", username = "cassi", uuid = OWNER, status = "active", avatar = "")
    val participant = UserResponseData(firstName = "Анна", lastName = "Соколова", username = "anna", uuid = PARTICIPANT, status = "active", avatar = "")
    val stream = Stream(STREAM, 0, 0, 0, TIME, "Команда продукта", true, 0, defaultTopicUuid = TOPIC,
        notificationMode = "all", encryption = true, currentEncryptionKeyUuid = CURRENT, ownerUuid = OWNER, projectId = PROJECT)
    val bindings = listOf(StreamBindingResponseData(UUID.randomUUID().toString(), "owner", STREAM, OWNER),
        StreamBindingResponseData(UUID.randomUUID().toString(), "member", STREAM, PARTICIPANT))
    val messages = CopyOnWriteArrayList<MessageResponse>()
    val sentPlaintexts = CopyOnWriteArrayList<String>()
    val editedPlaintexts = CopyOnWriteArrayList<String>()
    val sentPayloads = CopyOnWriteArrayList<MessageResponsePayload>()
    val sentBodies = CopyOnWriteArrayList<String>()
    val rejectedPayloadKinds = CopyOnWriteArrayList<String>()
    val transportFailureTypes = CopyOnWriteArrayList<String>()
    val disconnectedImageDownloads = CopyOnWriteArrayList<Pair<String, String>>()
    val reactions = CopyOnWriteArrayList<String>()
    val imageDownloads = CopyOnWriteArrayList<EncryptedFixtureImageDownload>()
    @Volatile var uploadedImageUuid: String? = null
    val createdStreams = CopyOnWriteArrayList<Stream>()
    val streamCreationBodies = CopyOnWriteArrayList<String>()
    val invitationBodies = CopyOnWriteArrayList<String>()
    @Volatile var failNextInvitation = false
    @Volatile var nextCreationStatus: Int? = null
    @Volatile var dropNextCreationResponse = false
    @Volatile var nextMessageStatus: Int? = null
    @Volatile var nextMessageResponseGate: CountDownLatch? = null
    @Volatile var beforeImageResponse: (() -> Unit)? = null
    @Volatile var invitationAttempts = 0
    @Volatile var uploads = 0
    private val files = java.util.concurrent.ConcurrentHashMap<String, Pair<String, ByteArray>>()
    val image = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.rgb(255, 129, 45)); drawText("Workspace", 28f, 70f, Paint().apply { color = Color.BLACK; textSize = 30f }) }
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray()
    }

    init {
        messages += message(UUID.randomUUID().toString(), MessageResponsePayload("e2ee", "opaque-unavailable-history", "77777777-7777-4777-8777-777777777777"), PARTICIPANT, 0)
        messages += encrypted("Доступ к защищённому чату получен.", OWNER, 1)
        messages += encrypted("Макеты готовы, можно обсуждать.", PARTICIPANT, 2)
        messages += encrypted("Оставим привычные возможности чата.", OWNER, 3)
        workers.execute {
            while (running.get()) {
                val incoming = try { socket.accept() } catch (_: java.net.SocketException) { break }
                workers.execute {
                    runCatching { incoming.use(::handle) }
                        .onFailure {
                            transportFailureTypes += it.javaClass.simpleName
                            android.util.Log.w("CassiEncryptedUiTest", "Synthetic HTTP fixture failure", it)
                        }
                }
            }
        }
    }

    private fun encrypted(text: String, author: String, offset: Int): MessageResponse {
        val envelope = crypto.encryptMessage(groupKey, text.toByteArray(), ChatMessageContext(STREAM, CURRENT))
        return message(UUID.randomUUID().toString(), MessageResponsePayload("e2ee", WorkspaceChatCrypto.toBase64(envelope), CURRENT), author, offset)
    }

    fun incomingEncryptedMessage(text: String): MessageResponse = encrypted(text, PARTICIPANT, messages.size)
        .also { messages += it }

    private fun message(uuid: String, payload: MessageResponsePayload, author: String = OWNER, offset: Int = messages.size) = MessageResponse(
        uuid, TIME, "2026-10-15T10:00:${offset.toString().padStart(2, '0')}Z", STREAM, TOPIC, author, author,
        payload, author == OWNER, emptyMap(), true,
    )

    private fun handle(connection: Socket) {
        connection.soTimeout = 10_000
        val input = DataInputStream(connection.getInputStream())
        fun line(): String = buildString {
            while (true) { val byte = input.read(); if (byte < 0 || byte == 10) break; if (byte != 13) append(byte.toChar()) }
        }
        val requestLine = line().split(' ')
        val method = requestLine.getOrNull(0) ?: return
        val route = requestLine.getOrNull(1) ?: return
        val path = route.substringBefore('?')
        val headers = mutableMapOf<String, String>()
        while (true) { val value = line(); if (value.isEmpty()) break; headers[value.substringBefore(':').lowercase()] = value.substringAfter(':').trim() }
        val bytes = if (headers["transfer-encoding"] == "chunked") ByteArrayOutputStream().use { output ->
            while (true) { val size = line().substringBefore(';').toInt(16); if (size == 0) { line(); break }; output.write(ByteArray(size).also(input::readFully)); line() }
            output.toByteArray()
        } else ByteArray(headers["content-length"]?.toIntOrNull() ?: 0).also(input::readFully)
        val body = bytes.toString(Charsets.UTF_8)
        val requestingUser = if (headers["authorization"] == "Bearer synthetic-member") PARTICIPANT else OWNER
        when {
            path == "/api/workspace/v1/messenger/me/" -> respond(connection,
                Json.encodeToString(if (requestingUser == PARTICIPANT) participant else owner))
            path == "/api/workspace/v1/messenger/server_settings/" -> respond(connection,
                """{"email_auth_enabled":true,"realm_name":"CASSI test","realm_url":"$url","realm_icon":"","meet_url":"$url"}""")
            path == "/api/workspace/v1/messenger/stream_topics/" -> respond(connection,
                """[{"uuid":"$TOPIC","name":"Общий чат","color":0,"stream_uuid":"$STREAM","updated_at":"$TIME","unread_count":0,"active_unread_count":0,"is_done":false,"is_default":true,"notification_mode":"all"}]""")
            path == "/api/workspace/v1/messenger/stream_bindings/" -> respond(connection, Json.encodeToString(bindings))
            path == "/api/workspace/v1/messenger/users/" -> respond(connection, Json.encodeToString(listOf(owner, participant)))
            path == "/api/workspace/v1/messenger/streams/" && method == "POST" -> {
                streamCreationBodies += body
                nextCreationStatus?.let { status ->
                    nextCreationStatus = null
                    respondError(connection, status)
                    return
                }
                val request = Json.parseToJsonElement(body).jsonObject
                val supportedFields = setOf("uuid", "name", "description", "source_name", "invite_only",
                    "announce", "direct_user_uuid", "private", "color", "encryption", "current_encryption_key_uuid")
                if (request.keys.any { it !in supportedFields }) {
                    respondError(connection, status = 400)
                    return
                }
                val id = request["uuid"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
                val created = createdStreams.firstOrNull { it.uuid == id } ?: Stream(id, 0, 0, 0, TIME,
                    request.getValue("name").jsonPrimitive.content, true, 0,
                    defaultTopicUuid = UUID.randomUUID().toString(), notificationMode = "all",
                    encryption = request["encryption"]?.jsonPrimitive?.content == "true",
                    currentEncryptionKeyUuid = request["current_encryption_key_uuid"]?.jsonPrimitive?.contentOrNull,
                    ownerUuid = requestingUser, projectId = PROJECT, userUuid = requestingUser, role = "owner").also { createdStreams += it }
                if (dropNextCreationResponse) { dropNextCreationResponse = false; return }
                respond(connection, Json.encodeToString(created))
            }
            path.startsWith("/api/workspace/v1/messenger/streams/") && path.endsWith("/actions/add_users/invoke") -> {
                invitationAttempts++
                invitationBodies += body
                val invitation = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                val members = invitation?.get("member") as? JsonArray
                val validMemberRole = invitation?.keys == setOf("member") && !members.isNullOrEmpty() && members.all {
                    it is JsonPrimitive && it.isString && it.content in setOf(OWNER, PARTICIPANT)
                }
                // V2 treats each object key as a role. Reject plural "members" before the retry fixture.
                if (!validMemberRole) respondError(connection, status = 400)
                else if (failNextInvitation) { failNextInvitation = false; respondError(connection) }
                else respond(connection, Json.encodeToString(members!!.map { member ->
                    StreamBindingResponseData(UUID.randomUUID().toString(), "member",
                        path.substringAfter("/streams/").substringBefore('/'), member.jsonPrimitive.content)
                }))
            }
            path == "/api/workspace/v1/messenger/streams/" -> respond(connection, Json.encodeToString(listOf(stream) + createdStreams))
            path == "/api/workspace/v1/messenger/messages/" && method == "GET" -> {
                val requested = URI(route).rawQuery.orEmpty().split('&').filter { it.substringBefore('=').startsWith("uuid") }
                    .map { URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                respond(connection, Json.encodeToString(messages.filter { requested.isEmpty() || it.uuid in requested }
                    .map { it.copy(isOwn = it.authorUuid == requestingUser) }))
            }
            path == "/api/workspace/v1/messenger/messages/" && method == "POST" -> {
                sentBodies += body
                nextMessageStatus?.let { status -> nextMessageStatus = null; respondError(connection, status); return }
                val request = Json.parseToJsonElement(body).jsonObject
                val payloadObject = request.getValue("payload").jsonObject
                if (!acceptPayload(connection, payloadObject)) return
                val payload = Json.decodeFromString<MessageResponsePayload>(payloadObject.toString())
                sentPayloads += payload
                if (payload.kind == "e2ee" && payload.keyUuid == CURRENT) sentPlaintexts += decrypt(payload)
                val canonical = request.getValue("uuid").jsonPrimitive.content
                val created = message(encryptedMessagePlacementUuid(TOPIC, canonical), payload, requestingUser)
                if (payload.kind == "key_announced") stream.currentEncryptionKeyUuid = payload.keyUuid
                messages += created
                nextMessageResponseGate?.let { gate ->
                    nextMessageResponseGate = null
                    check(gate.await(5, TimeUnit.SECONDS)) { "Expected release of the synthetic message response" }
                }
                respond(connection, Json.encodeToString(created))
            }
            path.startsWith("/api/workspace/v1/messenger/messages/") && method == "PUT" -> {
                sentBodies += body
                val uuid = path.substringAfterLast('/')
                val payloadObject = Json.parseToJsonElement(body).jsonObject.getValue("payload").jsonObject
                if (!acceptPayload(connection, payloadObject)) return
                val payload = Json.decodeFromString<MessageResponsePayload>(payloadObject.toString())
                sentPayloads += payload
                editedPlaintexts += decrypt(payload)
                val changed = messages.single { it.uuid == uuid }.copy(payload = payload)
                messages.removeAll { it.uuid == uuid }; messages += changed
                respond(connection, Json.encodeToString(changed))
            }
            path == "/api/workspace/v1/messenger/files/" && method == "POST" -> {
                uploads++
                val uuid = UUID.randomUUID().toString()
                val name = Regex("filename=\"?([^\";\\r\\n]+)").find(body)?.groupValues?.get(1)
                    ?: error("Synthetic upload is missing its filename")
                files[uuid] = name to if (name.endsWith("png")) image else "Synthetic document".toByteArray()
                if (name.endsWith("png")) uploadedImageUuid = uuid
                respond(connection, """{"uuid":"$uuid","name":"$name"}""")
            }
            path.startsWith("/api/workspace/v1/messenger/files/") -> {
                val uuid = path.substringAfter("/api/workspace/v1/messenger/files/").substringBefore('/')
                val file = files[uuid] ?: ("Макет.png" to image)
                beforeImageResponse?.invoke()
                try {
                    respondBytes(connection, file.second, if (file.first.endsWith("png")) "image/png" else "text/plain")
                } catch (disconnected: SocketException) {
                    // Lazy image consumers can leave/recompose after their complete GET.
                    // Keep their response-write disconnects observable without treating
                    // them as malformed requests or failures of message/API responses.
                    val reason = disconnected.message
                    val peerClosed = reason in listOf("Broken pipe", "Connection reset", "Connection reset by peer")
                    if (method != "GET" || !file.first.endsWith("png") || !peerClosed) throw disconnected
                    disconnectedImageDownloads += uuid to requireNotNull(reason)
                    return
                }
                if (file.first.endsWith("png")) imageDownloads += EncryptedFixtureImageDownload(uuid,
                    headers["authorization"] == "Bearer synthetic-access",
                    MessageDigest.getInstance("SHA-256").digest(file.second).joinToString("") { "%02x".format(it) })
            }
            path == "/api/workspace/v1/messenger/message_reactions/" && method == "GET" -> respond(connection, "[]")
            path == "/api/workspace/v1/messenger/message_reactions/" -> {
                reactions += body
                respond(connection, """{"uuid":"${UUID.randomUUID()}","user_uuid":"$OWNER","emoji_name":"👍","message_uuid":"${messages.last().uuid}"}""")
            }
            else -> respond(connection, "{}")
        }
    }

    private fun decrypt(payload: MessageResponsePayload): String = crypto.decryptMessage(groupKey,
        WorkspaceChatCrypto.fromBase64(payload.content), ChatMessageContext(STREAM, requireNotNull(payload.keyUuid))).toString(Charsets.UTF_8)

    /** The backend rejects fields belonging to a different payload variant, including nulls. */
    private fun acceptPayload(connection: Socket, payload: JsonObject): Boolean {
        val kind = (payload["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val required = when (kind) {
            "e2ee" -> setOf("kind", "key_uuid", "content")
            "key_request" -> setOf("kind", "key_uuid", "device_id", "device_name", "public_key")
            "key_grant" -> setOf("kind", "key_uuid", "request_message_uuid", "sender_device_id", "recipient_device_id", "content")
            "key_reject" -> setOf("kind", "key_uuid", "request_message_uuid", "sender_device_id", "recipient_device_id")
            "key_announced" -> setOf("kind", "key_uuid")
            else -> emptySet()
        }
        val valid = required.isNotEmpty() && payload.keys == required &&
            payload.values.all { it is JsonPrimitive && it.isString }
        if (!valid) {
            rejectedPayloadKinds += kind
            respondError(connection, status = 400)
        }
        return valid
    }

    private fun respondError(connection: Socket, status: Int = 503) {
        val responseStatus = "$status Result"
        val body = "{\"error\":\"invitation failed\",\"error_message\":\"Synthetic invitation failure\"}".toByteArray()
        connection.getOutputStream().apply {
            write("HTTP/1.1 $responseStatus\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(body); flush()
        }
    }
    private fun respond(connection: Socket, body: String) = respondBytes(connection, body.toByteArray(), "application/json")
    private fun respondBytes(connection: Socket, body: ByteArray, type: String) {
        connection.getOutputStream().apply {
            write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(body); flush()
        }
    }

    override fun close() { running.set(false); socket.close(); workers.shutdownNow(); groupKey.fill(0) }

    companion object {
        private const val OWNER = EncryptedConversationUiTest.OWNER
        private const val PARTICIPANT = EncryptedConversationUiTest.PARTICIPANT
        private const val STREAM = EncryptedConversationUiTest.STREAM
        private const val TOPIC = EncryptedConversationUiTest.TOPIC
        private const val CURRENT = EncryptedConversationUiTest.CURRENT
        private const val PROJECT = EncryptedConversationUiTest.PROJECT
        private const val TIME = "2026-10-15T10:00:00Z"
    }
}
