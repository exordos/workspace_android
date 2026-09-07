package ru.genesiscorporation.workspace.beta.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

class MessageDeletionRepositoryTest {
    private val repository = EventsRepository()

    @After
    fun closeRepository() {
        repository.close()
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
        isDone = false,
        isDefault = true,
        lastMessageUuid = message.uuid,
        notificationMode = "all",
        lastMessage = message,
    )
}
