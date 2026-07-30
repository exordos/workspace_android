package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.http.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorKindTest {
    @Test
    fun `http statuses map to stable public error categories`() {
        assertEquals(ApiErrorKind.VALIDATION, apiErrorKind(400))
        assertEquals(ApiErrorKind.UNAUTHORIZED, apiErrorKind(401))
        assertEquals(ApiErrorKind.FORBIDDEN, apiErrorKind(403))
        assertEquals(ApiErrorKind.NOT_FOUND, apiErrorKind(404))
        assertEquals(ApiErrorKind.CONFLICT, apiErrorKind(409))
        assertEquals(ApiErrorKind.RATE_LIMITED, apiErrorKind(429))
        assertEquals(ApiErrorKind.SERVER, apiErrorKind(503))
        assertEquals(ApiErrorKind.UNKNOWN, apiErrorKind(418))
    }

    @Test
    fun `structured server errors preserve safe message and code`() {
        val error = httpApiError(
            409,
            """{"msg":"Folder changed","code":"revision_conflict"}""",
        )

        assertEquals("Folder changed", error.message)
        assertEquals("revision_conflict", error.code)
        assertEquals(ApiErrorKind.CONFLICT, error.kind)
        assertEquals(409, error.httpStatus)
    }

    @Test
    fun `unstructured response bodies are not exposed to the UI`() {
        val error = httpApiError(
            503,
            "<html>internal upstream diagnostics</html>",
        )

        assertEquals("Workspace service is temporarily unavailable", error.message)
        assertEquals("503", error.code)
        assertEquals(ApiErrorKind.SERVER, error.kind)
        assertNull(error.conflictBody)
        assertNull(error.entityTag)
    }

    @Test
    fun `precondition conflicts retain only bounded reconciliation data`() {
        val body = """{"current":{"revision":2}}"""
        val error = httpApiError(
            412,
            body,
            Headers.build { append("ETag", "\"2\"") },
        )

        assertEquals(body, error.conflictBody)
        assertEquals("\"2\"", error.entityTag)
        assertEquals(ApiErrorKind.CONFLICT, error.kind)

        val unrelated = httpApiError(
            500,
            body,
            Headers.build { append("ETag", "\"2\"") },
        )
        assertNull(unrelated.conflictBody)
        assertEquals("\"2\"", unrelated.entityTag)
    }

    @Test
    fun `oauth style errors retain a bounded machine code`() {
        val error = httpApiError(
            401,
            """{"error":"invalid_client","error_description":"OTP code is required"}""",
        )

        assertEquals("OTP code is required", error.message)
        assertEquals("invalid_client", error.code)
        assertEquals(401, error.httpStatus)
    }

    @Test
    fun `upload filenames cannot inject multipart headers or paths`() {
        assertEquals(
            "avatar___name.png",
            sanitizeUploadFileName("../../avatar\"\r\nname.png"),
        )
        assertEquals("image", sanitizeUploadFileName(null))
    }
}
