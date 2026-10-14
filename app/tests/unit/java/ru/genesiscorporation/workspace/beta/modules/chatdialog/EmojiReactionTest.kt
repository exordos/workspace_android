package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiReactionTest {
    @Test
    fun unicodeAndAliasesLoadTheBundledEmojiDatabase() {
        assertEquals("😀", toUnicodeEmoji("😀"))
        assertEquals("😀", toUnicodeEmoji("grinning"))
        assertEquals("😀", toUnicodeEmoji(":grinning:"))
        assertEquals("👍", toUnicodeEmoji("+1"))
    }

    @Test
    fun unknownCustomReactionRemainsReadable() {
        assertEquals("custom_reaction", toUnicodeEmoji("custom_reaction"))
        assertEquals("", toUnicodeEmoji(" "))
    }
}
