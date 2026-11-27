package com.exordos.workspace.data

import kotlinx.coroutines.flow.first
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.FolderItem
import com.exordos.workspace.data.remote.dto.FolderResponseData
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.data.uio.FolderUIO
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.data.uio.toUio
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
            context,
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
        var received: Triple<TopicUIO, Long, String>? = null
        repository.onPushTopicUpdated = { topic, epoch, generation -> received = Triple(topic, epoch, generation) }
        repository.didReceiveTopicEvent(Json.encodeToString(update), "updated", 300, "generation")
        assertEquals(Triple(update.toUio(), 300L, "generation"), received)
        received = null
        repository.didReceiveTopicEvent(Json.encodeToString(update), "updated")
        assertNull(received)
    }

    @Test
    fun topicUpdatedAppliesActiveUnreadToStoredTopic() = runBlocking {
        val before = topic(activeUnread = 0)
        val cached = before.toUio()
        repository.setInitialTopics(listOf(cached))
        val authoritative = before.copy(activeUnreadCount = 3, notificationMode = "follow")

        repository.didReceiveTopicEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative.toUio(), repository.topics.first().single())
        assertEquals(0, cached.activeUnreadCount)
    }

    @Test
    fun mutedAndReadTopicSnapshotsClearStoredActiveUnread() = runBlocking {
        val before = topic(activeUnread = 3).copy(notificationMode = "follow")
        repository.setInitialTopics(listOf(before).map { it.toUio() })
        val muted = before.copy(activeUnreadCount = 0, notificationMode = "mute")

        repository.didReceiveTopicEvent(Json.encodeToString(muted), "updated")

        assertEquals(muted.toUio(), repository.topics.first().single())
        val read = muted.copy(unreadCount = 0)
        repository.didReceiveTopicEvent(Json.encodeToString(read), "updated")
        assertEquals(read.toUio(), repository.topics.first().single())
    }

    @Test
    fun topicUpdatedInsertsPreviouslyUnknownTopic() = runBlocking {
        val before = topic(activeUnread = 0)
        val authoritative = before.copy(activeUnreadCount = 3, notificationMode = "follow")

        repository.didReceiveTopicEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative.toUio(), repository.topics.first().single())
    }

    @Test
    fun selectedFolderReceivesTheSameItemsAndCounterAsItsTab() = runBlocking {
        val before = folder().copy(unreadCount = 1, items = listOf(item("first", 1)))
        val cached = before.toUio()
        repository.setInitialFolders(listOf(cached))
        repository.updateCurrentlySelectedFolder(cached)
        val authoritative = before.copy(
            title = "CASSI updated folder",
            unreadCount = 17,
            items = before.items + item("second", 16),
        )

        repository.didReceiveFolderEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative.toUio(), repository.folders.first().single())
        assertEquals(authoritative.toUio(), repository.currentlySelectedFolder.value)
        assertEquals(listOf("first", "second"), repository.folderItemDao.observeFolderItems(before.uuid).first().map { it.streamUuid }.sorted())
    }

    @Test
    fun removingTheLastItemClearsTheSelectedFolderWithoutSwitchingTabs() = runBlocking {
        val before = folder().copy(unreadCount = 1, items = listOf(item("first", 1)))
        repository.setInitialFolders(listOf(before).map { it.toUio() })
        repository.updateCurrentlySelectedFolder(before.toUio())
        val authoritative = before.copy(unreadCount = 0, items = emptyList())

        repository.didReceiveFolderEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(authoritative.toUio(), repository.currentlySelectedFolder.value)
        assertEquals(authoritative.toUio(), repository.folders.first().single())
    }

    @Test
    fun updatingAnotherFolderKeepsTheCurrentSelection() = runBlocking {
        val selected = folder().copy(uuid = "selected")
        val other = folder()
        repository.setInitialFolders(listOf(selected, other).map { it.toUio() })
        repository.updateCurrentlySelectedFolder(selected.toUio())
        val authoritative = other.copy(unreadCount = 3)

        repository.didReceiveFolderEvent(Json.encodeToString(authoritative), "updated")

        assertEquals(listOf(selected.toUio(), authoritative.toUio()), repository.folders.first())
        assertEquals(selected.toUio(), repository.currentlySelectedFolder.value)
    }

    @Test
    fun deletingTheSelectedFolderFallsBackToTheFirstRemainingFolder() = runBlocking {
        val all = folder().copy(uuid = "all", systemType = "all")
        val deleted = folder()
        repository.setInitialFolders(listOf(all, deleted).map { it.toUio() })
        repository.updateCurrentlySelectedFolder(deleted.toUio())
        repository.setInitialTopics(listOf(topic(activeUnread = 3)).map { it.toUio() })

        repository.didReceiveFolderEvent("""{"uuid":"folder"}""", "deleted")

        assertEquals(listOf(all.toUio()), repository.folders.first())
        assertEquals(all.toUio(), repository.currentlySelectedFolder.value)
        assertEquals(1, repository.topics.first().count { it.streamUuid == "stream" })
    }

    @Test
    fun deletingTheLastFolderClearsItsSelection() = runBlocking {
        val deleted = folder()
        repository.setInitialFolders(listOf(deleted).map { it.toUio() })
        repository.updateCurrentlySelectedFolder(deleted.toUio())

        repository.didReceiveFolderEvent("""{"uuid":"folder"}""", "deleted")

        assertEquals(emptyList<FolderUIO>(), repository.folders.first())
        assertNull(repository.currentlySelectedFolder.value)
    }

    @Test
    fun duplicateAndUnselectedDeletionsDoNotDisturbOtherFolders() = runBlocking {
        val selected = folder().copy(uuid = "selected")
        repository.setInitialFolders(listOf(selected, folder()).map { it.toUio() })
        repository.updateCurrentlySelectedFolder(selected.toUio())

        repeat(2) {
            repository.didReceiveFolderEvent("""{"uuid":"folder"}""", "deleted")
        }
        repository.didReceiveFolderEvent("""{"uuid":"unknown"}""", "deleted")

        assertEquals(listOf(selected.toUio()), repository.folders.first())
        assertEquals(selected.toUio(), repository.currentlySelectedFolder.value)
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
        "item-$streamUuid", "folder", streamUuid, "stream", unread,
    )
}
