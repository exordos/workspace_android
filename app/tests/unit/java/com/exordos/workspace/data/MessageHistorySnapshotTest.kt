package com.exordos.workspace.data

import org.junit.Assert.assertEquals
import org.junit.Test
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload

class MessageHistorySnapshotTest {
    @Test
    fun `snapshot removes missed deletions and applies missed edits`() {
        val stale = message("stale")
        val old = message("edited")
        val updated = old.copy(payload = MessageResponsePayload("markdown", "New text"))

        assertEquals(listOf(updated), mergeMessageHistorySnapshot(
            listOf(stale, old), listOf(stale, old), listOf(updated), emptySet(),
        ))
    }

    @Test
    fun `pending send that predates the request survives a snapshot without it`() {
        val pending = message("pending")

        assertEquals(listOf(pending), mergeMessageHistorySnapshot(
            listOf(pending), listOf(pending), emptyList(), setOf(pending.uuid),
        ))
    }

    @Test
    fun `incoming message during initial history load survives and is deduplicated`() {
        val incoming = message("incoming")

        for (snapshot in listOf(emptyList(), listOf(incoming))) {
            assertEquals(listOf(incoming), mergeMessageHistorySnapshot(
                emptyList(), listOf(incoming), snapshot, emptySet(),
            ))
        }
    }

    @Test
    fun `edit received during request takes precedence over an older response`() {
        val old = message("edited")
        val edited = old.copy(payload = MessageResponsePayload("markdown", "Edited during request"))

        assertEquals(listOf(edited), mergeMessageHistorySnapshot(
            listOf(old), listOf(edited), listOf(old), emptySet(),
        ))
    }

    @Test
    fun `deletion received during request is not resurrected by its response`() {
        val deleted = message("deleted")

        assertEquals(emptyList<MessageResponse>(), mergeMessageHistorySnapshot(
            listOf(deleted), emptyList(), listOf(deleted), emptySet(),
        ))
    }

    @Test
    fun `HTTP send confirmation during reload keeps one canonical UUID`() {
        val pending = message("pending")
        val confirmed = pending.copy(uuid = "confirmed")

        assertEquals(listOf(confirmed), mergeMessageHistorySnapshot(
            listOf(pending), listOf(confirmed), listOf(confirmed), emptySet(),
        ))
    }

    private fun message(id: String) = MessageResponse(
        uuid = id, updatedAt = "2026-10-15T12:00:00Z", createdAt = "2026-10-15T12:00:00Z",
        streamUuid = "stream", topicUuid = "topic", userUuid = "user", authorUuid = "user",
        payload = MessageResponsePayload("markdown", id), isOwn = false, reactions = emptyMap(), read = false,
    )
}
