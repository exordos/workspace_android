package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerEmojiTest {
    @Test
    fun `inserts a multi-codepoint emoji at the exact cursor`() {
        val result = insertComposerEmoji(
            value = TextFieldValue(
                text = "before after",
                selection = TextRange(7),
            ),
            glyph = "👩🏽‍💻",
        )

        assertEquals("before 👩🏽‍💻after", result.text)
        assertEquals(
            TextRange("before 👩🏽‍💻".length),
            result.selection,
        )
    }

    @Test
    fun `replaces only the selected range and collapses the cursor`() {
        val result = insertComposerEmoji(
            value = TextFieldValue(
                text = "hello world!",
                selection = TextRange(6, 11),
            ),
            glyph = "🌍",
        )

        assertEquals("hello 🌍!", result.text)
        assertEquals(TextRange("hello 🌍".length), result.selection)
    }

    @Test
    fun `supports a reversed selection`() {
        val result = insertComposerEmoji(
            value = TextFieldValue(
                text = "abc def",
                selection = TextRange(7, 4),
            ),
            glyph = "✅",
        )

        assertEquals("abc ✅", result.text)
        assertEquals(TextRange("abc ✅".length), result.selection)
    }

    @Test
    fun `rejects blank control and unbounded picker values`() {
        val value = TextFieldValue(
            text = "safe",
            selection = TextRange(2),
        )

        assertEquals(value, insertComposerEmoji(value, " "))
        assertEquals(value, insertComposerEmoji(value, "\n"))
        assertEquals(value, insertComposerEmoji(value, "x".repeat(65)))
    }
}
