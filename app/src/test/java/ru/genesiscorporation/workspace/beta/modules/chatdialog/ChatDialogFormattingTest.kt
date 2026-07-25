package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatDialogFormattingTest {
    @Test
    fun parsesSingleWorkspaceImageLink() {
        val upload = "Подпись\n[photo.png](urn:image:1234)".parseUserUploadMarkdownOrNull()

        assertNotNull(upload)
        assertEquals("Подпись", upload?.caption)
        assertEquals("photo.png", upload?.fileName)
        assertEquals("urn:image:1234", upload?.relativePath)
    }

    @Test
    fun parsesMultipleMarkdownImages() {
        val upload = """
            Галерея
            ![one.png](urn:image:first)
            ![two.png](urn:image:second)
        """.trimIndent().parseUserUploadMarkdownOrNull()

        assertNotNull(upload)
        assertEquals("Галерея", upload?.caption)
        assertEquals(
            listOf("urn:image:first", "urn:image:second"),
            upload?.attachments?.map { it.relativePath },
        )
    }
}
