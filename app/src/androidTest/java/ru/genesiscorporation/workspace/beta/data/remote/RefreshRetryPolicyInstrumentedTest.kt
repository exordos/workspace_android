package ru.genesiscorporation.workspace.beta.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshRetryPolicyInstrumentedTest {
    @Test
    fun revokedRefreshTokenIsTerminalButServerFailureIsRecoverable() {
        val rejected = httpApiError(
            statusCode = 400,
            responseBody = """{"error":"invalid_grant"}""",
        )
        val unavailable = httpApiError(
            statusCode = 503,
            responseBody = """{"message":"Try later"}""",
        )

        assertTrue(shouldRemoveAccountAfterRefresh(rejected))
        assertFalse(shouldBackoffRefresh(rejected))
        assertFalse(shouldRemoveAccountAfterRefresh(unavailable))
        assertTrue(shouldBackoffRefresh(unavailable))
    }

    @Test
    fun ownerRetryWindowsRemainIndependentOnAndroidRuntime() {
        val gate = RefreshRetryGate(longArrayOf(1_000L, 2_000L))
        val timeout = ApiError("Timed out", "TIMEOUT", ApiErrorKind.TIMEOUT)

        gate.recordFailure("owner-a", timeout, nowMillis = 5_000L)
        gate.recordFailure("owner-a", timeout, nowMillis = 6_000L)

        assertEquals(2_000L, gate.remainingDelayMillis("owner-a", 6_000L))
        assertEquals(0L, gate.remainingDelayMillis("owner-b", 6_000L))
    }
}
