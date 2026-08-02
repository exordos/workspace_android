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
    fun `fully muted stream stays muted even with unread mention`() {
        assertEquals(
            StreamUnreadBadgeState(muted = true, mentioned = false),
            streamUnreadBadgeState(
                notificationMode = "muted",
                hasUnreadMention = true,
            ),
        )
    }
}
