package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class EventsRepositoryTest {
    @Test
    fun `replacing a conversation window drops stale server rows but keeps local outbox`() {
        val repository = EventsRepository()
        val stale = message(
            uuid = "10000000-0000-4000-8000-000000000001",
            content = "stale",
        )
        val local = message(
            uuid = "local-20000000-0000-4000-8000-000000000002",
            content = "pending",
        )
        val anchor = message(
            uuid = "30000000-0000-4000-8000-000000000003",
            content = "anchor",
        ).copy(updatedAt = "2026-07-26T00:00:02Z")
        val staleAnchor = anchor.copy(
            updatedAt = "2026-07-26T00:00:01Z",
            payload = anchor.payload.copy(content = "stale anchor"),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(stale, local, anchor),
        )

        repository.replaceStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(staleAnchor),
        )

        assertEquals(
            listOf(local.uuid, anchor.uuid),
            repository.streamTopicMessages.value["$STREAM_UUID.$TOPIC_UUID"]
                ?.map(MessageResponse::uuid),
        )
        assertEquals(
            "anchor",
            repository.streamTopicMessages.value["$STREAM_UUID.$TOPIC_UUID"]
                ?.last()
                ?.payload
                ?.content,
        )
    }


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
    fun `an older history page cannot roll back a newer realtime edit`() {
        val repository = EventsRepository()
        val realtimeMessage = message(
            uuid = MESSAGE_UUID,
            content = "new realtime content",
        ).copy(updatedAt = "2026-07-30T10:00:02Z")
        val stalePageMessage = message(
            uuid = MESSAGE_UUID,
            content = "old page content",
        ).copy(updatedAt = "2026-07-30T10:00:01Z")

        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(realtimeMessage),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(stalePageMessage),
        )

        assertEquals(
            "new realtime content",
            repository.streamTopicMessages.value
                .getValue(TOPIC_KEY)
                .single()
                .payload
                .content,
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

    @Test
    fun `deleted message event removes the message from all projections`() {
        val repository = EventsRepository()
        val deletedMessage = message(uuid = MESSAGE_UUID, content = "gone")
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ).copy(
                    lastMessageUuid = MESSAGE_UUID,
                    lastMessage = deletedMessage,
                ),
            ),
        )
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(
                    lastMessageUuid = MESSAGE_UUID,
                    lastMessage = deletedMessage,
                ),
            ),
        )
        repository.processTextFrame(
            messageEvent(epoch = 1, action = "created", content = "gone"),
        )

        repository.processTextFrame(
            deletedEvent(epoch = 2, objectType = "message", uuid = MESSAGE_UUID),
        )

        assertTrue(repository.messagesPool.value.isEmpty())
        assertTrue(repository.streamTopicMessages.value[TOPIC_KEY].orEmpty().isEmpty())
        assertEquals(null, repository.streams.value.single().lastMessageUuid)
        assertEquals(
            null,
            repository.streamTopics.value
                .getValue(STREAM_UUID)
                .single()
                .lastMessageUuid,
        )
    }

    @Test
    fun `deleted stream event removes its topics messages and folder items`() {
        val repository = EventsRepository()
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                TopicsResponseData(
                    uuid = TOPIC_UUID,
                    name = "Topic",
                    color = 0,
                    streamUuid = STREAM_UUID,
                    updatedAt = "2026-07-26T00:00:00Z",
                    unreadCount = 0,
                    isDone = false,
                    isDefault = true,
                ),
            ),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(message(uuid = MESSAGE_UUID, content = "gone")),
        )

        repository.processTextFrame(
            deletedEvent(epoch = 2, objectType = "stream", uuid = STREAM_UUID),
        )

        assertTrue(repository.streamTopics.value[STREAM_UUID].isNullOrEmpty())
        assertTrue(repository.streamTopicMessages.value[TOPIC_KEY].isNullOrEmpty())
    }

    @Test
    fun `topic update uses the matching realtime message as its preview`() {
        val repository = EventsRepository()
        val oldMessage = message(uuid = "old-message", content = "old")
        val newMessage = message(uuid = "new-message", content = "new")
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(topic(lastMessageUuid = oldMessage.uuid, lastMessage = oldMessage)),
        )
        repository.updateMessagesPool(listOf(newMessage))

        repository.updateTopic(
            topic(lastMessageUuid = newMessage.uuid),
        )

        val updated = repository.streamTopics.value.getValue(STREAM_UUID).single()
        assertEquals(newMessage.uuid, updated.lastMessageUuid)
        assertEquals(newMessage.uuid, updated.lastMessage?.uuid)
        assertEquals("new", updated.lastMessage?.payload?.content)
    }

    @Test
    fun `topic update clears a stale preview when the new message is not loaded`() {
        val repository = EventsRepository()
        val oldMessage = message(uuid = "old-message", content = "old")
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(topic(lastMessageUuid = oldMessage.uuid, lastMessage = oldMessage)),
        )

        repository.updateTopic(
            topic(lastMessageUuid = "not-loaded"),
        )

        val updated = repository.streamTopics.value.getValue(STREAM_UUID).single()
        assertEquals("not-loaded", updated.lastMessageUuid)
        assertEquals(null, updated.lastMessage)
    }

    @Test
    fun `older topic snapshots do not roll back a newer realtime projection`() {
        val repository = EventsRepository()
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(
                    lastMessageUuid = null,
                    name = "Realtime",
                    updatedAt = "2026-07-26T00:00:02Z",
                ),
            ),
        )

        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(
                    lastMessageUuid = null,
                    name = "Stale snapshot",
                    updatedAt = "2026-07-26T00:00:01Z",
                ),
            ),
        )

        assertEquals(
            "Realtime",
            repository.streamTopics.value.getValue(STREAM_UUID).single().name,
        )
    }

    @Test
    fun `api and realtime stream creation upsert instead of duplicating rows`() {
        val repository = EventsRepository()
        val original = stream(defaultTopicUuid = "default-topic", notificationMode = "all_messages")
        repository.addStream(original)
        repository.addStream(original.copy(name = "Updated"))

        assertEquals(1, repository.streams.value.size)
        assertEquals("Updated", repository.streams.value.single().name)

        repository.updateStream(
            original.copy(
                notificationMode = "muted",
                defaultTopicUuid = null,
            ),
        )
        assertEquals("muted", repository.streams.value.single().notificationMode)
        assertEquals(
            "default-topic",
            repository.streams.value.single().defaultTopicUuid,
        )
    }

    @Test
    fun `authoritative all-topic snapshot removes stale rows and records empty streams`() {
        val repository = EventsRepository()
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(topic(lastMessageUuid = null, name = "Stale")),
        )
        val anotherStreamUuid = "stream-2"
        val currentTopic = TopicsResponseData(
            uuid = "topic-2",
            name = "Current",
            streamUuid = anotherStreamUuid,
            updatedAt = "2026-07-30T10:00:00Z",
            unreadCount = 2,
            isDone = false,
            isDefault = true,
        )

        repository.replaceAllStreamTopics(
            streamUuids = setOf(STREAM_UUID, anotherStreamUuid),
            topics = listOf(
                currentTopic,
                currentTopic.copy(
                    uuid = "foreign-topic",
                    streamUuid = "not-visible",
                ),
            ),
        )

        assertEquals(emptyList<TopicsResponseData>(), repository.streamTopics.value[STREAM_UUID])
        assertEquals(
            listOf("topic-2"),
            repository.streamTopics.value[anotherStreamUuid]?.map { it.uuid },
        )
        assertTrue("not-visible" !in repository.streamTopics.value)
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

    private fun deletedEvent(epoch: Int, objectType: String, uuid: String): String = """
        {
          "schema_version": 1,
          "epoch_version": $epoch,
          "object_type": "$objectType",
          "action": "deleted",
          "payload": {
            "kind": "$objectType.deleted",
            "uuid": "$uuid"
          }
        }
    """.trimIndent()

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

    private fun topic(
        lastMessageUuid: String?,
        lastMessage: MessageResponse? = null,
        name: String = "Topic",
        updatedAt: String = "2026-07-26T00:00:00Z",
    ) = TopicsResponseData(
        uuid = TOPIC_UUID,
        name = name,
        color = 0,
        streamUuid = STREAM_UUID,
        updatedAt = updatedAt,
        unreadCount = 0,
        isDone = false,
        isDefault = true,
        lastMessageUuid = lastMessageUuid,
        lastMessage = lastMessage,
    )

    private fun stream(
        defaultTopicUuid: String?,
        notificationMode: String,
    ) = Stream(
        uuid = STREAM_UUID,
        unreadCount = 0,
        updatedAt = "2026-07-26T00:00:00Z",
        name = "Stream",
        isPrivate = false,
        notificationMode = notificationMode,
        defaultTopicUuid = defaultTopicUuid,
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
