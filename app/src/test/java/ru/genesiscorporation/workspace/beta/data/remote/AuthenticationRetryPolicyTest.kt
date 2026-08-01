package ru.genesiscorporation.workspace.beta.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationRetryPolicyTest {
    @Test
    fun authenticatedUnauthorizedResponseAttemptsRefresh() {
        assertTrue(
            shouldAttemptTokenRefresh(
                statusCode = 401,
                requiresApiKey = true,
            ),
        )
    }

    @Test
    fun publicUnauthorizedResponseCannotRefreshOrRemoveAnAccount() {
        assertFalse(
            shouldAttemptTokenRefresh(
                statusCode = 401,
                requiresApiKey = false,
            ),
        )
    }

    @Test
    fun nonUnauthorizedResponseDoesNotAttemptRefresh() {
        assertFalse(
            shouldAttemptTokenRefresh(
                statusCode = 403,
                requiresApiKey = true,
            ),
        )
    }

    @Test
    fun revokedRefreshCredentialsRemoveOnlyTheRejectedOwner() {
        listOf(
            ApiError("Expired", "invalid_grant", ApiErrorKind.VALIDATION, 400),
            ApiError("Expired", "invalid-refresh-token", ApiErrorKind.VALIDATION, 400),
            ApiError("Expired", "InvalidRefreshTokenError", ApiErrorKind.VALIDATION, 400),
            ApiError("Unauthorized", "401", ApiErrorKind.UNAUTHORIZED, 401),
            ApiError("Forbidden", "403", ApiErrorKind.FORBIDDEN, 403),
        ).forEach { error ->
            assertTrue(error.code, shouldRemoveAccountAfterRefresh(error))
            assertFalse(error.code, shouldBackoffRefresh(error))
        }
    }

    @Test
    fun clientConfigurationAndTransientFailuresKeepTheAccount() {
        val transientErrors = listOf(
            ApiError("Bad client", "invalid_client", ApiErrorKind.VALIDATION, 400),
            ApiError("Busy", "429", ApiErrorKind.RATE_LIMITED, 429),
            ApiError("Unavailable", "503", ApiErrorKind.SERVER, 503),
            ApiError("Timed out", "TIMEOUT", ApiErrorKind.TIMEOUT),
            ApiError("Offline", "NETWORK", ApiErrorKind.NETWORK),
        )

        transientErrors.forEach { error ->
            assertFalse(error.code, shouldRemoveAccountAfterRefresh(error))
            assertTrue(error.code, shouldBackoffRefresh(error))
        }
        assertFalse(shouldBackoffRefresh(accountChangedError()))
    }

    @Test
    fun retryGateUsesBoundedProgressiveDelay() {
        val gate = RefreshRetryGate(longArrayOf(100L, 200L, 400L))
        val transientError = ApiError("Offline", "NETWORK", ApiErrorKind.NETWORK)

        gate.recordFailure("owner-a", transientError, nowMillis = 1_000L)
        assertEquals(100L, gate.remainingDelayMillis("owner-a", 1_000L))
        assertEquals(40L, gate.remainingDelayMillis("owner-a", 1_060L))
        assertEquals(0L, gate.remainingDelayMillis("owner-a", 1_100L))

        gate.recordFailure("owner-a", transientError, nowMillis = 1_100L)
        assertEquals(200L, gate.remainingDelayMillis("owner-a", 1_100L))
        gate.recordFailure("owner-a", transientError, nowMillis = 1_300L)
        assertEquals(400L, gate.remainingDelayMillis("owner-a", 1_300L))
        gate.recordFailure("owner-a", transientError, nowMillis = 1_700L)
        assertEquals(400L, gate.remainingDelayMillis("owner-a", 1_700L))
    }

    @Test
    fun retryGateIsOwnerScopedAndSuccessResetsTheSchedule() {
        val gate = RefreshRetryGate(longArrayOf(100L, 200L))
        val transientError = ApiError("Offline", "NETWORK", ApiErrorKind.NETWORK)

        gate.recordFailure("owner-a", transientError, nowMillis = 10L)
        gate.recordFailure("owner-a", transientError, nowMillis = 110L)
        assertEquals(200L, gate.remainingDelayMillis("owner-a", 110L))
        assertEquals(0L, gate.remainingDelayMillis("owner-b", 110L))

        gate.clear("owner-a")
        gate.recordFailure("owner-a", transientError, nowMillis = 500L)
        assertEquals(100L, gate.remainingDelayMillis("owner-a", 500L))
    }

    @Test
    fun terminalFailureClearsAnExistingRetryWindow() {
        val gate = RefreshRetryGate(longArrayOf(100L))
        gate.recordFailure(
            "owner-a",
            ApiError("Offline", "NETWORK", ApiErrorKind.NETWORK),
            nowMillis = 0L,
        )

        gate.recordFailure(
            "owner-a",
            ApiError("Expired", "invalid_grant", ApiErrorKind.VALIDATION, 400),
            nowMillis = 10L,
        )

        assertEquals(0L, gate.remainingDelayMillis("owner-a", 10L))
    }
}
