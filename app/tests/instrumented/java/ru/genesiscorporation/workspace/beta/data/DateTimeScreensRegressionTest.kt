package ru.genesiscorporation.workspace.beta.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.Draft
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import ru.genesiscorporation.workspace.beta.fixtures.WorkspaceTestAccount
import ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsScreen
import ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel
import ru.genesiscorporation.workspace.beta.modules.homementions.HomeMentionsScreen
import ru.genesiscorporation.workspace.beta.modules.homementions.HomeMentionsViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class DateTimeScreensRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private val models = mutableListOf<ViewModel>()
    private val early = "2026-10-30T00:15:00+03:00"
    private val late = "2026-10-29T21:30:00Z"
    private val stream = Stream("stream", 0, 0, 0, late, "CASSI dates", false, 0,
        notificationMode = "all_messages")
    private val topics = listOf("early", "late").map {
        TopicsResponseData(it, it, 0, stream.uuid, late, 0, 0, false, false, notificationMode = "default")
    }
    private val drafts = listOf(draft("early", early), draft("late", late))
    private val mentions = listOf(message("early", early), message("late", late))
    private val folders = listOf(FolderResponseData("early", "Early", 0, "", early),
        FolderResponseData("late", "Late", 0, "", late))

    @After fun cleanup() = runBlocking {
        withContext(Dispatchers.Main) { models.forEach { it.viewModelScope.cancel() } }
        if (::account.isInitialized) account.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun foldersLoadInAbsoluteCreationOrder(): Unit = runBlocking {
        setup()
        // Loading folders continues into the long-lived event connection; stop it after the snapshot.
        val loading = launch { account.repo.loadFolders() }
        try {
            val loaded = withTimeout(5_000) { account.repo.folders.first { it.size == 2 } }
            assertEquals(listOf("early", "late"), loaded.map { it.uuid })
        } finally { loading.cancelAndJoin() }
    }

    @Test fun draftsShowTheMostRecentInstantFirst(): Unit = runBlocking {
        setup()
        val model = withContext(Dispatchers.Main) {
            HomeDraftsViewModel(account.api, account.events).also { models += it }
        }
        show { HomeDraftsScreen(model, rememberNavController()) }
        assertLateBeforeEarly()
    }

    @Test fun mentionsShowTheMostRecentInstantFirst(): Unit = runBlocking {
        setup()
        val model = withContext(Dispatchers.Main) {
            HomeMentionsViewModel(account.api, account.events).also { models += it }
        }
        show { HomeMentionsScreen(model, rememberNavController()) }
        // Markdown previews are Android TextViews, outside the Compose text semantics tree.
        compose.waitUntil(5_000) {
            runCatching { onView(withText("CASSI late")).check(matches(isCompletelyDisplayed())) }.isSuccess
        }
        fun top(text: String): Int {
            val position = IntArray(2)
            onView(withText(text)).check { view, error ->
                if (error != null) throw error
                view.getLocationOnScreen(position)
            }
            return position[1]
        }
        assertTrue("The later mention must be the first row", top("CASSI late") < top("CASSI early"))
    }

    private suspend fun setup() {
        server = LoopbackWorkspaceServer { request ->
            val body = when (request.path) {
                "/api/workspace/v1/messenger/messages/" -> Json.encodeToString(mentions)
                "/api/workspace/v1/messenger/folders/" -> Json.encodeToString(folders)
                "/api/workspace/v1/messenger/drafts/" -> Json.encodeToString(drafts)
                "/api/workspace/v1/messenger/streams/" -> Json.encodeToString(listOf(stream))
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(body)
        }
        account = WorkspaceTestAccount.create(server.url)
        account.repo.setInitialStreams(listOf(stream))
        account.repo.appendItemsToTopicsPool(topics)
        account.repo.setInitialDraftsPool(drafts)
        account.repo.setInitialUsers(listOf(UserResponseData(username = "cassi", uuid = "user",
            status = "active", avatar = "")))
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { WokspaceTheme(darkTheme = false, dynamicColor = false) { content() } }
    }

    private fun assertLateBeforeEarly() {
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("CASSI late").fetchSemanticsNode() }.isSuccess
        }
        val lateNode = compose.onNodeWithText("CASSI late").assertIsDisplayed().fetchSemanticsNode()
        val earlyNode = compose.onNodeWithText("CASSI early").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("The later instant must be the first row", lateNode.boundsInRoot.top < earlyNode.boundsInRoot.top)
    }

    private fun draft(id: String, date: String) =
        Draft(id, "stream", id, date, MessageResponsePayload("markdown", "CASSI $id"), 1)

    private fun message(id: String, date: String) = MessageResponse(id, date, date, "stream", id,
        "user", "user", MessageResponsePayload("markdown", "CASSI $id"), true, emptyMap(), true)
}
