package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import java.io.File

class ForwardContentPreparationTest {
    @Test fun `materialized forwarding embeds text and destination-scoped attachments`() = runBlocking {
        val copies = mutableListOf<Triple<String, String, String>>()
        val source = message(SOURCE, "Private source text\n\n[report.pdf](urn:file:$FILE)")
        val preparation = ForwardContentPreparation(listOf(source), resolve = { null }, copyFile = { uuid, name, stream ->
            copies += Triple(uuid, name, stream); COPIED
        })
        val prepared = preparation.prepare(TARGET)
        assertTrue(prepared.contains("> Private source text"))
        assertTrue(prepared.contains("urn:file:$COPIED"))
        assertFalse(prepared.contains("urn:file:$FILE"))
        assertFalse(prepared.contains("urn:quote:"))
        assertEquals(listOf(Triple(FILE, "report.pdf", STREAM)), copies)
        assertEquals(prepared, preparation.prepare(TARGET))
        assertEquals(1, copies.size)
        assertTrue(source.payload.content.contains("urn:file:$FILE"))
    }

    @Test fun `legacy references are expanded before forwarding and cannot hide a source ACL dependency`() = runBlocking {
        val source = message(SOURCE, "[Nested Author](urn:quote:$NESTED)\n\nMy reply")
        val preparation = ForwardContentPreparation(listOf(source), resolve = { message(NESTED, "Private nested text") }, copyFile = { _, _, _ -> error("No files") })
        val prepared = preparation.prepare(TARGET)
        assertTrue(prepared.contains("Private nested text"))
        assertTrue(prepared.contains("Nested Author"))
        assertFalse(prepared.contains("urn:quote:"))
    }

    @Test fun `unknown upload recovery only reads and reuses confirmed destination copy`() = runBlocking {
        var uploads = 0
        var visible = false
        val copies = DestinationFileCopies(
            find = { _, _, _ -> ApiResult.Success(if (visible) listOf(record()) else emptyList()) },
            load = { _, _ -> preparedFile() },
            upload = { _, _ -> uploads++; ApiResult.Error(ApiError("lost", "REQUEST_FAILED")) },
            currentUserUuid = AUTHOR,
        )
        assertTrue(runCatching { copies.copy(FILE, "report.pdf", STREAM) }.exceptionOrNull() is ForwardPreparationFailure)
        assertTrue(runCatching { copies.copy(FILE, "report.pdf", STREAM) }.exceptionOrNull() is ForwardPreparationFailure)
        assertEquals(1, uploads)
        visible = true
        assertEquals(COPIED, copies.copy(FILE, "report.pdf", STREAM))
        assertEquals(1, uploads)
    }

    @Test fun `upload success must confirm exact file metadata before a message can reference it`() = runBlocking {
        var uploads = 0
        var uploaded = false
        val copies = DestinationFileCopies(
            find = { _, _, _ -> ApiResult.Success(if (uploaded) listOf(record()) else emptyList()) },
            load = { _, _ -> preparedFile() },
            upload = { _, stream -> assertEquals(STREAM, stream); uploads++; uploaded = true; ApiResult.Success(COPIED) },
            currentUserUuid = AUTHOR,
        )
        assertEquals(COPIED, copies.copy(FILE, "report.pdf", STREAM))
        assertEquals(COPIED, copies.copy(FILE, "report.pdf", STREAM))
        assertEquals(1, uploads)
    }

    @Test fun `a copy from the source stream cannot be mistaken for a destination copy`() = runBlocking {
        val copies = DestinationFileCopies(
            find = { _, _, _ -> ApiResult.Success(listOf(record().copy(streamUuid = SOURCE))) },
            load = { _, _ -> preparedFile() }, upload = { _, _ -> ApiResult.Success(COPIED) }, currentUserUuid = AUTHOR)
        assertTrue(runCatching { copies.copy(FILE, "report.pdf", STREAM) }.exceptionOrNull() is ForwardPreparationFailure)
    }

    @Test fun `special filename labels are preserved while only file identity is replaced`() = runBlocking {
        var copiedName: String? = null
        val preparation = ForwardContentPreparation(listOf(message(SOURCE, "[report\\[1\\].pdf](urn:file:$FILE)")),
            resolve = { null }, copyFile = { _, name, _ -> copiedName = name; COPIED })
        val prepared = preparation.prepare(TARGET)
        assertEquals("report[1].pdf", copiedName)
        assertTrue(prepared.contains("[report\\[1\\].pdf](urn:file:$COPIED)"))
    }

