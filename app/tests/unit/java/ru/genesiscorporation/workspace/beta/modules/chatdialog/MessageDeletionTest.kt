package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class MessageDeletionTest {
    @Test
    fun `only own persisted messages from this conversation can be deleted`() {
        val message = message()
        assertTrue(canDeleteMessage(message, STREAM_UUID, TOPIC_UUID))
        assertFalse(canDeleteMessage(message.copy(isOwn = false), STREAM_UUID, TOPIC_UUID))
        assertFalse(canDeleteMessage(message.copy(uuid = ""), STREAM_UUID, TOPIC_UUID))
        assertFalse(canDeleteMessage(message.copy(uuid = "1-1-1-1-1"), STREAM_UUID, TOPIC_UUID))
        assertFalse(canDeleteMessage(message, "another-stream", TOPIC_UUID))
        assertFalse(canDeleteMessage(message, STREAM_UUID, "another-topic"))
        assertFalse(canDeleteMessage(message, STREAM_UUID, TOPIC_UUID, message.uuid))
    }

    @Test
    fun `repeated tap sends one request and removes the message only after success`() = runBlocking {
        val result = CompletableDeferred<ApiResult<String, ApiError>>()
        val requests = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val deletion = deletion(
            request = { requests += it; result.await() },
            onDeleted = { removed += it },
        )
        val first = launch(start = CoroutineStart.UNDISPATCHED) { deletion.delete(message()) }

        assertEquals(setOf(MESSAGE_UUID), deletion.deletingMessageUuids.value)
        assertTrue(removed.isEmpty())
        deletion.delete(message())
        assertEquals(listOf(MESSAGE_UUID), requests)

        result.complete(ApiResult.Success(""))
        first.join()
        assertEquals(listOf(MESSAGE_UUID), removed)
        assertTrue(deletion.deletingMessageUuids.value.isEmpty())
        assertNull(deletion.actionError.value)
    }

    @Test
    fun `server failure retains message clears pending state and allows retry`() = runBlocking {
        val removed = mutableListOf<String>()
        var response: ApiResult<String, ApiError> = ApiResult.Error(ApiError("Forbidden", "403"))
        val deletion = deletion(request = { response }, onDeleted = { removed += it })

        deletion.delete(message())
        assertTrue(removed.isEmpty())
        assertTrue(deletion.deletingMessageUuids.value.isEmpty())
        assertNotNull(deletion.actionError.value)

        response = ApiResult.Success("")
        deletion.delete(message())
        assertEquals(listOf(MESSAGE_UUID), removed)
        assertNull(deletion.actionError.value)
    }

    @Test
    fun `invalid ownership does not call the API`() = runBlocking {
        var requests = 0
        val deletion = deletion(request = { requests++; ApiResult.Success("") })

        deletion.delete(message().copy(isOwn = false))

        assertEquals(0, requests)
        assertNotNull(deletion.actionError.value)
        assertEquals(R.string.message_delete_not_allowed, deletion.actionError.value?.resourceId)
        deletion.clearActionError(requireNotNull(deletion.actionError.value))
        assertNull(deletion.actionError.value)
    }

    @Test
    fun `request exception retains message and reports failure`() = runBlocking {
        var removed = false
        val deletion = deletion(
            request = { throw IllegalStateException("Connection closed") },
            onDeleted = { removed = true },
        )

        deletion.delete(message())

        assertFalse(removed)
        assertNotNull(deletion.actionError.value)
        assertEquals(R.string.message_delete_failed, deletion.actionError.value?.resourceId)
        assertTrue(deletion.deletingMessageUuids.value.isEmpty())
    }

    @Test
    fun `identical errors get new tokens and acknowledging an older event keeps the latest`() = runBlocking {
        val deletion = deletion(request = { ApiResult.Error(ApiError("Failure", "500")) })
        deletion.delete(message())
        val first = requireNotNull(deletion.actionError.value)
        deletion.delete(message())
        val second = requireNotNull(deletion.actionError.value)

        assertEquals(first.resourceId, second.resourceId)
        assertEquals(first.formatArgs, second.formatArgs)
        assertNotEquals(first.token, second.token)
        deletion.clearActionError(first)
        assertEquals(second, deletion.actionError.value)
        deletion.clearActionError(second)
        assertNull(deletion.actionError.value)
    }

    @Test
    fun `cancellation releases pending state without removing the message`() = runBlocking {
        var removed = false
        val deletion = deletion(request = { awaitCancellation() }, onDeleted = { removed = true })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { deletion.delete(message()) }

        job.cancelAndJoin()

        assertFalse(removed)
        assertNull(deletion.actionError.value)
        assertTrue(deletion.deletingMessageUuids.value.isEmpty())
    }

    private fun deletion(
        request: suspend (String) -> ApiResult<String, ApiError>,
        onDeleted: (String) -> Unit = {},
    ) = MessageDeletion(
        canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
        request = request,
        onDeleted = onDeleted,
    )

    private fun message() = MessageResponse(
        uuid = MESSAGE_UUID,
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = "current-user",
        authorUuid = "current-user",
        payload = MessageResponsePayload("markdown", "Message to delete"),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )

    private companion object {
        const val MESSAGE_UUID = "ac0819d1-a91f-4b87-a1bc-e4b0d353f162"
        const val STREAM_UUID = "stream"
        const val TOPIC_UUID = "topic"
    }
}
