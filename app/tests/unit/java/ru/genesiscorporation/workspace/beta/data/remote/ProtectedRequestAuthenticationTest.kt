package ru.genesiscorporation.workspace.beta.data.remote

import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.EncryptedSendMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class ProtectedRequestAuthenticationTest {
    @Test fun `recovery retains captured authorization and delegates the wire contract`() {
        val source = EncryptedSendMessageRequest("stream", "topic", MessageResponsePayload("key_announced", keyUuid = "key"), "canonical")
        val authentication = ProtectedRequestAuthentication("synthetic-old-token")
        val recovery = authentication.recoverSession(source)
        assertFalse(recovery.requiresApiKey)
        assertTrue(recovery is RecoverableProtectedRequest)
        assertSame(authentication, (recovery as RecoverableProtectedRequest).authentication)
        assertEquals(authentication.pin(source).additionalHeaders, recovery.additionalHeaders)
        assertSame(source.data, recovery.data)
        assertFalse(authentication.pin(source) is RecoverableProtectedRequest)
    }

    @Test fun `pins credential and disables implicit retry without changing encrypted wire request`() {
        val source = EncryptedSendMessageRequest("stream", "topic", MessageResponsePayload("key_announced", keyUuid = "key"), "canonical")
        val authentication = ProtectedRequestAuthentication("synthetic-old-token")
        val pinned = authentication.pin(source)
        assertFalse(pinned.requiresApiKey)
        assertEquals(mapOf("Authorization" to "Bearer synthetic-old-token"), pinned.additionalHeaders)
        assertSame(source.data, pinned.data)
        assertEquals(source.url, pinned.url)
        assertEquals(HTTPMethod.POST, pinned.method)
        assertTrue(source.requiresApiKey) // Ordinary/default transport policy stays unchanged.
        assertTrue(authentication.matchesAccessToken("synthetic-old-token"))
        assertFalse(authentication.matchesAccessToken("synthetic-replacement-token"))
        assertFalse(authentication.toString().contains("synthetic-old-token"))
    }
}
