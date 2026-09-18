package ru.genesiscorporation.workspace.beta.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationSessionPolicyTest {
    @Test
    fun `transient refresh failures preserve the session`() {
        assertFalse(shouldClearSessionAfterRefresh(ApiError("Temporary server error", "500")))
        assertFalse(shouldClearSessionAfterRefresh(ApiError("Request failed", "REQUEST_FAILED")))
        assertFalse(shouldClearSessionAfterRefresh(ApiError("Network unavailable", "NETWORK")))
        assertFalse(shouldClearSessionAfterRefresh(ApiError("Unauthorized", "401")))
        assertFalse(shouldClearSessionAfterRefresh(ApiError("Forbidden", "403")))
    }

    @Test
    fun `rejected refresh credentials clear the session`() {
        assertTrue(
            shouldClearSessionAfterRefresh(
                ApiError("{\"error\":\"invalid_grant\"}", "400"),
            ),
        )
        assertTrue(
            shouldClearSessionAfterRefresh(
                ApiError("InvalidRefreshTokenError", "400"),
            ),
        )
    }
}
