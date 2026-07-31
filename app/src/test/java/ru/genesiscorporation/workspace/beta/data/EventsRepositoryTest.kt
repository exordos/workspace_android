package ru.genesiscorporation.workspace.beta.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProviderReference
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

class EventsRepositoryTest {
    @Test
    fun `message realtime actions publish feed projection deltas`() = runBlocking {
        val repository = EventsRepository()
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            repository.messageProjectionEvents.take(3).toList()
        }
        repository.didReceiveMessageEvent(
            """
                {
                  "uuid": "$VALID_MESSAGE_UUID",
                  "updated_at": "2026-07-31T08:00:00Z",
                  "created_at": "2026-07-31T08:00:00Z",
                  "stream_uuid": "$VALID_STREAM_UUID",
                  "topic_uuid": "$VALID_TOPIC_UUID",
                  "user_uuid": "$VALID_USER_UUID",
                  "author_uuid": "$VALID_USER_UUID",
                  "payload": {"kind": "markdown", "content": "Created"},
                  "is_own": false,
                  "reactions": {},
                  "read": false,
                  "starred": false
                }
            """.trimIndent(),
            "created",
            VALID_OWNER_KEY,
        )
        repository.didReceiveMessageEvent(
            """
                {
                  "kind": "messages.read",
                  "message_uuids": ["$VALID_MESSAGE_UUID"]
                }
            """.trimIndent(),
            "read",
            VALID_OWNER_KEY,
        )
        repository.didReceiveMessageEvent(
            """{"uuid":"$VALID_MESSAGE_UUID"}""",
            "deleted",
            VALID_OWNER_KEY,
        )

