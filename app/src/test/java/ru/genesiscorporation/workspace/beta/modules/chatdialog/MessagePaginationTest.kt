package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.MessageProjectionEvent
import ru.genesiscorporation.workspace.beta.data.OwnedMessageProjectionEvent
import ru.genesiscorporation.workspace.beta.data.PersistedReadBoundary
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class MessagePaginationTest {
    @Test
    fun `conversation positioning keeps restored history after recreation`() {
        assertTrue(
            shouldPositionConversationAtLatest(
                hasPositionedConversation = false,
                lastVisibleIndex = null,
                lastListIndex = 49,
            ),
        )
        assertEquals(
            false,
            shouldPositionConversationAtLatest(
                hasPositionedConversation = true,
                lastVisibleIndex = null,
                lastListIndex = 49,
            ),
        )
        assertEquals(
            false,
            shouldPositionConversationAtLatest(
                hasPositionedConversation = true,
                lastVisibleIndex = 12,
                lastListIndex = 49,
            ),
        )
        assertTrue(
            shouldPositionConversationAtLatest(
                hasPositionedConversation = true,
                lastVisibleIndex = 48,
                lastListIndex = 49,
            ),
        )
    }

    @Test
    fun `viewport correction preserves the measured message position`() {
        assertEquals(-143, historyViewportCorrection(263, 406))
        assertEquals(143, historyViewportCorrection(406, 263))
        assertEquals(0, historyViewportCorrection(405, 406))
        assertEquals(0, historyViewportCorrection(406, 406))
    }

    @Test
    fun `matching canonical marker enables the next page`() {
        val state = validateMessagePageState(
            messages = listOf(message(NEWER_UUID), message(OLDER_UUID)),
            nextMarkerHeader = OLDER_UUID.uppercase(),
        )

        assertEquals(OLDER_UUID, state.nextMarker)
        assertNull(state.error)
    }

    @Test
    fun `missing marker means the oldest page was reached`() {
        val state = validateMessagePageState(
            messages = listOf(message(OLDER_UUID)),
            nextMarkerHeader = null,
        )

        assertNull(state.nextMarker)
        assertNull(state.error)
    }

    @Test
    fun `malformed mismatched and repeated markers fail closed`() {
        listOf(
            validateMessagePageState(
                messages = listOf(message(OLDER_UUID)),
                nextMarkerHeader = "not-a-uuid",
            ),
            validateMessagePageState(
                messages = listOf(message(OLDER_UUID)),
                nextMarkerHeader = NEWER_UUID,
            ),
            validateMessagePageState(
                messages = listOf(message(OLDER_UUID)),
                nextMarkerHeader = OLDER_UUID,
                previousMarker = OLDER_UUID,
            ),
        ).forEach { state ->
            assertNull(state.nextMarker)
            assertTrue(state.error?.contains("некорректную страницу") == true)
        }
    }

    @Test
    fun `a cross-conversation row makes the entire page fail closed`() {
        val state = validateMessagePageState(
            messages = listOf(message(OLDER_UUID)),
            nextMarkerHeader = null,
            rawMessageCount = 2,
        )

        assertNull(state.nextMarker)
        assertTrue(state.error?.contains("некорректную страницу") == true)
    }

    @Test
    fun `around-message pages accept only their strict keyset side and order`() {
        val anchor = message(ANCHOR_UUID)
        val older = validateMessageWindowPageState(
            messages = listOf(message(OLDER_UUID), message(OLDEST_UUID)),
            nextMarkerHeader = OLDEST_UUID,
            rawMessageCount = 2,
            boundary = anchor,
            direction = MessageWindowDirection.OLDER,
        )
        val newer = validateMessageWindowPageState(
            messages = listOf(message(NEWER_UUID), message(NEWEST_UUID)),
            nextMarkerHeader = NEWEST_UUID,
            rawMessageCount = 2,
            boundary = anchor,
            direction = MessageWindowDirection.NEWER,
        )

        assertEquals(OLDEST_UUID, older.nextMarker)
        assertNull(older.error)
        assertEquals(NEWEST_UUID, newer.nextMarker)
        assertNull(newer.error)
    }

    @Test
    fun `around-message pages reject gaps masked as the wrong side or order`() {
        val anchor = message(ANCHOR_UUID)
        listOf(
            validateMessageWindowPageState(
                messages = listOf(message(NEWER_UUID)),
                nextMarkerHeader = null,
                rawMessageCount = 1,
                boundary = anchor,
                direction = MessageWindowDirection.OLDER,
            ),
            validateMessageWindowPageState(
                messages = listOf(message(OLDEST_UUID), message(OLDER_UUID)),
                nextMarkerHeader = null,
                rawMessageCount = 2,
                boundary = anchor,
                direction = MessageWindowDirection.OLDER,
            ),
            validateMessageWindowPageState(
                messages = listOf(
                    message(OLDER_UUID, createdAt = "invalid"),
                ),
                nextMarkerHeader = null,
                rawMessageCount = 1,
                boundary = anchor,
                direction = MessageWindowDirection.OLDER,
            ),
        ).forEach { state ->
            assertNull(state.nextMarker)
            assertTrue(state.error?.contains("некорректную страницу") == true)
        }
    }

    @Test
    fun `visible read boundary selects only the newest incoming unread row`() {
        val messages = listOf(
            message(OLDEST_UUID, read = false),
            message(OLDER_UUID, read = false, isOwn = true),
            message(ANCHOR_UUID, read = true),
            message(NEWER_UUID, read = false),
            message(NEWEST_UUID, read = false),
        )

        val boundary = newestVisibleUnreadBoundary(
            messages = messages,
            visibleMessageUuids = setOf(
                OLDEST_UUID,
                OLDER_UUID,
                ANCHOR_UUID,
                NEWER_UUID,
            ),
        )

        assertEquals(NEWER_UUID, boundary?.uuid)
        assertNull(
            newestVisibleUnreadBoundary(
                messages = messages,
                visibleMessageUuids = setOf(OLDER_UUID, ANCHOR_UUID),
            ),
        )
    }

    @Test
    fun `first unread page accepts only one exact incoming unread row`() {
        val unread = message(OLDEST_UUID, read = false)

        assertEquals(
            unread,
            validateFirstUnreadPage(
                messages = listOf(unread),
                expectedStreamUuid = STREAM_UUID,
                expectedTopicUuid = TOPIC_UUID,
            ).message,
        )
        assertEquals(
            null,
            validateFirstUnreadPage(
                messages = emptyList(),
                expectedStreamUuid = STREAM_UUID,
                expectedTopicUuid = TOPIC_UUID,
            ).error,
        )
        listOf(
            listOf(unread, message(NEWER_UUID, read = false)),
            listOf(unread.copy(streamUuid = "foreign-stream")),
            listOf(unread.copy(topicUuid = "foreign-topic")),
            listOf(unread.copy(read = true)),
            listOf(unread.copy(isOwn = true)),
            listOf(unread.copy(uuid = "not-a-uuid")),
            listOf(unread.copy(createdAt = "not-a-time")),
        ).forEach { rejected ->
            assertTrue(
                validateFirstUnreadPage(
                    messages = rejected,
                    expectedStreamUuid = STREAM_UUID,
                    expectedTopicUuid = TOPIC_UUID,
                ).error != null,
            )
        }
    }

    @Test
    fun `complete visible unread tail is the only gesture free read case`() {
        fun decision(
            userScrollSeen: Boolean = false,
            isScreenResumed: Boolean = true,
            hasExplicitMessageRoute: Boolean = false,
            hasNewerMessages: Boolean = false,
            loadingNewerMessages: Boolean = false,
            loadedUnreadCount: Int = 2,
            topicUnreadCount: Int = 2,
            visibleUnreadCount: Int = 2,
            lastMessageFullyVisible: Boolean = true,
        ) = shouldAutoReadCompleteUnreadTail(
            userScrollSeen = userScrollSeen,
            isScreenResumed = isScreenResumed,
            hasExplicitMessageRoute = hasExplicitMessageRoute,
            hasNewerMessages = hasNewerMessages,
            loadingNewerMessages = loadingNewerMessages,
            loadedUnreadCount = loadedUnreadCount,
            topicUnreadCount = topicUnreadCount,
            visibleUnreadCount = visibleUnreadCount,
            lastMessageFullyVisible = lastMessageFullyVisible,
        )

        assertTrue(decision())
        assertTrue(decision(topicUnreadCount = 0))
        assertFalse(decision(userScrollSeen = true))
        assertFalse(decision(isScreenResumed = false))
        assertFalse(decision(hasExplicitMessageRoute = true))
        assertFalse(decision(hasNewerMessages = true))
        assertFalse(decision(loadingNewerMessages = true))
        assertFalse(decision(loadedUnreadCount = 0, visibleUnreadCount = 0))
        assertFalse(decision(topicUnreadCount = 3))
        assertFalse(decision(visibleUnreadCount = 1))
        assertFalse(decision(lastMessageFullyVisible = false))
    }

    @Test
    fun `read through confirmation requires the exact readable scope`() {
        val confirmed = message(ANCHOR_UUID, read = true)

        assertTrue(
            isConfirmedReadThrough(
                expectedMessageUuid = ANCHOR_UUID,
                expectedStreamUuid = STREAM_UUID,
                expectedTopicUuid = TOPIC_UUID,
                confirmed = confirmed,
            ),
        )
        listOf(
            confirmed.copy(uuid = NEWER_UUID),
            confirmed.copy(streamUuid = "foreign-stream"),
            confirmed.copy(topicUuid = "foreign-topic"),
            confirmed.copy(read = false),
            confirmed.copy(createdAt = "not-a-time"),
        ).forEach { rejected ->
            assertFalse(
                isConfirmedReadThrough(
                    expectedMessageUuid = ANCHOR_UUID,
                    expectedStreamUuid = STREAM_UUID,
                    expectedTopicUuid = TOPIC_UUID,
                    confirmed = rejected,
                ),
            )
        }
    }

    @Test
    fun `durable read boundaries coalesce by timestamp and uuid off window`() {
        val older = readBoundary(
            uuid = NEWEST_UUID,
            createdAt = "2026-07-30T00:00:00Z",
        )
        val newer = readBoundary(
            uuid = OLDEST_UUID,
            createdAt = "2026-07-30T00:00:01Z",
        )
        val sameTimeHigherUuid = readBoundary(
            uuid = NEWER_UUID,
            createdAt = newer.createdAt,
        )

        assertEquals(newer, newestReadBoundary(older, newer))
        assertEquals(newer, newestReadBoundary(newer, older))
        assertEquals(
            sameTimeHigherUuid,
            newestReadBoundary(newer, sameTimeHigherUuid),
        )
        assertEquals(newer, newestReadBoundary(null, newer))
    }

    @Test
    fun `only exact owner realtime evidence clears a durable read boundary`() {
        val boundary = readBoundary(
            uuid = ANCHOR_UUID,
            createdAt = "2026-07-30T00:00:00Z",
        )
        val batch = ownedProjection(
            MessageProjectionEvent.Read(listOf(ANCHOR_UUID)),
        )
        val full = ownedProjection(
            MessageProjectionEvent.Upsert(
                message(ANCHOR_UUID, read = true),
            ),
        )

        assertTrue(
            batch.confirmsReadBoundary(
                OWNER_KEY,
                STREAM_UUID,
                TOPIC_UUID,
                boundary,
            ),
        )
        assertTrue(
            full.confirmsReadBoundary(
                OWNER_KEY,
                STREAM_UUID,
                TOPIC_UUID,
                boundary,
            ),
        )
        assertFalse(
            batch.copy(ownerKey = "other-owner").confirmsReadBoundary(
                OWNER_KEY,
                STREAM_UUID,
                TOPIC_UUID,
                boundary,
            ),
        )
        assertFalse(
            full.copy(
                event = MessageProjectionEvent.Upsert(
                    message(ANCHOR_UUID, read = true).copy(
                        topicUuid = "other-topic",
                    ),
                ),
            ).confirmsReadBoundary(
                OWNER_KEY,
                STREAM_UUID,
                TOPIC_UUID,
                boundary,
            ),
        )
    }

    private fun message(
        uuid: String,
        createdAt: String = "2026-07-30T00:00:00Z",
        read: Boolean = true,
        isOwn: Boolean = false,
    ) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-07-30T00:00:00Z",
        createdAt = createdAt,
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = USER_UUID,
        authorUuid = USER_UUID,
        payload = MessageResponsePayload(kind = "markdown", content = uuid),
        isOwn = isOwn,
        reactions = emptyMap(),
        read = read,
    )

    private fun readBoundary(
        uuid: String,
        createdAt: String,
    ) = PersistedReadBoundary(
        messageUuid = uuid,
        createdAt = createdAt,
    )

    private fun ownedProjection(
        event: MessageProjectionEvent,
    ) = OwnedMessageProjectionEvent(
        ownerKey = OWNER_KEY,
        sequence = 1,
        event = event,
    )

    private companion object {
        const val OWNER_KEY = "owner"
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val TOPIC_UUID = "22222222-2222-4222-8222-222222222222"
        const val USER_UUID = "33333333-3333-4333-8333-333333333333"
        const val OLDEST_UUID = "11111111-1111-4111-8111-111111111112"
        const val OLDER_UUID = "22222222-2222-4222-8222-222222222223"
        const val ANCHOR_UUID = "44444444-4444-4444-8444-444444444444"
        const val NEWER_UUID = "55555555-5555-4555-8555-555555555555"
        const val NEWEST_UUID = "66666666-6666-4666-8666-666666666666"
    }
}
