/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.genesiscorporation.workspace.beta.modules.chatchannels

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.HomeFlow
import ru.genesiscorporation.workspace.beta.LocalBottomBarVisible
import ru.genesiscorporation.workspace.beta.data.remote.dto.*
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import ru.genesiscorporation.workspace.beta.fixtures.WorkspaceTestAccount
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsScreen
import ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel
import ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsScreen
import ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel
import ru.genesiscorporation.workspace.beta.modules.homementions.HomeMentionsScreen
import ru.genesiscorporation.workspace.beta.modules.homementions.HomeMentionsViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.File

/** The production row produces the route; the dialog consumes its decoded arguments. */
@RunWith(AndroidJUnit4::class)
class ChatTopicNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var chats: ChatViewModel
    private lateinit var inbox: HomeInboundsViewModel
    private lateinit var drafts: HomeDraftsViewModel
    private lateinit var mentions: HomeMentionsViewModel
    private enum class Entry { CHATS, INBOX, DRAFTS, MENTIONS }
    private val dialogs = mutableListOf<ChatDialogViewModel>()
    private val streamId = "00000000-0000-4000-8000-000000000090"
    private val topicId = "00000000-0000-4000-8001-000000000090"
    private val messageId = "00000000-0000-4000-8002-000000000090"
    private val viewerId = "00000000-0000-4000-8000-000000000057"
    private val authorId = "00000000-0000-4000-8000-000000000058"
    private val topicName = "Navigation topic"
    private val streamName = "Stored stream title"
    private val json = Json { encodeDefaults = true }

    @After fun close() {
        if (::chats.isInitialized) chats.viewModelScope.cancel()
        if (::inbox.isInitialized) inbox.viewModelScope.cancel()
        if (::drafts.isInitialized) drafts.viewModelScope.cancel()
        if (::mentions.isInitialized) mentions.viewModelScope.cancel()
        dialogs.forEach { it.viewModelScope.cancel() }
        if (::account.isInitialized) runBlocking { account.close() }
        if (::server.isInitialized) server.close()
    }

    @Test fun chatsPrivateGroupWithNullCounterpartKeepsGroupPresentation() = open(isPrivate = true)
    @Test fun chatsPublicGroupKeepsGroupPresentation() = open(isPrivate = false)
    @Test fun chatsDirectConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = authorId)
    @Test fun chatsSelfConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = viewerId)
    @Test fun chatsEncryptedPrivateGroupKeepsAvatarAndTopicMarker() = open(isPrivate = true, encrypted = true)
    @Test fun inboxPrivateGroupWithNullCounterpartKeepsGroupPresentation() = open(isPrivate = true, entry = Entry.INBOX)
    @Test fun inboxPublicGroupKeepsGroupPresentation() = open(isPrivate = false, entry = Entry.INBOX)
    @Test fun inboxDirectConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = authorId, entry = Entry.INBOX)
    @Test fun inboxSelfConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = viewerId, entry = Entry.INBOX)
    @Test fun inboxEncryptedPrivateGroupKeepsAvatarAndTopicMarker() = open(isPrivate = true, encrypted = true, entry = Entry.INBOX)

    @Test fun draftsPrivateGroupWithNullCounterpartKeepsGroupPresentation() = open(isPrivate = true, entry = Entry.DRAFTS)
    @Test fun draftsPublicGroupKeepsGroupPresentation() = open(isPrivate = false, entry = Entry.DRAFTS)
    @Test fun draftsDirectConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = authorId, entry = Entry.DRAFTS)
    @Test fun draftsSelfConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = viewerId, entry = Entry.DRAFTS)
    @Test fun mentionsPrivateGroupWithNullCounterpartKeepsGroupPresentation() = open(isPrivate = true, entry = Entry.MENTIONS)
    @Test fun mentionsPublicGroupKeepsGroupPresentation() = open(isPrivate = false, entry = Entry.MENTIONS)
    @Test fun mentionsDirectConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = authorId, entry = Entry.MENTIONS)
    @Test fun mentionsSelfConversationKeepsDirectPresentation() = open(isPrivate = true, counterpart = viewerId, entry = Entry.MENTIONS)
    @Test fun draftsEncryptedPrivateGroupKeepsAvatarAndTopicMarker() = open(isPrivate = true, encrypted = true, entry = Entry.DRAFTS)

    private fun open(isPrivate: Boolean, counterpart: String? = null, encrypted: Boolean = false, entry: Entry = Entry.CHATS) {
        val viewer = UserResponseData(username = "Viewer", uuid = viewerId, status = "active", avatar = "")
        val author = UserResponseData(firstName = "Alice", lastName = "Example", username = "alice",
            uuid = authorId, status = "active", avatar = "")
        val stream = Stream(streamId, 1, 1, 0, "2026-10-17T10:00:00Z", streamName, isPrivate, 0,
            defaultTopicUuid = topicId, directUserUuid = counterpart, notificationMode = "all_messages",
            encryption = encrypted, ownerUuid = authorId, role = "member")
        val topic = TopicsResponseData(topicId, topicName, 0, streamId, "2026-10-17T10:00:00Z",
            1, 1, false, true, notificationMode = "default")
        val self = counterpart == viewerId
        val message = MessageResponse(messageId, "2026-10-17T10:00:00Z", "2026-10-17T10:00:00Z",
            streamId, topicId, viewerId, if (self) viewerId else authorId,
            MessageResponsePayload(if (encrypted) "e2ee" else "markdown", "Synthetic navigation message",
                keyUuid = if (encrypted) "00000000-0000-4000-8003-000000000090" else null),
            self, emptyMap(), false)
        server = LoopbackWorkspaceServer { request ->
            // Stop the initial-load chain before its long-lived event connection.
            if ("/message_reactions/" in request.path)
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", 503)
            val body = when {
                "/me/" in request.path -> json.encodeToString(viewer)
                "/stream_bindings/" in request.path -> json.encodeToString(
                    if (self) listOf(StreamBindingResponseData("viewer", "owner", streamId, viewerId))
                    else listOf(StreamBindingResponseData("viewer", "member", streamId, viewerId),
                        StreamBindingResponseData("author", "owner", streamId, authorId)))
                "/stream_topics/" in request.path -> json.encodeToString(listOf(topic))
                request.path.endsWith("/messages/") -> json.encodeToString(listOf(message))
                "/actions/read" in request.path -> "{}"
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(body)
        }
        runBlocking {
            account = WorkspaceTestAccount.create(server.url)
            account.repo.setInitialStreams(listOf(stream))
            account.repo.loadUserInfo()
            account.repo.setInitialUsers(listOf(viewer, author))
            if (entry == Entry.DRAFTS) account.repo.setInitialDraftsPool(listOf(
                Draft("synthetic-navigation-draft", streamId, topicId, "2026-10-17T10:00:00Z",
                    MessageResponsePayload("markdown", "Synthetic draft"), 1)))
        }
        compose.runOnUiThread {
            when (entry) {
                Entry.CHATS -> chats = ChatViewModel(account.api, account.user, account.events, null, {})
                Entry.INBOX -> inbox = HomeInboundsViewModel(account.api, account.events)
                Entry.DRAFTS -> drafts = HomeDraftsViewModel(account.api, account.events)
                Entry.MENTIONS -> mentions = HomeMentionsViewModel(account.api, account.events)
            }
        }
        compose.setContent {
            WokspaceTheme {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    val nav = rememberNavController()
                    val start: Any = when (entry) {
                        Entry.CHATS -> ChatFlow.ChatList
                        Entry.INBOX -> HomeFlow.HomeInbounds
                        Entry.DRAFTS -> HomeFlow.HomeDrafts
                        Entry.MENTIONS -> HomeFlow.HomeMentions
                    }
                    NavHost(nav, startDestination = start) {
                        composable<ChatFlow.ChatList> { ChatTopic(chats, topic, stream, nav) }
                        composable<HomeFlow.HomeInbounds> { HomeInboundsScreen(inbox, nav) }
                        composable<HomeFlow.HomeDrafts> { HomeDraftsScreen(drafts, nav) }
                        composable<HomeFlow.HomeMentions> { HomeMentionsScreen(mentions, nav) }
                        composable<ChatFlow.ChatDialog> { entry -> dialog(entry.toRoute<ChatFlow.ChatDialog>(), nav) }
                        composable<HomeFlow.ChatDialog> { entry ->
                            val args = entry.toRoute<HomeFlow.ChatDialog>()
                            dialog(ChatFlow.ChatDialog(args.title, args.chatId, args.topicName, args.topicUuid,
                                args.isDirectMessages, args.userId), nav, args.firstUnreadMessageUuid)
                        }
                    }
                }
            }
        }
        if (entry == Entry.INBOX) {
            waitForTag("inbox-topic-$topicId")
            compose.onNodeWithTag("inbox-topic-$topicId").performClick()
        } else if (entry == Entry.MENTIONS) {
            val authorName = if (self) "Viewer" else "Alice Example"
            compose.waitUntil(12_000) { compose.onAllNodesWithText(authorName).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(authorName).performClick()
        } else {
            compose.waitUntil(12_000) { compose.onAllNodesWithText(topicName).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(topicName).performClick()
        }
        waitForTag("chat-message-$messageId")
        val direct = counterpart != null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("navigationScreenshots") == "true") {
            val name = "${entry.name.lowercase()}-${if (self) "self" else if (direct) "direct" else if (isPrivate) "private" else "public"}${if (encrypted) "-encrypted" else ""}"
            val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "navigation-screenshots").apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
        }
        // The old route shows the stored stream name for private groups.
        if (entry != Entry.MENTIONS || direct) {
            compose.onAllNodesWithText(if (!direct) topicName else if (self) "Viewer" else "Alice Example")
                .onFirst().assertIsDisplayed()
        } else {
            // Mentions currently supplies an empty topic name; its topic-resolution bug is separate.
            compose.onNodeWithText(streamName).assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals("Decoded production navigation argument", direct, dialogs.single().isDirectMessages) }
        if (direct) compose.onNodeWithText("A", useUnmergedTree = true).assertDoesNotExist()
        else compose.onNodeWithText("A", useUnmergedTree = true).assertIsDisplayed()
        if (encrypted) compose.onNodeWithText("# $topicName", useUnmergedTree = true).assertIsDisplayed()
    }

    @Composable private fun dialog(args: ChatFlow.ChatDialog, nav: NavHostController, firstUnread: String? = null) {
        val model = remember(args) {
            ChatDialogViewModel(account.api, account.user, args.title, args.chatId, args.topicName, args.topicUuid,
                args.isDirectMessages, account.events, args.userId, AttachmentStorage(compose.activity, account.api), firstUnread)
                .also { dialogs += it }
        }
        ChatDialogScreen(model, nav)
    }

    private fun waitForTag(tag: String) = compose.waitUntil(12_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}
