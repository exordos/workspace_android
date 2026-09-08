package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class MessageForwardingTest {
    @Test fun `forward snapshots preserve body order and escape author markdown`() {
        val first = source(FIRST).copy(user = UserResponseData(username = "A ](link)\\nB", uuid = AUTHOR, status = "active", avatar = ""))
        val second = source(SECOND).copy(payload = MessageResponsePayload("markdown", "second source text"))
        val content = requireNotNull(buildWorkspaceForwardMarkdown(listOf(second, first)))
        assertTrue(content.indexOf("second source text") < content.lastIndexOf("source text"))
        assertTrue(content.contains("A \\]\\(link\\)\\\\nB"))
        assertTrue(content.contains("> source text"))
        assertFalse(content.contains("urn:quote:"))
        assertNull(buildWorkspaceForwardMarkdown(listOf(first, first)))
        assertNull(buildWorkspaceForwardMarkdown(listOf(first.copy(uuid = "local-one"))))
        assertNull(buildWorkspaceForwardMarkdown(emptyList()))
    }

    @Test fun `readback uses returned uuid and confirms exact destination content and author`() = runBlocking {
        val fixture = Fixture()
        fixture.sender.send(TARGET)
        assertEquals(listOf(RETURNED), fixture.reads)
        assertEquals(ForwardDeliveryStatus.COMPLETED, fixture.sender.state.value.status)
        assertEquals(1, fixture.confirmed.size)
        fixture.sender.send(TARGET)
        assertEquals(1, fixture.posts)
    }

    @Test fun `forbidden send retains sources and allows an explicit retry`() = runBlocking {
        val fixture = Fixture(response = ApiResult.Error(ApiError("Forbidden", "403")))
        fixture.sender.send(TARGET)
        assertEquals(ForwardDeliveryStatus.EDITING, fixture.sender.state.value.status)
        assertNotNull(fixture.sender.state.value.error)
        assertTrue(fixture.confirmed.isEmpty())
        assertTrue(fixture.reads.isEmpty())
        fixture.sender.send(TARGET)
        assertEquals(2, fixture.posts)
    }

    @Test fun `ambiguous send only verifies supplied uuid and never posts twice`() = runBlocking {
        val fixture = Fixture(response = ApiResult.Error(ApiError("connection lost", "REQUEST_FAILED")), readable = false)
        fixture.sender.send(TARGET)
        assertEquals(ForwardDeliveryStatus.UNCERTAIN, fixture.sender.state.value.status)
        assertEquals(listOf(REQUESTED), fixture.reads)
        fixture.sender.send(TARGET)
        fixture.sender.verify()
        assertEquals(1, fixture.posts)
        assertEquals(listOf(REQUESTED, REQUESTED), fixture.reads)
        assertTrue(fixture.confirmed.isEmpty())
    }

    @Test fun `wrong stream author or content never count as success`() = runBlocking {
        for (changed in listOf<(MessageResponse) -> MessageResponse>(
            { it.copy(streamUuid = FIRST) },
            { it.copy(authorUuid = FIRST) },
            { it.copy(payload = MessageResponsePayload("markdown", "different")) },
        )) {
            val fixture = Fixture(transform = changed)
            fixture.sender.send(TARGET)
            assertEquals(ForwardDeliveryStatus.UNCERTAIN, fixture.sender.state.value.status)
            assertTrue(fixture.confirmed.isEmpty())
        }
    }

    @Test fun `double tap while post waits performs a single request`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var posts = 0
        val sender = MessageForwarding(listOf(source(FIRST)), AUTHOR,
            post = { _, _, _ -> posts++; gate.await(); ApiResult.Error(ApiError("forbidden", "403")) },
            read = { ApiResult.Success(emptyList()) }, onConfirmed = {}, newUuid = { REQUESTED })
        val first = launch(start = CoroutineStart.UNDISPATCHED) { sender.send(TARGET) }
        sender.send(TARGET)
        assertEquals(1, posts)
        gate.complete(Unit)
        first.join()
    }

    @Test fun `dismiss and reopen resumes the unresolved controller and original requested uuid`() = runBlocking {
        val store = ForwardSessionStore()
        val fixture = Fixture(response = ApiResult.Error(ApiError("lost", "REQUEST_FAILED")), readable = false)
        val first = store.getOrCreate("owner") { fixture.sender }
        first.send(TARGET)
        val reopened = store.getOrCreate("owner") { error("Must not replace an unresolved send") }
        assertSame(first, reopened)
        reopened.send(TARGET)
        reopened.verify()
        assertEquals(1, fixture.posts)
        assertEquals(listOf(REQUESTED, REQUESTED), fixture.reads)
        assertNull(store.unresolved("another-owner"))
    }

    @Test fun `uncertain attachment preparation never starts message post until verified`() = runBlocking {
        var prepared = false
        var posts = 0
        val sender = MessageForwarding(listOf(source(FIRST)), AUTHOR,
            post = { _, _, _ -> posts++; ApiResult.Error(ApiError("forbidden", "403")) },
            read = { ApiResult.Success(emptyList()) }, onConfirmed = {}, newUuid = { REQUESTED },
            prepare = { if (!prepared) throw ForwardPreparationFailure(R.string.forward_upload_uncertain, true) else "materialized" })
        sender.send(TARGET)
        assertEquals(ForwardDeliveryStatus.UNCERTAIN, sender.state.value.status)
        sender.send(TARGET)
        assertEquals(0, posts)
        prepared = true
        sender.verify()
        assertEquals(1, posts)
    }

    @Test fun `explicit abandonment unlocks a new selection without retrying the old post`() = runBlocking {
        val store = ForwardSessionStore()
        val fixture = Fixture(response = ApiResult.Error(ApiError("lost", "REQUEST_FAILED")), readable = false)
        val old = store.getOrCreate("owner") { fixture.sender }
        old.send(TARGET)
        assertFalse(store.abandon("another-owner", old))
        assertSame(old, store.unresolved("owner"))
        assertTrue(store.abandon("owner", old))
        assertEquals(1, fixture.posts)
        assertEquals(listOf(REQUESTED), fixture.reads)
        assertNull(store.unresolved("owner"))
        var newPosts = 0
        val fresh = store.getOrCreate("owner") {
            MessageForwarding(listOf(source(SECOND)), AUTHOR,
                post = { _, _, _ -> newPosts++; ApiResult.Error(ApiError("lost", "REQUEST_FAILED")) },
                read = { ApiResult.Success(emptyList()) }, onConfirmed = {})
        }
        fresh.send(TARGET)
        assertEquals(1, newPosts)
        assertEquals(listOf(SECOND), fresh.sources.map { it.uuid })
        assertFalse(store.abandon("owner", old))
        assertSame(fresh, store.unresolved("owner"))
        assertEquals(1, fixture.posts)
    }

    @Test fun `upload uncertainty can be abandoned only after the active request has stopped`() = runBlocking {
        val store = ForwardSessionStore()
        val gate = CompletableDeferred<Unit>()
        var preparations = 0
        var posts = 0
        val sender = store.getOrCreate("owner") {
            MessageForwarding(listOf(source(FIRST)), AUTHOR,
                post = { _, _, _ -> posts++; ApiResult.Error(ApiError("lost", "REQUEST_FAILED")) },
                read = { ApiResult.Success(emptyList()) }, onConfirmed = {},
                prepare = { preparations++; gate.await(); throw ForwardPreparationFailure(R.string.forward_upload_uncertain, true) })
        }
        val sending = launch(start = CoroutineStart.UNDISPATCHED) { sender.send(TARGET) }
        assertFalse(store.abandon("owner", sender))
        gate.complete(Unit)
        sending.join()
        assertTrue(store.abandon("owner", sender))
        assertNull(store.unresolved("owner"))
        assertEquals(1, preparations)
        assertEquals(0, posts)
    }

    private class Fixture(
        response: ApiResult<SendMessageResponse, ApiError> = ApiResult.Success(SendMessageResponse(RETURNED, TOPIC)),
        readable: Boolean = true,
        transform: (MessageResponse) -> MessageResponse = { it },
    ) {
        var posts = 0
        val reads = mutableListOf<String>()
        val confirmed = mutableListOf<MessageResponse>()
        val sender = MessageForwarding(listOf(source(FIRST), source(SECOND)), AUTHOR,
            post = { uuid, target, content ->
                posts++
                assertEquals(REQUESTED, uuid)
                assertEquals(TARGET, target)
                assertEquals(buildWorkspaceForwardMarkdown(listOf(source(FIRST), source(SECOND))), content)
                response
            },
            read = { id ->
                reads += id
                ApiResult.Success(if (!readable) emptyList() else listOf(transform(source(id).copy(
                    streamUuid = STREAM, topicUuid = TOPIC,
                    payload = MessageResponsePayload("markdown", requireNotNull(buildWorkspaceForwardMarkdown(listOf(source(FIRST), source(SECOND))))),
                ))))
            }, onConfirmed = { confirmed += it }, newUuid = { REQUESTED })
    }

    companion object {
        private const val FIRST = "00000000-0000-0000-0000-000000000001"
        private const val SECOND = "00000000-0000-0000-0000-000000000002"
        private const val REQUESTED = "00000000-0000-0000-0000-000000000003"
        private const val RETURNED = "00000000-0000-0000-0000-000000000004"
        private const val STREAM = "00000000-0000-0000-0000-000000000005"
        private const val TOPIC = "00000000-0000-0000-0000-000000000006"
        private const val AUTHOR = "00000000-0000-0000-0000-000000000007"
        private val TARGET = ForwardDestination(STREAM, TOPIC)
        private fun source(uuid: String) = MessageResponse(uuid, "2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z",
            STREAM, TOPIC, AUTHOR, AUTHOR, MessageResponsePayload("markdown", "source text"), true, emptyMap(), true)
    }
}
