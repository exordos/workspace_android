package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class UnreadMentionProjectionTest {
    @Test
    fun `projects only unread mentioned streams`() {
        assertEquals(
            setOf("stream-mentioned"),
            unreadMentionStreamUuids(
                messages = listOf(
                    message("stream-mentioned", mentioned = true, read = false),
                    message("stream-read", mentioned = true, read = true),
                    message("stream-ordinary", mentioned = false, read = false),
                ),
                unreadStreamUuids = setOf(
                    "stream-mentioned",
                    "stream-read",
                    "stream-ordinary",
                ),
            ),
        )
    }

    @Test
    fun `ignores mentions outside current unread stream catalog`() {
        assertEquals(
            emptySet<String>(),
            unreadMentionStreamUuids(
                messages = listOf(
                    message("stale-stream", mentioned = true, read = false),
                ),
                unreadStreamUuids = setOf("current-stream"),
            ),
        )
    }

    @Test
    fun `projects unread mentioned topics`() {
        assertEquals(
            setOf("topic-mentioned"),
            unreadMentionTopicUuids(
                messages = listOf(
                    message(
                        streamUuid = "stream",
                        topicUuid = "topic-mentioned",
                        mentioned = true,
                        read = false,
                    ),
                    message(
                        streamUuid = "stream",
                        topicUuid = "topic-read",
                        mentioned = true,
                        read = true,
                    ),
                ),
                unreadTopicUuids = setOf("topic-mentioned", "topic-read"),
            ),
        )
    }

    @Test
    fun `counts only unread mentioned messages`() {
        assertEquals(
            1,
            unreadMentionCount(
                listOf(
                    message("stream", mentioned = true, read = false),
                    message("stream", mentioned = true, read = true),
                    message("stream", mentioned = false, read = false),
                ),
            ),
        )
    }

    private fun message(
        streamUuid: String,
        topicUuid: String = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        mentioned: Boolean,
        read: Boolean,
    ) = MessageResponse(
        uuid = "11111111-1111-4111-8111-111111111111",
        updatedAt = "2026-08-02T13:00:00Z",
        createdAt = "2026-08-02T13:00:00Z",
        streamUuid = streamUuid,
        topicUuid = topicUuid,
        userUuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        authorUuid = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
        payload = MessageResponsePayload(kind = "markdown", content = "message"),
        isOwn = false,
        reactions = emptyMap(),
        read = read,
        mentioned = mentioned,
    )
}
