package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadFileResponseData
import java.io.File
import java.nio.file.Files
import java.time.Instant

class ChatDialogFormattingTest {
    @Test
    fun messageSortingAcceptsOffsetAndMalformedTimestamps() {
        assertEquals(
            Instant.parse("2026-07-26T10:15:30Z"),
            messageSortInstant("2026-07-26T13:15:30+03:00"),
        )
        assertEquals(Instant.EPOCH, messageSortInstant("not-a-timestamp"))
    }

    @Test
    fun parsesSingleWorkspaceImageLink() {
        val upload = """
            Подпись
            ![photo.png](urn:image:11111111-1111-4111-8111-111111111111?name=photo.png&content_type=image%2Fpng&size=42)
        """.trimIndent().parseWorkspaceAttachmentsOrNull()

        assertNotNull(upload)
        assertEquals("Подпись", upload?.caption)
        assertEquals("photo.png", upload?.attachments?.single()?.fileName)
        assertEquals(WorkspaceAttachmentKind.IMAGE, upload?.attachments?.single()?.kind)
        assertEquals(42L, upload?.attachments?.single()?.sizeBytes)
    }

    @Test
    fun parsesMixedWorkspaceAttachments() {
        val upload = """
            Галерея
            ![one.png](urn:image:11111111-1111-4111-8111-111111111111?name=one.png&content_type=image%2Fpng)
            [report.pdf](urn:file:22222222-2222-4222-8222-222222222222?name=report.pdf&content_type=application%2Fpdf)
            [clip.mp4](urn:video:33333333-3333-4333-8333-333333333333?name=clip.mp4&content_type=video%2Fmp4)
        """.trimIndent().parseWorkspaceAttachmentsOrNull()

        assertNotNull(upload)
        assertEquals("Галерея", upload?.caption)
        assertEquals(
            listOf(
                WorkspaceAttachmentKind.IMAGE,
                WorkspaceAttachmentKind.FILE,
                WorkspaceAttachmentKind.VIDEO,
            ),
            upload?.attachments?.map(WorkspaceAttachment::kind),
        )
    }

    @Test
    fun buildsDesktopCompatibleWorkspaceFileUrn() {
        assertEquals(
            "[report.pdf](urn:file:44444444-4444-4444-8444-444444444444?name=report.pdf&content_type=application%2Fpdf&size=1024)",
            buildWorkspaceAttachmentMarkdown(
                UploadFileResponseData(
                    uuid = "44444444-4444-4444-8444-444444444444",
                    name = "report.pdf",
                    contentType = "application/pdf",
                    sizeBytes = 1024,
                ),
            ),
        )
    }

    @Test
    fun formatsAttachmentSizes() {
        assertEquals("512 B", formatAttachmentSize(512))
        assertEquals("1.5 KB", formatAttachmentSize(1536))
        assertEquals("2.0 MB", formatAttachmentSize(2 * 1024 * 1024))
    }

    @Test
    fun `server mutation actions require an owned native canonical message`() {
        val native = message()

        assertTrue(canMutateNativeMessage(native))
        assertTrue(canDeleteMessage(native))
        assertFalse(canMutateNativeMessage(native.copy(isOwn = false)))
        assertFalse(
            canMutateNativeMessage(
                native.copy(
                    provider = ProviderReference(
                        kind = "zulip",
                        externalId = "42",
                    ),
                ),
            ),
        )
        assertFalse(canMutateNativeMessage(native.copy(uuid = "local-draft")))
        assertFalse(canMutateNativeMessage(native.copy(uuid = "1-1-1-1-1")))
    }

    @Test
    fun prunesExpiredAttachmentCacheEntries() {
        val directory = Files.createTempDirectory("workspace-attachments").toFile()
        try {
            val now = 2_000_000_000L
            val expired = File(directory, "expired.txt").apply {
                writeText("expired")
                setLastModified(now - 25L * 60L * 60L * 1000L)
            }
            val fresh = File(directory, "fresh.txt").apply {
                writeText("fresh")
                setLastModified(now)
            }

            pruneAttachmentCache(directory, incomingBytes = 1, nowMillis = now)

            assertFalse(expired.exists())
            assertTrue(fresh.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun prunesOldestEntryBeforeCacheFileLimit() {
        val directory = Files.createTempDirectory("workspace-attachments").toFile()
        try {
            val files = (0 until 8).map { index ->
                File(directory, "$index.txt").apply {
                    writeText("$index")
                    setLastModified(10_000L + index)
                }
            }

            pruneAttachmentCache(directory, incomingBytes = 1, nowMillis = 10_000L)

            assertFalse(files.first().exists())
            assertEquals(7, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun message() = MessageResponse(
        uuid = "11111111-1111-4111-8111-111111111111",
        updatedAt = "2026-07-30T00:00:00Z",
        createdAt = "2026-07-30T00:00:00Z",
        streamUuid = "22222222-2222-4222-8222-222222222222",
        topicUuid = "33333333-3333-4333-8333-333333333333",
        userUuid = "44444444-4444-4444-8444-444444444444",
        authorUuid = "44444444-4444-4444-8444-444444444444",
        payload = MessageResponsePayload(kind = "markdown", content = "text"),
        isOwn = true,
        reactions = emptyMap(),
    )
}
