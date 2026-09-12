package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class ExternalMessageShareTest {
    @Test fun `external payload materializes quotes attachments and authors in source order`() = runBlocking {
        val quote = message(SECOND, "Original text", "Quoted Author")
        val first = message(FIRST,
            "[Quoted Author](urn:quote:$SECOND)\n\nReply from [Someone](urn:user:$SECOND)\n\n[report.pdf](urn:file:$FILE)", "CASSI")
        val result = materializeExternalMessages(listOf(first, message(THIRD, "Last text", "Last Author"))) { quote }
        assertTrue(result.text.contains("CASSI:\n> Quoted Author:\n> Original text"))
        assertTrue(result.text.contains("Reply from Someone"))
        assertTrue(result.text.indexOf("CASSI:") < result.text.indexOf("Last Author:"))
        assertFalse(result.text.contains("urn:"))
        assertEquals(listOf(ExternalShareFile(FILE, "report.pdf")), result.files)
    }

    @Test fun `cyclic inaccessible and invalid attachment references fail without leaking urns`() = runBlocking {
        val selfQuote = message(FIRST, "[CASSI](urn:quote:$FIRST)", "CASSI")
        assertTrue(runCatching { materializeExternalMessages(listOf(selfQuote)) { selfQuote } }.isFailure)
        assertTrue(runCatching { materializeExternalMessages(listOf(message(FIRST, "[Other](urn:quote:$SECOND)", "CASSI"))) { null } }.isFailure)
        assertTrue(runCatching { materializeExternalMessages(listOf(message(FIRST, "[bad](urn:file:../../bad)", "CASSI"))) { null } }.isFailure)
    }

    @Test fun `attachment references are deduplicated and MIME type matches every attachment`() = runBlocking {
        val result = materializeExternalMessages(listOf(message(FIRST, "![a.png](urn:image:$FILE)\n[copy.png](urn:file:$FILE)", "CASSI"))) { null }
        assertEquals(1, result.files.size)
        assertEquals("text/plain", commonShareMimeType(emptyList()))
        assertEquals("image/png", commonShareMimeType(listOf("image/png", "image/png")))
        assertEquals("image/*", commonShareMimeType(listOf("image/png", "image/jpeg")))
        assertEquals("*/*", commonShareMimeType(listOf("image/png", "application/pdf")))
    }

    @Test fun `selected quote text shares only the captured snippet without resolving its source`() = runBlocking {
        var resolutions = 0
        val source = message(FIRST, "[Quoted Author](urn:quote:$FIRST?text=Selected%20excerpt%20only)", "CASSI")
        val result = materializeExternalMessages(listOf(source)) {
            resolutions++
            message(FIRST, "Full source must not be shared", "Quoted Author")
        }
        assertEquals(0, resolutions)
        assertTrue(result.text.contains("Quoted Author:\n> Selected excerpt only"))
        assertFalse(result.text.contains("Full source"))
        assertFalse(result.text.contains("urn:"))
        assertTrue(result.files.isEmpty())
    }

    @Test fun `plain forward snapshot shares literal reference labels without resolving or attaching them`() = runBlocking {
        var resolutions = 0
        val snapshot = buildForwardSnapshotReference(
            "Original Author",
            SECOND,
            "[literal file](urn:file:$FILE) and [literal quote](urn:quote:$THIRD)",
            plainText = true,
        )
        val result = materializeExternalMessages(listOf(message(FIRST, snapshot, "CASSI"))) {
            resolutions++
            message(THIRD, "Must not be shared", "Quoted Author")
        }
        assertEquals(0, resolutions)
        assertTrue(result.files.isEmpty())
        assertTrue(result.text.contains("literal file and literal quote"))
        assertFalse(result.text.contains("Must not be shared"))
        assertFalse(result.text.contains("urn:"))
    }

    companion object {
        private const val FIRST = "00000000-0000-0000-0000-000000000001"
        private const val SECOND = "00000000-0000-0000-0000-000000000002"
        private const val THIRD = "00000000-0000-0000-0000-000000000003"
        private const val FILE = "00000000-0000-0000-0000-000000000004"
        private fun message(uuid: String, content: String, author: String) = MessageResponse(uuid,
            "2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z", FIRST, SECOND, THIRD, THIRD,
            MessageResponsePayload("markdown", content), true, emptyMap(), true,
            UserResponseData(username = author, uuid = THIRD, status = "active", avatar = ""))
    }
}
