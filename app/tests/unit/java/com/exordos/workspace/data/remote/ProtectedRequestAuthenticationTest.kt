package com.exordos.workspace.data.remote

import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.data.remote.dto.EncryptedSendMessageRequest
import com.exordos.workspace.data.remote.dto.MessageResponsePayload

class ProtectedRequestAuthenticationTest {
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
