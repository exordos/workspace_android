package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.DisplayedUnreadCount
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream

class StreamUnreadBadgeStateTest {
    @Test
    fun `mentions only ordinary unread is muted count`() {
        assertEquals(
            StreamUnreadBadgeState(muted = true, mentioned = false),
            streamUnreadBadgeState(
                notificationMode = "mentions_only",
                hasUnreadMention = false,
            ),
        )
    }

    @Test
    fun `mentions only unread mention is red at sign`() {
        assertEquals(
            StreamUnreadBadgeState(muted = false, mentioned = true),
            streamUnreadBadgeState(
                notificationMode = "mentions_only",
                hasUnreadMention = true,
            ),
        )
    }

    @Test
    fun `split mentions only unread mention keeps active numeric badge`() {
        assertEquals(
            StreamUnreadBadgeState(muted = false, mentioned = false),
            streamUnreadBadgeState(
                notificationMode = "mentions_only",
                hasUnreadMention = true,
                hasSplitCounters = true,
            ),
        )
    }

    @Test
    fun `fully muted stream stays muted even with unread mention`() {
        assertEquals(
            StreamUnreadBadgeState(muted = true, mentioned = false),
            streamUnreadBadgeState(
                notificationMode = "muted",
                hasUnreadMention = true,
            ),
        )
    }

    @Test
    fun `split active counter stays bright inside muted stream`() {
        assertEquals(
            StreamUnreadBadgeState(muted = false, mentioned = false),
            streamUnreadBadgeState(
                notificationMode = "muted",
                hasUnreadMention = false,
                displayedUnreadIsPassive = false,
                hasSplitCounters = true,
            ),
        )
    }

    @Test
    fun `split passive counter is gray regardless of stream mode`() {
        assertEquals(
            StreamUnreadBadgeState(muted = true, mentioned = false),
            streamUnreadBadgeState(
                notificationMode = "all_messages",
                hasUnreadMention = true,
                displayedUnreadIsPassive = true,
                hasSplitCounters = true,
            ),
        )
    }

    @Test
    fun `folder item counters override stream counters`() {
        val stream = stream(active = 7, passive = 3)
        val folderItem = folderItem(active = 0, passive = 2)

        assertEquals(
            DisplayedUnreadCount(count = 2, passive = true),
            displayedUnreadForStream(stream, folderItem),
        )
    }

    @Test
    fun `zero folder item does not fall back to stream counter`() {
        assertNull(
            displayedUnreadForStream(
                stream = stream(active = 7, passive = 3),
                folderItem = folderItem(active = 0, passive = 0),
            ),
        )
    }

    private fun stream(active: Int, passive: Int) = Stream(
        uuid = "stream",
        unreadCount = active + passive,
        activeUnreadCount = active,
        passiveUnreadCount = passive,
        updatedAt = "2026-08-04T10:00:00Z",
        name = "Stream",
        isPrivate = false,
    )

    private fun folderItem(active: Int, passive: Int) = FolderItem(
        uuid = "item",
        streamUuid = "stream",
        chatType = "stream",
        unreadCount = active + passive,
        activeUnreadCount = active,
        passiveUnreadCount = passive,
    )
}
