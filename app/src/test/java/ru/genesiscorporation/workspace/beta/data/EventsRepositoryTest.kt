package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class EventsRepositoryTest {

    @Test
    fun `created event is retained before the topic snapshot loads`() {
        val repository = EventsRepository()

        repository.processTextFrame(messageEvent(epoch = 1, action = "created", content = "live"))

        assertEquals(
            listOf("live"),
            repository.streamTopicMessages.value[TOPIC_KEY]?.map { it.payload.content },
        )

        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(message(uuid = "snapshot-message", content = "snapshot")),
        )

        assertEquals(
            setOf("live", "snapshot"),
            repository.streamTopicMessages.value[TOPIC_KEY]
                ?.map { it.payload.content }
                ?.toSet(),
        )
    }

    @Test
    fun `event cursor deduplicates replayed messages and advances on ready`() {
        val repository = EventsRepository()
        val event = messageEvent(epoch = 7, action = "created", content = "once")

        repository.processTextFrame(event)
        repository.processTextFrame(event)
        repository.processTextFrame(
            """{"type":"ready","epoch_generation":"generation-1","epoch_version":9}""",
        )

        assertEquals(1, repository.streamTopicMessages.value[TOPIC_KEY]?.size)
        assertEquals(9, repository.latestEpoch)
        assertEquals("generation-1", repository.epochGeneration)
    }

    @Test
    fun `updated message replaces content and reactions in an open topic`() {
        val repository = EventsRepository()
        repository.processTextFrame(messageEvent(epoch = 1, action = "created", content = "before"))

        repository.processTextFrame(
            messageEvent(
                epoch = 2,
                action = "updated",
                content = "after",
                reactions = """"thumbs_up":2""",
            ),
        )

        val updated = repository.streamTopicMessages.value[TOPIC_KEY]?.single()
        assertEquals("after", updated?.payload?.content)
        assertEquals(2, updated?.reactions?.get("thumbs_up"))
    }

    @Test
    fun `reaction create update and delete keep current user selection in sync`() {
        val repository = EventsRepository()
        repository.currentUser = UserResponseData(
            username = "cassi",
            uuid = USER_UUID,
            status = "online",
            avatar = "",
        )

        repository.processTextFrame(reactionEvent(epoch = 1, action = "created"))
        repository.processTextFrame(reactionEvent(epoch = 2, action = "updated"))
        assertEquals(1, repository.userReactions.value.size)

        repository.processTextFrame(reactionEvent(epoch = 3, action = "deleted"))
        assertTrue(repository.userReactions.value.isEmpty())
    }

    private fun messageEvent(
        epoch: Int,
        action: String,
        content: String,
        reactions: String = "",
    ): String = """
        {
          "schema_version": 1,
          "epoch_version": $epoch,
          "object_type": "message",
          "action": "$action",
          "payload": {
            "kind": "message.$action",
            "uuid": "$MESSAGE_UUID",
            "updated_at": "2026-07-26T00:00:00Z",
            "created_at": "2026-07-26T00:00:00Z",
            "stream_uuid": "$STREAM_UUID",
            "topic_uuid": "$TOPIC_UUID",
            "user_uuid": "$USER_UUID",
            "author_uuid": "$USER_UUID",
            "payload": {"kind": "markdown", "content": "$content"},
            "is_own": false,
            "reactions": {$reactions}
          }
        }
    """.trimIndent()

    private fun reactionEvent(epoch: Int, action: String): String {
        val payload = if (action == "deleted") {
            """
                {
                  "kind": "message_reaction.deleted",
                  "uuid": "$REACTION_UUID",
                  "user_uuid": "$USER_UUID"
                }
            """.trimIndent()
        } else {
            """
                {
                  "kind": "message_reaction.$action",
                  "uuid": "$REACTION_UUID",
                  "user_uuid": "$USER_UUID",
                  "emoji_name": "thumbs_up",
                  "message_uuid": "$MESSAGE_UUID"
                }
            """.trimIndent()
        }
        return """
            {
              "schema_version": 1,
              "epoch_version": $epoch,
              "object_type": "message_reaction",
              "action": "$action",
              "payload": $payload
            }
        """.trimIndent()
    }

    private fun message(uuid: String, content: String) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-07-26T00:00:00Z",
        createdAt = "2026-07-26T00:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = USER_UUID,
        authorUuid = USER_UUID,
        payload = MessageResponsePayload(kind = "markdown", content = content),
        isOwn = false,
        reactions = emptyMap(),
    )

    companion object {
        private const val STREAM_UUID = "stream-1"
        private const val TOPIC_UUID = "topic-1"
        private const val TOPIC_KEY = "$STREAM_UUID.$TOPIC_UUID"
        private const val USER_UUID = "user-1"
        private const val MESSAGE_UUID = "message-1"
        private const val REACTION_UUID = "reaction-1"
    }
}
