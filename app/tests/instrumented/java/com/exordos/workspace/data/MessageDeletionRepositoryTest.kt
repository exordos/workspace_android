package com.exordos.workspace.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.Before
import org.junit.runner.RunWith
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.exordos.workspace.data.uio.MessageReactionUIO
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.uio.MessagePayloadUIO
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.uio.StreamBindingUIO
import com.exordos.workspace.data.uio.DraftUIO
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.data.uio.toLastMessageUIO

@RunWith(AndroidJUnit4::class)
class MessageDeletionRepositoryTest {
    private lateinit var repository: EventsRepository
    private lateinit var http: HttpClient

    @Before
    fun createRepository() {
        // The repository now owns Android-backed token storage. Keep these
        // cache and event assertions in the instrumented runtime.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "cassi-message-cache-${UUID.randomUUID()}"
        http = HttpClient(CIO)
        repository = EventsRepository(
            id,
            ServerConfig(id, "http://127.0.0.1", "", "Message cache test"),
            SecureTokenStore(context),
            WorkspaceAPIClient(http),
            context,
        )
    }

    @After
    fun closeRepository() {
        repository.close()
        http.close()
    }

    @Test
    fun `deletion event removes only matching message and clears its cached previews`() = runBlocking {
        val deleted = message("deleted")
        val kept = message("kept")
        repository.addStreamTopicMessages("stream", "topic", listOf(kept, deleted))
        repository.messageDao.upsert(listOf(kept, deleted))
        repository.setInitialLastMessages(listOf(kept, deleted).map { it.toLastMessageUIO() })
        repository.setInitialMessageReactions(
            listOf(
                MessageReactionUIO("deleted-reaction", "user", "smile", deleted.uuid),
                MessageReactionUIO("kept-reaction", "user", "smile", kept.uuid),
            ),
        )
        repository.setInitialStreams(listOf(stream(deleted), stream(kept).copy(uuid = "other")))
        repository.setInitialTopics(listOf(topic(deleted)))

        repository.didReceiveMessageEvent(
            """{"kind":"message.deleted","uuid":"deleted","stream_uuid":"stream","topic_uuid":"topic"}""",
            "deleted",
        )

        assertEquals(listOf(kept), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(listOf(kept), repository.messages.first())
        assertEquals(listOf("kept-reaction"), repository.userReactions.value.map { it.uuid })
        assertNull(repository.streams.first().single { it.uuid == "stream" }.lastMessageUuid)
        assertNull(repository.lastMessageDao.getLastMessage(deleted.uuid))
        assertEquals(kept.uuid, repository.streams.first().single { it.uuid == "other" }.lastMessageUuid)
        assertNull(repository.topics.first().first().lastMessageUuid)

        repository.removeMessage(deleted.uuid)
        assertEquals(listOf(kept), repository.messages.first())
    }

    @Test
    fun `minimal deletion payload removes cached copies from every conversation`() {
        val deleted = message("deleted")
        repository.addStreamTopicMessages("stream", "topic", listOf(deleted))
        repository.addStreamTopicMessages("stream", "another-topic", listOf(deleted))

        repository.didReceiveMessageEvent("""{"uuid":"deleted"}""", "deleted")

        assertEquals(emptyList<MessageUIO>(), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(emptyList<MessageUIO>(), repository.streamTopicMessages.value["stream.another-topic"])
    }

    @Test
    fun `deletion signal also reaches active quote resolvers when the source was not cached`() = runBlocking {
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { repository.messageDeletedEvents.first() }
        }

        repository.didReceiveMessageEvent("""{"uuid":"cross-chat-source"}""", "deleted")

        assertEquals("cross-chat-source", event.await())
    }

    @Test
    fun `source edit signal is delivered even when its conversation is not loaded`() = runBlocking {
        val update = message("cross-chat-source")
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { repository.messageUpdatedEvents.first() }
        }

        repository.updateMessage(update)
        val received = event.await()
        update.payload.content = "Changed outside event"

        assertEquals("cross-chat-source", received.payload.content)
        assertTrue(repository.streamTopicMessages.value.isEmpty())
    }

