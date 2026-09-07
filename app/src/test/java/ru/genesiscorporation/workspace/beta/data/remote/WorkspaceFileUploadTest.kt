package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileUploadTest {
    @Test
    fun `file part has one complete form-data disposition`() {
        val fileName = "report \"final\".txt"
        val parts = workspaceFileUploadParts(
            fileName = fileName,
            mime = "text/plain",
            bytes = "content".encodeToByteArray(),
            streamUuid = "d994e854-3d03-4b35-a464-794317491015",
        )

        val filePart = parts.first()
        val dispositions = requireNotNull(filePart.headers.getAll(HttpHeaders.ContentDisposition))
        val disposition = ContentDisposition.parse(dispositions.single())

        assertTrue(filePart is PartData.FileItem)
        assertEquals("form-data", disposition.disposition)
        assertEquals("file", disposition.parameter(ContentDisposition.Parameters.Name))
        assertEquals(fileName, disposition.parameter(ContentDisposition.Parameters.FileName))
        assertEquals("text/plain", filePart.headers[HttpHeaders.ContentType])
        assertEquals("7", filePart.headers[HttpHeaders.ContentLength])
        assertEquals(2, parts.size)

        val streamPart = parts[1] as PartData.FormItem
        val streamDisposition = ContentDisposition.parse(
            requireNotNull(streamPart.headers[HttpHeaders.ContentDisposition])
        )
        assertEquals("d994e854-3d03-4b35-a464-794317491015", streamPart.value)
        assertEquals("stream_uuid", streamDisposition.parameter(ContentDisposition.Parameters.Name))
    }

    @Test
    fun `avatar upload omits stream field`() {
        val parts = workspaceFileUploadParts(
            fileName = "image.jpg",
            mime = "image/jpeg",
            bytes = byteArrayOf(1, 2, 3),
        )

        assertEquals(1, parts.size)
        assertTrue(parts.single() is PartData.FileItem)
    }
}
