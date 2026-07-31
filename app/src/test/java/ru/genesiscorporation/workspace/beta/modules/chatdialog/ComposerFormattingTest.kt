package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerFormattingTest {
    @Test
    fun `every inline action uses its desktop compatible marker`() {
        val markers = listOf(
            ComposerFormattingAction.BOLD to "**",
            ComposerFormattingAction.ITALIC to "*",
            ComposerFormattingAction.STRIKETHROUGH to "~~",
            ComposerFormattingAction.CODE to "`",
            ComposerFormattingAction.SPOILER to "||",
        )

        markers.forEach { (action, marker) ->
            val formatted = applyComposerFormatting(
                value = TextFieldValue(
                    text = "x",
                    selection = TextRange(0, 1),
                ),
                action = action,
                linkText = "link text",
            )

            assertEquals("$marker" + "x" + marker, formatted.text)
            assertEquals(
                TextRange(formatted.text.length),
                formatted.selection,
            )
        }
    }

    @Test
    fun `bold wraps exact selection and collapses cursor after it`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "say hello now",
                selection = TextRange(4, 9),
            ),
            action = ComposerFormattingAction.BOLD,
            linkText = "link text",
        )

        assertEquals("say **hello** now", formatted.text)
        assertEquals(TextRange(13), formatted.selection)
    }

    @Test
    fun `empty inline action inserts paired markers around cursor`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "ab",
                selection = TextRange(1),
            ),
            action = ComposerFormattingAction.SPOILER,
            linkText = "link text",
        )

        assertEquals("a||||b", formatted.text)
        assertEquals(TextRange(3), formatted.selection)
    }

    @Test
    fun `quote prefixes every selected line`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "before\none\ntwo\nafter",
                selection = TextRange(7, 14),
            ),
            action = ComposerFormattingAction.QUOTE,
            linkText = "link text",
        )

        assertEquals("before\n> one\n> two\nafter", formatted.text)
        assertEquals(TextRange(18), formatted.selection)
    }

    @Test
    fun `numbered list uses desktop compatible one based prefixes`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "alpha\nbeta",
                selection = TextRange(0, 10),
            ),
            action = ComposerFormattingAction.NUMBERED_LIST,
            linkText = "link text",
        )

        assertEquals("1. alpha\n2. beta", formatted.text)
        assertEquals(TextRange(16), formatted.selection)
    }

    @Test
    fun `empty bullet action inserts first prefix at cursor`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "ab",
                selection = TextRange(1),
            ),
            action = ComposerFormattingAction.BULLETED_LIST,
            linkText = "link text",
        )

        assertEquals("a- b", formatted.text)
        assertEquals(TextRange(3), formatted.selection)
    }

    @Test
    fun `selected code block is fenced and cursor follows it`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "val answer = 42",
                selection = TextRange(0, 15),
            ),
            action = ComposerFormattingAction.CODE_BLOCK,
            linkText = "link text",
        )

        assertEquals("```\nval answer = 42\n```", formatted.text)
        assertEquals(TextRange(formatted.text.length), formatted.selection)
    }

    @Test
    fun `empty code block positions cursor inside fences`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "prefix ",
                selection = TextRange(7),
            ),
            action = ComposerFormattingAction.CODE_BLOCK,
            linkText = "link text",
        )

        assertEquals("prefix ```\n\n```", formatted.text)
        assertEquals(TextRange(11), formatted.selection)
    }

    @Test
    fun `link wraps selected label and selects editable URL`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "read docs",
                selection = TextRange(5, 9),
            ),
            action = ComposerFormattingAction.LINK,
            linkText = "link text",
        )

        assertEquals("read [docs](https://)", formatted.text)
        assertEquals(TextRange(12, 20), formatted.selection)
    }

    @Test
    fun `link without selection uses localized fallback label`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "",
                selection = TextRange.Zero,
            ),
            action = ComposerFormattingAction.LINK,
            linkText = "текст ссылки",
        )

        assertEquals("[текст ссылки](https://)", formatted.text)
        assertEquals(TextRange(15, 23), formatted.selection)
    }

    @Test
    fun `link selects placeholder URL even when label contains https`() {
        val formatted = applyComposerFormatting(
            value = TextFieldValue(
                text = "https://docs",
                selection = TextRange(0, 12),
            ),
            action = ComposerFormattingAction.LINK,
            linkText = "link text",
        )

        assertEquals("[https://docs](https://)", formatted.text)
        assertEquals(
            "https://",
            formatted.text.substring(
                formatted.selection.min,
                formatted.selection.max,
            ),
        )
    }

    @Test
    fun `reconciliation clamps selection to authoritative bounded text`() {
        val candidate = TextFieldValue(
            text = "accepted****",
            selection = TextRange(12),
        )

        assertEquals(
            TextFieldValue(
                text = "accepted",
                selection = TextRange(8),
            ),
            reconcileComposerEditorValue(
                candidate = candidate,
                acceptedText = "accepted",
            ),
        )
    }
}
