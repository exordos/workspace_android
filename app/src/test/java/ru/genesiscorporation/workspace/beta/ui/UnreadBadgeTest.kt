package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadBadgeTest {
    @Test
    fun keepsSingleDigitAndCapsLargeCounts() {
        assertEquals("5", unreadBadgeLabel(5))
        assertEquals("999", unreadBadgeLabel(1_204))
    }

    @Test
    fun showsAtSignForUnreadMention() {
        assertEquals("@", unreadBadgeLabel(37, mentioned = true))
    }
}
