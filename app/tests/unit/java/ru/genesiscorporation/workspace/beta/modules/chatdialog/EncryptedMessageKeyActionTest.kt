package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatFailure
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatRequestStatus
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatSessionState

class EncryptedMessageKeyActionTest {
    @Test fun `historical message action uses its exact generation and ciphertext recovery probe`() {
        val wire = message(MessageResponsePayload("e2ee", "original-ciphertext", keyUuid = "historical"))
        val target = requireNotNull(encryptedMessageKeyTarget(wire, "stream", "topic", "project"))
        assertEquals("historical", target.keyUuid)
        assertEquals("original-ciphertext", target.rawMessage.payload.content)
        assertNull(target.rawMessage.originalEncryptedPayload)
        val state = EncryptedChatSessionState(currentKeyUuid = "latest", sendingKeyUuid = "latest", hasCurrentKey = true)
        assertFalse(messageKeyRequestPending(target.keyUuid, state))
    }

    @Test fun `projection recovers original ciphertext without sending its displayed plaintext`() {
        val encrypted = MessageResponsePayload("e2ee", "opaque-envelope", keyUuid = "old-generation")
        val projection = message(MessageResponsePayload("markdown", "private displayed text"))
            .copy(originalEncryptedPayload = encrypted)
        val target = requireNotNull(encryptedMessageKeyTarget(projection, "stream", "topic", "project"))
        assertEquals("old-generation", target.keyUuid)
        assertEquals("e2ee", target.rawMessage.payload.kind)
        assertEquals("opaque-envelope", target.rawMessage.payload.content)
        assertNull(target.rawMessage.originalEncryptedPayload)
        assertEquals("private displayed text", projection.payload.content)
    }

    @Test fun `ordinary and service payloads or another conversation cannot request a message key`() {
        listOf("markdown", "key_request", "key_grant", "key_reject", "key_announced").forEach { kind ->
            assertNull(encryptedMessageKeyTarget(message(MessageResponsePayload(kind, "content", keyUuid = "key")),
                "stream", "topic", "project"))
        }
        val wire = message(MessageResponsePayload("e2ee", "ciphertext", keyUuid = "key"))
        assertNull(encryptedMessageKeyTarget(wire, "other-stream", "topic", "project"))
        assertNull(encryptedMessageKeyTarget(wire, "stream", "other-topic", "project"))
        assertNull(encryptedMessageKeyTarget(wire, "stream", "topic", "other-project"))
        assertNull(encryptedMessageKeyTarget(wire.copy(payload = wire.payload.copy(keyUuid = "")), "stream", "topic", "project"))
    }

    @Test fun `pending request disables only its generation and rejection enables retry`() {
        val state = EncryptedChatSessionState(currentKeyUuid = "latest",
            requestStatus = mapOf("historical" to EncryptedChatRequestStatus.REQUESTED))
        assertTrue(messageKeyRequestPending("historical", state))
        assertFalse(messageKeyRequestPending("latest", state))
        assertFalse(messageKeyRequestPending("historical", state.copy(requestStatus = mapOf("historical" to EncryptedChatRequestStatus.REJECTED))))
        assertTrue(messageKeyRequestPending("historical", state.copy(pending = true, requestStatus = emptyMap())))
        assertTrue(messageKeyRequestPending("historical", state.copy(error = EncryptedChatFailure.STALE, requestStatus = emptyMap())))
    }

    private fun message(payload: MessageResponsePayload) = MessageResponse("message", "2026-10-15T10:00:00Z",
        "2026-10-15T10:00:00Z", "stream", "topic", "viewer", "author", payload, false, emptyMap(), true,
        projectId = "project")
}
