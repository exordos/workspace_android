package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `unread mention is colored at sign`() {
        assertEquals(
            StreamUnreadBadgeState(muted = false, mentioned = true),
            streamUnreadBadgeState(
                notificationMode = "all_messages",
                hasUnreadMention = true,
            ),
        )
    }

    @Test
    fun `unread mention remains visible on muted stream`() {
        assertEquals(
            StreamUnreadBadgeState(muted = false, mentioned = true),
            streamUnreadBadgeState(
                notificationMode = "muted",
                hasUnreadMention = true,
            ),
        )
    }
}
