package ru.genesiscorporation.workspace.beta.data

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
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageReaction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

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
        )
    }

    @After
    fun closeRepository() {
        repository.close()
        http.close()
    }

    @Test
    fun `deletion event removes only matching message and clears its cached previews`() {
        val deleted = message("deleted")
        val kept = message("kept")
        repository.addStreamTopicMessages("stream", "topic", listOf(kept, deleted))
        repository.setInitialMessagesPool(listOf(kept, deleted))
        repository.setInitialMessageReactions(
            listOf(
                MessageReaction("deleted-reaction", "user", "smile", deleted.uuid),
                MessageReaction("kept-reaction", "user", "smile", kept.uuid),
            ),
        )
        repository.setInitialStreams(listOf(stream(deleted), stream(kept).copy(uuid = "other")))
        repository.addStreamTopics("stream", listOf(topic(deleted)))

        repository.didReceiveMessageEvent(
            """{"kind":"message.deleted","uuid":"deleted","stream_uuid":"stream","topic_uuid":"topic"}""",
            "deleted",
        )

        assertEquals(listOf(kept), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(listOf(kept), repository.messagesPool.value)
        assertEquals(listOf("kept-reaction"), repository.userReactions.value.map { it.uuid })
        assertNull(repository.streams.value.first().lastMessageUuid)
        assertNull(repository.streams.value.first().lastMessage)
        assertEquals(kept.uuid, repository.streams.value.last().lastMessageUuid)
        assertNull(repository.streamTopics.value["stream"]?.first()?.lastMessageUuid)
        assertNull(repository.streamTopics.value["stream"]?.first()?.lastMessage)
        assertNull(repository.topicsPool.value.first().lastMessageUuid)
        assertNull(repository.topicsPool.value.first().lastMessage)

        repository.removeMessage(deleted.uuid)
        assertEquals(listOf(kept), repository.messagesPool.value)
    }

    @Test
    fun `minimal deletion payload removes cached copies from every conversation`() {
        val deleted = message("deleted")
        repository.addStreamTopicMessages("stream", "topic", listOf(deleted))
        repository.addStreamTopicMessages("stream", "another-topic", listOf(deleted))

        repository.didReceiveMessageEvent("""{"uuid":"deleted"}""", "deleted")

        assertEquals(emptyList<MessageResponse>(), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(emptyList<MessageResponse>(), repository.streamTopicMessages.value["stream.another-topic"])
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
    fun `WebSocket before HTTP confirmation leaves one message without mutating the pending snapshot`() {
        val pending = message("pending-client-id")
        val server = message("server-id").copy(payload = MessageResponsePayload("markdown", "Server content"))
        repository.addStreamTopicMessages("stream", "topic", listOf(pending))
        repository.setInitialMessagesPool(listOf(pending))
        repository.addMessageToStreamTopic(server)
        repository.updateMessagesPool(listOf(server))

        repository.reconcileSentMessage(pending.uuid, pending.copy(uuid = server.uuid))

        assertEquals("pending-client-id", pending.uuid)
        assertEquals(listOf(server), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(listOf(server), repository.messagesPool.value)
    }

    @Test
    fun `HTTP confirmation before WebSocket leaves one canonical message`() {
        val pending = message("pending-client-id")
        val confirmed = pending.copy(uuid = "server-id")
        repository.addStreamTopicMessages("stream", "topic", listOf(pending))
        repository.setInitialMessagesPool(listOf(pending))

        repository.reconcileSentMessage(pending.uuid, confirmed)
        repository.addMessageToStreamTopic(confirmed)

        assertEquals("pending-client-id", pending.uuid)
        assertEquals(listOf(confirmed), repository.streamTopicMessages.value["stream.topic"])
        assertEquals(listOf(confirmed), repository.messagesPool.value)
    }

    private fun message(uuid: String) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = "stream",
        topicUuid = "topic",
        userUuid = "user",
        authorUuid = "user",
        payload = MessageResponsePayload("markdown", uuid),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )

    private fun stream(message: MessageResponse) = Stream(
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
        lastMessage = message,
    )

    private fun topic(message: MessageResponse) = TopicsResponseData(
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
        lastMessage = message,
    )
}
