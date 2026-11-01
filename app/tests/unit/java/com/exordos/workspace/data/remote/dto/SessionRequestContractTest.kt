package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRequestContractTest {
    private val json = Json

    @Test
    fun loginBodyUsesIamFieldsAndPreservesSpecialCharacters() {
        val request = LoginRequest("cassi+fixture@example.invalid", "synthetic \"fixture\"\nпароль", "123456")
        val wire = json.parseToJsonElement(json.encodeToString(request.data)).jsonObject

        assertEquals(json.parseToJsonElement("""{
            "login":"cassi+fixture@example.invalid","password":"synthetic \"fixture\"\nпароль",
            "grant_type":"login+password","scope":"openid email profile",
            "ttl":"3600","refresh_ttl":"31536000"
        }"""), wire)
        assertFalse(request.requiresApiKey)
        assertFalse(wire.containsKey("otp"))
        assertFalse(wire.containsKey("username"))
        assertEquals(mapOf("X-OTP" to "123456"), request.additionalHeaders)
    }

    @Test
    fun absentOtpDoesNotAddAnEmptyCredentialHeader() {
        for (otp in listOf(null, "")) {
            assertTrue(LoginRequest("fixture", "synthetic-fixture", otp).additionalHeaders.isEmpty())
        }
    }

    @Test
    fun refreshUsesItsOwnGrantAndOptionalProjectScope() {
        val request = TokenRefreshRequest("synthetic-refresh", "openid project:fixture")

        assertEquals(json.parseToJsonElement("""{
            "grant_type":"refresh_token","refresh_token":"synthetic-refresh","scope":"openid project:fixture"
        }"""), json.parseToJsonElement(json.encodeToString(request.data)))
        assertFalse(request.requiresApiKey)
        assertTrue(request.additionalHeaders.isEmpty())
        assertEquals(LoginRequest("fixture", "synthetic-fixture").url, request.url)
    }

    @Test
    fun refreshResponseDecodesSnakeCaseCredentials() {
        val response = json.decodeFromString<LoginResponse>(
            """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh"}""",
        )

        assertEquals("synthetic-access", response.accessToken)
        assertEquals("synthetic-refresh", response.refreshToken)
    }
}
