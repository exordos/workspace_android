package ru.genesiscorporation.workspace.beta.modules.homementions

import ru.genesiscorporation.workspace.beta.data.appendItemsToTopicsPool
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
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
import ru.genesiscorporation.workspace.beta.modules.chatdialog.*
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.File
import java.util.UUID
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.TokenPair
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Clicks the real Mentions row and feeds decoded production route arguments to the real dialog. */
@RunWith(AndroidJUnit4::class)
class MentionNavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var mentions: HomeMentionsViewModel
    private val dialogs = mutableListOf<ChatDialogViewModel>()
    private val topicProducers = mutableListOf<androidx.lifecycle.ViewModel>()
    @Volatile private var ownerStatus = 200
    @Volatile private var delayTopicMessages = false
    private var expireTopicMessages = false
    private val topicMessagesEntered = CountDownLatch(1)
    private val releaseTopicMessages = CountDownLatch(1)
    private val lateProducerSuccesses = AtomicInteger()
    private val requests = CopyOnWriteArrayList<LoopbackWorkspaceServer.Request>()
    private var decoded: HomeFlow.ChatDialog? = null
    private var secondaryId: String? = null
    private val releaseOwnerCheck = CountDownLatch(1)
    @Volatile private var delayOwnerCheck = false
    private val releaseMessages = CountDownLatch(1)
    private var messagesEntered = CountDownLatch(1)
    private val releaseTopics = CountDownLatch(1)
    private var topicsEntered = CountDownLatch(1)
    @Volatile private var topicStatus = 200
    @Volatile private var mentionedStatus = 200
    @Volatile private var topicRows = emptyList<TopicsResponseData>()
    @Volatile private var delayTopics = false
    @Volatile private var delayMessages = false
    private val unauthorizedResponses = AtomicInteger()
    @Volatile private var expireMessages = false
    @Volatile private var expireTopics = false
    @Volatile private var rejectRefreshedCredential = false
    private var refreshStatus = 200
    private var refreshOwnerReplacement = false
    private var workspaceBootstrapAfterRefresh = false
    private var replacementSharesTopicId = false
    private var replacementTopicStatus = 200
    private var mixedTopics = false
    private var distinctReplacementDirectory = false
    private var replacementHasDifferentStream = false
    private var directoryHasLastMessage = false
    private var replacementEpochStatus = 200
    private val successfulEpochRequests = CopyOnWriteArrayList<Pair<LoopbackWorkspaceServer.Request, Int>>()
    private val epochVersions = AtomicInteger()
    private var sharedBootstrap = false
    private var projectLogin = false
    private var directoryFolders = false
    private var draftRows = false
    private var replacementDirectoryStatus = 200
    private var expireDirectoryPath: String? = null
    private var delayedDirectoryPath: String? = null
    private val directoryEntered = CountDownLatch(1)
    private val releaseDirectory = CountDownLatch(1)
    private val lateDirectorySuccesses = AtomicInteger()
    private val replacementStreamId = "00000000-0000-4000-8000-000000000095"
    private val json = Json { encodeDefaults = true }
    private val viewerId = "00000000-0000-4000-8000-000000000057"
    private val authorId = "00000000-0000-4000-8000-000000000058"
    private val streamId = "00000000-0000-4000-8000-000000000092"
    private val defaultId = "00000000-0000-4000-8001-000000000092"
    private val otherId = "00000000-0000-4000-8001-000000000093"
    private val replacementTopicId = "00000000-0000-4000-8001-000000000094"
    private val messageId = "00000000-0000-4000-8002-000000000092"
    private val time = "2026-10-18T10:00:00Z"
    private val body = "Mention outside default"
    private var rowLabel = "Alice Example"

    @After fun close() {
        releaseDirectory.countDown()
        releaseTopicMessages.countDown()
        releaseTopics.countDown()
        releaseMessages.countDown()
        releaseOwnerCheck.countDown()
        if (::mentions.isInitialized) mentions.viewModelScope.cancel()
        dialogs.forEach { it.viewModelScope.cancel() }
        topicProducers.forEach { it.viewModelScope.cancel() }
        if (::account.isInitialized) runBlocking {
            secondaryId?.let { account.preferences.removeServer(it) }
            account.close()
        }
        if (::server.isInitialized) server.close()
    }

    @Test fun publicNonDefaultCold() = checkOpen()
    @Test fun publicNonDefaultWithDefaultHistoryCached() = checkOpen(cacheDefault = true)
    @Test fun publicDefault() = checkOpen(default = true)
    @Test fun privateNonDefault() = checkOpen(private = true, counterpart = authorId)
    @Test fun privateDefault() = checkOpen(private = true, counterpart = authorId, default = true)
    @Test fun directKeepsCounterpartHeader() = checkOpen(private = true, counterpart = authorId, default = true)
    @Test fun selfKeepsViewerHeader() = checkOpen(private = true, counterpart = viewerId, default = true)
    @Test fun cachedTopicNameAvoidsLookup() = checkOpen(cacheTopic = true)

    private fun topic(id: String) = TopicsResponseData(id,
        if (id == defaultId) "Default topic" else "Mention topic", 0, streamId, time,
        1, 1, false, id == defaultId, notificationMode = "default")

    private fun setup(private: Boolean = false, counterpart: String? = null, default: Boolean = false,
                      cacheDefault: Boolean = false, cacheTopic: Boolean = false, replaceBeforeModel: Boolean = false, showMentions: Boolean = true, showInbox: Boolean = false, showDrafts: Boolean = false) {
        val selectedId = if (default) defaultId else otherId
        rowLabel = if (counterpart == viewerId) "Viewer" else "Alice Example"
        val viewer = UserResponseData(username = "Viewer", uuid = viewerId, status = "active", avatar = "")
        val author = UserResponseData(firstName = "Alice", lastName = "Example", username = "alice",
            uuid = authorId, status = "active", avatar = "")
        val stream = Stream(streamId, 1, 1, 0, time, "Stored stream title", private, 0,
            defaultTopicUuid = defaultId, directUserUuid = counterpart, notificationMode = "all_messages",
            lastMessageUuid = if (directoryHasLastMessage) messageId else null,
            ownerUuid = authorId, role = "member")
        val message = MessageResponse(messageId, time, time, streamId, selectedId, viewerId,
            if (counterpart == viewerId) viewerId else authorId, MessageResponsePayload("markdown", body),
            counterpart == viewerId, emptyMap(), false)
        val cachedMention = message.copy(uuid = "cached-mention", topicUuid = defaultId,
            authorUuid = viewerId, isOwn = true, payload = MessageResponsePayload("markdown", "Cached mention stays usable"))
        val control = message.copy(uuid = "default-control", topicUuid = defaultId,
            payload = MessageResponsePayload("markdown", "Wrong default history"))
        if (topicRows.isEmpty()) topicRows = listOf(topic(selectedId))
        server = LoopbackWorkspaceServer { request ->
            requests += request
            if (request.path.endsWith("/actions/get_token/invoke")) {
                if (projectLogin && "project:" in request.body)
                    return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("""{"access_token":"cassi-fixture-project","refresh_token":"cassi-fixture-project-refresh"}""")
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response(
                    """{"access_token":"${if (refreshOwnerReplacement) "cassi-fixture-replacement" else "cassi-fixture-refreshed"}","refresh_token":"cassi-fixture-refreshed-refresh"}""", refreshStatus)
            }
            val credential = request.headers["authorization"]
            val refreshed = credential == "Bearer cassi-fixture-refreshed"
            if ((expireMessages && request.query["mentioned"] == listOf("true") ||
                    expireTopics && "/stream_topics/" in request.path ||
                    expireTopicMessages && request.path.endsWith("/messages/") && request.query["uuid"] == listOf(messageId)) &&
                (credential == "Bearer cassi-fixture-0" || refreshed && rejectRefreshedCredential)) {
                unauthorizedResponses.incrementAndGet()
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", 401)
            }
            val replacementRequest = credential == "Bearer cassi-fixture-replacement"
            if ("/epoch/" in request.path) {
                if (replacementRequest && replacementEpochStatus != 200) return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", replacementEpochStatus)
                val version = epochVersions.incrementAndGet()
                if (replacementRequest) successfulEpochRequests += request to version
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("""{"epoch_version":$version,"epoch_generation":"fixture"}""")
            }
            val visibleMessage = if (replacementRequest) message.copy(uuid = "replacement-mention", streamUuid = if (replacementHasDifferentStream) replacementStreamId else streamId, topicUuid = if (replacementSharesTopicId) otherId else replacementTopicId,
                userUuid = authorId, isOwn = true,
                payload = MessageResponsePayload("markdown", "Replacement account mention")) else message
            if (request.path.endsWith("/users/") || request.path.endsWith("/streams/") || request.path.endsWith("/folders/")) {
                if (expireDirectoryPath?.let { request.path.endsWith(it) } == true && credential == "Bearer cassi-fixture-0") {
                    unauthorizedResponses.incrementAndGet()
                    return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", 401)
                }
                val held = delayedDirectoryPath?.let { request.path.endsWith(it) } == true && !replacementRequest
                if (held) { directoryEntered.countDown(); check(releaseDirectory.await(7, TimeUnit.SECONDS)) }
                if (replacementRequest && replacementDirectoryStatus != 200)
                    return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", replacementDirectoryStatus)
                val replacementAuthor = if (distinctReplacementDirectory) author.copy(firstName = "Replacement", lastName = "Author", avatar = "replacement-avatar.png") else author
                val freshStream = stream.copy(uuid = if (replacementHasDifferentStream) replacementStreamId else streamId,
                    name = if (distinctReplacementDirectory) "Replacement stream title" else stream.name)
                val directory = when {
                    request.path.endsWith("/users/") -> json.encodeToString(if (replacementRequest) listOf(replacementAuthor) else listOf(viewer, author))
                    request.path.endsWith("/folders/") -> json.encodeToString(if (directoryFolders) listOf(directoryFolder(replacementRequest)) else emptyList())
                    else -> json.encodeToString(listOf(if (replacementRequest) freshStream else stream))
                }
                if (held) lateDirectorySuccesses.incrementAndGet()
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response(directory)
            }
            if ("/message_reactions/" in request.path)
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response(
                    if ((workspaceBootstrapAfterRefresh && refreshed || sharedBootstrap && replacementRequest || projectLogin && credential == "Bearer cassi-fixture-project")) "[]" else "{}",
                    if ((workspaceBootstrapAfterRefresh && refreshed || sharedBootstrap && replacementRequest || projectLogin && credential == "Bearer cassi-fixture-project")) 200 else 503)
            if ("/stream_topics/" in request.path) {
                topicsEntered.countDown()
                val held = delayTopics && !replacementRequest
                if (held) check(releaseTopics.await(7, TimeUnit.SECONDS))
                val responseTopics = if (replacementRequest) topicRows.map { it.copy(uuid = if (replacementSharesTopicId) otherId else replacementTopicId, name = "Replacement mention topic", streamUuid = if (replacementHasDifferentStream) replacementStreamId else streamId) } else topicRows
                val responseStatus = if (replacementRequest) replacementTopicStatus else topicStatus
                if (held && responseStatus == 200) lateProducerSuccesses.incrementAndGet()
                if (responseStatus == 401) unauthorizedResponses.incrementAndGet()
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response(json.encodeToString(responseTopics), responseStatus)
            }
            if (request.path.endsWith("/messages/") && request.query["mentioned"] == listOf("true")) {
                messagesEntered.countDown()
                if (delayMessages && !replacementRequest) check(releaseMessages.await(7, TimeUnit.SECONDS))
                if (!replacementRequest && mentionedStatus != 200) {
                    if (mentionedStatus == 401) unauthorizedResponses.incrementAndGet()
                    return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", mentionedStatus)
                }
            }
            if (request.path.endsWith("/messages/") && request.query["uuid"] == listOf(messageId) && delayTopicMessages && !replacementRequest) {
                topicMessagesEntered.countDown()
                check(releaseTopicMessages.await(7, TimeUnit.SECONDS))
                lateProducerSuccesses.incrementAndGet()
            }
            if ("/me/" in request.path && delayOwnerCheck) check(releaseOwnerCheck.await(7, TimeUnit.SECONDS))
            if ("/me/" in request.path && ownerStatus != 200)
                return@LoopbackWorkspaceServer LoopbackWorkspaceServer.Response("{}", ownerStatus)
            val response = when {
                "/server_settings/" in request.path -> """{"email_auth_enabled":false,"realm_name":"Fixture","realm_url":"fixture","realm_icon":"","meet_url":""}"""
                "/me/" in request.path -> json.encodeToString(if (replacementRequest) author else viewer)
                "/stream_bindings/" in request.path -> json.encodeToString(listOf(
                    StreamBindingResponseData("viewer", "member", streamId, viewerId),
                    StreamBindingResponseData("author", "owner", streamId, authorId)))
                request.path.endsWith("/messages/") -> json.encodeToString(when {
                    mixedTopics && !replacementRequest && request.query["mentioned"] == listOf("true") -> listOf(message, cachedMention)
                    mixedTopics && request.query["topic_uuid"] == listOf(defaultId) -> listOf(cachedMention)
                    request.query["topic_uuid"] == listOf(defaultId) && !default -> listOf(control)
                    else -> listOf(visibleMessage)
                })
                request.path.endsWith("/drafts/") -> json.encodeToString(if (draftRows) listOf(Draft(
                    if (replacementRequest) "replacement-draft" else "old-draft", streamId,
                    if (replacementRequest) replacementTopicId else otherId, time,
                    MessageResponsePayload("markdown", if (replacementRequest) "New owner's draft" else "Old owner's draft"), 1)) else emptyList())
                "/actions/read" in request.path -> "{}"
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(response)
        }
        runBlocking {
            account = WorkspaceTestAccount.create(server.url, enableWebSockets = !showMentions)
            account.repo.setInitialStreams(listOf(stream))
            account.repo.loadUserInfo()
            account.repo.setInitialUsers(listOf(viewer, author))
            if (cacheDefault) account.repo.addStreamTopicMessages(streamId, defaultId, listOf(control))
            if (cacheTopic) account.repo.appendItemsToTopicsPool(listOf(topic(selectedId)))
            if (mixedTopics) account.repo.appendItemsToTopicsPool(listOf(topic(defaultId)))
            if (replaceBeforeModel) {
                assertTrue(account.user.setTokensAndWait("cassi-fixture-replacement", "cassi-fixture-replacement-refresh", account.config.id))
                assertNull("Explicit replacement must hide the previous /me", account.repo.currentUser.value)
            }
        }
        if (!showMentions) {
            lateinit var chat: ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
            compose.runOnUiThread {
                chat = ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel(account.api, account.user, account.events, null, {})
                topicProducers += chat
            }
            compose.waitUntil(5_000) { chat.eventsRepo.value === account.repo }
            account.repo.setInitialFolders(listOf(directoryFolder(false)))
            account.repo.updateCurrentlySelectedFolder(directoryFolder(false))
            lateinit var inbox: ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel
            if (showInbox) compose.runOnUiThread {
                inbox = ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel(account.api, account.events)
                topicProducers += inbox
            }
            lateinit var drafts: ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel
            if (showDrafts) {
                runBlocking { account.repo.loadDrafts(startRealtime = false) }
                compose.runOnUiThread {
                    drafts = ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel(account.api, account.events)
                    topicProducers += drafts
                }
            }
            compose.setContent { WokspaceTheme {
                if (showDrafts) ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsScreen(drafts, rememberNavController())
                else if (showInbox) ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsScreen(inbox, rememberNavController())
                else ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatWithTopics(chat, rememberNavController(), false, {})
            } }
            lateinit var baseline: Job
            compose.runOnUiThread { baseline = chat.viewModelScope.launch { account.repo.loadDrafts() } }
            compose.waitUntil(5_000) { account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
            compose.waitUntil(5_000) { account.repo.epochGeneration.isNotBlank() }
            runBlocking { baseline.cancelAndJoin() }
            if (showDrafts) {
                if (!delayTopics) {
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("Old owner's draft").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Old owner's draft").assertIsDisplayed()
                }
            } else if (showInbox) compose.waitUntil(5_000) { account.repo.streamTopics.value[streamId].orEmpty().isNotEmpty() }
            else compose.onNodeWithText("Stored stream title").assertIsDisplayed()
            return
        }
        compose.runOnUiThread { mentions = HomeMentionsViewModel(account.api, account.events) }
        compose.setContent {
            WokspaceTheme {
                CompositionLocalProvider(LocalBottomBarVisible provides {}) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = HomeFlow.HomeMentions) {
                        composable<HomeFlow.HomeMentions> { HomeMentionsScreen(mentions, nav) }
                        composable<HomeFlow.ChatDialog> { entry ->
                            val args = entry.toRoute<HomeFlow.ChatDialog>()
                            val model = remember(entry) {
                                decoded = args
                                ChatDialogViewModel(account.api, account.user, args.title, args.chatId,
                                    args.topicName, args.topicUuid, args.isDirectMessages, account.events,
                                    args.userId, AttachmentStorage(compose.activity, account.api), args.firstUnreadMessageUuid)
                                    .also { dialogs += it }
                            }
                            ChatDialogScreen(model, nav)
                        }
                    }
                }
            }
        }
    }

    private fun checkOpen(private: Boolean = false, counterpart: String? = null, default: Boolean = false,
                          cacheDefault: Boolean = false, cacheTopic: Boolean = false) {
        setup(private, counterpart, default, cacheDefault, cacheTopic)
        waitForBody()
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        compose.waitUntil(12_000) {
            compose.onAllNodesWithTag("chat-message-$messageId").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("chat-message-default-control").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("route-${if (private) "private" else "public"}-${if (default) "default" else "other"}")
        val selectedId = if (default) defaultId else otherId
        compose.runOnIdle {
            assertEquals("Mention row must select the message's topic", selectedId, decoded!!.topicUuid)
            assertEquals(topic(selectedId).name, decoded!!.topicName)
            assertEquals(streamId, decoded!!.chatId)
        }
        compose.waitUntil(12_000) { compose.onAllNodesWithTag("chat-message-$messageId").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Wrong default history").assertDoesNotExist()
        if (!private || counterpart != null) {
            compose.onAllNodesWithText(when (counterpart) { viewerId -> "Viewer"; authorId -> "Alice Example"; else -> topic(selectedId).name })
                .onFirst().assertIsDisplayed()
        }
        assertTrue("Actual HTTP history must use the selected topic", requests.any {
            it.path.endsWith("/messages/") && it.query["topic_uuid"] == listOf(selectedId) })
        assertFalse("Never fetch the default history for a different mention", !default && requests.any {
            it.path.endsWith("/messages/") && it.query["topic_uuid"] == listOf(defaultId) })
        assertEquals("Warm topic cache avoids extra lookup", if (cacheTopic) 0 else 1,
            requests.count { "/stream_topics/" in it.path && it.query["uuid"] == listOf(selectedId) })
        assertTrue(requests.filter { "/stream_topics/" in it.path }.all { it.headers["authorization"] == "Bearer cassi-fixture-0" })
        screenshot("${if (private) "private" else "public"}-${counterpart?.takeLast(3) ?: "group"}-${if (default) "default" else "other"}-${if (cacheDefault) "cached" else "cold"}")
    }

    @Test fun topicLookupFailureCanRetryWithoutOpeningDefaultHistory() {
        topicStatus = 503
        setup()
        compose.waitUntil(10_000) { mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        assertNull(decoded)
        topicStatus = 200
        runBlocking { mentions.loadMentionedMessages() }
        waitForBody()
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
    }

    @Test fun forbiddenTopicNeverFallsBackToCachedDefaultHistory() {
        topicStatus = 403
        setup(cacheDefault = true)
        compose.waitUntil(10_000) { mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        compose.runOnIdle { assertNull(decoded) }
        assertFalse(requests.any { it.query["topic_uuid"] == listOf(defaultId) })
    }

    @Test fun mismatchedStreamTopicCannotOpenAnotherConversation() {
        topicRows = listOf(topic(otherId).copy(streamUuid = "different-stream"))
        setup()
        waitForBody()
        compose.onNodeWithText(rowLabel).performClick()
        compose.runOnIdle { assertNull(decoded) }
        assertTrue(account.repo.topicsPool.value.none { it.streamUuid == "different-stream" })
    }

    @Test fun delayedTopicResponseAfterAccountSwitchDoesNotPopulateOldRepository() {
        delayTopics = true
        setup()
        assertTrue(topicsEntered.await(7, TimeUnit.SECONDS))
        switchAccount()
        releaseTopics.countDown()
        compose.waitUntil(10_000) { mentions.messagesQueryState.value == ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Idle }
        compose.runOnIdle { assertNull(decoded); assertTrue(account.repo.topicsPool.value.isEmpty()) }
    }

    @Test fun accountSwitchAfterLoadedRowsDoesNotNavigate() {
        setup()
        waitForBody()
        switchAccount()
        compose.waitUntil(5_000) { mentions.messages.value.isEmpty() }
        compose.onNodeWithText(rowLabel).assertDoesNotExist()
        compose.runOnIdle { assertNull(decoded) }
    }

    @Test fun delayedMessagesAfterSameServerLoginDoNotReplaceNewOwnerRows() {
        delayMessages = true
        setup()
        assertTrue(messagesEntered.await(7, TimeUnit.SECONDS))
        replaceLogin()
        waitForBody("Replacement account mention")
        releaseMessages.countDown()
        runBlocking { mentions.loadMentionedMessages() }
        assertEquals("replacement-mention", mentions.messages.value.single().uuid)
        onView(withText(body)).check(doesNotExist())
        assertTrue(mentions.isCurrentAccount())
        assertNull(decoded)
    }

    @Test fun delayedTopicsAfterSameServerLoginDoNotReplaceNewOwnerMetadata() {
        delayTopics = true
        setup()
        assertTrue(topicsEntered.await(7, TimeUnit.SECONDS))
        replaceLogin()
        waitForBody("Replacement account mention")
        releaseTopics.countDown()
        runBlocking { mentions.loadMentionedMessages() }
        assertEquals("Replacement mention topic", account.repo.topicsPool.value.single().name)
        assertEquals("replacement-mention", mentions.messages.value.single().uuid)
        onView(withText(body)).check(doesNotExist())
        assertNull(decoded)
    }

    @Test fun sameServerLoginHidesOldRowsUntilNewOwnerIsVerified() {
        setup()
        waitForBody()
        delayOwnerCheck = true
        try {
            replaceLogin()
            compose.waitUntil(5_000) { mentions.messages.value.isEmpty() }
            onView(withText(body)).check(doesNotExist())
            assertFalse(mentions.isCurrentAccount())
            compose.runOnIdle { assertNull(decoded) }
        } finally { delayOwnerCheck = false; releaseOwnerCheck.countDown() }
        waitForBody("Replacement account mention")
        assertTrue(mentions.isCurrentAccount())
    }

    @Test fun credentialRefreshRetriesOnlyAfterVerifyingTheSameOwner() {
        delayTopics = true
        setup()
        assertTrue(topicsEntered.await(7, TimeUnit.SECONDS))
        runBlocking {
            assertTrue(account.preferences.saveRefreshedTokensIfCurrent(account.config.id,
                "cassi-fixture-refresh", TokenPair("cassi-fixture-refreshed", "cassi-fixture-refreshed-refresh")))
        }
        delayTopics = false
        releaseTopics.countDown()
        waitForBody()
        assertTrue(mentions.isCurrentAccount())
        assertEquals(2, requests.count { "/stream_topics/" in it.path })
        assertEquals(2, requests.count { "/me/" in it.path })
        assertTrue(requests.last { "/stream_topics/" in it.path }.headers["authorization"] == "Bearer cassi-fixture-refreshed")
    }

    @Test fun failedMissingTopicLookupKeepsCachedMentionUsable() {
        mixedTopics = true
        topicStatus = 503
        setup()
        waitForBody()
        compose.runOnIdle { assertTrue(mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success) }
        screenshot("mixed-cached-topic-error")
        compose.onNodeWithText(rowLabel).performClick()
        compose.runOnIdle { assertNull("Missing topic must not open the default", decoded) }
        compose.onNodeWithText("Viewer").performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(defaultId, decoded!!.topicUuid)
        assertEquals("Default topic", decoded!!.topicName)
        compose.waitUntil(12_000) { compose.onAllNodesWithTag("chat-message-cached-mention").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(requests.any { it.query["topic_uuid"] == listOf(defaultId) })
    }

    @Test fun missingTopicCanRetryWhileCachedMentionRemainsVisible() {
        mixedTopics = true
        topicStatus = 503
        setup()
        waitForBody()
        topicStatus = 200
        delayTopics = true
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(ru.genesiscorporation.workspace.beta.R.string.mentions_topic_retry)).performClick()
        compose.waitUntil(5_000) { requests.count { "/stream_topics/" in it.path } == 2 }
        compose.onNodeWithText("Viewer").assertIsDisplayed()
        compose.runOnIdle { assertTrue(mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success) }
        delayTopics = false
        releaseTopics.countDown()
        compose.waitUntil(10_000) { account.repo.topicsPool.value.any { it.uuid == otherId } }
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
        compose.waitUntil(12_000) { compose.onAllNodesWithTag("chat-message-$messageId").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(requests.any { it.query["topic_uuid"] == listOf(otherId) })
        assertFalse(requests.any { it.query["topic_uuid"] == listOf(defaultId) })
    }

    @Test fun loadedBodiesClearWhileScreenStoppedAfterReplacementLogin() {
        setup()
        waitForBody()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        delayOwnerCheck = true
        try {
            replaceLogin()
            runBlocking { withTimeout(5_000) { mentions.messages.first { it.isEmpty() } } }
            assertFalse(mentions.isCurrentAccount())
        } finally {
            delayOwnerCheck = false
            releaseOwnerCheck.countDown()
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
        waitForBody("Replacement account mention")
        onView(withText(body)).check(doesNotExist())
        compose.runOnIdle { assertNull(decoded) }
    }

    @Test fun topicRetryDoesNotReloadMessagesOrHideCachedRows() {
        mixedTopics = true
        topicStatus = 503
        setup()
        waitForBody()
        mentionedStatus = 503
        topicStatus = 200
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(ru.genesiscorporation.workspace.beta.R.string.mentions_topic_retry)).performClick()
        compose.waitUntil(10_000) {
            mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error ||
                account.repo.topicsPool.value.any { it.uuid == otherId }
        }
        compose.onNodeWithText("Viewer").assertIsDisplayed()
        assertEquals("Metadata retry must not repeat the messages request", 1,
            requests.count { it.path.endsWith("/messages/") && it.query["mentioned"] == listOf("true") })
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
    }

    @Test fun sameCredentialReverificationKeepsLoadedMentionsUsable() {
        setup()
        waitForBody()
        runBlocking { account.repo.loadUserInfo() }
        compose.onNodeWithText(rowLabel).assertIsDisplayed()
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
    }

    @Test fun loadedRowsReloadAfterVerifyingRefreshedCredentials() {
        setup()
        waitForBody()
        delayOwnerCheck = true
        runBlocking {
            assertTrue(account.preferences.saveRefreshedTokensIfCurrent(account.config.id,
                "cassi-fixture-refresh", TokenPair("cassi-fixture-refreshed", "cassi-fixture-refreshed-refresh")))
        }
        try {
            compose.waitUntil(5_000) { requests.count { "/me/" in it.path } == 2 }
            assertTrue("Old bodies must stay hidden until the new credential verifies its owner", mentions.messages.value.isEmpty())
        } finally { delayOwnerCheck = false; releaseOwnerCheck.countDown() }
        waitForBody()
        assertTrue(mentions.isCurrentAccount())
        assertEquals(2, requests.count { it.path.endsWith("/messages/") && it.query["mentioned"] == listOf("true") })
        assertEquals("Bearer cassi-fixture-refreshed", requests.last { it.query["mentioned"] == listOf("true") }.headers["authorization"])
    }

    @Test fun originalOrganizationRestoresMentionsAfterSwitchingAwayAndBack() {
        setup()
        waitForBody()
        switchAccount()
        compose.waitUntil(5_000) { mentions.messages.value.isEmpty() }
        runBlocking { account.preferences.setSelectedServerId(account.config.id) }
        compose.waitUntil(5_000) { account.user.selectedServer.value?.id == account.config.id }
        waitForBody()
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
        assertEquals(2, requests.count { it.query["mentioned"] == listOf("true") })
    }

    @Test fun originalOwnerRestoresMentionsAfterReplacementLoginAndReturn() {
        setup()
        waitForBody()
        replaceLogin()
        waitForBody("Replacement account mention")
        assertEquals(authorId, account.repo.currentUser.value?.uuid)
        runBlocking { account.user.setTokensAndWait("cassi-fixture-0", "cassi-fixture-refresh", account.config.id) }
        waitForBody()
        assertTrue(mentions.isCurrentAccount())
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
    }

    @Test fun organizationDisplayNameChangeDoesNotLeaveMentionsBlank() {
        setup()
        waitForBody()
        runBlocking { account.preferences.updateServer(account.config.copy(name = "Renamed organization")) }
        compose.waitUntil(5_000) { account.user.selectedServer.value?.name == "Renamed organization" }
        compose.waitUntil(10_000) { mentions.isCurrentAccount() &&
            mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        waitForBody()
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
    }

    @Test fun firstOpenAfterReplacementLoginUsesOnlyTheVerifiedOwner() {
        setup(replaceBeforeModel = true)
        waitForBody("Replacement account mention")
        assertEquals(authorId, mentions.messages.value.single().userUuid)
        assertEquals("Replacement account mention", mentions.messages.value.single().payload.content)
        assertTrue(mentions.isCurrentAccount())
        assertTrue(requests.filter { it.path.endsWith("/messages/") || "/stream_topics/" in it.path }
            .all { it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(replacementTopicId, decoded!!.topicUuid)
    }

    @Test fun retainedScreenRebindsAfterVerifiedReplacementLogin() =
        checkRetainedReplacement(stopped = false, explicitReload = false)

    @Test fun stoppedScreenRebindsAndExplicitReloadUsesNewOwner() =
        checkRetainedReplacement(stopped = true, explicitReload = true)

    private fun checkRetainedReplacement(stopped: Boolean, explicitReload: Boolean) {
        setup()
        waitForBody()
        val retained = mentions
        if (stopped) compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        delayOwnerCheck = true
        try {
            replaceLogin()
            compose.waitUntil(5_000) { requests.any { "/me/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-replacement" } }
            assertTrue("Old rows must clear before the new owner is verified", mentions.messages.value.isEmpty())
            assertFalse(mentions.isCurrentAccount())
        } finally { delayOwnerCheck = false; releaseOwnerCheck.countDown() }
        compose.waitUntil(5_000) { account.repo.currentUser.value?.uuid == authorId }
        if (stopped) compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForBody("Replacement account mention")
        assertSame(retained, mentions)
        assertTrue(mentions.isCurrentAccount())
        assertEquals(authorId, mentions.messages.value.single().userUuid)
        assertEquals("replacement-mention", mentions.messages.value.single().uuid)
        onView(withText(body)).check(doesNotExist())
        if (explicitReload) {
            val before = requests.count { it.query["mentioned"] == listOf("true") }
            runBlocking { mentions.loadMentionedMessages() }
            assertEquals(before + 1, requests.count { it.query["mentioned"] == listOf("true") })
            assertEquals(authorId, mentions.messages.value.single().userUuid)
        }
        assertEquals("Bearer cassi-fixture-replacement",
            requests.last { it.query["mentioned"] == listOf("true") }.headers["authorization"])
        screenshot("retained-new-owner-rows")
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(replacementTopicId, decoded!!.topicUuid)
        compose.waitUntil(12_000) {
            compose.onAllNodesWithTag("chat-message-replacement-mention").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("retained-new-owner-history")
    }

    @Test fun replacementOwnerGetsFreshStreamAndAuthorForCollidingIds() = checkReplacementDirectory(differentStream = false)
    @Test fun replacementOwnerGetsMentionInItsDifferentStream() = checkReplacementDirectory(differentStream = true)

    private fun checkReplacementDirectory(differentStream: Boolean) {
        distinctReplacementDirectory = true
        replacementHasDifferentStream = differentStream
        setup()
        waitForBody()
        replaceLogin()
        compose.waitUntil(10_000) { mentions.isCurrentAccount() && mentions.messages.value.any { it.uuid == "replacement-mention" } }
        assertEquals("Mention authors must come from the replacement account", "Replacement Author", mentions.messages.value.single().user?.displayableName())
        assertEquals("replacement-avatar.png", mentions.messages.value.single().user?.avatar)
        assertTrue("The replacement account must fetch its own stream directory", account.repo.streams.value.any {
            it.uuid == (if (differentStream) replacementStreamId else streamId) && it.name == "Replacement stream title" })
        rowLabel = "Replacement Author"
        waitForBody("Replacement account mention")
        compose.onNodeWithText("Replacement Author").performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(if (differentStream) replacementStreamId else streamId, decoded!!.chatId)
        assertEquals("Replacement stream title", decoded!!.title)
        screenshot("replacement-fresh-directory")
    }

    @Test fun failedReplacementDirectoryDoesNotExposePreviousAccountMetadata() {
        distinctReplacementDirectory = true
        replacementDirectoryStatus = 503
        setup()
        waitForBody()
        replaceLogin()
        compose.waitUntil(10_000) { mentions.isCurrentAccount() &&
            (mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success ||
                mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error) }
        assertTrue("Replacement must clear old streams before a failed directory reload", account.repo.streams.value.isEmpty())
        assertTrue("Replacement must clear old users before a failed directory reload", account.repo.users.value.isEmpty())
        assertTrue(mentions.messages.value.isEmpty())
        assertTrue(mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error)
    }

    private fun directoryFolder(replacement: Boolean) = FolderResponseData(
        if (replacement) "replacement-folder" else "old-folder", if (replacement) "Replacement folder" else "Old folder",
        0, "custom", time, listOf(FolderItem("fixture-folder-item", if (replacement && replacementHasDifferentStream) replacementStreamId else streamId, "public", 0)))

    @Test fun finalProjectCredentialRestoresChatAfterPreliminaryLogin() = checkFinalProject(different = false)
    @Test fun finalProjectVerificationUsesTheNewProjectConfiguration() = checkFinalProject(different = true)

    private fun checkFinalProject(different: Boolean) {
        projectLogin = true
        setup(showMentions = false)
        val login = ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel(account.api, account.user, false)
        runBlocking { login.onLoginClick() }
        compose.waitUntil(10_000) { account.repo.verifiedOwnerAuthentication.value?.authentication
            ?.matchesAccessToken("cassi-fixture-refreshed") == true &&
            account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        lateinit var projects: ru.genesiscorporation.workspace.beta.modules.projects.ProjectsViewModel
        compose.runOnUiThread {
            projects = ru.genesiscorporation.workspace.beta.modules.projects.ProjectsViewModel(account.api, account.user, false)
            topicProducers += projects
        }
        val target = if (different) "next-project" else "project"
        runBlocking { projects.refreshToken(target) }
        compose.waitUntil(10_000) {
            val live = account.events.get(account.config.id)
            live?.verifiedOwnerAuthentication?.value?.authentication?.matchesAccessToken("cassi-fixture-project") == true &&
                live.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success
        }
        val live = checkNotNull(account.events.get(account.config.id))
        assertEquals(target, live.verifiedOwnerAuthentication.value?.capturedServer?.projectUuid)
        if (different) assertNotSame(account.repo, live)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Stored stream title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Stored stream title").assertIsDisplayed()
        assertTrue(requests.any { "/me/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-project" })
    }

    @Test fun retainedDraftsLoadReplacementOwnersTopics() = checkRetainedDrafts(delayed = false)
    @Test fun retainedDraftsRecoverAfterOldTopicRequestBecomesStale() = checkRetainedDrafts(delayed = true)

    @Test fun draftTopicExpiryKeepsOneSharedRecoveryBudget() {
        draftRows = true
        setup(showMentions = false, showDrafts = true)
        expireTopics = true
        rejectRefreshedCredential = true
        account.repo.setInitialDraftsPool(listOf(Draft("new-topic-draft", streamId, defaultId, time,
            MessageResponsePayload("markdown", "Draft needing another topic"), 1)))
        val model = topicProducers.filterIsInstance<ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel>().single()
        compose.waitUntil(10_000) { model.topicsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        assertEquals("Draft observer must preserve the common one-refresh budget", 1,
            requests.count { it.path.endsWith("/actions/get_token/invoke") })
    }

    private fun checkRetainedDrafts(delayed: Boolean) {
        draftRows = true
        sharedBootstrap = true
        delayTopics = delayed
        setup(showMentions = false, showDrafts = true)
        if (delayed) assertTrue(topicsEntered.await(5, TimeUnit.SECONDS))
        try {
            replaceLogin()
            runBlocking { account.repo.verifyCurrentAccount() }
            releaseTopics.countDown()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("New owner's draft").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("New owner's draft").assertIsDisplayed()
            compose.onNodeWithText("Replacement mention topic").assertIsDisplayed()
            compose.onNodeWithText("Old owner's draft").assertDoesNotExist()
            assertTrue(account.repo.topicsPool.value.none { it.uuid == otherId })
            assertTrue(requests.any { "/stream_topics/" in it.path && it.query["uuid"] == listOf(replacementTopicId) &&
                it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        } finally { releaseTopics.countDown() }
    }

    @Test fun explicitReplacementHidesAllAccountProjectionsBeforeMe() {
        sharedBootstrap = true
        setup(showMentions = false)
        val visible = MessageResponse(messageId, time, time, streamId, otherId, viewerId, authorId,
            MessageResponsePayload("markdown", "Previous owner's body"), false, emptyMap(), false)
        account.repo.setInitialMessagesPool(listOf(visible))
        account.repo.addStreamTopicMessages(streamId, otherId, listOf(visible))
        account.repo.addStreamBindings(streamId, listOf(StreamBindingResponseData("old", "member", streamId, viewerId)))
        delayOwnerCheck = true
        try {
            replaceLogin()
            assertTrue("Explicit replacement must hide old streams before /me", account.repo.streams.value.isEmpty())
            assertTrue(account.repo.users.value.isEmpty())
            assertTrue(account.repo.folders.value.isEmpty())
            assertNull(account.repo.currentlySelectedFolder.value)
            assertTrue(account.repo.messagesPool.value.isEmpty())
            assertTrue(account.repo.streamTopicMessages.value.isEmpty())
            assertTrue(account.repo.draftsPool.value.isEmpty())
            assertTrue(account.repo.userReactions.value.isEmpty())
            assertTrue(account.repo.streamBindings.value.isEmpty())
            assertTrue(account.repo.streamsQueryState.value !is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success)
            compose.onNodeWithText("Stored stream title").assertDoesNotExist()
        } finally { delayOwnerCheck = false; releaseOwnerCheck.countDown() }
        runBlocking { account.repo.verifyCurrentAccount() }
        compose.waitUntil(10_000) { account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        assertEquals(authorId, account.repo.currentUser.value?.uuid)
        assertTrue(account.repo.messagesPool.value.none { it.payload.content == "Previous owner's body" })
    }

    @Test fun loginVerifiesReplacementWithoutAnyWebsocketEvent() {
        sharedBootstrap = true
        refreshOwnerReplacement = true
        distinctReplacementDirectory = true
        setup(showMentions = false)
        val login = ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel(account.api, account.user, false)
        runBlocking { login.onLoginClick() }
        compose.waitUntil(10_000) { account.repo.currentUser.value?.uuid == authorId &&
            account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        compose.onNodeWithText("Replacement stream title").assertIsDisplayed()
        assertFalse(::mentions.isInitialized)
    }

    @Test fun failedSameOwnerProofKeepsInboxHiddenUntilRetry() {
        setup(showMentions = false, showInbox = true)
        ownerStatus = 503
        runBlocking {
            assertTrue(account.user.setTokensAndWait("cassi-fixture-refreshed", "cassi-fixture-refreshed-refresh", account.config.id))
            account.repo.verifyCurrentAccount()
        }
        assertTrue("Failed /me must not release suspended account data", account.repo.streams.value.isEmpty())
        assertTrue(account.repo.streamTopics.value.isEmpty())
        assertTrue(account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error)
        compose.onNodeWithText("Stored stream title").assertDoesNotExist()
        ownerStatus = 200
        runBlocking { account.repo.verifyCurrentAccount() }
        compose.onNodeWithText("Stored stream title").assertIsDisplayed()
    }

    @Test fun sameOwnerReauthenticationRestoresRetainedInboxTopics() {
        setup(showMentions = false, showInbox = true)
        compose.onNodeWithText("Stored stream title").assertIsDisplayed()
        val before = account.repo.streamTopics.value
        runBlocking {
            assertTrue(account.user.setTokensAndWait("cassi-fixture-refreshed", "cassi-fixture-refreshed-refresh", account.config.id))
            account.repo.verifyCurrentAccount()
        }
        compose.waitForIdle()
        assertTrue("Same-owner reauthentication must restore topics for retained consumers",
            account.repo.streamTopics.value[streamId].orEmpty().isNotEmpty())
        compose.onNodeWithText("Stored stream title").assertIsDisplayed()
        assertEquals(before[streamId]?.map { it.uuid }, account.repo.streamTopics.value[streamId]?.map { it.uuid })
    }

    @Test fun realtimeReplacementReloadsChatWithoutOpeningMentions() = checkChatOwnerRecovery(failed = false)
    @Test fun realtimeReplacementShowsRetryAndRecoversChatWithoutMentions() = checkChatOwnerRecovery(failed = true)

    @Test fun replacementCursorFailureShowsRetryAndRecapturesBeforeSnapshots() = checkChatOwnerRecovery(failed = true, cursorFailed = true)

    private fun checkChatOwnerRecovery(failed: Boolean, cursorFailed: Boolean = false) {
        sharedBootstrap = true
        directoryFolders = true
        distinctReplacementDirectory = true
        replacementHasDifferentStream = true
        if (failed) {
            if (cursorFailed) replacementEpochStatus = 503 else replacementDirectoryStatus = 503
        }
        setup(showMentions = false)
        assertFalse(::mentions.isInitialized)
        replaceLogin()
        lateinit var reconnect: Job
        compose.runOnUiThread { reconnect = topicProducers.single().viewModelScope.launch { account.repo.start() } }
        try {
            compose.waitUntil(10_000) { account.repo.currentUser.value?.uuid == authorId &&
                account.repo.streamsQueryState.value !is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Loading }
            if (failed) {
                assertTrue("Owner replacement failure must expose a shared retry state", account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error)
                compose.onNodeWithText("Повторить запрос").assertIsDisplayed()
                replacementDirectoryStatus = 200
                replacementEpochStatus = 200
                compose.onNodeWithText("Повторить запрос").performClick()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Replacement stream title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Replacement stream title").assertIsDisplayed()
            compose.onNodeWithText("Список каналов пуст").assertDoesNotExist()
            assertEquals(replacementStreamId, account.repo.streams.value.single().uuid)
            assertEquals("replacement-folder", account.repo.currentlySelectedFolder.value?.uuid)
            val drafts = requests.indexOfLast { it.path.endsWith("/drafts/") && it.headers["authorization"] == "Bearer cassi-fixture-replacement" }
            val readySocket: (LoopbackWorkspaceServer.Request) -> Boolean = { request ->
                request.path.endsWith("/events/ws") && request.headers["sec-websocket-protocol"]?.contains("cassi-fixture-replacement") == true &&
                    requests.indexOfFirst { it === request } > drafts && successfulEpochRequests.any { request.query["last_epoch_version"] == listOf(it.second.toString()) }
            }
            compose.waitUntil(10_000) { requests.any(readySocket) }
            val socket = requests.last(readySocket)
            val used = socket.query.getValue("last_epoch_version").single().toInt()
            val cursorRequest = successfulEpochRequests.last { it.second == used }.first
            val epoch = requests.indexOfFirst { it === cursorRequest }
            val own = requests.indexOfLast { requests.indexOfFirst { saved -> saved === it } < drafts && "/me/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-replacement" }
            val metadata = requests.indexOfFirst { requests.indexOfFirst { saved -> saved === it } > own && "/message_reactions/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-replacement" }
            assertTrue("The cursor actually used for replay must precede its metadata snapshots", epoch >= 0 && epoch < metadata)
            assertFalse("Shared recovery must not depend on creating Mentions", ::mentions.isInitialized)
        } finally { runBlocking { reconnect.cancelAndJoin() } }
    }

    @Test fun sameOwnerDirectoryReloadPreservesMatchingConversationPreview() {
        directoryHasLastMessage = true
        setup()
        waitForBody()
        val message = mentions.messages.value.single()
        account.repo.setInitialStreams(account.repo.streams.value.map { it.copy(lastMessage = message) })
        runBlocking { assertTrue(account.user.setTokensAndWait("cassi-fixture-refreshed", "cassi-fixture-refreshed-refresh", account.config.id)) }
        compose.waitUntil(10_000) { mentions.isCurrentAccount() && requests.any {
            it.path.endsWith("/streams/") && it.headers["authorization"] == "Bearer cassi-fixture-refreshed" } &&
            mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        assertEquals("A same-owner directory reload must preserve the matching conversation preview", message.uuid,
            account.repo.streams.value.single().lastMessage?.uuid)
    }

    @Test fun directoryUsersExpiryUsesSharedRecovery() = checkDirectoryExpiry("/users/")
    @Test fun directoryStreamsExpiryUsesSharedRecovery() = checkDirectoryExpiry("/streams/")

    private fun checkDirectoryExpiry(path: String) {
        expireDirectoryPath = path
        setup()
        waitForBody()
        assertEquals(1, unauthorizedResponses.get())
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        assertTrue(requests.any { it.path.endsWith(path) && it.headers["authorization"] == "Bearer cassi-fixture-refreshed" })
        assertTrue(mentions.isCurrentAccount())
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
    }

    @Test fun lateBootstrapUsersCannotReplaceNewOwnersDirectory() = checkLateDirectory("/users/")
    @Test fun lateBootstrapStreamsCannotReplaceNewOwnersDirectory() = checkLateDirectory("/streams/")
    @Test fun lateBootstrapFoldersCannotRestorePreviousOwnersSelection() = checkLateDirectory("/folders/")

    private fun checkLateDirectory(path: String) {
        distinctReplacementDirectory = true
        if (path == "/folders/") directoryFolders = true
        setup()
        waitForBody()
        delayedDirectoryPath = path
        val freshStream = account.repo.streams.value.single().copy(name = "Replacement stream title")
        lateinit var pending: Job
        compose.runOnUiThread { pending = mentions.viewModelScope.launch {
            when (path) {
                "/users/" -> account.repo.loadAllUsersInfo()
                "/folders/" -> account.repo.loadFolders()
                else -> account.repo.loadSubscribedChannels()
            }
        } }
        try {
            assertTrue(directoryEntered.await(7, TimeUnit.SECONDS))
            replaceLogin()
            compose.waitUntil(10_000) { mentions.isCurrentAccount() && mentions.messages.value.singleOrNull()?.uuid == "replacement-mention" }
            // Isolate late publication from the separate directory-reload regression.
            account.repo.setInitialUsers(listOf(UserResponseData(firstName = "Replacement", lastName = "Author",
                username = "replacement", uuid = authorId, status = "active", avatar = "replacement-avatar.png")))
            account.repo.setInitialStreams(listOf(freshStream))
            if (path == "/folders/") {
                account.repo.setInitialFolders(listOf(directoryFolder(true)))
                account.repo.updateCurrentlySelectedFolder(directoryFolder(true))
            }
            releaseDirectory.countDown()
            compose.waitUntil(7_000) { lateDirectorySuccesses.get() == 1 && (pending.isCompleted ||
                account.repo.users.value.any { it.uuid == viewerId } || account.repo.streams.value.any { it.name == "Stored stream title" } ||
                    path == "/folders/" && account.repo.currentlySelectedFolder.value?.uuid == "old-folder") }
            assertTrue("Late bootstrap must not restore the previous account's users", account.repo.users.value.none { it.uuid == viewerId })
            assertTrue("Late bootstrap must not restore the previous account's streams", account.repo.streams.value.none { it.name == "Stored stream title" })
            if (path == "/folders/") assertEquals("Late folders must not restore an old owner's filter", "replacement-folder", account.repo.currentlySelectedFolder.value?.uuid)
        } finally { releaseDirectory.countDown(); runBlocking { pending.cancelAndJoin() } }
    }

    @Test fun lateChatTopicLoadCannotRepopulateReplacementOwnersCache() = checkLateTopicProducer(chat = true)
    @Test fun lateInboxTopicLoadCannotRepopulateReplacementOwnersCache() = checkLateTopicProducer(chat = false)
    @Test fun lateInboxMessageEnrichmentCannotPublishOldTopicsOrBodies() = checkLateTopicProducer(chat = false, enrichment = true)

    @Test fun cosmeticOrganizationChangePreservesChatTopicLoad() = checkCosmeticTopicLoad(chat = true)
    @Test fun cosmeticOrganizationChangePreservesInboxTopicLoad() = checkCosmeticTopicLoad(chat = false)

    private fun renameOrganization() {
        val renamed = account.config.copy(name = "Renamed organization", imageUrl = "renamed-icon.png")
        runBlocking { account.preferences.updateServer(renamed) }
        compose.waitUntil(5_000) { account.user.servers.value.single { it.id == renamed.id } == renamed }
    }

    private fun checkCosmeticTopicLoad(chat: Boolean) {
        setup(showMentions = false)
        val load = topicProducer(chat)
        topicsEntered = CountDownLatch(1)
        delayTopics = true
        lateinit var pending: Job
        compose.runOnUiThread { pending = topicProducers.last().viewModelScope.launch { load() } }
        try {
            assertTrue(topicsEntered.await(5, TimeUnit.SECONDS))
            renameOrganization()
            releaseTopics.countDown()
            runBlocking { withTimeout(10_000) { pending.join() } }
            assertEquals("The held response must actually succeed", 1, lateProducerSuccesses.get())
            val state = when (val model = topicProducers.last()) {
                is ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel -> model.topicsQueryState.value
                is ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel -> model.topicsQueryState.value
                else -> error("Unexpected topic producer")
            }
            assertTrue("Cosmetic organization changes must not discard successful topic reads: $state",
                state is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success)
            assertEquals("Mention topic", account.repo.streamTopics.value.getValue(streamId).single().name)
        } finally { releaseTopics.countDown(); runBlocking { pending.cancelAndJoin() } }
    }

    @Test fun cosmeticOrganizationChangePreservesRetainedDraftTopics() {
        draftRows = true
        delayTopics = true
        setup(showMentions = false, showDrafts = true)
        try {
            assertTrue(topicsEntered.await(5, TimeUnit.SECONDS))
            renameOrganization()
            releaseTopics.countDown()
            val model = topicProducers.filterIsInstance<ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel>().single()
            compose.waitUntil(5_000) { model.topicsQueryState.value !is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Loading }
            assertTrue("The retained Drafts screen must keep the in-flight read",
                model.topicsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success)
            compose.onNodeWithText("Old owner's draft").assertIsDisplayed()
            compose.onNodeWithText("Mention topic").assertIsDisplayed()
        } finally { releaseTopics.countDown() }
    }

    private fun topicProducer(chat: Boolean): suspend () -> Unit {
        val original = account.repo.streams.value
        // Keep the Inbox observer idle so the test controls exactly one production load call.
        account.repo.setInitialStreams(original.map { it.copy(activeUnreadCount = 0) })
        return if (chat) {
            lateinit var model: ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
            compose.runOnUiThread {
                model = ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel(account.api, account.user, account.events, null, {})
                topicProducers += model
            };
            compose.waitUntil(5_000) { model.eventsRepo.value === account.repo };
            val action: suspend () -> Unit = { model.loadTopics(original.single()) }
            action
        } else {
            lateinit var model: ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel
            compose.runOnUiThread {
                model = ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel(account.api, account.events)
                topicProducers += model
            };
            val action: suspend () -> Unit = { model.loadTopics(original) }
            action
        }
    }

    private fun checkLateTopicProducer(chat: Boolean, enrichment: Boolean = false) {
        replacementSharesTopicId = true
        replacementTopicStatus = 403
        topicRows = listOf(topic(otherId).copy(lastMessageUuid = messageId))
        setup(cacheTopic = true)
        waitForBody()
        val load = topicProducer(chat)
        topicsEntered = CountDownLatch(1)
        if (enrichment) delayTopicMessages = true else delayTopics = true
        lateinit var pending: Job
        compose.runOnUiThread { pending = mentions.viewModelScope.launch { load() } }
        assertTrue((if (enrichment) topicMessagesEntered else topicsEntered).await(7, TimeUnit.SECONDS))
        replaceLogin()
        compose.waitUntil(6_000) { account.repo.currentUser.value?.uuid == authorId &&
            mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        if (enrichment) releaseTopicMessages.countDown() else releaseTopics.countDown()
        runBlocking { withTimeout(12_000) { pending.join() } }
        assertEquals("The held old-owner response must actually succeed", 1, lateProducerSuccesses.get())
        assertTrue("A late non-Mentions producer must not restore old-owner topic metadata",
            account.repo.topicsPool.value.none { it.name == "Mention topic" })
        assertTrue("Old-owner enrichment must not publish old message bodies",
            account.repo.messagesPool.value.none { it.uuid == messageId })
        assertFalse("Old message IDs must not be requested with replacement credentials", requests.any {
            it.path.endsWith("/messages/") && it.query["uuid"] == listOf(messageId) &&
                it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        // A newly loaded replacement directory can legitimately wake the Inbox observer.
        // Stop that independent producer before measuring the explicit metadata retry.
        runBlocking { topicProducers.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        val beforeRetry = requests.count { "/stream_topics/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-replacement" }
        runBlocking { mentions.loadMentionedMessages() }
        assertTrue(mentions.messages.value.isEmpty())
        assertEquals(beforeRetry + 1, requests.count { "/stream_topics/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
    }

    @Test fun ownerOnlyFailureKeepsSharedStreamsSuccessful() {
        setup(cacheTopic = true)
        waitForBody()
        lateinit var bootstrap: Job
        compose.runOnUiThread { bootstrap = mentions.viewModelScope.launch { account.repo.loadDrafts() } }
        compose.waitUntil(5_000) { account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        runBlocking { bootstrap.cancelAndJoin() }
        ownerStatus = 503
        runBlocking { assertTrue(account.preferences.saveRefreshedTokensIfCurrent(account.config.id,
            "cassi-fixture-refresh", TokenPair("cassi-fixture-refreshed", "cassi-fixture-refreshed-refresh"))) }
        compose.waitUntil(10_000) { mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        assertTrue("Owner-only failure must preserve shared Home/Chat state",
            account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success)
        assertTrue(account.repo.streams.value.isNotEmpty())
    }

    @Test fun fullBootstrapOwnerFailureStillReportsSharedError() {
        setup(cacheTopic = true)
        waitForBody()
        lateinit var bootstrap: Job
        compose.runOnUiThread { bootstrap = mentions.viewModelScope.launch { account.repo.loadDrafts() } }
        compose.waitUntil(5_000) { account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success }
        runBlocking { bootstrap.cancelAndJoin() }
        assertTrue("Bootstrap setup must reach Success before the failed owner check",
            account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success)
        ownerStatus = 503
        runBlocking { account.repo.loadUserInfo() }
        assertTrue(account.repo.streamsQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error)
    }

    @Test fun chatTopicLoadPreservesSharedTokenExpiryRecovery() = checkProducerExpiry(chat = true)
    @Test fun inboxTopicLoadPreservesSharedTokenExpiryRecovery() = checkProducerExpiry(chat = false)
    private fun checkProducerExpiry(chat: Boolean) {
        setup(cacheTopic = true)
        waitForBody()
        val load = topicProducer(chat)
        expireTopics = true
        runBlocking { withTimeout(12_000) { load() } }
        waitForBody()
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        assertEquals("Bearer cassi-fixture-refreshed", requests.last { "/stream_topics/" in it.path }.headers["authorization"])
        assertTrue(account.repo.streamTopics.value[streamId].orEmpty().isNotEmpty())
    }

    @Test fun inboxEnrichmentExpiryRestartsTheWholeReadAfterVerification() {
        topicRows = listOf(topic(otherId).copy(lastMessageUuid = messageId))
        setup(cacheTopic = true)
        waitForBody()
        val load = topicProducer(chat = false)
        expireTopicMessages = true
        runBlocking { withTimeout(12_000) { load() } }
        waitForBody()
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        assertEquals(2, requests.count { "/stream_topics/" in it.path })
        assertEquals("Bearer cassi-fixture-refreshed", requests.last { it.query["uuid"] == listOf(messageId) }.headers["authorization"])
        assertEquals(body, account.repo.streamTopics.value.getValue(streamId).single().lastMessage?.payload?.content)
    }

    @Test fun inboxRecoveryNeverReplaysCapturedStreamIdsAsAnotherOwner() {
        replacementHasDifferentStream = true
        setup(cacheTopic = true)
        waitForBody()
        val load = topicProducer(chat = false)
        expireTopics = true
        refreshOwnerReplacement = true
        runBlocking { withTimeout(12_000) { load() } }
        waitForBody("Replacement account mention")
        assertFalse(requests.any { "/stream_topics/" in it.path && it.query["stream_uuid"] == listOf(streamId) &&
            it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        assertEquals(authorId, mentions.messages.value.single().userUuid)
    }

    @Test fun retainedAccountCannotUsePreviousOwnersCachedTopicWhenForbidden() =
        checkCollidingTopicCache(firstOpen = false, status = 403)
    @Test fun firstOpenCannotUsePreviousOwnersCachedTopicWhenForbidden() =
        checkCollidingTopicCache(firstOpen = true, status = 403)
    @Test fun replacementAccountFetchesItsOwnNameForACachedTopicId() =
        checkCollidingTopicCache(firstOpen = false, status = 200)

    private fun checkCollidingTopicCache(firstOpen: Boolean, status: Int) {
        replacementSharesTopicId = true
        replacementTopicStatus = status
        setup(cacheTopic = true, replaceBeforeModel = firstOpen)
        if (!firstOpen) { waitForBody(); replaceLogin() }
        compose.waitUntil(12_000) { account.repo.currentUser.value?.uuid == authorId && mentions.isCurrentAccount() &&
            (mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Success ||
                mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error) }
        assertEquals("A new owner must authenticate metadata even for the same stream/topic UUID pair", 1,
            requests.count { "/stream_topics/" in it.path && it.query["uuid"] == listOf(otherId) &&
                it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        assertTrue(account.repo.topicsPool.value.none { it.name == "Mention topic" })
        onView(withText(body)).check(doesNotExist())
        if (status == 403) {
            assertTrue(mentions.messages.value.isEmpty())
            assertTrue(account.repo.topicsPool.value.none { it.uuid == otherId })
            assertNull(decoded)
        } else {
            waitForBody("Replacement account mention")
            assertEquals("Replacement mention topic", account.repo.topicsPool.value.single().name)
            compose.onNodeWithText(rowLabel).performClick()
            compose.waitUntil(12_000) { decoded != null }
            assertEquals(otherId, decoded!!.topicUuid)
            assertEquals("Replacement mention topic", decoded!!.topicName)
            compose.waitUntil(12_000) { compose.onAllNodesWithTag("chat-message-replacement-mention").fetchSemanticsNodes().isNotEmpty() }
            screenshot("same-topic-new-owner-history")
        }
    }

    @Test fun ownerReverificationDoesNotWaitForRealtimeBootstrap() {
        expireMessages = true
        workspaceBootstrapAfterRefresh = true
        setup()
        waitForBody()
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        assertTrue(requests.any { "/me/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-refreshed" })
        assertFalse("Owner verification must not start or await full workspace synchronization", requests.any {
            "/message_reactions/" in it.path && it.headers["authorization"] == "Bearer cassi-fixture-refreshed" })
        assertTrue(mentions.isCurrentAccount())
    }

    @Test fun expiredMentionCredentialRecoversThroughSharedRefresh() = checkExpiredCredential(metadata = false)
    @Test fun expiredTopicCredentialRecoversThroughSharedRefresh() = checkExpiredCredential(metadata = true)

    private fun checkExpiredCredential(metadata: Boolean) {
        if (metadata) expireTopics = true else expireMessages = true
        setup()
        compose.waitUntil(12_000) { mentions.messages.value.isNotEmpty() ||
            mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        assertEquals("Expired credentials must use the shared refresh endpoint", 1,
            requests.count { it.path.endsWith("/actions/get_token/invoke") })
        waitForBody()
        assertEquals(1, unauthorizedResponses.get())
        val refreshIndex = requests.indexOfFirst { it.path.endsWith("/actions/get_token/invoke") }
        val verifiedIndex = requests.indexOfFirst { "/me/" in it.path &&
            it.headers["authorization"] == "Bearer cassi-fixture-refreshed" }
        val freshMessagesIndex = requests.indexOfFirst { it.query["mentioned"] == listOf("true") &&
            it.headers["authorization"] == "Bearer cassi-fixture-refreshed" }
        assertTrue("The owner must verify before the fresh query", refreshIndex < verifiedIndex && verifiedIndex < freshMessagesIndex)
        assertEquals(viewerId, account.repo.currentUser.value?.uuid)
        assertTrue(mentions.isCurrentAccount())
        screenshot("token-expiry-${if (metadata) "topics" else "messages"}-recovered")
        compose.onNodeWithText(rowLabel).performClick()
        compose.waitUntil(12_000) { decoded != null }
        assertEquals(otherId, decoded!!.topicUuid)
        compose.waitUntil(12_000) { compose.onAllNodesWithTag("chat-message-$messageId").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun topicRetryRecoversExpiredCredentialAndRevalidatesOwner() {
        mixedTopics = true
        topicStatus = 503
        setup()
        waitForBody()
        expireTopics = true
        topicStatus = 200
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(ru.genesiscorporation.workspace.beta.R.string.mentions_topic_retry)).performClick()
        compose.waitUntil(12_000) { requests.any { it.path.endsWith("/actions/get_token/invoke") } &&
            account.repo.topicsPool.value.any { it.uuid == otherId } && mentions.isCurrentAccount() }
        waitForBody()
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        assertEquals("Bearer cassi-fixture-refreshed", requests.last { "/stream_topics/" in it.path }.headers["authorization"])
    }

    @Test fun refreshedCredentialRejectionStopsAfterOneRecovery() {
        expireMessages = true
        rejectRefreshedCredential = true
        setup()
        compose.waitUntil(12_000) { requests.any { it.path.endsWith("/actions/get_token/invoke") } &&
            mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        assertEquals(2, unauthorizedResponses.get())
        assertTrue(mentions.messages.value.isEmpty())
    }

    @Test fun refreshFailureCanRecoverWithExplicitRetry() {
        expireMessages = true
        refreshStatus = 503
        setup()
        compose.waitUntil(12_000) { mentions.messagesQueryState.value is ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState.Error }
        assertEquals(1, requests.count { it.path.endsWith("/actions/get_token/invoke") })
        refreshStatus = 200
        runBlocking { mentions.loadMentionedMessages() }
        waitForBody()
        assertEquals(2, requests.count { it.path.endsWith("/actions/get_token/invoke") })
    }

    @Test fun refreshOwnerChangeStartsFreshQueryWithoutOldTopicIds() {
        expireTopics = true
        refreshOwnerReplacement = true
        setup()
        waitForBody("Replacement account mention")
        assertEquals(authorId, mentions.messages.value.single().userUuid)
        assertFalse(requests.any { "/stream_topics/" in it.path && it.query["uuid"] == listOf(otherId) &&
            it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
    }

    @Test fun delayedMention401CannotReplayWithReplacementCredentials() =
        checkUnauthorizedOperationDoesNotChangeOwner(metadata = false)

    @Test fun delayedTopic401CannotReplayOldIdsWithReplacementCredentials() =
        checkUnauthorizedOperationDoesNotChangeOwner(metadata = true)

    private fun checkUnauthorizedOperationDoesNotChangeOwner(metadata: Boolean) {
        if (metadata) { mixedTopics = true; topicStatus = 503 }
        setup()
        waitForBody()
        if (metadata) {
            topicsEntered = CountDownLatch(1)
            delayTopics = true
            topicStatus = 401
        } else {
            messagesEntered = CountDownLatch(1)
            delayMessages = true
            mentionedStatus = 401
        }
        lateinit var stale: Job
        compose.runOnUiThread {
            stale = mentions.viewModelScope.launch {
                if (metadata) mentions.retryMissingTopics() else mentions.loadMentionedMessages()
            }
        }
        assertTrue((if (metadata) topicsEntered else messagesEntered).await(7, TimeUnit.SECONDS))
        replaceLogin()
        waitForBody("Replacement account mention")
        val replacementMessages = requests.count { it.query["mentioned"] == listOf("true") &&
            it.headers["authorization"] == "Bearer cassi-fixture-replacement" }
        if (metadata) releaseTopics.countDown() else releaseMessages.countDown()
        runBlocking { withTimeout(12_000) { stale.join() } }
        assertEquals("The held operation must actually receive a 401", 1, unauthorizedResponses.get())
        assertFalse("Old topic IDs must never be replayed under the replacement credential",
            requests.any { "/stream_topics/" in it.path && it.query["uuid"] == listOf(otherId) &&
                it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        assertEquals("The old messages operation must not be replayed as the replacement account",
            replacementMessages, requests.count { it.query["mentioned"] == listOf("true") &&
                it.headers["authorization"] == "Bearer cassi-fixture-replacement" })
        assertEquals(authorId, mentions.messages.value.single().userUuid)
        assertEquals(replacementTopicId, mentions.messages.value.single().topicUuid)
        onView(withText(body)).check(doesNotExist())
        assertTrue(mentions.isCurrentAccount())
    }

    private fun replaceLogin() {
        runBlocking { assertTrue(account.user.setTokensAndWait("cassi-fixture-replacement", "cassi-fixture-replacement-refresh", account.config.id)) }
        assertEquals(account.config.id, account.user.selectedServerId.value)
    }

    private fun switchAccount() {
        val config = ServerConfig("cassi-mention-switch-${UUID.randomUUID()}", server.url, "", "Other fixture", projectUuid = "project")
        secondaryId = config.id
        runBlocking {
            account.preferences.addServer(config, TokenPair("cassi-fixture-other", "cassi-fixture-other-refresh"))
            account.preferences.setSelectedServerId(config.id)
        }
        compose.waitUntil(5_000) { account.user.selectedServerId.value == config.id }
    }

    private fun waitForBody(expected: String = body) {
        compose.waitUntil(12_000) {
            mentions.messages.value.any { it.payload.content == expected } &&
                compose.onAllNodesWithText(rowLabel).fetchSemanticsNodes().isNotEmpty()
        }
        onView(withText(expected)).check(matches(isDisplayed()))
    }
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("mentionScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "mentions92").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
