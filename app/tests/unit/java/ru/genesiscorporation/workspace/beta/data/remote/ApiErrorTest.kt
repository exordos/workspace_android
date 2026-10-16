package ru.genesiscorporation.workspace.beta.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorTest {
    @Test
    fun constructedErrorExposesItsTextThroughThrowable() {
        val error = ApiError("Первая строка\n\"вторая\"", "VALIDATION_ERROR", 422)
        val throwable: Throwable = error

        assertEquals(error.errorMessage, throwable.message)
        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals(422, error.httpStatusCode)
    }

    @Test
    fun decodedErrorExposesItsTextThroughThrowable() {
        val error = Json.decodeFromString<ApiError>(
            """{"errorMessage":"Synthetic login error","code":"LOGIN_FAILED","httpStatusCode":400}""",
        )

        assertEquals("Synthetic login error", (error as Throwable).message)
        assertEquals("LOGIN_FAILED", error.code)
        assertEquals(400, error.httpStatusCode)
    }

    @Test
    fun roundTripPreservesMessageAndExistingWireFieldsOnly() {
        val original = ApiError("Ошибка\n\"повторите\"", "OTP_REJECTED", 401)
        val encoded = Json.encodeToString(original)
        val restored = Json.decodeFromString<ApiError>(encoded)

        assertEquals(Json.parseToJsonElement(
            """{"errorMessage":"Ошибка\n\"повторите\"","code":"OTP_REJECTED","httpStatusCode":401}""",
        ), Json.parseToJsonElement(encoded))
        assertEquals(original, restored)
        assertEquals(original.errorMessage, (restored as Throwable).message)
    }

    @Test
    fun omittedStatusStillUsesNumericCodeDefaultAfterDecoding() {
        val error = Json.decodeFromString<ApiError>("""{"errorMessage":"Unavailable","code":"503"}""")

        assertEquals("Unavailable", error.message)
        assertEquals(503, error.httpStatusCode)
        assertEquals("503", error.code)
    }

    @Test
    fun symbolicCodeWithoutStatusRemainsUnspecifiedAfterRoundTrip() {
        val original = ApiError("Request failed", "REQUEST_FAILED")
        val restored = Json.decodeFromString<ApiError>(Json.encodeToString(original))

        assertEquals("Request failed", restored.message)
        assertEquals("REQUEST_FAILED", restored.code)
        assertNull(restored.httpStatusCode)
    }

    @Test
    fun copiedErrorUsesTheNewMessageWithoutChangingCodeOrStatus() {
        val original = ApiError("Original", "VALIDATION_ERROR", 422)
        val copied = original.copy(errorMessage = "Corrected")

        assertEquals("Original", original.message)
        assertEquals("Corrected", copied.message)
        assertEquals(original.code, copied.code)
        assertEquals(original.httpStatusCode, copied.httpStatusCode)
    }

    @Test
    fun whitespaceWireMessageIsPreservedWhileThrowableUsesReadableFallback() {
        val original = ApiError(" \t\r\n", "REQUEST_FAILED", 500)
        val restored = Json.decodeFromString<ApiError>(Json.encodeToString(original))

        assertEquals(original, restored)
        assertEquals(" \t\r\n", restored.errorMessage)
        assertEquals("REQUEST_FAILED", restored.code)
        assertEquals(500, restored.httpStatusCode)
        assertEquals("Произошла ошибка, повторите позднее", restored.message)
    }

    @Test
    fun emptyWireMessageIsPreservedWhileThrowableUsesReadableFallback() {
        val error = Json.decodeFromString<ApiError>(Json.encodeToString(ApiError("", "400")))

        assertEquals("", error.errorMessage)
        assertEquals("Произошла ошибка, повторите позднее", error.message)
        assertEquals(400, error.httpStatusCode)
    }
}
