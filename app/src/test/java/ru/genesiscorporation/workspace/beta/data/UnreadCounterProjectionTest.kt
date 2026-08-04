package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

class UnreadCounterProjectionTest {
    @Test
    fun `authoritative stream counters update folder active total`() {
        val repository = EventsRepository()
        repository.setInitialStreams(listOf(stream(raw = 9, active = 7, passive = 2)))
        repository.setInitialFolders(
            listOf(folder(folderItem(raw = 9, active = 7, passive = 2))),
        )

        repository.updateStream(stream(raw = 9, active = 3, passive = 6))

        val item = repository.folders.value.single().items.single()
        assertEquals(9, item.unreadCount)
        assertEquals(3, item.activeUnreadCount)
        assertEquals(6, item.passiveUnreadCount)
        assertEquals(3, repository.folders.value.single().unreadCount)
    }

    @Test
    fun `topic realtime redistribution preserves split stream and folder counters`() {
        val repository = EventsRepository()
        repository.setInitialStreams(listOf(stream(raw = 4, active = 1, passive = 3)))
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(topic(mode = "unmute", raw = 2, active = 1, passive = 1)),
        )
        repository.setInitialFolders(
            listOf(folder(folderItem(raw = 4, active = 1, passive = 3))),
        )

        repository.updateTopic(topic(mode = "follow", raw = 2, active = 2, passive = 0))

        val projectedStream = repository.streams.value.single()
        val projectedItem = repository.folders.value.single().items.single()
        assertEquals(2, projectedStream.activeUnreadCount)
        assertEquals(2, projectedStream.passiveUnreadCount)
        assertEquals(2, projectedItem.activeUnreadCount)
        assertEquals(2, repository.folders.value.single().unreadCount)
    }

    @Test
    fun `optimistic read classifies unmute mentions and follow messages`() {
        val repository = EventsRepository()
        repository.setInitialStreams(listOf(stream(raw = 3, active = 2, passive = 1)))
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(mode = "unmute", raw = 2, active = 1, passive = 1),
                topic(
                    uuid = FOLLOW_TOPIC_UUID,
                    mode = "follow",
                    raw = 1,
                    active = 1,
                    passive = 0,
                ),
            ),
        )
        repository.setInitialFolders(
            listOf(folder(folderItem(raw = 3, active = 2, passive = 1))),
        )
        val mention = message("mention", TOPIC_UUID, mentioned = true)
        val ordinaryUnmute = message("ordinary-unmute", TOPIC_UUID, mentioned = false)
        val ordinaryFollow = message("ordinary-follow", FOLLOW_TOPIC_UUID, mentioned = false)
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(mention, ordinaryUnmute),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            FOLLOW_TOPIC_UUID,
            listOf(ordinaryFollow),
        )

        repository.markMessagesRead(listOf(mention.uuid, ordinaryUnmute.uuid, ordinaryFollow.uuid))

        val topics = repository.streamTopics.value.getValue(STREAM_UUID)
        assertEquals(listOf(0, 0), topics.map { it.activeUnreadCount })
        assertEquals(listOf(0, 0), topics.map { it.passiveUnreadCount })
        val projectedStream = repository.streams.value.single()
        assertEquals(0, projectedStream.unreadCount)
        assertEquals(0, projectedStream.activeUnreadCount)
        assertEquals(0, projectedStream.passiveUnreadCount)
        assertEquals(0, repository.folders.value.single().unreadCount)
    }

    @Test
    fun `optimistic read cannot over decrement split projections`() {
        val repository = EventsRepository()
        repository.setInitialStreams(listOf(stream(raw = 1, active = 1, passive = 0)))
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(topic(mode = "follow", raw = 1, active = 1, passive = 0)),
        )
        repository.setInitialFolders(
            listOf(folder(folderItem(raw = 1, active = 1, passive = 0))),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(
                message("first", TOPIC_UUID, mentioned = false),
                message("stale-extra", TOPIC_UUID, mentioned = false),
            ),
        )

        repository.markMessagesRead(listOf("first", "stale-extra"))

        val topic = repository.streamTopics.value.getValue(STREAM_UUID).single()
        val stream = repository.streams.value.single()
        val item = repository.folders.value.single().items.single()
        assertEquals(0, topic.unreadCount)
        assertEquals(0, topic.activeUnreadCount)
        assertEquals(0, stream.unreadCount)
        assertEquals(0, stream.activeUnreadCount)
        assertEquals(0, item.activeUnreadCount)
        assertEquals(0, repository.folders.value.single().unreadCount)
    }

    private fun stream(raw: Int, active: Int, passive: Int) = Stream(
        uuid = STREAM_UUID,
        unreadCount = raw,
        activeUnreadCount = active,
        passiveUnreadCount = passive,
        updatedAt = "2026-08-04T10:00:00Z",
        name = "Stream",
        isPrivate = false,
        notificationMode = "muted",
    )

    private fun topic(
        uuid: String = TOPIC_UUID,
        mode: String,
        raw: Int,
        active: Int,
        passive: Int,
    ) = TopicsResponseData(
        uuid = uuid,
        name = uuid,
        streamUuid = STREAM_UUID,
        updatedAt = "2026-08-04T10:00:00Z",
        unreadCount = raw,
        activeUnreadCount = active,
        passiveUnreadCount = passive,
        isDone = false,
        isDefault = false,
        notificationMode = mode,
    )

    private fun folderItem(raw: Int, active: Int, passive: Int) = FolderItem(
        uuid = "item",
        folderUuid = "folder",
        streamUuid = STREAM_UUID,
        chatType = "stream",
        unreadCount = raw,
        activeUnreadCount = active,
        passiveUnreadCount = passive,
    )

    private fun folder(item: FolderItem) = FolderResponseData(
        uuid = "folder",
        title = "All",
        unreadCount = item.activeUnreadCount ?: item.unreadCount,
        creationDate = "2026-08-04T10:00:00Z",
        items = listOf(item),
    )

    private fun message(
        uuid: String,
        topicUuid: String,
        mentioned: Boolean,
    ) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-08-04T10:00:00Z",
        createdAt = "2026-08-04T10:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = topicUuid,
        userUuid = "user",
        authorUuid = "user",
        payload = MessageResponsePayload(kind = "markdown", content = uuid),
        isOwn = false,
        reactions = emptyMap(),
        read = false,
        mentioned = mentioned,
    )

    private companion object {
        const val STREAM_UUID = "stream"
        const val TOPIC_UUID = "topic-unmute"
        const val FOLLOW_TOPIC_UUID = "topic-follow"
    }
}
