package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMarkdownSpoilerTest {
    @Test
    fun `inline spoilers render functional internal links until revealed`() {
        val document = parseWorkspaceInlineSpoilers(
            "Before ||secret|| and ||second **value**|| after",
        )

        assertEquals(setOf(0, 1), document.spoilerIds)
        assertEquals(
            "Before [Show](urn:workspace-mobile-inline-spoiler:0) and " +
                "[Show](urn:workspace-mobile-inline-spoiler:1) after",
            document.render(emptySet(), "Show", "Hide"),
        )
        assertEquals(
            "Before [Hide](urn:workspace-mobile-inline-spoiler:0) secret and " +
                "[Hide](urn:workspace-mobile-inline-spoiler:1) " +
                "second **value** after",
            document.render(setOf(0, 1), "Show", "Hide"),
        )
    }

    @Test
    fun `inline spoiler label is escaped as markdown`() {
        val document = parseWorkspaceInlineSpoilers("||secret||")

        assertEquals(
            "[Show \\[safe\\]](urn:workspace-mobile-inline-spoiler:0)",
            document.render(emptySet(), "Show [safe]", "Hide"),
        )
    }

    @Test
    fun `inline code fenced code link destinations and unmatched pairs stay inert`() {
        val markdown = listOf(
            "`||inline code||`",
            "",
            "```md",
            "||fenced code||",
            "```",
            "",
            "[link](https://example.test/a||b||c)",
            "empty |||| unmatched ||tail",
            "real ||secret||",
        ).joinToString("\n")
        val document = parseWorkspaceInlineSpoilers(markdown)

        assertEquals(setOf(0), document.spoilerIds)
        val hidden = document.render(emptySet(), "Show", "Hide")
        assertTrue(hidden.contains("`||inline code||`"))
        assertTrue(hidden.contains("||fenced code||"))
        assertTrue(hidden.contains("https://example.test/a||b||c"))
        assertTrue(hidden.contains("empty |||| unmatched ||tail"))
        assertTrue(
            hidden.endsWith(
                "real [Show](urn:workspace-mobile-inline-spoiler:0)",
            ),
        )
    }

    @Test
    fun `block spoilers require a matching same-character fence`() {
        val segments = parseWorkspaceBlockSpoilers(
            """
            Before
            ```spoiler Hidden details
            payload **bold**
            ````
            After
            """.trimIndent(),
        )

        assertEquals(3, segments.size)
        assertEquals(
            "Before\n",
            (segments[0] as WorkspaceBlockSpoilerSegment.Text).markdown,
        )
        assertEquals(
            WorkspaceBlockSpoilerSegment.Spoiler(
                id = 0,
                header = "Hidden details",
                bodyMarkdown = "payload **bold**",
            ),
            segments[1],
        )
        assertEquals(
            "\nAfter",
            (segments[2] as WorkspaceBlockSpoilerSegment.Text).markdown,
        )
    }

    @Test
    fun `tilde spoiler defaults its header and unclosed fence remains code`() {
        val parsed = parseWorkspaceBlockSpoilers(
            "~~~SpOiLeR\nsecret\n~~~\n\n```spoiler Missing\npayload",
        )

        val spoiler = parsed
            .filterIsInstance<WorkspaceBlockSpoilerSegment.Spoiler>()
            .single()
        assertNull(spoiler.header)
        assertEquals("secret", spoiler.bodyMarkdown)
        assertTrue(
            parsed
                .filterIsInstance<WorkspaceBlockSpoilerSegment.Text>()
                .joinToString("") { it.markdown }
                .contains("```spoiler Missing\npayload"),
        )
    }

    @Test
    fun `spoiler syntax inside ordinary fenced code stays inert`() {
        val markdown = """
            ````markdown
            ```spoiler Not interactive
            payload
            ```
            ````
            ```spoiler Interactive
            secret
            ```
        """.trimIndent()

        val parsed = parseWorkspaceBlockSpoilers(markdown)

        val spoiler = parsed
            .filterIsInstance<WorkspaceBlockSpoilerSegment.Spoiler>()
            .single()
        assertEquals("Interactive", spoiler.header)
        assertEquals("secret", spoiler.bodyMarkdown)
        assertTrue(
            (parsed.first() as WorkspaceBlockSpoilerSegment.Text)
                .markdown
                .contains("```spoiler Not interactive\npayload\n```"),
        )
    }

    @Test
    fun `internal spoiler urn parser is strict and bounded`() {
        assertEquals(
            63,
            parseWorkspaceInlineSpoilerUrn(
                "urn:workspace-mobile-inline-spoiler:63",
            ),
        )
        listOf(
            "urn:workspace-mobile-inline-spoiler:",
            "urn:workspace-mobile-inline-spoiler:-1",
            "urn:workspace-mobile-inline-spoiler:64",
            "urn:workspace-mobile-inline-spoiler:1?x=2",
            "URN:workspace-mobile-inline-spoiler:1",
            "https://example.test",
        ).forEach { assertNull(parseWorkspaceInlineSpoilerUrn(it)) }
    }
}
