package ru.genesiscorporation.workspace.beta.data.remote

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
}
