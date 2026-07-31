package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationPaginationStateTest {
    @Test
    fun `latest window preserves confirmed boundaries`() {
        val state = normalizeConversationPaginationState(
            state = ConversationPaginationState(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                mode = ConversationWindowMode.LATEST,
                olderPageMarker = OLDEST_UUID,
            ),
            retainedMessageUuids = listOf(OLDEST_UUID, NEWEST_UUID),
            sourceFirstMessageUuid = OLDEST_UUID,
            sourceLastMessageUuid = NEWEST_UUID,
        )

        assertEquals(
            ConversationPaginationState(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                mode = ConversationWindowMode.LATEST,
                olderPageMarker = OLDEST_UUID,
            ),
            state,
        )
    }

    @Test
    fun `trimming an oldest boundary never claims complete history`() {
        val state = normalizeConversationPaginationState(
            state = ConversationPaginationState(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                mode = ConversationWindowMode.LATEST,
                olderPageMarker = null,
            ),
            retainedMessageUuids = listOf(MIDDLE_UUID, NEWEST_UUID),
            sourceFirstMessageUuid = OLDEST_UUID,
            sourceLastMessageUuid = NEWEST_UUID,
        )

        assertEquals(MIDDLE_UUID, state?.olderPageMarker)
        assertEquals(ConversationWindowMode.LATEST, state?.mode)
        assertNull(state?.newerPageMarker)
    }

    @Test
    fun `damaged cache becomes an unknown two sided window`() {
        val state = normalizeConversationPaginationState(
            state = ConversationPaginationState(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                mode = ConversationWindowMode.CONTEXT,
                contextAnchorUuid = MIDDLE_UUID,
            ),
            retainedMessageUuids = listOf(OLDEST_UUID, NEWEST_UUID),
            sourceFirstMessageUuid = OLDEST_UUID,
            sourceLastMessageUuid = NEWEST_UUID,
            cacheIsComplete = false,
        )

        assertEquals(ConversationWindowMode.UNKNOWN, state?.mode)
        assertNull(state?.contextAnchorUuid)
        assertEquals(OLDEST_UUID, state?.olderPageMarker)
        assertEquals(NEWEST_UUID, state?.newerPageMarker)
    }

    @Test
    fun `missing context anchor becomes an unknown two sided window`() {
        val state = normalizeConversationPaginationState(
            state = ConversationPaginationState(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                mode = ConversationWindowMode.CONTEXT,
                contextAnchorUuid = MIDDLE_UUID,
                olderPageMarker = null,
                newerPageMarker = null,
            ),
            retainedMessageUuids = listOf(OLDEST_UUID, NEWEST_UUID),
            sourceFirstMessageUuid = OLDEST_UUID,
            sourceLastMessageUuid = NEWEST_UUID,
        )

        assertEquals(ConversationWindowMode.UNKNOWN, state?.mode)
        assertEquals(OLDEST_UUID, state?.olderPageMarker)
        assertEquals(NEWEST_UUID, state?.newerPageMarker)
    }

    @Test
    fun `malformed identifiers fail closed`() {
        val state = normalizeConversationPaginationState(
            state = ConversationPaginationState(
                streamUuid = STREAM_UUID,
                topicUuid = TOPIC_UUID,
                mode = ConversationWindowMode.LATEST,
                olderPageMarker = "not-a-uuid",
            ),
            retainedMessageUuids = listOf(OLDEST_UUID, NEWEST_UUID),
            sourceFirstMessageUuid = OLDEST_UUID,
            sourceLastMessageUuid = NEWEST_UUID,
        )

        assertEquals(ConversationWindowMode.UNKNOWN, state?.mode)
        assertEquals(OLDEST_UUID, state?.olderPageMarker)
        assertEquals(NEWEST_UUID, state?.newerPageMarker)
    }

    companion object {
        private const val STREAM_UUID =
            "10000000-0000-4000-8000-000000000001"
        private const val TOPIC_UUID =
            "20000000-0000-4000-8000-000000000001"
        private const val OLDEST_UUID =
            "30000000-0000-4000-8000-000000000001"
        private const val MIDDLE_UUID =
            "30000000-0000-4000-8000-000000000002"
        private const val NEWEST_UUID =
            "30000000-0000-4000-8000-000000000003"
    }
}
