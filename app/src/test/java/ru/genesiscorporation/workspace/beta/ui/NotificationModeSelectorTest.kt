package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationModeSelectorTest {

    @Test
    fun `stream selector matches desktop notification contract`() {
        assertEquals(
            listOf("mentions_only", "muted", "all_messages"),
            STREAM_NOTIFICATION_MODE_OPTIONS.map(NotificationModeOption::value),
        )
    }

    @Test
    fun `topic selector matches desktop notification contract`() {
        assertEquals(
            listOf("mute", "default", "follow"),
            TOPIC_NOTIFICATION_MODE_OPTIONS.map(NotificationModeOption::value),
        )
        assertEquals(
            listOf("mute", "default", "unmute", "follow"),
            topicNotificationModeOptions("muted").map(NotificationModeOption::value),
        )
    }

    @Test
    fun `topic unmute and follow stay distinct`() {
        assertFalse(notificationModeMatches("unmute", "follow"))
        assertTrue(notificationModeMatches("unmute", "unmute"))
        assertTrue(notificationModeMatches("muted", "muted"))
        assertFalse(notificationModeMatches("mute", "default"))
    }
}
