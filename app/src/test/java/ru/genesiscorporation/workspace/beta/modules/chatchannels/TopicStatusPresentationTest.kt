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
    fun `muted unread topics follow unmuted unread topics`() {
        val topics = listOf(
            topic(
                uuid = "muted-new",
                isDone = false,
                updatedAt = "2026-08-02T12:00:00Z",
                unreadCount = 3,
                notificationMode = "mute",
            ),
            topic(
                uuid = "active-old",
                isDone = false,
                updatedAt = "2026-08-01T12:00:00Z",
                unreadCount = 1,
                notificationMode = "default",
            ),
        )

        assertEquals(
            listOf("active-old", "muted-new"),
            orderTopicsForDisplay(topics).map(TopicsResponseData::uuid),
        )
    }

    @Test
    fun `inherited muted topics follow explicit active topics even when read`() {
        val inherited = topic(
            uuid = "inherited",
            isDone = false,
            updatedAt = "2026-08-04T12:00:00Z",
            notificationMode = "default",
        )
        val explicit = topic(
            uuid = "explicit",
            isDone = false,
            updatedAt = "2026-08-01T12:00:00Z",
            notificationMode = "unmute",
        )

        assertEquals(
            listOf("explicit", "inherited"),
            orderTopicsForDisplay(
                topics = listOf(inherited, explicit),
                streamNotificationMode = "muted",
            ).map(TopicsResponseData::uuid),
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

    @Test
    fun `legacy topic header inherits muted counter presentation`() {
        val header = TopicHeader.from(
            topic = topic(
                uuid = "legacy-muted",
                isDone = false,
                updatedAt = "2026-08-04T10:00:00Z",
                unreadCount = 3,
                notificationMode = "default",
            ),
            channelName = "Channel",
            channelId = "stream-id",
            lastMessage = null,
            streamNotificationMode = "muted",
        )

        assertEquals(3, header.unreadCount)
        assertTrue(header.unreadPassive)
        assertTrue(header.effectivelyMuted)
    }

    private fun topic(
        uuid: String,
        isDone: Boolean,
        updatedAt: String,
        unreadCount: Int = 0,
        notificationMode: String = "default",
    ) = TopicsResponseData(
        uuid = uuid,
        name = uuid,
        streamUuid = "stream-id",
        updatedAt = updatedAt,
        unreadCount = unreadCount,
        notificationMode = notificationMode,
        isDone = isDone,
        isDefault = false,
    )
}
