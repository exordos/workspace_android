package ru.genesiscorporation.workspace.beta.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationReloginPolicyTest {
    @Test
    fun `plain retry rejection preserves its HTTP status`() {
        assertEquals(
            ApiError("Payload Too Large", "413"),
            responseError("Payload Too Large", 413),
        )
    }

    @Test
    fun `structured retry rejection preserves backend error details`() {
        assertEquals(
            ApiError("Unsupported file type", "UNSUPPORTED_MEDIA_TYPE", 415),
            responseError(
                """{"msg":"Unsupported file type","code":"UNSUPPORTED_MEDIA_TYPE"}""",
                415,
            ),
        )
    }

    @Test
    fun `temporary refresh failures do not require relogin`() {
        assertFalse(shouldRequireReloginAfterRefresh(ApiError("Temporary failure", "500")))
        assertFalse(shouldRequireReloginAfterRefresh(ApiError("Request failed", "REQUEST_FAILED")))
        assertFalse(shouldRequireReloginAfterRefresh(ApiError("Network unavailable", "NETWORK")))
    }

    @Test
    fun `rejected refresh credentials require relogin`() {
        assertTrue(shouldRequireReloginAfterRefresh(ApiError("Unauthorized", "401")))
        assertTrue(
            shouldRequireReloginAfterRefresh(
                ApiError("{\"error\":\"invalid_grant\"}", "400"),
            ),
        )
        assertTrue(
            shouldRequireReloginAfterRefresh(
                ApiError("InvalidRefreshTokenError", "400"),
            ),
        )
    }
}