        val collected = withTimeout(1_000) { events.await() }
        assertTrue(collected.all { it.ownerKey == VALID_OWNER_KEY })
        assertEquals(listOf(1L, 2L, 3L), collected.map { it.sequence })
        assertEquals(
            VALID_MESSAGE_UUID,
            (collected[0].event as MessageProjectionEvent.Upsert).message.uuid,
        )
        assertEquals(
            listOf(VALID_MESSAGE_UUID),
            (collected[1].event as MessageProjectionEvent.Read).messageUuids,
        )
        assertEquals(
            VALID_MESSAGE_UUID,
            (collected[2].event as MessageProjectionEvent.Deleted).messageUuid,
        )
    }

    @Test
    fun `offline hydration preserves newer network state and local outbox rows`() {
        val repository = EventsRepository()
        val currentMessage = message(MESSAGE_UUID, "network")
            .copy(updatedAt = "2026-07-27T00:00:00Z")
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ).copy(
                    name = "Network stream",
                    updatedAt = "2026-07-27T00:00:00Z",
                ),
            ),
        )
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(
                    lastMessageUuid = MESSAGE_UUID,
                    name = "Network topic",
                    updatedAt = "2026-07-27T00:00:00Z",
                ),
            ),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(
                currentMessage,
                message("local-pending", "outbox"),
            ),
        )
        repository.setInitialFolders(
            listOf(folder("Network folder")),
        )
        repository.setInitialUsers(
            listOf(user("network-user")),
        )
        repository.setInitialStreamBindings(
            listOf(binding(role = "owner")),
        )
        val networkPagination = ConversationPaginationState(
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            mode = ConversationWindowMode.LATEST,
            olderPageMarker = MESSAGE_UUID,
        )
        repository.updateConversationPagination(networkPagination)

        repository.hydrateCachedSnapshot(
            WorkspaceSnapshot(
                streams = listOf(
                    stream(
                        defaultTopicUuid = TOPIC_UUID,
                        notificationMode = "all_messages",
                    ).copy(name = "Cached stream"),
                ),
                topicsByStream = mapOf(
                    STREAM_UUID to listOf(
                        topic(
                            lastMessageUuid = MESSAGE_UUID,
                            name = "Cached topic",
                        ),
                    ),
                ),
                messagesByConversation = mapOf(
                    TOPIC_KEY to listOf(
                        message(MESSAGE_UUID, "cached"),
                        message("cached-only", "offline history"),
                    ),
                ),
                paginationByConversation = mapOf(
                    TOPIC_KEY to ConversationPaginationState(
                        streamUuid = STREAM_UUID,
                        topicUuid = TOPIC_UUID,
                        mode = ConversationWindowMode.UNKNOWN,
                        olderPageMarker = "cached-only",
                        newerPageMarker = MESSAGE_UUID,
                    ),
                ),
                folders = listOf(folder("Cached folder")),
                users = listOf(user("cached-user")),
                streamBindings = listOf(binding(role = "member")),
            ),
        )

        assertEquals("Network stream", repository.streams.value.single().name)
        assertEquals(
            "Network topic",
            repository.streamTopics.value
                .getValue(STREAM_UUID)
                .single()
                .name,
        )
        val messages = repository.streamTopicMessages.value
            .getValue(TOPIC_KEY)
        assertEquals(
            "network",
            messages.single { it.uuid == MESSAGE_UUID }.payload.content,
        )
        assertTrue(messages.any { it.uuid == "cached-only" })
        assertTrue(messages.any { it.uuid == "local-pending" })
        assertEquals(
            "Network folder",
            repository.folders.value.single().title,
        )
        assertEquals(
            "network-user",
            repository.users.value.single().username,
        )
        assertEquals(
            "owner",
            repository.streamBindings.value.single().role,
        )
        assertEquals(
            messages.map(MessageResponse::uuid),
            repository.workspaceSnapshot()
                .messagesByConversation
                .getValue(TOPIC_KEY)
                .map(MessageResponse::uuid),
        )
        assertEquals(
            repository.folders.value,
            repository.workspaceSnapshot().folders,
        )
        assertEquals(
            repository.users.value,
            repository.workspaceSnapshot().users,
        )
        assertEquals(
            repository.streamBindings.value,
            repository.workspaceSnapshot().streamBindings,
        )
        assertEquals(
            networkPagination,
            repository.workspaceSnapshot()
                .paginationByConversation
                .getValue(TOPIC_KEY),
        )
    }

    @Test
    fun `authoritative empty catalog projections are not repopulated by cache`() {
        val repository = EventsRepository()
        repository.setInitialFolders(emptyList())
        repository.setInitialUsers(emptyList())
        repository.setInitialStreamBindings(emptyList())

        repository.hydrateCachedSnapshot(
            WorkspaceSnapshot(
                folders = listOf(folder("Stale folder")),
                users = listOf(user("stale-user")),
                streamBindings = listOf(binding(role = "member")),
            ),
        )

        assertTrue(repository.folders.value.isEmpty())
        assertTrue(repository.users.value.isEmpty())
        assertTrue(repository.streamBindings.value.isEmpty())
    }

    @Test
    fun `account reset permits the next owner cache to hydrate`() {
        val repository = EventsRepository()
        repository.setInitialFolders(emptyList())
        repository.setInitialUsers(emptyList())
        repository.setInitialStreamBindings(emptyList())

        repository.resetAccountState()
        repository.hydrateCachedSnapshot(
            WorkspaceSnapshot(
                folders = listOf(folder("Next owner folder")),
                users = listOf(user("next-owner")),
                streamBindings = listOf(binding(role = "member")),
            ),
        )

        assertEquals(
            "Next owner folder",
            repository.folders.value.single().title,
        )
        assertEquals(
            "next-owner",
            repository.users.value.single().username,
        )
        assertEquals(
            "member",
            repository.streamBindings.value.single().role,
        )
    }

    @Test
    fun `conversation pagination projection is bounded and account reset clears it`() {
        val repository = EventsRepository()
        repeat(MAX_CACHED_CONVERSATIONS + 3) { index ->
            repository.updateConversationPagination(
                ConversationPaginationState(
                    streamUuid = "stream-$index",
                    topicUuid = "topic-$index",
                    mode = ConversationWindowMode.UNKNOWN,
                ),
            )
        }

        assertEquals(
            MAX_CACHED_CONVERSATIONS,
            repository.conversationPagination.value.size,
        )
        assertTrue(
            repository.conversationPagination.value.keys.none {
                it == "stream-0.topic-0"
            },
        )

        repository.resetAccountState()

        assertTrue(repository.conversationPagination.value.isEmpty())
    }

    @Test
    fun `realtime retry backs off across flapping connections and resets after stability`() {
        assertEquals(
            2_000L,
            nextRealtimeRetryDelay(
                currentDelayMillis = 1_000L,
                readyReceived = false,
                connectedDurationMillis = 0L,
            ),
        )
        assertEquals(
            30_000L,
            nextRealtimeRetryDelay(
                currentDelayMillis = 30_000L,
                readyReceived = true,
                connectedDurationMillis = 59_999L,
            ),
        )
        assertEquals(
            1_000L,
            nextRealtimeRetryDelay(
                currentDelayMillis = 30_000L,
                readyReceived = true,
                connectedDurationMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `expired realtime cursor clears derived data but retains local outbox rows`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.latestEpoch = 42
        repository.epochGeneration = "old-generation"
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ),
            ),
        )
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(topic(lastMessageUuid = MESSAGE_UUID)),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(
                message(MESSAGE_UUID, "server"),
                message("local-pending", "pending"),
            ),
        )

        assertTrue(repository.handleRealtimeConnectionClosed(4_410))

        assertEquals(0, repository.latestEpoch)
        assertEquals("", repository.epochGeneration)
        assertTrue(repository.streams.value.isEmpty())
        assertTrue(repository.externalAccounts.value.isEmpty())
        assertTrue(repository.externalChats.value.isEmpty())
        assertTrue(repository.streamTopics.value.isEmpty())
        assertEquals(
            listOf("local-pending"),
            repository.streamTopicMessages.value
                .getValue(TOPIC_KEY)
                .map(MessageResponse::uuid),
        )
        assertEquals(1L, repository.realtimeRecoveryVersion.value)
    }

    @Test
    fun `ordinary realtime close leaves cursor and projections untouched`() {
        val repository = EventsRepository()
        repository.latestEpoch = 42
        repository.epochGeneration = "generation"
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ),
            ),
        )

        assertFalse(repository.handleRealtimeConnectionClosed(1_006))

        assertEquals(42, repository.latestEpoch)
        assertEquals("generation", repository.epochGeneration)
        assertEquals(1, repository.streams.value.size)
        assertEquals(0L, repository.realtimeRecoveryVersion.value)
    }

    @Test
    fun `stream binding realtime events update the cached membership projection`() {
        val repository = EventsRepository()

        repository.processTextFrame(
            """
            {
              "schema_version": 1,
              "epoch_version": 1,
              "object_type": "stream_binding",
              "action": "created",
              "payload": {
                "kind": "stream_bindings.created",
                "uuid": "$STREAM_UUID",
                "items": [
                  {
                    "uuid": "binding-1",
                    "stream_uuid": "$STREAM_UUID",
                    "user_uuid": "$USER_UUID",
                    "who_uuid": "$USER_UUID",
                    "role": "member",
                    "notification_mode": "mentions_only"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("binding-1"),
            repository.streamBindings.value.map { it.uuid },
        )

        repository.processTextFrame(
            """
            {
              "schema_version": 1,
              "epoch_version": 2,
              "object_type": "stream_binding",
              "action": "deleted",
              "payload": {
                "kind": "stream_binding.deleted",
                "uuid": "binding-1",
                "stream_uuid": "$STREAM_UUID",
                "user_uuid": "$USER_UUID"
              }
            }
            """.trimIndent(),
        )

        assertTrue(repository.streamBindings.value.isEmpty())
    }

    @Test
    fun `confirmed read through marks the composite prefix and updates badges`() {
        val repository = EventsRepository()
        val oldestUuid = "10000000-0000-4000-8000-000000000001"
        val boundaryUuid = "20000000-0000-4000-8000-000000000002"
        val newestUuid = "30000000-0000-4000-8000-000000000003"
        val unreadMessages = listOf(
            message(oldestUuid, "oldest").copy(read = false),
            message(boundaryUuid, "boundary").copy(read = false),
            message(newestUuid, "newest").copy(read = false),
        )
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ).copy(unreadCount = 3),
            ),
        )
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(lastMessageUuid = newestUuid).copy(unreadCount = 3),
            ),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            unreadMessages,
        )

        val marked = repository.markStreamTopicMessagesReadThrough(
            STREAM_UUID,
            TOPIC_UUID,
            boundaryUuid,
        )

        assertEquals(listOf(oldestUuid, boundaryUuid), marked)
        val stored = repository.streamTopicMessages.value.getValue(TOPIC_KEY)
        assertTrue(stored.single { it.uuid == oldestUuid }.read)
        assertTrue(stored.single { it.uuid == boundaryUuid }.read)
        assertFalse(stored.single { it.uuid == newestUuid }.read)
        assertEquals(
            1,
            repository.streamTopics.value
                .getValue(STREAM_UUID)
                .single()
                .unreadCount,
        )
        assertEquals(1, repository.streams.value.single().unreadCount)
    }

    @Test
    fun `bulk read realtime event updates loaded rows and projections once`() {
        val repository = EventsRepository()
        val firstUuid = "40000000-0000-4000-8000-000000000004"
        val secondUuid = "50000000-0000-4000-8000-000000000005"
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ).copy(unreadCount = 2),
            ),
        )
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(lastMessageUuid = secondUuid).copy(unreadCount = 2),
            ),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(
                message(firstUuid, "first").copy(read = false),
                message(secondUuid, "second").copy(read = false),
            ),
        )

        repository.processTextFrame(
            """
                {
                  "schema_version": 1,
                  "epoch_version": 1,
                  "object_type": "message",
                  "action": "read",
                  "payload": {
                    "kind": "messages.read",
                    "message_uuids": ["$firstUuid", "$secondUuid"]
                  }
                }
            """.trimIndent(),
        )
        repository.processTextFrame(
            """
                {
                  "schema_version": 1,
                  "epoch_version": 2,
                  "object_type": "message",
                  "action": "read",
                  "payload": {
                    "kind": "messages.read",
                    "message_uuids": ["$firstUuid", "$secondUuid"]
                  }
                }
            """.trimIndent(),
        )

        assertTrue(
            repository.streamTopicMessages.value
                .getValue(TOPIC_KEY)
                .all(MessageResponse::read),
        )
        assertEquals(
            0,
            repository.streamTopics.value
                .getValue(STREAM_UUID)
                .single()
                .unreadCount,
        )
        assertEquals(0, repository.streams.value.single().unreadCount)
    }

    @Test
    fun `single read realtime snapshot updates the complete loaded message`() {
        val repository = EventsRepository()
        repository.setInitialStreams(
            listOf(
                stream(
                    defaultTopicUuid = TOPIC_UUID,
                    notificationMode = "all_messages",
                ).copy(unreadCount = 1),
            ),
        )
        repository.addStreamTopics(
            STREAM_UUID,
            listOf(
                topic(lastMessageUuid = MESSAGE_UUID).copy(unreadCount = 1),
            ),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(
                message(MESSAGE_UUID, "before").copy(read = false),
            ),
        )

        repository.processTextFrame(
            messageEvent(
                epoch = 1,
                action = "read",
                content = "authoritative snapshot",
                read = true,
            ),
        )

        val stored = repository.streamTopicMessages.value
            .getValue(TOPIC_KEY)
            .single()
        assertTrue(stored.read)
        assertEquals("authoritative snapshot", stored.payload.content)
        assertEquals(
            0,
            repository.streamTopics.value
                .getValue(STREAM_UUID)
                .single()
                .unreadCount,
        )
        assertEquals(0, repository.streams.value.single().unreadCount)
    }

    @Test
    fun `a stale page cannot resurrect a confirmed read flag`() {
        val repository = EventsRepository()
        val confirmed = message(MESSAGE_UUID, "before").copy(
            read = true,
            updatedAt = "2026-07-30T10:00:01Z",
        )
        val laterSnapshotWithStaleReadFlag = confirmed.copy(
            read = false,
            updatedAt = "2026-07-30T10:00:02Z",
            payload = confirmed.payload.copy(content = "newer content"),
        )
        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(confirmed),
        )

        repository.addStreamTopicMessages(
            STREAM_UUID,
            TOPIC_UUID,
            listOf(laterSnapshotWithStaleReadFlag),
        )

        val stored = repository.streamTopicMessages.value
            .getValue(TOPIC_KEY)
            .single()
        assertTrue(stored.read)
        assertEquals("newer content", stored.payload.content)
    }

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

    @Test
    fun `inbox catalog compare and set preserves realtime changes`() {
        val repository = EventsRepository()
        val initialStream = stream(
            defaultTopicUuid = TOPIC_UUID,
            notificationMode = "all_messages",
        ).copy(name = "Initial")
        val initialTopic = topic(lastMessageUuid = null, name = "Initial")
        repository.setInitialStreams(listOf(initialStream))
        repository.addStreamTopics(STREAM_UUID, listOf(initialTopic))
        val staleReference = repository.inboxCatalogReference()

        val realtimeTopic = initialTopic.copy(
            name = "Realtime",
            updatedAt = "2026-07-31T11:00:00Z",
        )
        repository.updateTopic(realtimeTopic)

        assertFalse(
            repository.applyInboxCatalogIfUnchanged(
                expected = staleReference,
                streams = listOf(initialStream.copy(name = "Stale REST")),
                topics = emptyList(),
            ),
        )
        assertEquals("Initial", repository.streams.value.single().name)
        assertEquals(
            "Realtime",
            repository.streamTopics.value[STREAM_UUID]?.single()?.name,
        )

        val currentReference = repository.inboxCatalogReference()
        assertTrue(
            repository.applyInboxCatalogIfUnchanged(
                expected = currentReference,
                streams = listOf(initialStream.copy(name = "Current REST")),
                topics = emptyList(),
            ),
        )
        assertEquals("Current REST", repository.streams.value.single().name)
        assertEquals(
            emptyList<TopicsResponseData>(),
            repository.streamTopics.value[STREAM_UUID],
        )
    }

    @Test
    fun `external account revisions reject rollback and tombstone resurrection`() {
        val repository = EventsRepository()

        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "updated",
                revision = 3,
                status = "live",
            ),
        )
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 2,
                action = "updated",
                revision = 2,
                status = "degraded",
            ),
        )

        assertEquals(3, repository.externalAccounts.value.single().revision)
        assertEquals(
            "LIVE",
            repository.externalAccounts.value.single().status.name,
        )

        repository.processTextFrame(
            externalAccountEvent(
                epoch = 3,
                action = "deleted",
                revision = 4,
            ),
        )
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 4,
                action = "created",
                revision = 4,
            ),
        )
        assertTrue(repository.externalAccounts.value.isEmpty())

        repository.processTextFrame(
            externalAccountEvent(
                epoch = 5,
                action = "created",
                revision = 5,
            ),
        )
        assertEquals(5, repository.externalAccounts.value.single().revision)
    }

    @Test
    fun `external chat deletion is revision aware and removes its projection`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 2,
                action = "updated",
                revision = 3,
                displayName = "Current",
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 3,
                action = "updated",
                revision = 2,
                displayName = "Stale",
            ),
        )
        repository.addStream(
            stream(
                defaultTopicUuid = null,
                notificationMode = "all_messages",
            ).copy(uuid = PROJECTION_STREAM_UUID),
        )

        assertEquals(
            "Current",
            repository.externalChats.value
                .getValue(EXTERNAL_ACCOUNT_UUID)
                .single()
                .displayName,
        )

        repository.processTextFrame(
            externalChatEvent(
                epoch = 4,
                action = "deleted",
                revision = 4,
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 5,
                action = "created",
                revision = 4,
                displayName = "Replay",
            ),
        )

        assertTrue(
            repository.externalChats.value[EXTERNAL_ACCOUNT_UUID]
                .isNullOrEmpty(),
        )
        assertTrue(repository.streams.value.isEmpty())
    }

    @Test
    fun `external account deletion clears chats and provider streams`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 2,
                action = "created",
                revision = 1,
            ),
        )
        repository.addStream(
            stream(
                defaultTopicUuid = null,
                notificationMode = "all_messages",
            ).copy(
                uuid = PROJECTION_STREAM_UUID,
                provider = ProviderReference(
                    kind = "zulip",
                    accountUuid = EXTERNAL_ACCOUNT_UUID,
                ),
            ),
        )

        repository.processTextFrame(
            externalAccountEvent(
                epoch = 3,
                action = "deleted",
                revision = 2,
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 4,
                action = "updated",
                revision = 2,
            ),
        )

        assertTrue(repository.externalAccounts.value.isEmpty())
        assertTrue(repository.externalChats.value.isEmpty())
        assertTrue(repository.streams.value.isEmpty())
    }

    @Test
    fun `authoritative account refresh removes missing snapshots and projections`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 2,
                action = "created",
                revision = 1,
            ),
        )
        repository.addStream(
            stream(
                defaultTopicUuid = null,
                notificationMode = "all_messages",
            ).copy(
                uuid = PROJECTION_STREAM_UUID,
                provider = ProviderReference(
                    kind = "zulip",
                    accountUuid = EXTERNAL_ACCOUNT_UUID,
                ),
            ),
        )

        repository.reconcileExternalAccountSnapshots(
            responses = emptyList(),
            baselineRevisions = mapOf(EXTERNAL_ACCOUNT_UUID to 1),
        )
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 3,
                action = "updated",
                revision = 1,
            ),
        )

        assertTrue(repository.externalAccounts.value.isEmpty())
        assertTrue(repository.externalChats.value.isEmpty())
        assertTrue(repository.streams.value.isEmpty())
    }

    @Test
    fun `authoritative refresh does not erase a newer realtime snapshot`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        val baseline = mapOf(EXTERNAL_ACCOUNT_UUID to 1)
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 2,
                action = "updated",
                revision = 2,
                status = "live",
            ),
        )

        repository.reconcileExternalAccountSnapshots(
            responses = emptyList(),
            baselineRevisions = baseline,
        )

        assertEquals(2, repository.externalAccounts.value.single().revision)
        assertTrue(repository.externalAccounts.value.single().liveReady)
    }

    @Test
    fun `authoritative chat refresh prunes only unchanged missing rows`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalChatEvent(
                epoch = 2,
                action = "created",
                revision = 1,
            ),
        )
        val baseline = mapOf(EXTERNAL_CHAT_UUID to 1)
        repository.processTextFrame(
            externalChatEvent(
                epoch = 3,
                action = "updated",
                revision = 2,
                displayName = "Current",
            ),
        )

        repository.reconcileExternalChatSnapshots(
            externalAccountUuid = EXTERNAL_ACCOUNT_UUID,
            responses = emptyList(),
            baselineRevisions = baseline,
        )

        assertEquals(
            "Current",
            repository.externalChats.value
                .getValue(EXTERNAL_ACCOUNT_UUID)
                .single()
                .displayName,
        )
    }

    @Test
    fun `external operation realtime tombstone rejects stale replay`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalOperationEvent(
                epoch = 2,
                action = "created",
                revision = 1,
                status = "failed",
            ),
        )
        repository.processTextFrame(
            externalOperationEvent(
                epoch = 3,
                action = "updated",
                revision = 2,
                status = "queued",
            ),
        )

        assertEquals(
            "queued",
            repository.externalOperations.value
                .getValue(EXTERNAL_ACCOUNT_UUID)
                .single()
                .status
                .name
                .lowercase(),
        )

        repository.processTextFrame(
            externalOperationEvent(
                epoch = 4,
                action = "deleted",
                revision = 3,
                status = "discarded",
            ),
        )
        repository.processTextFrame(
            externalOperationEvent(
                epoch = 5,
                action = "updated",
                revision = 2,
                status = "failed",
            ),
        )

        assertTrue(
            repository.externalOperations.value[EXTERNAL_ACCOUNT_UUID]
                .isNullOrEmpty(),
        )
    }

    @Test
    fun `authoritative operation refresh preserves newer realtime snapshot`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalOperationEvent(
                epoch = 2,
                action = "created",
                revision = 1,
                status = "failed",
            ),
        )
        val baseline = mapOf(EXTERNAL_OPERATION_UUID to 1)
        repository.processTextFrame(
            externalOperationEvent(
                epoch = 3,
                action = "updated",
                revision = 2,
                status = "running",
            ),
        )

        repository.reconcileExternalOperationSnapshots(
            externalAccountUuid = EXTERNAL_ACCOUNT_UUID,
            responses = emptyList(),
            baselineRevisions = baseline,
        )

        val operation = repository.externalOperations.value
            .getValue(EXTERNAL_ACCOUNT_UUID)
            .single()
        assertEquals(2, operation.revision)
        assertEquals("RUNNING", operation.status.name)
    }

    @Test
    fun `external account deletion clears operation queue`() {
        val repository = EventsRepository()
        repository.processTextFrame(
            externalAccountEvent(
                epoch = 1,
                action = "created",
                revision = 1,
            ),
        )
        repository.processTextFrame(
            externalOperationEvent(
                epoch = 2,
                action = "created",
                revision = 1,
                status = "failed",
            ),
        )

        repository.processTextFrame(
            externalAccountEvent(
                epoch = 3,
                action = "deleted",
                revision = 2,
            ),
        )

        assertTrue(repository.externalOperations.value.isEmpty())
    }

    @Test
    fun `malformed external snapshot is skipped without poisoning the cursor`() {
        val repository = EventsRepository()
        val mismatchedSnapshot = externalAccountEvent(
            epoch = 7,
            action = "created",
            revision = 1,
            eventUuid = OTHER_EXTERNAL_ACCOUNT_UUID,
        )

        repository.processTextFrame(mismatchedSnapshot)

        assertTrue(repository.externalAccounts.value.isEmpty())
        assertEquals(7, repository.latestEpoch)
    }

    private fun messageEvent(
        epoch: Int,
        action: String,
        content: String,
        reactions: String = "",
        read: Boolean? = null,
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
            ${read?.let { ""","read":$it""" }.orEmpty()}
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

    private fun externalAccountEvent(
        epoch: Int,
        action: String,
        revision: Int,
        status: String = "connecting",
        eventUuid: String = EXTERNAL_ACCOUNT_UUID,
    ): String = """
        {
          "schema_version": 1,
          "epoch_version": $epoch,
          "object_type": "external_account",
          "action": "$action",
          "payload": {
            "kind": "external_account.$action",
            "uuid": "$eventUuid",
            "snapshot": {
              "uuid": "$EXTERNAL_ACCOUNT_UUID",
              "settings": {
                "kind": "zulip",
                "server_url": "https://zulip.example.com",
                "email": "user@example.com",
                "selection_mode": "explicit",
                "history_depth": "30_days",
                "default_project_id": "$EXTERNAL_PROJECT_UUID"
              },
              "credential_present": true,
              "status": "$status",
              "live_ready": ${status == "live"},
              "capabilities": {},
              "safe_error": null,
              "desired_generation": 1,
              "applied_generation": 0,
              "last_progress_at": null,
              "revision": $revision,
              "created_at": "2026-07-30T10:00:00Z",
              "updated_at": "2026-07-30T10:00:00Z"
            }
          }
        }
    """.trimIndent()

    private fun externalChatEvent(
        epoch: Int,
        action: String,
        revision: Int,
        displayName: String = "Engineering",
    ): String = """
        {
          "schema_version": 1,
          "epoch_version": $epoch,
          "object_type": "external_chat",
          "action": "$action",
          "payload": {
            "kind": "external_chat.$action",
            "uuid": "$EXTERNAL_CHAT_UUID",
            "snapshot": {
              "uuid": "$EXTERNAL_CHAT_UUID",
              "external_account_uuid": "$EXTERNAL_ACCOUNT_UUID",
              "source": {
                "kind": "zulip",
                "chat_type": "channel",
                "original_url": "https://zulip.example.com/#narrow/channel/1"
              },
              "display_name": "$displayName",
              "selected": true,
              "project_id": "$EXTERNAL_PROJECT_UUID",
              "history_depth": "30_days",
              "projection_stream_uuid": "$PROJECTION_STREAM_UUID",
              "status": "live",
              "capabilities": {},
              "safe_error": null,
              "transition_pending": false,
              "revision": $revision,
              "created_at": "2026-07-30T10:00:00Z",
              "updated_at": "2026-07-30T10:00:00Z"
            }
          }
        }
    """.trimIndent()

    private fun externalOperationEvent(
        epoch: Int,
        action: String,
        revision: Int,
        status: String,
    ): String {
        val canRetry = status == "failed"
        val canDiscard = status == "failed" || status == "queued"
        return """
            {
              "schema_version": 1,
              "epoch_version": $epoch,
              "object_type": "external_operation",
              "action": "$action",
              "payload": {
                "kind": "external_operation.$action",
                "uuid": "$EXTERNAL_OPERATION_UUID",
                "snapshot": {
                  "uuid": "$EXTERNAL_OPERATION_UUID",
                  "external_account_uuid": "$EXTERNAL_ACCOUNT_UUID",
                  "action": "message.send",
                  "target_type": "message",
                  "target_uuid": "$EXTERNAL_CHAT_UUID",
                  "status": "$status",
                  "safe_error": null,
                  "can_retry": $canRetry,
                  "can_discard": $canDiscard,
                  "duplicate_risk": $canRetry,
                  "retry_requires_confirmation": $canRetry,
                  "original_url": "https://zulip.example.com/#narrow/channel/1",
                  "reconciliation_state": "not_required",
                  "reconciliation_reason": null,
                  "reconciliation_evidence": {},
                  "attempt": 1,
                  "attempt_history": [],
                  "details": {},
                  "revision": $revision,
                  "created_at": "2026-07-30T10:00:00Z",
                  "updated_at": "2026-07-30T10:00:00Z"
                }
              }
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

    private fun folder(title: String) = FolderResponseData(
        uuid = "folder-1",
        title = title,
        unreadCount = 0,
        systemType = "created",
        creationDate = "2026-07-26T00:00:00Z",
        items = listOf(
            FolderItem(
                uuid = "folder-item-1",
                folderUuid = "folder-1",
                streamUuid = STREAM_UUID,
                chatType = "stream",
                unreadCount = 0,
            ),
        ),
    )

    private fun user(username: String) = UserResponseData(
        username = username,
        uuid = USER_UUID,
        status = "active",
        avatar = "",
    )

    private fun binding(role: String) = StreamBindingResponseData(
        uuid = "binding-1",
        streamUuid = STREAM_UUID,
        userUuid = USER_UUID,
        whoUuid = USER_UUID,
        role = role,
    )

    companion object {
        private const val STREAM_UUID = "stream-1"
        private const val TOPIC_UUID = "topic-1"
        private const val TOPIC_KEY = "$STREAM_UUID.$TOPIC_UUID"
        private const val USER_UUID = "user-1"
        private const val MESSAGE_UUID = "message-1"
        private const val REACTION_UUID = "reaction-1"
        private const val VALID_STREAM_UUID =
            "11000000-0000-4000-8000-000000000001"
        private const val VALID_OWNER_KEY =
            "10000000-0000-4000-8000-000000000000"
        private const val VALID_TOPIC_UUID =
            "22000000-0000-4000-8000-000000000002"
        private const val VALID_USER_UUID =
            "33000000-0000-4000-8000-000000000003"
        private const val VALID_MESSAGE_UUID =
            "44000000-0000-4000-8000-000000000004"
        private const val EXTERNAL_ACCOUNT_UUID =
            "10000000-0000-4000-8000-000000000001"
        private const val OTHER_EXTERNAL_ACCOUNT_UUID =
            "10000000-0000-4000-8000-000000000099"
        private const val EXTERNAL_CHAT_UUID =
            "20000000-0000-4000-8000-000000000002"
        private const val EXTERNAL_OPERATION_UUID =
            "50000000-0000-4000-8000-000000000005"
        private const val EXTERNAL_PROJECT_UUID =
            "30000000-0000-4000-8000-000000000003"
        private const val PROJECTION_STREAM_UUID =
            "40000000-0000-4000-8000-000000000004"
    }
}
