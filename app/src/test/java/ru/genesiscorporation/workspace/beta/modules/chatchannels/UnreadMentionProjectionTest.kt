package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class UnreadMentionProjectionTest {
    @Test
    fun `projects only unread mentioned streams`() {
        val unreadMention = message(
            uuid = "11111111-1111-4111-8111-111111111111",
            streamUuid = "stream-mentioned",
            mentioned = true,
            read = false,
        )
        val readMention = message(
            uuid = "22222222-2222-4222-8222-222222222222",
            streamUuid = "stream-read",
            mentioned = true,
            read = true,
        )
        val ordinaryUnread = message(
            uuid = "33333333-3333-4333-8333-333333333333",
            streamUuid = "stream-ordinary",
            mentioned = false,
            read = false,
        )

        assertEquals(
            setOf("stream-mentioned"),
            unreadMentionStreamUuids(
                messages = listOf(unreadMention, readMention, ordinaryUnread),
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
                    message(
                        uuid = "44444444-4444-4444-8444-444444444444",
                        streamUuid = "stale-stream",
                        mentioned = true,
                        read = false,
                    ),
                ),
                unreadStreamUuids = setOf("current-stream"),
            ),
        )
    }

    private fun message(
        uuid: String,
        streamUuid: String,
        mentioned: Boolean,
        read: Boolean,
    ) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-08-02T13:00:00Z",
        createdAt = "2026-08-02T13:00:00Z",
        streamUuid = streamUuid,
        topicUuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        userUuid = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        authorUuid = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
        payload = MessageResponsePayload(kind = "markdown", content = "message"),
        isOwn = false,
        reactions = emptyMap(),
        read = read,
        mentioned = mentioned,
    )
}
