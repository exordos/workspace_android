package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMarkdownStructureTest {
    @Test
    fun `plain and inline markdown do not invent block semantics`() {
        val structure = analyzeWorkspaceMarkdownStructure(
            "Hello **bold** with `inline code` and [link](https://example.test)",
        )

        assertTrue(structure.blockKinds.isEmpty())
        assertTrue(structure.codeLanguages.isEmpty())
        assertFalse(structure.sourceWasTruncated)
        assertNull(structure.accessibilityDescription(TEST_LABELS))
    }

    @Test
    fun `quote lists and code preserve desktop block meaning`() {
        val structure = analyzeWorkspaceMarkdownStructure(
            """
            > quoted
            >
            > 1. nested first
            > 2. nested second

            - first
            - second

            ```kotlin
            val answer = 42
            ```
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                WorkspaceMarkdownBlockKind.Quote,
                WorkspaceMarkdownBlockKind.OrderedList,
                WorkspaceMarkdownBlockKind.BulletList,
                WorkspaceMarkdownBlockKind.CodeBlock,
            ),
            structure.blockKinds,
        )
        assertEquals(listOf("kotlin"), structure.codeLanguages)
        assertEquals(
            "Цитата. Нумерованный список. Маркированный список. Блок кода: kotlin",
            structure.accessibilityDescription(TEST_LABELS),
        )
    }

    @Test
    fun `indented and repeated fenced code are one bounded semantic class`() {
        val structure = analyzeWorkspaceMarkdownStructure(
            """
                indented code

            ```typescript extra attributes
            const value = 1
            ```

            ```typescript
            const value = 2
            ```
            """.trimIndent(),
        )

        assertEquals(
            listOf(WorkspaceMarkdownBlockKind.CodeBlock),
            structure.blockKinds,
        )
        assertEquals(listOf("typescript"), structure.codeLanguages)
        assertEquals(
            "Блок кода: typescript",
            structure.accessibilityDescription(TEST_LABELS),
        )
    }

    @Test
    fun `code language label drops control and markup characters`() {
        val structure = analyzeWorkspaceMarkdownStructure(
            "```kotlin<script>\nval answer = 42\n```",
        )

        assertEquals(listOf("kotlinscript"), structure.codeLanguages)
        assertEquals(
            "Блок кода: kotlinscript",
            structure.accessibilityDescription(TEST_LABELS),
        )
    }

    @Test
    fun `crlf input is normalized before structural analysis`() {
        val structure = analyzeWorkspaceMarkdownStructure(
            "> quote\r\n\r\n1. first\r\n2. second",
        )

        assertEquals(
            listOf(
                WorkspaceMarkdownBlockKind.Quote,
                WorkspaceMarkdownBlockKind.OrderedList,
            ),
            structure.blockKinds,
        )
    }

    @Test
    fun `very long source is bounded and reported without throwing`() {
        val structure = analyzeWorkspaceMarkdownStructure(
            "plain ".repeat(8_000) + "\n\n```kotlin\nlate code\n```",
        )

        assertTrue(structure.sourceWasTruncated)
        assertEquals(
            "Длинное сообщение",
            structure.accessibilityDescription(TEST_LABELS),
        )
    }

    private companion object {
        val TEST_LABELS = WorkspaceMarkdownAccessibilityLabels(
            quote = "Цитата",
            orderedList = "Нумерованный список",
            bulletList = "Маркированный список",
            codeBlock = "Блок кода",
            longMessage = "Длинное сообщение",
        )
    }
}
