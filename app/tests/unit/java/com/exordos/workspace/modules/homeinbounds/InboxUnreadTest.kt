package com.exordos.workspace.modules.homeinbounds

import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.data.remote.dto.*
import com.exordos.workspace.modules.chatdialog.initialChatMessageIndex

class InboxUnreadTest {
    private fun stream(mode: String = "all_messages", active: Int = 2, encrypted: Boolean = false) =
        Stream("stream", 3, active, 3 - active, "2026-10-16T10:00:00Z", "Stream", false, 0,
            notificationMode = mode, encryption = encrypted)
    private fun topic(mode: String = "default", active: Int = 0) =
        TopicsResponseData("topic", "Topic", 0, "stream", "2026-10-16T10:00:00Z", 3, active, false, true, notificationMode = mode)
    private fun message(id: String, time: String, read: Boolean = false, topic: String = "topic", kind: String = "markdown") =
        MessageResponse(id, time, time, "stream", topic, "user", "author", MessageResponsePayload(kind, "Synthetic"), false, emptyMap(), read)

    @Test fun `stream visibility follows active counters including followed topics in muted streams`() {
        assertFalse(stream("muted", active = 0).hasInboxUnread)
        assertFalse(stream("muted", active = 0, encrypted = true).hasInboxUnread)
        assertTrue(stream("muted", active = 1).hasInboxUnread)
        assertTrue(stream(encrypted = true).hasInboxUnread)
        assertFalse(stream().copy(unreadCount = 0, activeUnreadCount = 0).hasInboxUnread)
    }

    @Test fun `passive unread stays hidden and the server active counter controls visibility`() {
        assertFalse(stream("mentions_only", active = 0).hasInboxUnread)
        assertTrue(stream("mentions_only", active = 1).hasInboxUnread)
        assertFalse(topic(active = 0).hasInboxUnread)
        assertTrue(topic("follow", active = 1).hasInboxUnread)
        assertFalse(topic("mute", active = 1).hasInboxUnread)
    }

    @Test fun `all twelve stream and topic modes match the server notification policy`() {
        for (streamMode in listOf("all_messages", "mentions_only", "muted")) {
            for (topicMode in listOf("default", "mute", "follow", "unmute")) {
                val nonMention = topicMode == "follow" || (topicMode == "default" && streamMode == "all_messages")
                val mention = topicMode == "follow" || topicMode == "unmute" ||
                    (topicMode == "default" && streamMode != "muted")
                assertEquals("$streamMode/$topicMode plain", nonMention, message("plain", "2026-10-16T10:00:00Z").isActiveUnread(streamMode, topicMode))
                assertEquals("$streamMode/$topicMode mention", mention, message("mention", "2026-10-16T10:00:00Z").copy(mentioned = true).isActiveUnread(streamMode, topicMode))
            }
        }
    }

    @Test fun `first unread ignores read messages and other topics and orders by Instant`() {
        val messages = listOf(message("read", "2026-10-16T08:00:00Z", true),
            message("other", "2026-10-16T08:30:00Z", topic = "elsewhere"),
            message("late", "2026-10-16T12:00:00+01:00"),
            message("b", "2026-10-16T10:00:00Z", kind = "e2ee"),
            message("a", "2026-10-16T11:00:00+01:00", kind = "key_request"))
        assertEquals("a", firstInboxUnread(messages, stream(), topic())?.uuid)
        assertNull(firstInboxUnread(messages, stream(), topic().copy(uuid = "absent")))
    }

    @Test fun `mentions mode skips earlier passive unread while follow overrides it`() {
        val plain = message("plain", "2026-10-16T09:00:00Z")
        val mention = message("mention", "2026-10-16T10:00:00Z").copy(mentioned = true)
        val history = listOf(plain, mention)
        assertEquals("mention", firstInboxUnread(history, stream("mentions_only"), topic())?.uuid)
        assertEquals("plain", firstInboxUnread(history, stream("mentions_only"), topic("follow"))?.uuid)
        assertEquals("plain", firstInboxUnread(history, stream("muted"), topic("follow"))?.uuid)
        assertEquals("mention", firstInboxUnread(history, stream("muted"), topic("unmute"))?.uuid)
        assertNull(firstInboxUnread(history, stream("all_messages"), topic("mute")))
    }

    @Test fun `initial anchor survives read updates with a deleted anchor fallback`() {
        val messages = listOf(message("read", "2026-10-16T08:00:00Z", true),
            message("first", "2026-10-16T09:00:00Z"), message("last", "2026-10-16T10:00:00Z"))
        assertEquals(1, initialChatMessageIndex(messages, "first"))
        assertEquals(1, initialChatMessageIndex(messages.map { it.copy(read = true) }, "first"))
        assertEquals(1, initialChatMessageIndex(messages, "deleted"))
        assertEquals(2, initialChatMessageIndex(messages.map { it.copy(read = true) }, "deleted"))
        assertEquals(2, initialChatMessageIndex(messages, null))
    }
}
