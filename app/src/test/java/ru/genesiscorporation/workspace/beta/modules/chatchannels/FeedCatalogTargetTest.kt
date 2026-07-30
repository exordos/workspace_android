package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData

class FeedCatalogTargetTest {
    @Test
    fun `stream selection canonicalizes the exact UUID`() {
        val result = selectFeedStream(
            streamUuid = STREAM_UUID,
            candidates = listOf(stream(STREAM_UUID.uppercase())),
        )

        assertFalse(result.conflicting)
        assertEquals(STREAM_UUID, result.value?.uuid)
    }

    @Test
    fun `case variant duplicate streams fail closed`() {
        val result = selectFeedStream(
            streamUuid = STREAM_UUID,
            candidates = listOf(
                stream(STREAM_UUID),
                stream(STREAM_UUID.uppercase()),
            ),
        )

        assertTrue(result.conflicting)
        assertNull(result.value)
    }

    @Test
    fun `topic selection requires the exact stream and topic pair`() {
        val result = selectFeedTopic(
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            candidates = listOf(
                topic(TOPIC_UUID, FOREIGN_STREAM_UUID),
                topic(TOPIC_UUID.uppercase(), STREAM_UUID.uppercase()),
            ),
        )

        assertFalse(result.conflicting)
        assertEquals(TOPIC_UUID, result.value?.uuid)
        assertEquals(STREAM_UUID, result.value?.streamUuid)
    }

    @Test
    fun `case variant duplicate topics in one stream fail closed`() {
        val result = selectFeedTopic(
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            candidates = listOf(
                topic(TOPIC_UUID, STREAM_UUID),
                topic(TOPIC_UUID.uppercase(), STREAM_UUID),
            ),
        )

        assertTrue(result.conflicting)
        assertNull(result.value)
    }

    @Test
    fun `missing and malformed targets never guess a destination`() {
        val missing = selectFeedTopic(
            streamUuid = STREAM_UUID,
            topicUuid = TOPIC_UUID,
            candidates = listOf(topic(OTHER_TOPIC_UUID, STREAM_UUID)),
        )
        val malformed = selectFeedTopic(
            streamUuid = STREAM_UUID,
            topicUuid = "not-a-uuid",
            candidates = listOf(topic(TOPIC_UUID, STREAM_UUID)),
        )

        assertFalse(missing.conflicting)
        assertNull(missing.value)
        assertTrue(malformed.conflicting)
        assertNull(malformed.value)
    }

    private fun stream(uuid: String) = Stream(
        uuid = uuid,
        unreadCount = 0,
        updatedAt = "2026-07-30T00:00:00Z",
        name = "Feed target",
        isPrivate = false,
    )

    private fun topic(
        uuid: String,
        streamUuid: String,
    ) = TopicsResponseData(
        uuid = uuid,
        name = "Feed target topic",
        streamUuid = streamUuid,
        updatedAt = "2026-07-30T00:00:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = false,
    )

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val FOREIGN_STREAM_UUID =
            "22222222-2222-4222-8222-222222222222"
        const val TOPIC_UUID = "33333333-3333-4333-8333-333333333333"
        const val OTHER_TOPIC_UUID =
            "44444444-4444-4444-8444-444444444444"
    }
}
