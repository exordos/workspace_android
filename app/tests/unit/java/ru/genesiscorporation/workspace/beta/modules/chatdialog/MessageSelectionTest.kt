package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class MessageSelectionTest {
    @Test
    fun `selection accepts own and foreign persisted rows from this conversation`() {
        val selected = fixture()
        selected.selection.startMessageSelection(message(FIRST_UUID))
        selected.selection.toggleMessageSelection(message(SECOND_UUID, own = false))

        assertEquals(setOf(FIRST_UUID, SECOND_UUID), selected.selection.selectedMessageUuids.value)
        assertTrue(selected.selection.isSelectionMode.value)
        assertFalse(selected.selection.canDeleteSelectedMessages.value)
        assertFalse(canSelectMessage(message(THIRD_UUID), STREAM_UUID, TOPIC_UUID, THIRD_UUID))
        assertFalse(canSelectMessage(message(THIRD_UUID), "other-stream", TOPIC_UUID))
        assertFalse(canSelectMessage(message(THIRD_UUID).copy(uuid = ""), STREAM_UUID, TOPIC_UUID))
    }

    @Test
    fun `successful batch deletes every selected row and exits selection`() = runBlocking {
        val fixture = fixture()
        fixture.selection.startMessageSelection(message(FIRST_UUID))
        fixture.selection.toggleMessageSelection(message(SECOND_UUID))

        fixture.selection.deleteSelectedMessages()

        assertEquals(listOf(FIRST_UUID, SECOND_UUID), fixture.requests)
        assertEquals(listOf(FIRST_UUID, SECOND_UUID), fixture.deleted)
        assertTrue(fixture.selection.selectedMessageUuids.value.isEmpty())
        assertFalse(fixture.selection.isSelectionMode.value)
        assertFalse(fixture.selection.deletingSelectedMessages.value)
        assertNull(fixture.deletion.actionError.value)
    }

    @Test
    fun `partial failure keeps failed rows selected and retries only those rows`() = runBlocking {
        var failFirst = true
        val fixture = fixture { uuid ->
            if (uuid == FIRST_UUID && failFirst) ApiResult.Error(ApiError("Failure", "500"))
            else ApiResult.Success("")
        }
        fixture.selection.startMessageSelection(message(FIRST_UUID))
        fixture.selection.toggleMessageSelection(message(SECOND_UUID))

        fixture.selection.deleteSelectedMessages()

        assertEquals(listOf(SECOND_UUID), fixture.deleted)
        assertEquals(setOf(FIRST_UUID), fixture.selection.selectedMessageUuids.value)
        assertTrue(fixture.selection.canDeleteSelectedMessages.value)
        assertEquals(
            R.string.messages_delete_partial_failure,
            fixture.deletion.actionError.value?.resourceId,
        )
        assertEquals(listOf(1, 2, 1), fixture.deletion.actionError.value?.formatArgs)

        failFirst = false
        fixture.selection.deleteSelectedMessages()

        assertEquals(listOf(FIRST_UUID, SECOND_UUID, FIRST_UUID), fixture.requests)
        assertEquals(listOf(SECOND_UUID, FIRST_UUID), fixture.deleted)
        assertTrue(fixture.selection.selectedMessageUuids.value.isEmpty())
        assertNull(fixture.deletion.actionError.value)
    }

    @Test
    fun `mixed ownership rejects whole selection before making any request`() = runBlocking {
        val fixture = fixture()
        fixture.selection.startMessageSelection(message(FIRST_UUID))
        fixture.selection.toggleMessageSelection(message(SECOND_UUID, own = false))

        fixture.selection.deleteSelectedMessages()

        assertTrue(fixture.requests.isEmpty())
        assertTrue(fixture.deleted.isEmpty())
        assertEquals(setOf(FIRST_UUID, SECOND_UUID), fixture.selection.selectedMessageUuids.value)
        assertFalse(fixture.selection.canDeleteSelectedMessages.value)
        assertEquals(R.string.messages_selection_own_only, fixture.deletion.actionError.value?.resourceId)
    }

    @Test
    fun `pending batch freezes selection and ignores repeated confirmation`() = runBlocking {
        val result = CompletableDeferred<ApiResult<String, ApiError>>()
        val fixture = fixture { result.await() }
        fixture.selection.startMessageSelection(message(FIRST_UUID))
        fixture.selection.toggleMessageSelection(message(SECOND_UUID))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.selection.deleteSelectedMessages()
        }

        assertTrue(fixture.selection.deletingSelectedMessages.value)
        assertFalse(fixture.selection.canDeleteSelectedMessages.value)
        fixture.selection.clearMessageSelection()
        fixture.selection.toggleMessageSelection(message(FIRST_UUID))
        fixture.selection.startMessageSelection(message(THIRD_UUID))
        fixture.selection.deleteSelectedMessages()
        assertEquals(setOf(FIRST_UUID, SECOND_UUID), fixture.selection.selectedMessageUuids.value)
        assertEquals(listOf(FIRST_UUID), fixture.requests)

        result.complete(ApiResult.Success(""))
        job.join()
        assertEquals(listOf(FIRST_UUID, SECOND_UUID), fixture.requests)
        assertFalse(fixture.selection.deletingSelectedMessages.value)
    }

    @Test
    fun `deleted realtime rows are pruned without changing remaining selection`() {
        val fixture = fixture()
        fixture.selection.startMessageSelection(message(FIRST_UUID))
        fixture.selection.toggleMessageSelection(message(SECOND_UUID))

        fixture.selection.refreshMessages(listOf(message(SECOND_UUID), message(THIRD_UUID)))

        assertEquals(setOf(SECOND_UUID), fixture.selection.selectedMessageUuids.value)
        assertEquals(listOf(SECOND_UUID), fixture.selection.selectedMessages().map { it.uuid })
    }

    @Test
    fun `selection limit is enforced without dropping previously selected rows`() {
        val fixture = fixture()
        repeat(MAX_SELECTED_MESSAGES + 1) { index ->
            fixture.selection.toggleMessageSelection(
                message("00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"),
            )
        }

        assertEquals(MAX_SELECTED_MESSAGES, fixture.selection.selectedMessageUuids.value.size)
        assertEquals(R.string.messages_selection_limit, fixture.deletion.actionError.value?.resourceId)
        assertEquals(listOf(50), fixture.deletion.actionError.value?.formatArgs)
    }

    @Test
    fun `selected forwarding snapshot owns its payload and realtime deletion drops its row`() {
        val fixture = fixture()
        val source = message(FIRST_UUID)
        fixture.selection.startMessageSelection(source)
        val snapshot = fixture.selection.selectedMessages()
        source.payload.content = "Changed source"
        fixture.selection.selectedMessages().first().payload.content = "Changed returned copy"

        assertEquals("Selection fixture", snapshot.first().payload.content)
        assertEquals("Selection fixture", fixture.selection.selectedMessages().first().payload.content)
        fixture.selection.removeMessage(FIRST_UUID)
        assertTrue(fixture.selection.selectedMessageUuids.value.isEmpty())
        assertFalse(fixture.selection.isSelectionMode.value)
    }

    private fun fixture(
        request: suspend (String) -> ApiResult<String, ApiError> = { ApiResult.Success("") },
    ): Fixture {
        val requests = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val deletion = MessageDeletion(
            canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
            request = { requests += it; request(it) },
            onDeleted = { deleted += it },
        )
        val selection = MessageSelection(
            canSelect = { canSelectMessage(it, STREAM_UUID, TOPIC_UUID) },
            canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
            deletion = deletion,
        )
        return Fixture(selection, deletion, requests, deleted)
    }

    private data class Fixture(
        val selection: MessageSelection,
        val deletion: MessageDeletion,
        val requests: List<String>,
        val deleted: List<String>,
    )

    private fun message(uuid: String, own: Boolean = true) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = "current-user",
        authorUuid = if (own) "current-user" else "other-user",
        payload = MessageResponsePayload("markdown", "Selection fixture"),
        isOwn = own,
        reactions = emptyMap(),
        read = true,
    )

    private companion object {
        const val FIRST_UUID = "ac0819d1-a91f-4b87-a1bc-e4b0d353f162"
        const val SECOND_UUID = "2ff4fdd2-cb41-43a9-8532-262b8b36b858"
        const val THIRD_UUID = "640e6a23-9239-41e9-b840-4ef0dc5453f8"
        const val STREAM_UUID = "stream"
        const val TOPIC_UUID = "topic"
    }
}
