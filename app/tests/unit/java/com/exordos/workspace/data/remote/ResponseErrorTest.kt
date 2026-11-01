package com.exordos.workspace.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseErrorTest {
    @Test
    fun structuredErrorRetainsHttpStatusEvenWhenBackendCodeIsNotNumeric() {
        val error = responseError(
            """{"msg":"Sign in again","code":"SESSION_EXPIRED","request_id":"fixture"}""",
            401,
        )

        assertEquals(ApiError("Sign in again", "SESSION_EXPIRED", 401), error)
        assertEquals("Sign in again", error.message)
        assertTrue(shouldRequireReloginAfterRefresh(error))
    }

    @Test
    fun malformedAndIncompleteErrorsRetainTheOriginalBodyAndStatus() {
        for (body in listOf("", "<html>Bad Gateway</html>", "{", "{}", "null", "[]",
            """{"msg":"Missing code"}""", """{"code":"MISSING_MESSAGE"}""")) {
            assertEquals(body, ApiError(body, "502", 502), responseError(body, 502))
        }
    }

    @Test
    fun structuredErrorDecodesEscapesWithoutChangingBackendCode() {
        val error = responseError("""{"msg":"Первая строка\n\"вторая\"","code":"VALIDATION_ERROR"}""", 422)

        assertEquals("Первая строка\n\"вторая\"", error.errorMessage)
        assertEquals(error.errorMessage, error.message)
        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals(422, error.httpStatusCode)
        assertFalse(shouldRequireReloginAfterRefresh(error))
    }

    @Test
    fun credentialRejectionsAreRecognizedAcrossBackendSpellings() {
        for (body in listOf(
            """{"msg":"Refresh rejected","code":"INVALID_GRANT"}""",
            """{"msg":"Invalid refresh-token","code":"BAD_REQUEST"}""",
            """{"msg":"invalid_grant","code":"TOKEN_ERROR"}""",
        )) assertTrue(body, shouldRequireReloginAfterRefresh(responseError(body, 400)))
    }

    @Test
    fun authorizationAndTemporaryFailuresDoNotInvalidateTheLogin() {
        for (status in listOf(403, 408, 429, 500, 502, 503, 504)) {
            assertFalse("HTTP $status", shouldRequireReloginAfterRefresh(responseError(
                """{"msg":"Request unavailable","code":"REQUEST_FAILED"}""", status,
            )))
        }
    }
}
