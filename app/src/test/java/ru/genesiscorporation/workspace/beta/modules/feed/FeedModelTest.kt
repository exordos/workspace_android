package ru.genesiscorporation.workspace.beta.modules.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.MessageProjectionEvent
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class FeedModelTest {
    @Test
    fun `descending server page becomes a stable chronological feed`() {
        val page = validateFeedPage(
            messages = listOf(
                message(NEWER_MESSAGE_UUID, "2026-07-30T10:02:00Z"),
                message(OLDER_MESSAGE_UUID, "2026-07-30T10:01:00Z"),
            ),
            nextMarkerHeader = OLDER_MESSAGE_UUID.uppercase(),
        )

        assertEquals(
            listOf(OLDER_MESSAGE_UUID, NEWER_MESSAGE_UUID),
            page.messages.map(MessageResponse::uuid),
        )
        assertEquals(OLDER_MESSAGE_UUID, page.nextPageMarker)
        assertNull(page.error)
    }

    @Test
    fun `older merge preserves current version of an overlapping message`() {
        val current = message(
            NEWER_MESSAGE_UUID,
            "2026-07-30T10:02:00Z",
            content = "Current edit",
        )
        val merged = mergeOlderFeedMessages(
            current = listOf(current),
            older = listOf(
                message(
                    NEWER_MESSAGE_UUID,
                    "2026-07-30T10:02:00Z",
                    content = "Stale copy",
                ),
                message(OLDER_MESSAGE_UUID, "2026-07-30T10:01:00Z"),
            ),
        )

        assertEquals(
            listOf(OLDER_MESSAGE_UUID, NEWER_MESSAGE_UUID),
            merged.map(MessageResponse::uuid),
        )
        assertEquals(
            "Current edit",
            merged.single { it.uuid == NEWER_MESSAGE_UUID }.payload.content,
        )
    }

    @Test
    fun `malformed repeated and unrelated markers fail closed`() {
        val page = listOf(message(OLDER_MESSAGE_UUID, "2026-07-30T10:01:00Z"))
        listOf(
            validateFeedPage(page, "not-a-uuid"),
            validateFeedPage(page, NEWER_MESSAGE_UUID),
            validateFeedPage(
                page,
                OLDER_MESSAGE_UUID,
                previousMarker = OLDER_MESSAGE_UUID,
            ),
        ).forEach { result ->
            assertNull(result.nextPageMarker)
            assertTrue(result.error?.contains("некорректную страницу") == true)
        }
    }

    @Test
    fun `invalid conversation references and duplicate messages reject the page`() {
        val valid = message(OLDER_MESSAGE_UUID, "2026-07-30T10:01:00Z")
        listOf(
            listOf(valid, valid.copy()),
            listOf(valid.copy(streamUuid = "")),
            listOf(valid.copy(topicUuid = "not-a-uuid")),
            listOf(valid.copy(createdAt = "not-a-timestamp")),
        ).forEach { page ->
            val result = validateFeedPage(page, null)
            assertTrue(result.messages.isEmpty())
            assertTrue(result.error?.contains("некорректную страницу") == true)
        }
    }

    @Test
    fun `feed identifiers are canonicalized before merge and navigation`() {
        val result = validateFeedPage(
            messages = listOf(
                message(
                    NEWER_MESSAGE_UUID.uppercase(),
                    "2026-07-30T10:02:00Z",
                ).copy(
                    streamUuid = STREAM_UUID.uppercase(),
                    topicUuid = TOPIC_UUID.uppercase(),
                ),
            ),
            nextMarkerHeader = NEWER_MESSAGE_UUID.uppercase(),
        )

        assertEquals(NEWER_MESSAGE_UUID, result.messages.single().uuid)
        assertEquals(STREAM_UUID, result.messages.single().streamUuid)
        assertEquals(TOPIC_UUID, result.messages.single().topicUuid)
        assertEquals(NEWER_MESSAGE_UUID, result.nextPageMarker)
    }

    @Test
    fun `starred timeline rejects an unstarred server row`() {
        val result = validateFeedPage(
            messages = listOf(
                message(
                    NEWER_MESSAGE_UUID,
                    "2026-07-30T10:02:00Z",
                ).copy(starred = false),
            ),
            nextMarkerHeader = null,
            requireStarred = true,
        )

        assertTrue(result.messages.isEmpty())
        assertTrue(result.error?.contains("некорректную страницу") == true)
    }

    @Test
    fun `starred timeline accepts only confirmed starred rows`() {
        val result = validateFeedPage(
            messages = listOf(
                message(
                    NEWER_MESSAGE_UUID,
                    "2026-07-30T10:02:00Z",
                ).copy(starred = true),
            ),
            nextMarkerHeader = null,
            requireStarred = true,
        )

        assertEquals(listOf(NEWER_MESSAGE_UUID), result.messages.map { it.uuid })
        assertNull(result.error)
    }

    @Test
    fun `feed summary hides raw attachment targets and bounds whitespace`() {
        assertEquals(
            "Изображение report Read this",
            feedMessageSummary(
                "![photo](urn:image:secret) " +
                    "[report](https://private.example/file)   " +
                    "[Read this](https://example.com)",
            ),
        )
        assertEquals("Сообщение без текста", feedMessageSummary("  **  "))
        assertEquals("1234…", feedMessageSummary("123456789", maxLength = 5))
    }

    @Test
    fun `realtime updates are replayed over a REST page without losing edits`() {
        val staleRest = message(
            NEWER_MESSAGE_UUID,
            "2026-07-30T10:02:00Z",
            content = "REST copy",
        )
        val realtimeEdit = staleRest.copy(
            updatedAt = "2026-07-30T10:03:00Z",
            payload = MessageResponsePayload(
                kind = "markdown",
                content = "Realtime edit",
            ),
            read = false,
        )

        val projection = applyFeedProjectionEvents(
            messages = listOf(staleRest),
            nextPageMarker = null,
            events = listOf(
                SequencedMessageProjectionEvent(
                    1,
                    MessageProjectionEvent.Upsert(realtimeEdit),
                ),
                SequencedMessageProjectionEvent(
                    2,
                    MessageProjectionEvent.Read(
                        listOf(NEWER_MESSAGE_UUID),
                    ),
                ),
            ),
            requireStarred = false,
        )

        assertEquals("Realtime edit", projection.messages.single().payload.content)
        assertTrue(projection.messages.single().read)
    }

    @Test
    fun `stale realtime edit cannot replace a newer cached message`() {
        val current = message(
            NEWER_MESSAGE_UUID,
            "2026-07-30T10:02:00Z",
            content = "Current",
        ).copy(updatedAt = "2026-07-30T10:05:00Z")
        val stale = current.copy(
            updatedAt = "2026-07-30T10:04:00Z",
            payload = MessageResponsePayload(
                kind = "markdown",
                content = "Stale",
            ),
        )

        val projection = applyFeedProjectionEvents(
            messages = listOf(current),
            nextPageMarker = null,
            events = listOf(
                SequencedMessageProjectionEvent(
                    1,
                    MessageProjectionEvent.Upsert(stale),
                ),
            ),
            requireStarred = false,
        )

        assertEquals("Current", projection.messages.single().payload.content)
    }

    @Test
    fun `later realtime snapshot cannot regress confirmed read state`() {
        val readMessage = message(
            NEWER_MESSAGE_UUID,
            "2026-07-30T10:02:00Z",
        ).copy(read = true)
        val laterUnreadSnapshot = readMessage.copy(
            updatedAt = "2026-07-30T10:03:00Z",
            read = false,
        )

        val projection = applyFeedProjectionEvents(
            messages = listOf(readMessage),
            nextPageMarker = null,
            events = listOf(
                SequencedMessageProjectionEvent(
                    1,
                    MessageProjectionEvent.Upsert(laterUnreadSnapshot),
                ),
            ),
            requireStarred = false,
        )

        assertTrue(projection.messages.single().read)
    }

    @Test
    fun `starred realtime projection removes unstarred and deleted rows`() {
        val starred = message(
            NEWER_MESSAGE_UUID,
            "2026-07-30T10:02:00Z",
        ).copy(starred = true)

        val unstarred = applyFeedProjectionEvents(
            messages = listOf(starred),
            nextPageMarker = NEWER_MESSAGE_UUID,
            events = listOf(
                SequencedMessageProjectionEvent(
                    1,
                    MessageProjectionEvent.Upsert(
                        starred.copy(
                            updatedAt = "2026-07-30T10:03:00Z",
                            starred = false,
                        ),
                    ),
                ),
            ),
            requireStarred = true,
        )
        assertTrue(unstarred.messages.isEmpty())
        assertNull(unstarred.nextPageMarker)

        val deleted = applyFeedProjectionEvents(
            messages = listOf(starred),
            nextPageMarker = NEWER_MESSAGE_UUID,
            events = listOf(
                SequencedMessageProjectionEvent(
                    2,
                    MessageProjectionEvent.Deleted(NEWER_MESSAGE_UUID),
                ),
            ),
            requireStarred = true,
        )
        assertTrue(deleted.messages.isEmpty())
        assertNull(deleted.nextPageMarker)
    }

    @Test
    fun `bounded realtime projection keeps a safe pagination marker`() {
        val oldest = message(
            OLDER_MESSAGE_UUID,
            "2026-07-30T10:01:00Z",
        )
        val middle = message(
            NEWER_MESSAGE_UUID,
            "2026-07-30T10:02:00Z",
        )
        val newestUuid = "66666666-6666-4666-8666-666666666666"
        val newest = message(
            newestUuid,
            "2026-07-30T10:03:00Z",
        )

        val projection = applyFeedProjectionEvents(
            messages = listOf(oldest, middle),
            nextPageMarker = null,
            events = listOf(
                SequencedMessageProjectionEvent(
                    1,
                    MessageProjectionEvent.Upsert(newest),
                ),
            ),
            requireStarred = false,
            maximumMessages = 2,
        )

        assertEquals(
            listOf(NEWER_MESSAGE_UUID, newestUuid),
            projection.messages.map(MessageResponse::uuid),
        )
        assertEquals(NEWER_MESSAGE_UUID, projection.nextPageMarker)
    }

    @Test
    fun `realtime sequence detects dropped or replayed repository events`() {
        assertFalse(isMessageProjectionSequenceGap(null, 7))
        assertFalse(isMessageProjectionSequenceGap(7, 8))
        assertTrue(isMessageProjectionSequenceGap(7, 9))
        assertTrue(isMessageProjectionSequenceGap(7, 7))
    }

    @Test
    fun `authoritative empty snapshot remains displayable after refresh failure`() {
        assertTrue(
            hasDisplayableFeedSnapshot(
                FeedUiState(
                    hasLoaded = true,
                    hasUsableSnapshot = true,
                    error = "offline",
                ),
            ),
        )
        assertFalse(
            hasDisplayableFeedSnapshot(
                FeedUiState(
                    hasLoaded = true,
                    error = "offline",
                ),
            ),
        )
    }

    private fun message(
        uuid: String,
        createdAt: String,
        content: String = uuid,
    ) = MessageResponse(
        uuid = uuid,
        updatedAt = createdAt,
        createdAt = createdAt,
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = USER_UUID,
        authorUuid = USER_UUID,
        payload = MessageResponsePayload(kind = "markdown", content = content),
        isOwn = false,
        reactions = emptyMap(),
    )

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val TOPIC_UUID = "22222222-2222-4222-8222-222222222222"
        const val USER_UUID = "33333333-3333-4333-8333-333333333333"
        const val NEWER_MESSAGE_UUID = "44444444-4444-4444-8444-444444444444"
        const val OLDER_MESSAGE_UUID = "55555555-5555-4555-8555-555555555555"
    }
}
