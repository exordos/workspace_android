package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

    private fun message(uuid: String) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-07-30T00:00:00Z",
        createdAt = "2026-07-30T00:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = USER_UUID,
        authorUuid = USER_UUID,
        payload = MessageResponsePayload(kind = "markdown", content = uuid),
        isOwn = false,
        reactions = emptyMap(),
    )

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val TOPIC_UUID = "22222222-2222-4222-8222-222222222222"
        const val USER_UUID = "33333333-3333-4333-8333-333333333333"
        const val NEWER_UUID = "44444444-4444-4444-8444-444444444444"
        const val OLDER_UUID = "55555555-5555-4555-8555-555555555555"
    }
}