    @Test fun `aggregate body limit applies across every selected source`() = runBlocking {
        val preparation = ForwardContentPreparation(listOf(message(SOURCE, "a".repeat(60_000)), message(NESTED, "b".repeat(60_000))),
            resolve = { null }, copyFile = { _, _, _ -> error("No files") })
        assertTrue(runCatching { preparation.prepare(TARGET) }.exceptionOrNull() is ForwardPreparationFailure)
    }

    @Test fun `node budget starts fresh for every preparation attempt`() = runBlocking {
        val content = (1..200).joinToString("\n") { "[file-$it](urn:file:$FILE)" }
        var firstAttemptCopies = 0
        val preparation = ForwardContentPreparation(listOf(message(SOURCE, content)),
            resolve = { null }, copyFile = { _, _, stream ->
                if (stream == STREAM && ++firstAttemptCopies == 200) {
                    throw ForwardPreparationFailure(ru.genesiscorporation.workspace.beta.R.string.forward_upload_failed)
                }
                COPIED
            })

        assertTrue(runCatching { preparation.prepare(TARGET) }.exceptionOrNull() is ForwardPreparationFailure)
        val retried = preparation.prepare(ForwardDestination(NESTED, TOPIC))

        assertTrue(retried.contains("file-1"))
        assertTrue(retried.contains("file-200"))
    }

    @Test fun `explicit upload rejection clears the pending marker for a deliberate retry`() = runBlocking {
        var uploads = 0
        val copies = DestinationFileCopies(find = { _, _, _ -> ApiResult.Success(emptyList()) },
            load = { _, _ -> preparedFile() }, upload = { _, _ -> uploads++; ApiResult.Error(ApiError("too large", "413")) },
            currentUserUuid = AUTHOR)
        repeat(2) {
            val failure = runCatching { copies.copy(FILE, "report.pdf", STREAM) }.exceptionOrNull() as ForwardPreparationFailure
            assertFalse(failure.uncertain)
        }
        assertEquals(2, uploads)
    }

    @Test fun `matching bytes with an incorrect stored MIME type are not reused`() = runBlocking {
        var uploads = 0
        var uploaded = false
        val correct = record().copy(contentType = "application/pdf")
        val incorrect = record().copy(uuid = FILE, contentType = "image/jpeg")
        val copies = DestinationFileCopies(
            find = { _, _, _ -> ApiResult.Success(if (uploaded) listOf(incorrect, correct) else listOf(incorrect)) },
            load = { _, _ -> preparedFile().copy(contentType = "application/pdf") },
            upload = { file, _ -> assertEquals("application/pdf", file.contentType); uploads++; uploaded = true; ApiResult.Success(COPIED) },
            currentUserUuid = AUTHOR)
        assertEquals(COPIED, copies.copy(FILE, "report.pdf", STREAM))
        assertEquals(1, uploads)
    }

    @Test fun `selected quote text forwards only the captured snippet without fetching the source`() = runBlocking {
        var resolutions = 0
        val source = message(SOURCE, "[Quoted Author](urn:quote:$SOURCE?text=Selected%20excerpt%20only)")
        val preparation = ForwardContentPreparation(listOf(source),
            resolve = { resolutions++; message(SOURCE, "Full source must not be forwarded") },
            copyFile = { _, _, _ -> error("No files in the selected snippet") })
        val prepared = preparation.prepare(TARGET)
        assertEquals(0, resolutions)
        assertTrue(prepared.contains("Selected excerpt only"))
        assertFalse(prepared.contains("Full source"))
        assertFalse(prepared.contains("urn:quote:"))
    }

    companion object {
        private const val SOURCE = "00000000-0000-0000-0000-000000000001"
        private const val NESTED = "00000000-0000-0000-0000-000000000002"
        private const val FILE = "00000000-0000-0000-0000-000000000003"
        private const val COPIED = "00000000-0000-0000-0000-000000000004"
        private const val STREAM = "00000000-0000-0000-0000-000000000005"
        private const val TOPIC = "00000000-0000-0000-0000-000000000006"
        private const val AUTHOR = "00000000-0000-0000-0000-000000000007"
        private val TARGET = ForwardDestination(STREAM, TOPIC)
        private fun preparedFile() = PreparedForwardFile(File("unused-fixture.pdf"), "report.pdf", "hash")
        private fun record() = ForwardFileRecord(COPIED, "report.pdf", "hash", STREAM, AUTHOR)
        private fun message(uuid: String, content: String) = MessageResponse(uuid, "2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z",
            SOURCE, TOPIC, AUTHOR, AUTHOR, MessageResponsePayload("markdown", content), true, emptyMap(), true)
    }
}
