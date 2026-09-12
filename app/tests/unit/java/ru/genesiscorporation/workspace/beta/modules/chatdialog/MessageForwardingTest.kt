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
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class MessageForwardingTest {
    @Test fun `forward snapshots preserve body order and escape author markdown`() {
        val first = source(FIRST).copy(user = UserResponseData(username = "A ](link)\\nB", uuid = AUTHOR, status = "active", avatar = ""))
        val second = source(SECOND).copy(payload = MessageResponsePayload("markdown", "second source text"))
        val content = requireNotNull(buildWorkspaceForwardMarkdown(listOf(second, first)))
        val forwards = MarkdownPayloadParser.parse(content).filterIsInstance<MessageElement.ForwardSnapshot>()
        assertEquals(listOf(SECOND, FIRST), forwards.map { it.uuid })
        assertEquals(listOf("second source text", "source text"), forwards.map { it.text })
        assertEquals(listOf(STREAM, STREAM), forwards.map { it.sourceLabel })
        assertEquals(listOf("2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z"), forwards.map { it.sourceCreatedAt })
        assertEquals("A ](link)\\nB", forwards[1].displayName)
        assertFalse(content.contains("source text"))
        assertFalse(content.contains("urn:quote:"))
        assertTrue(content.contains("urn:forward:"))
        assertNull(buildWorkspaceForwardMarkdown(listOf(first, first)))
        assertNull(buildWorkspaceForwardMarkdown(listOf(first.copy(uuid = "local-one"))))
        assertNull(buildWorkspaceForwardMarkdown(emptyList()))
    }

    @Test fun `forward snapshot messages cannot be edited as encoded urns`() {
        val forwarded = source(FIRST).copy(
            payload = MessageResponsePayload(
                "markdown",
                requireNotNull(buildWorkspaceForwardMarkdown(listOf(source(SECOND)))),
            ),
        )

        assertFalse(isWorkspaceMessageEditable(forwarded))
        assertTrue(isWorkspaceMessageEditable(source(FIRST)))
        assertFalse(isWorkspaceMessageEditable(source(FIRST).copy(isOwn = false)))
    }

    @Test fun `source navigation stops at the message ACL gate`() = runBlocking {
        var streamReads = 0
        var topicReads = 0
        val destination = resolveForwardSourceDestinationWithAcl(
            FIRST,
            readSource = { null },
            cachedStreams = { emptyList() },
            readStreams = { streamReads++; listOf(stream()) },
            cachedTopics = { emptyList() },
            readTopics = { topicReads++; listOf(topic()) },
        )
        assertNull(destination)
        assertEquals(0, streamReads)
        assertEquals(0, topicReads)
    }

    @Test fun `source navigation uses only route data returned after the ACL read`() = runBlocking {
        val destination = resolveForwardSourceDestinationWithAcl(
            FIRST,
            readSource = { source(FIRST) },
            cachedStreams = { listOf(stream()) },
            readStreams = { error("cached stream must win") },
            cachedTopics = { listOf(topic()) },
            readTopics = { error("cached topic must win") },
        )
        assertEquals(
            ForwardSourceDestination("Engineering", STREAM, "General", TOPIC, false, FIRST),
            destination,
        )
    }

    @Test fun `source navigation caches fallback route data before opening the dialog`() = runBlocking {
        val streams = mutableListOf<Stream>()
        val topics = mutableMapOf<String, MutableList<TopicsResponseData>>()
        val sources = mutableListOf<MessageResponse>()
        val destination = resolveForwardSourceDestinationWithAcl(
            FIRST,
            readSource = { source(FIRST) },
            cachedStreams = { streams },
            readStreams = { listOf(stream()) },
            cachedTopics = { streamUuid -> topics[streamUuid].orEmpty() },
            readTopics = { listOf(topic()) },
            cacheSource = { sources += it },
            cacheStream = { streams += it },
            cacheTopic = { topics.getOrPut(it.streamUuid, ::mutableListOf) += it },
        )

        assertNotNull(destination)
        assertEquals(listOf(STREAM), streams.map { it.uuid })
        assertEquals(listOf(TOPIC), topics[STREAM].orEmpty().map { it.uuid })
        assertEquals(listOf(FIRST), sources.map { it.uuid })
    }

    @Test fun `cached source anchors still load the conversation and all survive the merge`() {
        val cachedAnchor = source(FIRST)
        val otherCachedAnchor = source(REQUESTED)
        val loadedSource = source(SECOND)

        assertTrue(shouldLoadInitialMessages(listOf(cachedAnchor), FIRST))
        val merged = mergeLoadedMessagesPreservingAnchor(
            loadedMessages = listOf(loadedSource),
            cachedMessages = listOf(cachedAnchor, otherCachedAnchor),
            anchorMessageUuid = FIRST,
        )

        assertEquals(setOf(FIRST, SECOND, REQUESTED), merged.map { it.uuid }.toSet())
        assertSame(cachedAnchor, merged.single { it.uuid == FIRST })
        assertSame(otherCachedAnchor, merged.single { it.uuid == REQUESTED })
    }

    @Test fun `anchored initialization defers read receipts to visible messages`() {
        assertFalse(shouldMarkInitialPageReadThroughLatest(FIRST))
        assertTrue(shouldMarkInitialPageReadThroughLatest(null))
    }

    @Test fun `initial anchor scroll waits until the loaded list is rendered`() {
        assertFalse(shouldPerformInitialMessageScroll(isLoading = true, hasDoneInitialScroll = false, messageCount = 1))
        assertTrue(shouldPerformInitialMessageScroll(isLoading = false, hasDoneInitialScroll = false, messageCount = 2))
        assertFalse(shouldPerformInitialMessageScroll(isLoading = false, hasDoneInitialScroll = true, messageCount = 2))
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
        private fun stream() = Stream(
            STREAM, 0, 0, 0, "2026-09-07T12:00:00Z", "Engineering", false,
            0x7087FF, notificationMode = "all",
        )
        private fun topic() = TopicsResponseData(
            TOPIC, "General", 0x7087FF, STREAM, "2026-09-07T12:00:00Z", 0,
            false, true, notificationMode = "all",
        )
        private fun source(uuid: String) = MessageResponse(uuid, "2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z",
            STREAM, TOPIC, AUTHOR, AUTHOR, MessageResponsePayload("markdown", "source text"), true, emptyMap(), true)
    }
}
