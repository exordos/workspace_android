package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatKeyRequest
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatPayload
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatRequestOutcome
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatSessionState
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatWireMessage

class ChatHistoryPresentationTest {
    private val state = EncryptedChatSessionState(currentKeyUuid = "generation", deviceId = "device")

    @Test fun `visible history uses Instant order and a stable UUID tie break once`() {
        val late = message("c", "2026-10-15T11:00:00+01:00")
        val early = message("b", "2026-10-15T09:30:00Z")
        val tied = message("a", "2026-10-15T10:00:00Z")
        val result = derive(listOf(late, early, tied, early))
        assertEquals(listOf("b", "a", "c"), result.orderedMessages!!.map { it.uuid })
        assertEquals("b", result.firstMessageUuid)
        assertTrue(result.showInitialKeyRequest)
        assertNull(derive(null).orderedMessages)
        assertTrue(derive(emptyList()).orderedMessages!!.isEmpty())
    }

    @Test fun `latest own request includes pool and every topic with Instant then UUID tie break`() {
        val old = message("a", "2026-10-15T09:00:00Z", kind = "key_request")
        val visible = message("b", "2026-10-15T10:00:00Z", kind = "key_request")
        val latest = message("c", "2026-10-15T11:00:00+01:00", kind = "key_request")
        val result = derive(listOf(visible), pool = listOf(old), histories = mapOf("unrelated-topic-name" to listOf(latest)))
        val group = result.requestKey("stream", "generation")!!
        assertEquals("c", result.latestOwnRequestUuids[group])
        assertEquals(setOf(group), result.ownGenerationRequests)
        assertTrue(result.showInitialKeyRequest)
    }

    @Test fun `request grouping isolates stream generation viewer and device`() {
        val own = message("own", kind = "key_request")
        val elsewhere = message("other-stream", kind = "key_request", stream = "elsewhere")
        val oldGeneration = message("other-generation", kind = "key_request", generation = "old")
        val otherViewer = message("other-viewer", kind = "key_request", viewer = "someone")
        val otherDevice = message("other-device", kind = "key_request", device = "desktop")
        val pool = listOf(own, elsewhere, oldGeneration, otherViewer, otherDevice)
        val result = derive(emptyList(), pool = pool)
        assertEquals(3, result.latestOwnRequestUuids.size)
        assertEquals("own", result.latestOwnRequestUuids[result.requestKey("stream", "generation")])
        assertEquals("other-stream", result.latestOwnRequestUuids[result.requestKey("elsewhere", "generation")])
        assertEquals("other-generation", result.latestOwnRequestUuids[result.requestKey("stream", "old")])
        val changedViewer = derive(emptyList(), pool = pool, viewer = "someone")
        assertEquals(setOf("other-viewer"), changedViewer.latestOwnRequestUuids.values.toSet())
        val changedDevice = derive(emptyList(), pool = pool, encryption = state.copy(deviceId = "desktop"))
        assertEquals(setOf("other-device"), changedDevice.latestOwnRequestUuids.values.toSet())
        assertTrue(derive(emptyList(), pool = pool, viewer = null).latestOwnRequestUuids.isEmpty())
        assertTrue(derive(emptyList(), pool = pool, encrypted = false).latestOwnRequestUuids.isEmpty())
    }

    @Test fun `introduction ignores other viewers devices and generations and respects announcements`() {
        val unrelated = listOf(message("a", kind = "key_request", viewer = "someone"),
            message("b", kind = "key_request", device = "desktop"),
            message("c", kind = "key_request", generation = "old"),
            message("d", kind = "key_request", stream = "elsewhere"))
        assertTrue(derive(emptyList(), pool = unrelated).showInitialKeyRequest)
        assertFalse(derive(listOf(message("announcement", kind = "key_announced"))).showInitialKeyRequest)
        assertTrue(derive(listOf(message("old-announcement", kind = "key_announced", generation = "old"))).showInitialKeyRequest)
        assertFalse(derive(emptyList(), encryption = state.copy(currentKeyUuid = null)).showInitialKeyRequest)
        assertFalse(derive(emptyList(), encryption = state.copy(deviceId = null)).showInitialKeyRequest)
    }

    @Test fun `request outside the visible topic keeps an entry in the introduction`() {
        val request = EncryptedChatKeyRequest(EncryptedChatWireMessage("request", "project", "stream", "viewer",
            EncryptedChatPayload("key_request", "generation", deviceId = "device")), EncryptedChatRequestOutcome.GRANTED)
        val result = derive(emptyList(), encryption = state.copy(requests = listOf(request)))
        assertTrue(result.showInitialKeyRequest)
        assertTrue(result.requestKey("stream", "generation") in result.ownGenerationRequests)
        assertTrue(result.latestOwnRequestUuids.isEmpty())
        assertTrue(derive(listOf(message("announcement", kind = "key_announced")),
            encryption = state.copy(requests = listOf(request))).showInitialKeyRequest)
    }

    @Test fun `request on another owner device does not hide this devices request`() {
        val otherDevice = message("owner-first-device", kind = "key_request", device = "first-device")
        val result = derive(listOf(otherDevice), encryption = state.copy(canManageKeys = true, canRotate = true))
        assertTrue(result.showInitialKeyRequest)
        assertTrue(result.ownGenerationRequests.isEmpty())
    }

    @Test fun `new history and generation snapshots replace previously derived request decisions`() {
        val oldRequest = message("old", kind = "key_request")
        val newerRequest = message("new", "2026-10-15T11:00:00Z", kind = "key_request")
        val before = derive(listOf(oldRequest))
        val after = derive(listOf(oldRequest), histories = mapOf("another-topic" to listOf(newerRequest)))
        assertEquals("old", before.latestOwnRequestUuids[before.requestKey("stream", "generation")])
        assertFalse(before.showInitialKeyRequest)
        assertEquals("new", after.latestOwnRequestUuids[after.requestKey("stream", "generation")])
        assertTrue(derive(listOf(oldRequest), encryption = state.copy(currentKeyUuid = "rotated")).showInitialKeyRequest)
        assertTrue(derive(emptyList()).ownGenerationRequests.isEmpty())
    }

    private fun derive(visible: List<MessageResponse>?, pool: List<MessageResponse> = emptyList(),
        histories: Map<String, List<MessageResponse>> = emptyMap(), viewer: String? = "viewer",
        encryption: EncryptedChatSessionState = state, encrypted: Boolean = true) =
        deriveChatHistoryPresentation(visible, pool, histories, "stream", encrypted, viewer, encryption)

    private fun message(uuid: String, createdAt: String = "2026-10-15T10:00:00Z", kind: String = "markdown",
        stream: String = "stream", generation: String = "generation", viewer: String = "viewer", device: String = "device") =
        MessageResponse(uuid, createdAt, createdAt, stream, "topic", viewer, viewer,
            MessageResponsePayload(kind, keyUuid = generation, deviceId = device), true, emptyMap(), false)
}
