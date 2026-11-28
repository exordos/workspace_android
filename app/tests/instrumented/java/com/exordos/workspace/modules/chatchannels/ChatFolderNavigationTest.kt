package com.exordos.workspace.modules.chatchannels

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exordos.workspace.data.uio.FolderUIO
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.ui.theme.WokspaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatFolderNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: LoopbackWorkspaceServer
    private lateinit var account: WorkspaceTestAccount
    private lateinit var model: ChatViewModel
    private val pages get() = compose.onNodeWithTag("folder_pages")
    private val general get() = compose.onNodeWithText("General channel")
    private val empty get() = compose.onNodeWithText("Список каналов пуст")
    private val workTab = "Work and team discussions and planning"
    private val date = "2026-01-01T00:00:00Z"

    @After fun close() = runBlocking {
        if (::model.isInitialized) withContext(Dispatchers.Main) { model.viewModelScope.cancel() }
        if (::account.isInitialized) account.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun swipesAndTabsSelectTheSameFoldersAndLeaveVerticalScrollingAndTopicsWorking() {
        openChat(extraChannels = 24)
        general.assertIsDisplayed()
        pages.performTouchInput { swipeUp() }
        general.assertIsNotDisplayed()
        assertFolder("all", "Everything")

        pages.performTouchInput { swipeLeft() }
        assertFolder("empty", "Empty folder")
        empty.assertIsDisplayed()
        pages.performTouchInput { swipeLeft() }
        assertFolder("work", workTab)

        compose.onNodeWithText("Work channel").performClick()
        compose.onNodeWithText("Work topic").assertIsDisplayed()
        pages.performTouchInput { swipeRight(startX = width * 0.24f, endX = width * 0.92f) }
        compose.onNodeWithText("Work topic").assertIsNotDisplayed()
        assertFolder("work", workTab)

        compose.onNodeWithText("Empty folder").performClick()
        assertFolder("empty", "Empty folder")
        pages.performTouchInput { swipeRight() }
        assertFolder("all", "Everything")
    }

    @Test fun aRealtimeBadgeUpdateDoesNotMoveEitherPageUnderTheFinger() {
        openChat()
        pages.performTouchInput {
            down(center.copy(x = width * 0.85f))
            moveTo(center.copy(x = width * 0.20f), delayMillis = 180)
        }
        try {
            val position = empty.assertIsDisplayed().fetchSemanticsNode().positionInRoot.x
            runBlocking { account.repo.didReceiveFolderEvent(allFolder(unread = 7), "updated") }
            compose.waitUntil(5_000) { model.folders.value.firstOrNull()?.unreadCount == 7 }
            assertEquals(position, empty.fetchSemanticsNode().positionInRoot.x, 0.5f)
            assertEquals("all", account.repo.currentlySelectedFolder.value?.uuid)
        } finally {
            pages.performTouchInput { up() }
        }
        assertFolder("empty", "Empty folder")
    }

    @Test fun aLateRepositorySelectionUpdatesTheTabAndPageAfterCachedFoldersRender() {
        openChat(startWithCachedFolders = true)

        val tab = compose.onNode(hasText("Everything") and isSelected())
        tab.assertIsDisplayed()
        assertEquals(null, account.repo.currentlySelectedFolder.value)
        val folderIds = model.folders.value.map { it.uuid }

        account.repo.loadServerSettings()
        compose.waitUntil(10_000) {
            account.repo.currentlySelectedFolder.value?.uuid == "empty" &&
                model.streamsQueryState.value == QueryState.Success
        }
        assertEquals(folderIds, model.folders.value.map { it.uuid })
        assertFolder("empty", "Empty folder")
        empty.assertIsDisplayed()
    }

    @Test fun aRepositorySelectionWaitsForTheFingerToReleaseAndThePageToSettle() {
        openChat()
        pages.performTouchInput {
            down(center.copy(x = width * 0.85f))
            moveTo(center.copy(x = width * 0.55f), delayMillis = 180)
        }
        val position = general.fetchSemanticsNode().positionInRoot.x
        try {
            runBlocking {
                val work = account.repo.folders.first().single { it.uuid == "work" }
                account.repo.updateCurrentlySelectedFolder(work)
            }
            compose.waitUntil(5_000) { model.currentlySelectedFolder.value?.uuid == "work" }
            compose.onNode(hasText("Everything") and isSelected()).assertIsDisplayed()
            assertEquals(position, general.fetchSemanticsNode().positionInRoot.x, 0.5f)
            pages.performTouchInput { moveTo(center.copy(x = width * 0.76f), delayMillis = 180) }
            assertTrue(general.fetchSemanticsNode().positionInRoot.x > position + 10f)

            compose.mainClock.autoAdvance = false
        } finally {
            pages.performTouchInput { up() }
        }
        try {
            compose.mainClock.advanceTimeBy(48)
            compose.onNode(hasText("Everything") and isSelected()).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(400)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        assertFolder("work", workTab)
        compose.onNodeWithText("Work channel").assertIsDisplayed()
    }

    @Test fun theTabIndicatorMovesWithAPartialDragBeforeTheSelectionChanges() {
        openChat()
        val initial = indicatorMiddle()
        pages.performTouchInput {
            down(center.copy(x = width * 0.85f))
            moveTo(center.copy(x = width * 0.55f), delayMillis = 180)
        }
        try {
            val dragged = indicatorMiddle()
            assertTrue("The underline should follow the page during the drag", dragged > initial + 10f)
            assertEquals("all", account.repo.currentlySelectedFolder.value?.uuid)
            pages.performTouchInput { moveTo(center.copy(x = width * 0.76f), delayMillis = 180) }
            assertTrue("The underline should follow a change of direction", indicatorMiddle() < dragged - 10f)
        } finally {
            pages.performTouchInput { up() }
        }
        assertFolder("all", "Everything")
    }

    @Test fun bringingTheSelectedTabIntoViewDoesNotInterruptTheNextPan() {
        openChat()
        pages.performTouchInput { swipeLeft() }
        assertFolder("empty", "Empty folder")
        val tab = compose.onNodeWithText(workTab)
        val active = compose.onNode(hasText(workTab) and isSelected())
        val offsets = mutableListOf(tab.fetchSemanticsNode().positionInRoot.x)
        pages.performTouchInput {
            down(center.copy(x = width * 0.85f))
            moveTo(center.copy(x = width * 0.20f), delayMillis = 180)
        }
        compose.mainClock.autoAdvance = false
        var pointerDown = true
        try {
            pages.performTouchInput { up() }
            pointerDown = false
            for (frame in 1..42) {
                compose.mainClock.advanceTimeByFrame()
                offsets += tab.fetchSemanticsNode().positionInRoot.x
                if (active.isDisplayed()) break
            }
            active.assertIsDisplayed()
            pages.performTouchInput {
                down(center.copy(x = width * 0.12f))
                moveTo(center.copy(x = width * 0.77f), delayMillis = 0)
            }
            pointerDown = true
            val position = empty.assertIsDisplayed().fetchSemanticsNode().positionInRoot.x
            repeat(25) {
                compose.mainClock.advanceTimeByFrame()
                offsets += tab.fetchSemanticsNode().positionInRoot.x
                assertEquals(position, empty.fetchSemanticsNode().positionInRoot.x, 0.5f)
            }
            val first = offsets.first()
            val last = offsets.last()
            assertTrue("The clipped tab should move into view", first - last > 1f)
            assertTrue("The tab strip should move across frames", offsets.any { it in (last + 1f)..(first - 1f) })
        } finally {
            if (pointerDown) pages.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
        assertFolder("empty", "Empty folder")
    }

    @Test fun deletingTheActiveFolderSelectsTheRepositoryFallback() {
        openChat()
        compose.onNodeWithText(workTab).performClick()
        assertFolder("work", workTab)
        runBlocking { account.repo.removeFolder("work") }
        assertFolder("all", "Everything")
        general.assertIsDisplayed()
        pages.performTouchInput { swipeLeft() }
        assertFolder("empty", "Empty folder")
    }

    private fun assertFolder(uuid: String, title: String) {
        val tab = compose.onNode(hasText(title) and isSelected())
        compose.waitUntil(5_000) {
            account.repo.currentlySelectedFolder.value?.uuid == uuid && tab.isDisplayed()
        }
    }

    private fun indicatorMiddle() = compose.onNodeWithTag("folder_tab_indicator")
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.x

    private fun openChat(extraChannels: Int = 0, startWithCachedFolders: Boolean = false) {
        val emptyDate = if (startWithCachedFolders) date else "2026-01-02T00:00:00Z"
        val emptyFolder = """{"uuid":"empty","title":"Empty folder","unread_count":0,"system_type":"created",
             "created_at":"$emptyDate","folder_items":[]}"""
        val orderedFolders = if (startWithCachedFolders) "$emptyFolder,${allFolder()}" else "${allFolder()},$emptyFolder"
        val person = """{"uuid":"person","username":"Fixture","status":"active","avatar":""}"""
        val folders = """[$orderedFolders,
            {"uuid":"work","title":"$workTab","unread_count":0,"system_type":"created",
             "created_at":"2026-01-03T00:00:00Z","folder_items":[
                 {"uuid":"item","folder_uuid":"work","stream_uuid":"work-channel","chat_type":"stream","unread_count":0}
             ]}]"""
        val streams = (listOf(stream("general", "General channel"), stream("work-channel", "Work channel")) +
            List(extraChannels) { stream("extra-$it", "Extra channel $it") }).joinToString(",", "[", "]")
        val topics = """[{"uuid":"topic","stream_uuid":"work-channel","name":"Work topic","color":1,
            "updated_at":"$date","unread_count":0,"active_unread_count":0,
            "is_done":false,"is_default":true,"notification_mode":"default"}]"""
        server = LoopbackWorkspaceServer { request ->
            val body = when {
                request.path.endsWith("/server_settings/") ->
                    """{"email_auth_enabled":false,"realm_name":"Fixture","realm_url":"","realm_icon":"","meet_url":""}"""
                request.path.endsWith("/me/") -> person
                request.path.endsWith("/users/") -> "[$person]"
                request.path.endsWith("/folders/") -> folders
                request.path.endsWith("/streams/") -> streams
                request.path.endsWith("/stream_topics/") -> topics
                else -> "[]"
            }
            LoopbackWorkspaceServer.Response(body)
        }
        runBlocking {
            account = WorkspaceTestAccount.create(server.url)
            if (startWithCachedFolders) {
                account.repo.setInitialFolders(listOf(
                    FolderUIO("all", "Everything", 0, "all", date),
                    FolderUIO("empty", "Empty folder", 0, "created", emptyDate),
                    FolderUIO("work", workTab, 0, "created", "2026-01-03T00:00:00Z"),
                ))
            } else {
                account.repo.loadServerSettings()
                withTimeout(10_000) { account.repo.streamsQueryState.first { it == QueryState.Success } }
            }
            model = withContext(Dispatchers.Main) { ChatViewModel(account.api, account.user, account.events, null) {} }
        }
        compose.setContent {
            WokspaceTheme { ChatScreen(model, rememberNavController()) }
        }
        if (startWithCachedFolders) {
            compose.waitUntil(5_000) { compose.onNode(hasText("Everything") and isSelected()).isDisplayed() }
        } else {
            assertFolder("all", "Everything")
            general.assertIsDisplayed()
        }
    }

    private fun allFolder(unread: Int = 0) =
        """{"uuid":"all","title":"Everything","unread_count":$unread,"system_type":"all",
            "created_at":"$date","folder_items":[]}"""

    private fun stream(uuid: String, title: String) =
        """{"uuid":"$uuid","name":"$title","updated_at":"$date","unread_count":0,
            "active_unread_count":0,"passive_unread_count":0,"private":false,"color":0,
            "notification_mode":"all_messages"}"""
}
