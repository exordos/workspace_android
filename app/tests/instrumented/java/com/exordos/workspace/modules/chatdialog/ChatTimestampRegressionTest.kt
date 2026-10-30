package com.exordos.workspace.modules.chatdialog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalLocale
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.LocalBottomBarVisible
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.modules.encryptedchat.EncryptedChatSessionState
import com.exordos.workspace.ui.theme.WokspaceTheme

/** Run on Android 12: the host JVM accepts offsets that its Instant parser rejects. */
@RunWith(AndroidJUnit4::class)
class ChatTimestampRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var chat: ChatDialogViewModel
    private val originalZone = TimeZone.getDefault()
    private val releaseResponse = CountDownLatch(1)
    private var midnightHeader = ""
    private val readUpToRequests = CopyOnWriteArrayList<String>()

    @After fun cleanup() = runBlocking {
        releaseResponse.countDown()
        if (::chat.isInitialized) withContext(Dispatchers.Main) {
            chat.onChatClosed()
            chat.encryptionRuntime.close()
            chat.viewModelScope.cancel()
        }
        if (::account.isInitialized) account.close()
        if (::server.isInitialized) server.close()
        TimeZone.setDefault(originalZone)
    }

    @Test fun historyOrdersOffsetAndUtcTimestampsByInstant() {
        val offset = message("b", "2026-10-30T11:39:50.168+03:00")
        val sameInstant = message("a", "2026-10-30T08:39:50.168Z")
        val earlier = message("c", "2026-10-30T03:39:49.168-05:00")
        val history = deriveChatHistoryPresentation(listOf(offset, earlier, sameInstant, offset), emptyList(),
            emptyMap(), STREAM, false, OWNER, EncryptedChatSessionState())
        assertEquals(listOf("c", "a", "b"), history.orderedMessages!!.map { it.uuid })
    }

    @Test fun offsetKeyRequestIsNotSilentlyDiscardedOnOlderAndroid() {
        val request = message("request", "2026-10-30T11:39:50.168+03:00").copy(
            payload = MessageResponsePayload("key_request", keyUuid = "generation", deviceId = "device"),
        )
        val history = deriveChatHistoryPresentation(emptyList(), listOf(request), emptyMap(), STREAM, true,
            OWNER, EncryptedChatSessionState(currentKeyUuid = "generation", deviceId = "device"))
        assertEquals("request", history.latestOwnRequestUuids[history.requestKey(STREAM, "generation")])
        assertEquals(true, history.showInitialKeyRequest)
    }

    @Test fun offsetHistoryRendersTheRealChatAndMessageTime(): Unit = runBlocking {
        showConversation(listOf(message("history", "2026-10-30T11:39:50.168+03:00")))
        compose.onNodeWithTag("chat-message-history").assertIsDisplayed()
    }

    @Test fun messageNeighborsMatchAbsoluteHistoryOrder(): Unit = runBlocking {
        showConversation(listOf(message("early", "2026-10-30T00:15:00+03:00"),
            message("late", "2026-10-29T21:30:00Z")))
        assertNull(chat.previousMessageByUuid("early"))
        assertEquals("late", chat.nextMessageByUuid("early")?.uuid)
        assertEquals("early", chat.previousMessageByUuid("late")?.uuid)
        assertNull(chat.nextMessageByUuid("late"))
    }

    @Test fun latestHistoryReadBoundaryUsesTheNewestAbsoluteInstant(): Unit = runBlocking {
        showConversation(listOf(message("early", "2026-10-30T00:15:00+03:00"),
            message("late", "2026-10-29T21:30:00Z")))
        readUpToRequests.clear()
        chat.loadLatestMessages()
        assertEquals(listOf("/api/workspace/v1/messenger/messages/late/actions/read_up_to/invoke"),
            readUpToRequests.toList())
    }

    @Test fun dayHeaderAppearsWhenDeviceDayChangesWithinOneUtcDate(): Unit = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        showConversation(listOf(message("early", "2026-10-29T20:50:00Z"),
            message("late", "2026-10-29T21:10:00Z")))
        compose.onNodeWithText(midnightHeader).assertIsDisplayed()
    }

    @Test fun dayHeaderDoesNotSplitTheSameDeviceDayAcrossUtcDates(): Unit = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        showConversation(listOf(message("early", "2026-10-29T23:50:00Z"),
            message("late", "2026-10-30T03:10:00+03:00")))
        compose.onNodeWithTag("chat-message-late").assertIsDisplayed()
        compose.onNodeWithText(midnightHeader).assertDoesNotExist()
    }

    @Test fun sendingFromOffsetZoneRendersPendingAndConfirmedMessage(): Unit = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        assertEquals("GMT+03:00", ZoneId.systemDefault().id)
        showConversation(listOf(message("history", "2026-10-30T08:39:50.168Z")))
        compose.onNodeWithTag("chat-message-history").assertIsDisplayed()
        compose.onNodeWithTag("message-composer-input").performTextInput("CASSI offset message")
        compose.onNodeWithTag("message-composer-send").performClick()
        compose.waitUntil(5_000) {
            account.repo.streamTopicMessages.value["$STREAM.$TOPIC"].orEmpty()
                .any { it.payload.content == "CASSI offset message" }
        }
        val pending = account.repo.streamTopicMessages.value.getValue("$STREAM.$TOPIC")
            .single { it.payload.content == "CASSI offset message" }
        assertTrue(pending.createdAt.endsWith("Z"))
        assertEquals(pending.createdAt, pending.updatedAt)
        compose.onNodeWithTag("chat-message-${pending.uuid}").assertIsDisplayed()
        releaseResponse.countDown()
        compose.waitUntil(5_000) {
            account.repo.streamTopicMessages.value["$STREAM.$TOPIC"].orEmpty().any { it.uuid == CONFIRMED }
        }
        compose.onNodeWithTag("chat-message-$CONFIRMED").assertIsDisplayed()
    }

    private suspend fun showConversation(history: List<MessageResponse>) {
        server = LoopbackWorkspaceServer { request ->
            when {
                request.path.endsWith("/actions/read_up_to/invoke") -> {
                    readUpToRequests += request.path
                    LoopbackWorkspaceServer.Response("{}")
                }
                request.method == "POST" && request.path == "/api/workspace/v1/messenger/messages/" -> {
                    check(releaseResponse.await(20, TimeUnit.SECONDS))
                    LoopbackWorkspaceServer.Response("""{"uuid":"$CONFIRMED","topic_uuid":"$TOPIC"}""")
                }
                request.path == "/api/workspace/v1/messenger/messages/" ->
                    LoopbackWorkspaceServer.Response(Json.encodeToString(history))
                else -> LoopbackWorkspaceServer.Response("[]")
            }
        }
        account = WorkspaceTestAccount.create(server.url)
        val owner = UserResponseData(username = "cassi", uuid = OWNER, status = "active", avatar = "")
        account.repo.updateCurrentUser(owner)
        account.repo.setInitialUsers(listOf(owner))
        account.repo.setInitialStreams(listOf(Stream(STREAM, 0, 0, 0, history.first().createdAt,
            "CASSI timestamps", false, 0, defaultTopicUuid = TOPIC, notificationMode = "all_messages")))
        account.repo.addStreamTopicMessages(STREAM, TOPIC, history)
        chat = withContext(Dispatchers.Main) {
            ChatDialogViewModel(account.api, account.user, "CASSI timestamps", STREAM, "Topic", TOPIC,
                false, account.events, null,
                AttachmentStorage(InstrumentationRegistry.getInstrumentation().targetContext, account.api))
        }
        compose.setContent {
            midnightHeader = LocalDate.of(2026, 10, 30)
                .format(DateTimeFormatter.ofPattern("d MMM", LocalLocale.current.platformLocale))
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    ChatDialogScreen(chat, rememberNavController())
                }
            }
        }
    }

    private fun message(uuid: String, timestamp: String) = MessageResponse(uuid, timestamp, timestamp,
        STREAM, TOPIC, OWNER, OWNER, MessageResponsePayload("markdown", "CASSI history"), true, emptyMap(), true)

    companion object {
        private const val STREAM = "33333333-3333-4333-8333-333333333333"
        private const val TOPIC = "44444444-4444-4444-8444-444444444444"
        private const val OWNER = "11111111-1111-4111-8111-111111111111"
        private const val CONFIRMED = "55555555-5555-4555-8555-555555555555"
    }
}