    @Test
    fun `WebSocket before HTTP confirmation leaves one message without mutating the pending snapshot`() = runBlocking {
        val pending = message("pending-client-id")
        val server = message("server-id").copy(payload = MessagePayloadUIO("markdown", "Server content"))
        repository.addStreamTopicMessages("stream", "topic", listOf(pending))
        repository.messageDao.upsert(listOf(pending))
        repository.addMessageToStreamTopic(server)
        repository.messageDao.upsert(listOf(server))

        repository.reconcileSentMessage(pending.uuid, pending.copy(uuid = server.uuid))

        assertEquals("pending-client-id", pending.uuid)
        assertEquals(listOf(server), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(listOf(server), repository.messages.first())
    }

    @Test
    fun `HTTP confirmation before WebSocket leaves one canonical message`() = runBlocking {
        val pending = message("pending-client-id")
        val confirmed = pending.copy(uuid = "server-id")
        repository.addStreamTopicMessages("stream", "topic", listOf(pending))
        repository.messageDao.upsert(listOf(pending))

        repository.reconcileSentMessage(pending.uuid, confirmed)
        repository.addMessageToStreamTopic(confirmed)

        assertEquals("pending-client-id", pending.uuid)
        assertEquals(listOf(confirmed), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(listOf(confirmed), repository.messages.first())
    }

    @Test
    fun `backend stream deletion clears its projections and leaves other streams intact`() = runBlocking {
        val deleted = message("deleted")
        val kept = message("kept").copy(streamUuid = "other")
        repository.setInitialStreams(listOf(stream(deleted), stream(kept).copy(uuid = "other")))
        repository.addStreamBindings("stream", listOf(StreamBindingUIO("binding", "member", "stream", "user")))
        repository.addStreamBindings("other", listOf(StreamBindingUIO("other-binding", "member", "other", "user")))
        repository.addStreamTopicMessages("stream", "topic", listOf(deleted))
        repository.addStreamTopicMessages("other", "topic", listOf(kept))
        repository.messageDao.upsert(listOf(deleted, kept))
        repository.setInitialTopics(listOf(topic(deleted)))
        repository.setInitialTopics(listOf(topic(kept).copy(uuid = "other-topic", streamUuid = "other")))
        repository.setInitialDrafts(listOf(DraftUIO("draft", "stream", "topic", deleted.updatedAt, deleted.payload, 1)))
        repository.setInitialMessageReactions(listOf(MessageReactionUIO("reaction", "user", "smile", deleted.uuid)))
        val signal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { repository.streamDeletedEvents.first() }
        }
        repository.didReceiveStreamEvent("""{"kind":"stream.deleted","uuid":"stream","source_name":"native","source":{"kind":"native"}}""", "deleted")
        assertEquals("stream", signal.await())
        assertEquals(listOf("other"), repository.streams.first().map { it.uuid })
        assertEquals(setOf("other"), repository.streamBindings.value.keys)
        assertEquals(listOf("other"), repository.topics.first().map { it.streamUuid })
        assertEquals(setOf("other.topic"), repository.streamTopicMessages.value.keys)
        assertEquals(listOf(kept), repository.messages.first())
        assertTrue(repository.drafts.first().isEmpty())
        assertTrue(repository.userReactions.value.isEmpty())
        repository.removeStream("stream") // HTTP completion after the same event is harmless.
        assertEquals(listOf(kept), repository.messages.first())
    }

    private fun message(uuid: String) = MessageUIO(
        uuid = uuid,
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = "stream",
        topicUuid = "topic",
        userUuid = "user",
        authorUuid = "user",
        payload = MessagePayloadUIO("markdown", uuid),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )

    private fun stream(message: MessageUIO) = StreamUIO(
        uuid = "stream",
        unreadCount = 0,
        activeUnreadCount = 0,
        passiveUnreadCount = 0,
        updatedAt = message.updatedAt,
        name = "Conversation",
        isPrivate = false,
        color = 0,
        lastMessageUuid = message.uuid,
        notificationMode = "all",
    )

    private fun topic(message: MessageUIO) = TopicUIO(
        uuid = "topic",
        name = "Topic",
        color = 0,
        streamUuid = "stream",
        updatedAt = message.updatedAt,
        unreadCount = 0,
        activeUnreadCount = 0,
        isDone = false,
        isDefault = true,
        lastMessageUuid = message.uuid,
        notificationMode = "all",
    )
}
