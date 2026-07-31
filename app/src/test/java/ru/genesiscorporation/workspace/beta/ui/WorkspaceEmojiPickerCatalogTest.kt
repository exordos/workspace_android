package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEmojiPickerCatalogTest {
    @Test
    fun `builder collapses glyph aliases and keeps skin tones distinct`() {
        val entries = buildWorkspaceEmojiPickerEntries(
            linkedMapOf(
                "thumbs_up" to "👍",
                "+1" to "👍",
                "thumbsup" to "👍",
                "thumbs_up_tone3" to "👍🏽",
                "" to "ignored",
                "blank_glyph" to "",
            ),
        )

        assertEquals(listOf("👍", "👍🏽"), entries.map { it.glyph })
        assertEquals("+1", entries.first().primaryShortcode)
        assertEquals(
            listOf("+1", "thumbsup", "thumbs_up"),
            entries.first().aliases,
        )
        assertEquals(
            "thumbs_up_tone3",
            entries.last().primaryShortcode,
        )
    }

    @Test
    fun `search accepts glyph shortcode punctuation spaces and aliases`() {
        val entries = buildWorkspaceEmojiPickerEntries(
            linkedMapOf(
                "smile" to "😄",
                "thumbs_up" to "👍",
                "thumbsup" to "👍",
                "open_mouth" to "😮",
            ),
        )

        assertEquals(
            listOf("👍"),
            filterWorkspaceEmojiPickerEntries(
                entries,
                ":THUMBS-UP:",
            ).map { it.glyph },
        )
        assertEquals(
            listOf("😮"),
            filterWorkspaceEmojiPickerEntries(
                entries,
                "open mouth",
            ).map { it.glyph },
        )
        assertEquals(
            listOf("😄"),
            filterWorkspaceEmojiPickerEntries(
                entries,
                "😄",
            ).map { it.glyph },
        )
        assertEquals(
            listOf("👍"),
            filterWorkspaceEmojiPickerEntries(
                entries,
                "thumbsup",
            ).map { it.glyph },
        )
        assertTrue(
            filterWorkspaceEmojiPickerEntries(
                entries,
                "missing",
            ).isEmpty(),
        )
    }

    @Test
    fun `reaction display resolves server names and preserves unicode`() {
        val resolver = mapOf(
            "test_tube" to "🧪",
            "thumbs_up" to "👍",
        )::get

        assertEquals(
            "🧪",
            workspaceReactionDisplayText("test_tube", resolver),
        )
        assertEquals(
            "👍",
            workspaceReactionDisplayText("👍", resolver),
        )
        assertEquals(
            ":custom_party:",
            workspaceReactionDisplayText("custom_party", resolver),
        )
        assertEquals("", workspaceReactionDisplayText(" ", resolver))
    }
}
