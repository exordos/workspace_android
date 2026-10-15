package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import android.graphics.Bitmap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule as createQueuedAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import ru.genesiscorporation.workspace.beta.LocalBottomBarVisible
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.SecureTokenStore
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.ServerRepository
import ru.genesiscorporation.workspace.beta.data.TokenPair
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyAddress
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyOwner
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyScope
import ru.genesiscorporation.workspace.beta.data.crypto.ChatMessageContext
import ru.genesiscorporation.workspace.beta.data.crypto.EncryptedChatKeyStore
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspaceChatCrypto
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
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
        user = UserViewModel(preferences)
        api.attachUserViewModel(user)
        serverId = "cassi-encrypted-conversation-${UUID.randomUUID()}"
        val config = ServerConfig(serverId, fixture.url, "", "CASSI test", projectUuid = PROJECT)
        preferences.addServer(config, TokenPair("synthetic-access", "synthetic-refresh"))
        preferences.setSelectedServerId(serverId)
        withTimeout(5_000) { user.selectedServer.first { it?.id == serverId } }
        withTimeout(5_000) { user.accessToken.first { it != null } }
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

    @Test fun fullConversationRetainsComposerAttachmentsReplyEditSelectionAndReactions() = runBlocking {
        // Match MainActivity and WorkspaceApp. The default test Activity can pan its
        // entire window above the screen instead of dispatching the production IME insets.
        compose.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    ChatDialogScreen(chat, rememberNavController())
                    }
                }
            }
        }
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
        // Observe the actual window and stable button coordinates before one touch.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val decor = compose.activity.window.decorView
        var previous: androidx.compose.ui.geometry.Rect? = null
        var stableSince = 0L
        compose.waitUntil(5_000) {
            var imeVisible = false
            val visibleWindow = Rect()
            instrumentation.runOnMainSync {
                imeVisible = ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                decor.getWindowVisibleDisplayFrame(visibleWindow)
            }
            val bounds = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInWindow
            val now = SystemClock.uptimeMillis()
            val reachable = bounds.left >= visibleWindow.left && bounds.right <= visibleWindow.right &&
                bounds.top >= visibleWindow.top && bounds.bottom <= visibleWindow.bottom
            if (!imeVisible || !reachable || bounds != previous) {
                previous = bounds
                stableSince = now
                false
            } else now - stableSince >= 200
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
            appendLine("composerLength=${chat.mentions.text.length}; postedBodyCount=${fixture.sentBodies.size}; acceptedPayloadCount=${fixture.sentPayloads.size}; rejectedPayloadKinds=${fixture.rejectedPayloadKinds}; actionError=${chat.actionError.value?.resourceId}")
            appendLine("fixtureTransportFailureTypes=${fixture.transportFailureTypes}")
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
    val reactions = CopyOnWriteArrayList<String>()
    val imageDownloads = CopyOnWriteArrayList<EncryptedFixtureImageDownload>()
    @Volatile var uploadedImageUuid: String? = null
    val createdStreams = CopyOnWriteArrayList<Stream>()
    val streamCreationBodies = CopyOnWriteArrayList<String>()
    val invitationBodies = CopyOnWriteArrayList<String>()
    @Volatile var failNextInvitation = false
    @Volatile var nextCreationStatus: Int? = null
    @Volatile var dropNextCreationResponse = false
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
                        .onFailure { transportFailureTypes += it.javaClass.simpleName }
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
                else respond(connection, "{}")
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
                val name = if (uploads == 2) "Макет.png" else "План.txt"
                files[uuid] = name to if (name.endsWith("png")) image else "Synthetic document".toByteArray()
                if (name.endsWith("png")) uploadedImageUuid = uuid
                respond(connection, """{"uuid":"$uuid","name":"$name"}""")
            }
            path.startsWith("/api/workspace/v1/messenger/files/") -> {
                val uuid = path.substringAfter("/api/workspace/v1/messenger/files/").substringBefore('/')
                val file = files[uuid] ?: ("Макет.png" to image)
                if (file.first.endsWith("png")) imageDownloads += EncryptedFixtureImageDownload(uuid,
                    headers["authorization"] == "Bearer synthetic-access",
                    MessageDigest.getInstance("SHA-256").digest(file.second).joinToString("") { "%02x".format(it) })
                respondBytes(connection, file.second, if (file.first.endsWith("png")) "image/png" else "text/plain")
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
