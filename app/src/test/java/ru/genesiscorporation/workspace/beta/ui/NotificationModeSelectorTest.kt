package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.R

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
            listOf("mute", "default", "follow"),
            topicNotificationModeOptions().map(NotificationModeOption::value),
        )
        assertEquals(
            listOf("mute", "default", "unmute", "follow"),
            topicNotificationModeOptions("unmute").map(NotificationModeOption::value),
        )
    }

    @Test
    fun `topic unmute and follow stay distinct`() {
        assertFalse(notificationModeMatches("unmute", "follow"))
        assertTrue(notificationModeMatches("unmute", "unmute"))
        assertTrue(notificationModeMatches("muted", "muted"))
        assertFalse(notificationModeMatches("mute", "default"))
    }

    @Test
    fun `notification glyphs use the figma vector proportions`() {
        assertEquals(
            R.drawable.ic_topic_notification_muted,
            notificationModeGlyphVisual(NotificationModeGlyph.MUTED).drawableRes,
        )
        assertEquals(
            DpSize(width = 18.dp, height = 19.dp),
            notificationModeGlyphVisual(NotificationModeGlyph.MUTED).size,
        )
        assertEquals(
            R.drawable.ic_topic_notification_inherit,
            notificationModeGlyphVisual(NotificationModeGlyph.DEFAULT).drawableRes,
        )
        assertEquals(
            DpSize(width = 14.dp, height = 19.dp),
            notificationModeGlyphVisual(NotificationModeGlyph.DEFAULT).size,
        )
        assertEquals(
            R.drawable.ic_topic_notification_mentions,
            notificationModeGlyphVisual(NotificationModeGlyph.MENTIONS).drawableRes,
        )
        assertEquals(
            DpSize(width = 18.dp, height = 18.dp),
            notificationModeGlyphVisual(NotificationModeGlyph.MENTIONS).size,
        )
        assertEquals(
            R.drawable.ic_topic_notification_follow,
            notificationModeGlyphVisual(NotificationModeGlyph.FOLLOW).drawableRes,
        )
        assertEquals(
            DpSize(width = 16.dp, height = 16.dp),
            notificationModeGlyphVisual(NotificationModeGlyph.FOLLOW).size,
        )
    }
}
