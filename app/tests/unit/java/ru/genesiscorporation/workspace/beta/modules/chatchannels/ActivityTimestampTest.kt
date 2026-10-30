package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.forwardableStreams

class ActivityTimestampTest {
    @Test fun `forward destinations use absolute update time instead of ISO string order`() {
        val early = stream("33333333-3333-4333-8333-333333333333", "2026-10-30T00:15:00+03:00")
        val late = stream("44444444-4444-4444-8444-444444444444", "2026-10-29T21:30:00Z")
        assertEquals(listOf(late.uuid, early.uuid), forwardableStreams(listOf(early, late)).map { it.uuid })
    }

    @Test fun `stream activity compares instants across midnight and offsets`() {
        val early = stream("early", "2026-10-30T00:15:00+03:00")
        val late = stream("late", "2026-10-29T21:30:00Z")
        for (useLastMessage in listOf(false, true)) {
            val items = listOf(early, late).map { if (useLastMessage) it.copy(
                updatedAt = "2026-01-01T00:00:00Z", lastMessage = message(it.uuid, it.updatedAt),
            ) else it }
            assertEquals(listOf("late", "early"), items.sortedByActivity().map { it.uuid })
            assertEquals(listOf("late", "early"), items.sortedForInbox().map { it.uuid })
            assertEquals("early", items.map { if (it.uuid == "early") it.copy(activeUnreadCount = 1) else it }
                .sortedForInbox().first().uuid)
        }
    }

    @Test fun `topic activity compares instants while retaining mute and unread rules`() {
        val early = topic("early", "2026-10-30T00:15:00+03:00")
        val late = topic("late", "2026-10-29T21:30:00Z")
        for (useLastMessage in listOf(false, true)) {
            val items = listOf(early, late).map { if (useLastMessage) it.copy(
                updatedAt = "2026-01-01T00:00:00Z", lastMessage = message(it.uuid, it.updatedAt),
            ) else it }
            assertEquals(listOf("late", "early"), items.topicsSortedByActivity().map { it.uuid })
            assertEquals(listOf("late", "early"), items.topicsSortedForInbox().map { it.uuid })
            assertEquals("early", items.map { if (it.uuid == "late") it.copy(notificationMode = "mute") else it }
                .topicsSortedByActivity().first().uuid)
        }
    }

    private fun stream(id: String, date: String) =
        Stream(id, 0, 0, 0, date, id, false, 0, notificationMode = "all_messages")

    private fun topic(id: String, date: String) =
        TopicsResponseData(id, id, 0, "stream", date, 0, 0, false, false, notificationMode = "default")

    private fun message(id: String, date: String) = MessageResponse(id, date, date, "stream", "topic",
        "user", "user", MessageResponsePayload("markdown", "Synthetic"), true, emptyMap(), true)
}
