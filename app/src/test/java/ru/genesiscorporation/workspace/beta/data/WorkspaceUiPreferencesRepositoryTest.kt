package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceUiPreferencesRepositoryTest {
    @Test
    fun `missing and malformed payloads use safe defaults`() {
        assertEquals(
            WorkspaceUiPreferences(),
            decodeWorkspaceUiPreferences(null),
        )
        assertEquals(
            WorkspaceUiPreferences(),
            decodeWorkspaceUiPreferences("{not-json"),
        )
    }

    @Test
    fun `unknown enum values do not discard valid independent fields`() {
        val decoded = decodeWorkspaceUiPreferences(
            """
            {
              "themeMode": "FUTURE_THEME",
              "prioritizePersonalUnread": true,
              "prioritizeUnmutedUnreadChannels": true,
              "chatListDensity": "FUTURE_DENSITY",
              "notificationSound": "FUTURE_SOUND",
              "futureField": "retained-by-newer-client"
            }
            """.trimIndent(),
        )

        assertEquals(WorkspaceThemeMode.SYSTEM, decoded.themeMode)
        assertTrue(decoded.prioritizePersonalUnread)
        assertTrue(decoded.prioritizeUnmutedUnreadChannels)
        assertEquals(ChatListDensity.STANDARD, decoded.chatListDensity)
        assertEquals(
            WorkspaceNotificationSound.DEFAULT,
            decoded.notificationSound,
        )
    }

    @Test
    fun `round trip preserves every supported preference`() {
        val expected = WorkspaceUiPreferences(
            themeMode = WorkspaceThemeMode.DARK,
            prioritizePersonalUnread = true,
            prioritizeUnmutedUnreadChannels = false,
            chatListDensity = ChatListDensity.COMPACT,
            notificationSound = WorkspaceNotificationSound.GLASS,
        )

        val actual = decodeWorkspaceUiPreferences(
            encodeWorkspaceUiPreferences(expected),
        )

        assertEquals(expected, actual)
        assertFalse(actual.prioritizeUnmutedUnreadChannels)
        assertEquals(WorkspaceNotificationSound.GLASS, actual.notificationSound)
    }
}
