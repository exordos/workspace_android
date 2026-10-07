package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.*
import org.junit.Test

class AttachmentFileNameTest {
    @Test fun `ordinary filename and extension are preserved`() {
        assertEquals("Отчёт.pdf", localAttachmentFileName("Отчёт.pdf"))
        assertEquals("attachment", localAttachmentFileName("..."))
        assertEquals("report.pdf", localAttachmentFileName("../../report.pdf"))
        assertEquals("report.pdf", localAttachmentFileName("C:\\folder\\report.pdf"))
    }

    @Test fun `ASCII and multibyte names fit the filesystem byte limit with UUID prefix`() {
        for (name in listOf("report".repeat(100) + ".pdf", "Отчёт".repeat(100) + ".pdf",
            "资料".repeat(100) + ".pdf")) {
            val local = localAttachmentFileName(name)
            assertTrue(local.endsWith(".pdf"))
            assertTrue(local.toByteArray(Charsets.UTF_8).size <= 180)
            assertTrue(("00000000-0000-0000-0000-000000000001-$local").toByteArray(Charsets.UTF_8).size <= 255)
            assertFalse(local.contains('\uFFFD'))
            assertEquals(local, localAttachmentFileName(name))
        }
    }

    @Test fun `truncation keeps distinct attachment names distinct`() {
        val prefix = "report".repeat(100)
        assertNotEquals(localAttachmentFileName("${prefix}a.pdf"), localAttachmentFileName("${prefix}b.pdf"))
    }
}
