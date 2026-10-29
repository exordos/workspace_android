package ru.genesiscorporation.workspace.beta.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationIndicatorRepositoryTest {
    private lateinit var repository: EventsRepository
    private lateinit var http: HttpClient

    @Before
    fun createRepository() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "cassi-notification-indicator-${UUID.randomUUID()}"
        http = HttpClient(CIO)
        repository = EventsRepository(
            id,
            ServerConfig(id, "http://127.0.0.1", "", "Notification indicator test"),
            SecureTokenStore(context),
            WorkspaceAPIClient(http),
        )
    }

    @After
    fun closeRepository() {
        if (::repository.isInitialized) repository.close()
        if (::http.isInitialized) http.close()
    }

    @Test
    fun topicRealtimeCursorReachesThePushConsumer() {
        val update = topic(activeUnread = 0)
        var received: Triple<TopicsResponseData, Long, String>? = null
        repository.onPushTopicUpdated = { topic, epoch, generation -> received = Triple(topic, epoch, generation) }
        repository.didReceiveTopicEvent(Json.encodeToString(update), "updated", 300, "generation")
        assertEquals(Triple(update, 300L, "generation"), received)
        received = null
        repository.didReceiveTopicEvent(Json.encodeToString(update), "updated")
        assertNull(received)
    }

    @Test
    fun topicUpdatedAppliesActiveUnreadToEveryCachedProjection() {
        val before = topic(activeUnread = 0)
        repository.addStreamTopics(before.streamUuid, listOf(before))
        val authoritative = before.copy(activeUnreadCount = 3, notificationMode = "follow")

        repository.didReceiveTopicEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative, repository.streamTopics.value.getValue("stream").single())
        assertEquals(authoritative, repository.topicsPool.value.single())
        assertEquals(0, before.activeUnreadCount)
    }

    @Test
    fun mutedAndReadTopicSnapshotsClearActiveUnreadInBothCaches() {
        val before = topic(activeUnread = 3).copy(notificationMode = "follow")
        repository.addStreamTopics(before.streamUuid, listOf(before))
        val muted = before.copy(activeUnreadCount = 0, notificationMode = "mute")

        repository.didReceiveTopicEvent(Json.encodeToString(muted), "updated")

        assertEquals(muted, repository.streamTopics.value.getValue("stream").single())
        assertEquals(muted, repository.topicsPool.value.single())
        val read = muted.copy(unreadCount = 0)
        repository.didReceiveTopicEvent(Json.encodeToString(read), "updated")
        assertEquals(read, repository.streamTopics.value.getValue("stream").single())
        assertEquals(read, repository.topicsPool.value.single())
    }

    @Test
    fun topicUpdatedRefreshesThePoolEvenWhenItsStreamTopicsAreNotLoaded() {
        val before = topic(activeUnread = 0)
        repository.appendItemsToTopicsPool(listOf(before))
        val authoritative = before.copy(activeUnreadCount = 3, notificationMode = "follow")

        repository.didReceiveTopicEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative, repository.topicsPool.value.single())
        assertEquals(emptyMap<String, List<TopicsResponseData>>(), repository.streamTopics.value)
    }

    @Test
    fun selectedFolderReceivesTheSameItemsAndCounterAsItsTab() {
        val before = folder().copy(unreadCount = 1, items = listOf(item("first", 1)))
        repository.setInitialFolders(listOf(before))
        repository.updateCurrentlySelectedFolder(before)
        val authoritative = before.copy(
            title = "CASSI updated folder",
            unreadCount = 17,
            items = before.items + item("second", 16),
        )

        repository.didReceiveFolderEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative, repository.folders.value.single())
        assertEquals(authoritative, repository.currentlySelectedFolder.value)
        assertEquals(listOf("first"), before.items.map { it.streamUuid })
    }

    @Test
    fun removingTheLastItemClearsTheSelectedFolderWithoutSwitchingTabs() {
        val before = folder().copy(unreadCount = 1, items = listOf(item("first", 1)))
        repository.setInitialFolders(listOf(before))
        repository.updateCurrentlySelectedFolder(before)
        val authoritative = before.copy(unreadCount = 0, items = emptyList())

        repository.didReceiveFolderEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative, repository.currentlySelectedFolder.value)
        assertEquals(authoritative, repository.folders.value.single())
    }

    @Test
    fun updatingAnotherFolderKeepsTheCurrentSelection() {
        val selected = folder().copy(uuid = "selected")
        val other = folder()
        repository.setInitialFolders(listOf(selected, other))
        repository.updateCurrentlySelectedFolder(selected)
        val authoritative = other.copy(unreadCount = 3)

        repository.didReceiveFolderEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(listOf(selected, authoritative), repository.folders.value)
        assertEquals(selected, repository.currentlySelectedFolder.value)
    }

    @Test
    fun deletingTheSelectedFolderFallsBackToTheFirstRemainingFolder() {
        val all = folder().copy(uuid = "all", systemType = "all")
        val deleted = folder()
        repository.setInitialFolders(listOf(all, deleted))
        repository.updateCurrentlySelectedFolder(deleted)
        repository.addStreamTopics("stream", listOf(topic(activeUnread = 3)))

        repository.didReceiveFolderEvent("""{"uuid":"folder"}""", "deleted")

        assertEquals(listOf(all), repository.folders.value)
        assertEquals(all, repository.currentlySelectedFolder.value)
        assertEquals(1, repository.streamTopics.value.getValue("stream").size)
    }

    @Test
    fun deletingTheLastFolderClearsItsSelection() {
        val deleted = folder()
        repository.setInitialFolders(listOf(deleted))
        repository.updateCurrentlySelectedFolder(deleted)

        repository.didReceiveFolderEvent("""{"uuid":"folder"}""", "deleted")

        assertEquals(emptyList<FolderResponseData>(), repository.folders.value)
        assertNull(repository.currentlySelectedFolder.value)
    }

    @Test
    fun duplicateAndUnselectedDeletionsDoNotDisturbOtherFolders() {
        val selected = folder().copy(uuid = "selected")
        repository.setInitialFolders(listOf(selected, folder()))
        repository.updateCurrentlySelectedFolder(selected)

        repeat(2) {
            repository.didReceiveFolderEvent("""{"uuid":"folder"}""", "deleted")
        }
        repository.didReceiveFolderEvent("""{"uuid":"unknown"}""", "deleted")

        assertEquals(listOf(selected), repository.folders.value)
        assertEquals(selected, repository.currentlySelectedFolder.value)
    }

    private fun topic(activeUnread: Int) = TopicsResponseData(
        uuid = "topic",
        name = "CASSI notification test",
        color = 0,
        streamUuid = "stream",
        updatedAt = "2026-10-16T05:00:00Z",
        unreadCount = 3,
        activeUnreadCount = activeUnread,
        isDone = false,
        isDefault = false,
        notificationMode = "mute",
    )

    private fun folder() = FolderResponseData(
        uuid = "folder",
        title = "CASSI notification test",
        unreadCount = 0,
        systemType = "created",
        creationDate = "2026-10-16T05:00:00Z",
    )

    private fun item(streamUuid: String, unread: Int) = FolderItem(
        "item-$streamUuid", streamUuid, "stream", unread,
    )
}
