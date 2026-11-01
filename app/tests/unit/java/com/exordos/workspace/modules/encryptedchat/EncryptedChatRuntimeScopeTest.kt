package com.exordos.workspace.modules.encryptedchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload

class EncryptedChatRuntimeScopeTest {
    private val scope = EncryptedChatScope("https://workspace.example", "project", "owner", 1, "android", "Android", "chat")

    @Test fun `chat and info panels share key invalidation across separate runtime generations`() {
        assertTrue(encryptedChatStorageScopeMatches(scope, scope.copy(runtimeGeneration = 999)))
        assertTrue(encryptedChatStorageScopeMatches(scope, scope.copy(deviceName = "renamed")))
        assertFalse(encryptedChatStorageScopeMatches(scope, scope.copy(server = "https://another.example")))
        assertFalse(encryptedChatStorageScopeMatches(scope, scope.copy(projectId = "another-project")))
        assertFalse(encryptedChatStorageScopeMatches(scope, scope.copy(userId = "another-user")))
        assertFalse(encryptedChatStorageScopeMatches(scope, scope.copy(deviceId = "another-device")))
        assertFalse(encryptedChatStorageScopeMatches(scope, scope.copy(streamId = "another-chat")))
    }

    @Test fun `receipt placement matches backend Python uuid5 namespace and canonical string`() {
        assertEquals("32f0ab1b-b04a-5872-b3e7-38233dda58dc", encryptedMessagePlacementUuid(
            "936aeed3-bc0c-42e1-b240-6994f8c1b784", "53335887-abf7-4377-ae7b-5fbab7423b1c"))
        assertEquals("32f0ab1b-b04a-5872-b3e7-38233dda58dc", encryptedMessagePlacementUuid(
            "936AEED3-BC0C-42E1-B240-6994F8C1B784", "53335887-ABF7-4377-AE7B-5FBAB7423B1C"))
    }

    @Test fun `edited ciphertext selects newest snapshot and conversation wins equal timestamps`() {
        val old = message("2026-10-15T10:00:00Z", "old ciphertext")
        val edit = message("2026-10-15T11:00:00Z", "edited ciphertext")
        assertEquals("edited ciphertext", latestEncryptedChatWireMessages(listOf(old), listOf(edit), "chat").single().payload.content)
        assertEquals("edited ciphertext", latestEncryptedChatWireMessages(listOf(edit), listOf(old), "chat").single().payload.content)
        val simultaneous = edit.copy(payload = edit.payload.copy(content = "conversation ciphertext"))
        assertEquals("conversation ciphertext", latestEncryptedChatWireMessages(listOf(edit), listOf(simultaneous), "chat").single().payload.content)
    }

    private fun message(updated: String, content: String) = MessageResponse(
        "message", updated, "2026-10-15T10:00:00Z", "chat", "topic", "owner", "owner",
        MessageResponsePayload("e2ee", content, keyUuid = "generation"), true, emptyMap(), false)
}
