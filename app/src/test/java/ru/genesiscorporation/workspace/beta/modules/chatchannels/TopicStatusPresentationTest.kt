package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

class TopicStatusPresentationTest {

    @Test
    fun `active topics precede completed topics regardless of recency`() {
        val topics = listOf(
            topic("done-new", isDone = true, updatedAt = "2026-08-02T12:00:00Z"),
            topic("active-old", isDone = false, updatedAt = "2026-08-01T12:00:00Z"),
        )

        assertEquals(
            listOf("active-old", "done-new"),
            orderTopicsForDisplay(topics).map(TopicsResponseData::uuid),
        )
    }

    @Test
    fun `topics keep newest first order inside each status group`() {
        val topics = listOf(
            topic("done-old", isDone = true, updatedAt = "2026-08-01T10:00:00Z"),
            topic("active-old", isDone = false, updatedAt = "2026-08-01T09:00:00Z"),
            topic("done-new", isDone = true, updatedAt = "2026-08-02T10:00:00Z"),
            topic("active-new", isDone = false, updatedAt = "2026-08-02T09:00:00Z"),
        )

        assertEquals(
            listOf("active-new", "active-old", "done-new", "done-old"),
            orderTopicsForDisplay(topics).map(TopicsResponseData::uuid),
        )
    }

    @Test
    fun `topic header retains completed state for legacy list`() {
        val completed = TopicHeader.from(
            topic = topic("done", isDone = true, updatedAt = "2026-08-02T10:00:00Z"),
            channelName = "Channel",
            channelId = "stream-id",
            lastMessage = null,
        )
        val active = TopicHeader.from(
            topic = topic("active", isDone = false, updatedAt = "2026-08-02T09:00:00Z"),
            channelName = "Channel",
            channelId = "stream-id",
            lastMessage = null,
        )

        assertTrue(completed.isDone)
        assertFalse(active.isDone)
    }

    private fun topic(
        uuid: String,
        isDone: Boolean,
        updatedAt: String,
    ) = TopicsResponseData(
        uuid = uuid,
        name = uuid,
        streamUuid = "stream-id",
        updatedAt = updatedAt,
        unreadCount = 0,
        isDone = isDone,
        isDefault = false,
    )
}
