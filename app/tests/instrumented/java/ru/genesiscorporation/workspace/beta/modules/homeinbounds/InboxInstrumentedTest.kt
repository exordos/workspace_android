package ru.genesiscorporation.workspace.beta.modules.homeinbounds

import ru.genesiscorporation.workspace.beta.data.addStreamTopics
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.HomeFlow
import ru.genesiscorporation.workspace.beta.LocalBottomBarVisible
import ru.genesiscorporation.workspace.beta.data.remote.dto.*
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import ru.genesiscorporation.workspace.beta.fixtures.WorkspaceTestAccount
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatTopic
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class InboxInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var inbox: HomeInboundsViewModel
    private val dialogs = mutableListOf<ChatDialogViewModel>()
    private val previews = mutableListOf<ChatViewModel>()
    private val showPreview = mutableStateOf(false)
    private val reads = CopyOnWriteArrayList<String>()
    private val topicRequests = AtomicInteger()
    @Volatile private var topics = emptyList<TopicsResponseData>()
    @Volatile private var messages = emptyList<MessageResponse>()
    @Volatile private var messagesStatus = 200
    @Volatile private var beforeTopicsResponse: (Int) -> Unit = {}
    private val streamId = "00000000-0000-4000-8000-000000000001"
    private val viewerId = "00000000-0000-4000-8000-000000000057"
    private val authorId = "00000000-0000-4000-8000-000000000058"
    private val topicId = "00000000-0000-4000-8000-000000000002"
    private val json = Json { encodeDefaults = true }

    @After fun close() {
        if (::inbox.isInitialized) inbox.viewModelScope.cancel()
        dialogs.forEach { it.viewModelScope.cancel() }
        previews.forEach { it.viewModelScope.cancel() }
        if (::account.isInitialized) runBlocking { account.close() }
        if (::server.isInitialized) server.close()
    }

    private fun setup(streams: List<Stream>, cached: List<MessageResponse> = emptyList()) = runBlocking {
        val viewer = UserResponseData(username = "fixture", uuid = viewerId, status = "active", avatar = "")
        server = LoopbackWorkspaceServer { request ->
            if ("/message_reactions/" in request.path) return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", 503)
            if (request.path.endsWith("/messages/") && messagesStatus != 200)
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", messagesStatus)
            val response = when {
                "/me/" in request.path -> json.encodeToString(viewer)
                "/stream_bindings/" in request.path -> json.encodeToString(listOf(
                    StreamBindingResponseData("member", "member", streamId, viewerId),
                    StreamBindingResponseData("owner", "owner", streamId, authorId)))
                "/stream_topics/" in request.path -> {
                    val requestNumber = topicRequests.incrementAndGet()
                    val response = json.encodeToString(topics)
                    beforeTopicsResponse(requestNumber)
                    response
                }
                request.path.endsWith("/messages/") -> json.encodeToString(messages)
                "/actions/read" in request.path -> { reads += request.path; "{}" }
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(response)
        }
        account = WorkspaceTestAccount.create(server.url)
        account.repo.setInitialStreams(streams)
        account.repo.loadUserInfo()
        account.repo.setInitialUsers(listOf(viewer,
            UserResponseData(username = "Preview author", uuid = authorId, status = "active", avatar = "")))
        if (cached.isNotEmpty()) account.repo.addStreamTopicMessages(streamId, topicId, cached)
        compose.runOnUiThread { inbox = HomeInboundsViewModel(account.api, account.events) }
        compose.setContent {
            WokspaceTheme {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    val nav = rememberNavController()
                    if (showPreview.value) {
                        val cachedTopics by account.repo.streamTopics.collectAsStateWithLifecycle()
                        val preview = remember {
                            ChatViewModel(account.api, account.user, account.events, null, {}).also { previews += it }
                        }
                        ChatTopic(preview, cachedTopics.getValue(streamId).single(), stream(), nav)
                    } else NavHost(nav, HomeFlow.HomeInbounds) {
                        composable<HomeFlow.HomeInbounds> { HomeInboundsScreen(inbox, nav) }
                        composable<HomeFlow.ChatDialog> { entry ->
                            val args = entry.toRoute<HomeFlow.ChatDialog>()
                            val model = remember(entry) {
                                ChatDialogViewModel(account.api, account.user, args.title, args.chatId,
                                    args.topicName, args.topicUuid, args.isDirectMessages, account.events,
                                    null, AttachmentStorage(compose.activity, account.api), args.firstUnreadMessageUuid)
                                    .also { dialogs += it }
                            }
                            ChatDialogScreen(model, nav)
                        }
                    }
                }
            }
        }
    }

    @Test fun topicsAppearAfterEmptyCacheAndStreamUpdatesWithCountsAndNoMutedRows() {
        setup(emptyList())
        val ordinary = stream()
        val secret = stream().copy(uuid = "secret", name = "Secret", encryption = true)
        val muted = stream().copy(uuid = "muted", name = "Muted", notificationMode = "muted", activeUnreadCount = 0)
        topics = listOf(topic().copy(unreadCount = 10, activeUnreadCount = 7),
            topic().copy(uuid = "secret-topic", streamUuid = "secret", name = "Secret topic", unreadCount = 4, activeUnreadCount = 4),
            topic().copy(uuid = "muted-topic", streamUuid = "muted", name = "Hidden muted stream topic"),
            topic().copy(uuid = "muted-child", name = "Hidden muted topic", notificationMode = "mute"),
            topic().copy(uuid = "read-child", name = "Fully read topic", unreadCount = 0, activeUnreadCount = 0),
            topic().copy(uuid = "passive-child", name = "Only passive unread", unreadCount = 8, activeUnreadCount = 0))
        // Reproduce the empty-topic cache which used to be treated as fully loaded forever.
        account.repo.addStreamTopics(ordinary.uuid, emptyList())
        compose.runOnIdle { account.repo.setInitialStreams(listOf(ordinary, secret, muted)) }
        waitForTag("inbox-topic-$topicId")
        compose.onNodeWithTag("inbox-count-$topicId", useUnmergedTree = true).assertTextEquals("7")
        compose.onNodeWithTag("inbox-topic-secret-topic").assertIsDisplayed()
        compose.onNodeWithTag("inbox-count-secret-topic", useUnmergedTree = true).assertTextEquals("4")
        compose.onNodeWithText("Muted").assertDoesNotExist()
        compose.onNodeWithText("Hidden muted topic").assertDoesNotExist()
        compose.onNodeWithText("Fully read topic").assertDoesNotExist()
        compose.onNodeWithText("Only passive unread").assertDoesNotExist()
        val previous = topicRequests.get()
        topics = topics.map { if (it.uuid == topicId) it.copy(unreadCount = 12, activeUnreadCount = 9) else it }
        compose.runOnIdle { account.repo.updateStream(ordinary.copy(unreadCount = 12, activeUnreadCount = 9)) }
        compose.waitUntil(8_000) { topicRequests.get() > previous }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("inbox-count-$topicId", useUnmergedTree = true).fetchSemanticsNodes().any {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { text -> text.text == "9" }
        } }
        compose.runOnIdle { account.repo.updateStream(ordinary.copy(notificationMode = "muted", activeUnreadCount = 0)) }
        compose.onNodeWithTag("inbox-topic-$topicId").assertDoesNotExist()
    }

    @Test fun topicOpensFirstUnreadAndDoesNotMarkOrScrollToTheLatestMessage() = openFirstUnread(false)

    @Test fun counterBurstsFinishTheCurrentRequestAndConflateThePendingRefresh() {
        val firstResponse = CountDownLatch(1)
        val secondResponse = CountDownLatch(1)
        beforeTopicsResponse = { number ->
            check(firstResponse.await(15, TimeUnit.SECONDS))
            if (number == 2) check(secondResponse.await(15, TimeUnit.SECONDS))
        }
        topics = listOf(topic())
        setup(listOf(stream()))
        try {
            compose.waitUntil(8_000) { topicRequests.get() == 1 }
            repeat(24) { index ->
                val count = 41 + index
                topics = listOf(topic().copy(unreadCount = count, activeUnreadCount = count))
                compose.runOnIdle { account.repo.updateStream(stream().copy(unreadCount = count, activeUnreadCount = count)) }
                Thread.sleep(120) // Incoming counters keep changing before the topic response arrives.
            }
            assertEquals("Counter updates must not cancel the in-flight request", 1, topicRequests.get())
            firstResponse.countDown()
            waitForTag("inbox-topic-$topicId")
            compose.onNodeWithTag("inbox-count-$topicId", useUnmergedTree = true).assertTextEquals("40")
            compose.waitUntil(8_000) { topicRequests.get() == 2 }
            secondResponse.countDown()
            compose.waitUntil(8_000) {
                account.repo.streamTopics.value[streamId]?.singleOrNull()?.activeUnreadCount == 64
            }
            compose.onNodeWithTag("inbox-count-$topicId", useUnmergedTree = true).assertTextEquals("64")
            assertEquals("The pending burst needs just one refresh", 2, topicRequests.get())
        } finally {
            firstResponse.countDown()
            secondResponse.countDown()
        }
    }
    @Test fun encryptedTopicOpensFirstUnreadWithoutAKeyAndKeepsItsPosition() = openFirstUnread(true)

    @Test fun mentionsOnlyOpensTheFirstUnreadMentionInsteadOfEarlierPassiveUnread() = openFirstUnread(false, true)

    @Test fun inboxRefreshPreservesAndRestoresChatTopicPreviewsWithoutKeepingAnOldMessage() {
        setup(emptyList())
        fun assertPreview(text: String) {
            // MarkdownText uses a native TextView, outside the Compose semantics tree.
            compose.waitUntil(5_000) {
                runCatching { onView(withText(text)).check(matches(isCompletelyDisplayed())) }.isSuccess
            }
            compose.onNodeWithText("Preview author").assertIsDisplayed()
        }
        val author = account.repo.users.value.first { it.uuid == authorId }
        val previous = message(48, false).copy(payload = MessageResponsePayload("markdown", "Cached preview"), user = author)
        topics = listOf(topic().copy(lastMessageUuid = previous.uuid, activeUnreadCount = 9))
        messagesStatus = 503
        compose.runOnIdle {
            account.repo.addStreamTopics(streamId, listOf(topics.single().copy(activeUnreadCount = 7, lastMessage = previous)))
            showPreview.value = true
        }
        assertPreview("Cached preview")

        fun refreshAndShowPreview(count: Int) {
            compose.runOnIdle {
                showPreview.value = false
                account.repo.updateStream(stream().copy(activeUnreadCount = count))
            }
            compose.waitUntil(8_000) {
                compose.onAllNodesWithTag("inbox-count-$topicId", useUnmergedTree = true).fetchSemanticsNodes().any {
                    it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                        .any { text -> text.text == "$count" }
                }
            }
            compose.runOnIdle { showPreview.value = true }
        }
        compose.runOnIdle { account.repo.setInitialStreams(listOf(stream().copy(activeUnreadCount = 7))) }
        refreshAndShowPreview(9)
        assertPreview("Cached preview")

        val latest = message(49, false).copy(payload = MessageResponsePayload("markdown", "Fresh preview"))
        messages = listOf(latest)
        messagesStatus = 200
        topics = listOf(topic().copy(lastMessageUuid = latest.uuid, activeUnreadCount = 11))
        refreshAndShowPreview(11)
        assertPreview("Fresh preview")
        onView(withText("Cached preview")).check(doesNotExist())

        // A changed UUID must not retain either the old topic preview or a pool entry.
        messagesStatus = 503
        topics = listOf(topic().copy(lastMessageUuid = message(50, false).uuid, activeUnreadCount = 12))
        refreshAndShowPreview(12)
        compose.onNodeWithText("Preview author").assertDoesNotExist()
        onView(withText("Fresh preview")).check(doesNotExist())
    }

    private fun openFirstUnread(encrypted: Boolean, mentionsOnly: Boolean = false) {
        topics = listOf(topic().copy(activeUnreadCount = if (mentionsOnly) 1 else 40))
        messages = (0 until 50).map { index -> message(index, encrypted).copy(mentioned = mentionsOnly && index == 20) }
        // A non-empty truncated cache must not replace the fresh unread history.
        setup(listOf(stream().copy(encryption = encrypted, notificationMode = if (mentionsOnly) "mentions_only" else "all_messages",
            currentEncryptionKeyUuid = if (encrypted) "00000000-0000-4000-8000-000000000003" else null)),
            cached = messages.takeLast(3))
        waitForTag("inbox-topic-$topicId")
        compose.onNodeWithTag("inbox-count-$topicId", useUnmergedTree = true).assertTextEquals(if (mentionsOnly) "1" else "40")
        compose.onNodeWithTag("inbox-topic-$topicId").performClick()
        val target = if (mentionsOnly) 20 else 10
        waitForTag("chat-message-${message(target, encrypted).uuid}")
        if (encrypted) compose.waitUntil(8_000) {
            dialogs.lastOrNull()?.encryptedPresentations?.value?.get(message(target, true).uuid) is
                ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatPresentation.Locked
        }
        compose.onNodeWithTag("chat-message-${message(target, encrypted).uuid}").assertIsDisplayed()
        compose.onNodeWithTag("chat-message-${message(49, encrypted).uuid}").assertDoesNotExist()
        compose.waitUntil(8_000) { reads.isNotEmpty() }
        assertTrue(reads.none { "read_up_to" in it || message(49, encrypted).uuid in it })
        compose.runOnIdle { account.repo.addMessageToStreamTopic(message(50, encrypted)) }
        compose.onNodeWithTag("chat-message-${message(target, encrypted).uuid}").assertIsDisplayed()
        compose.onNodeWithTag("chat-message-${message(50, encrypted).uuid}").assertDoesNotExist()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(12_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun stream() = Stream(streamId, 40, 40, 0, "2026-10-16T10:00:00Z", "Ordinary", false, 0,
        defaultTopicUuid = topicId, notificationMode = "all_messages", ownerUuid = authorId, role = "member")
    private fun topic() = TopicsResponseData(topicId, "Unread topic", 0, streamId, "2026-10-16T10:00:00Z",
        40, 40, false, true, notificationMode = "default")
    private fun message(index: Int, encrypted: Boolean): MessageResponse {
        val time = "2026-10-16T10:00:${index.toString().padStart(2, '0')}Z"
        return MessageResponse("00000000-0000-4000-8001-${index.toString().padStart(12, '0')}", time, time,
            streamId, topicId, viewerId, authorId, MessageResponsePayload(if (encrypted) "e2ee" else "markdown",
                "Synthetic message $index\n\nMore text for a visible conversation row.", keyUuid = if (encrypted) "00000000-0000-4000-8000-000000000003" else null),
            false, emptyMap(), index < 10)
    }
}
