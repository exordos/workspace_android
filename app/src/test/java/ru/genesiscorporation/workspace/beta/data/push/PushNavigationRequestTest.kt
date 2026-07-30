package ru.genesiscorporation.workspace.beta.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNavigationRequestTest {
    @Test
    fun `stream payload maps to canonical provider chat key`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "stream_chat_message",
                "workspace_message_id" to "901",
                "stream_id" to "42",
                "topic" to "Release",
            ),
        )

        assertEquals("channel:42", request?.providerChatKey)
        assertEquals("Release", request?.topicName)
        assertEquals(901, request?.workspaceMessageId)
    }

    @Test
    fun `legacy misspelled stream id remains compatible`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "stream_chat_message",
                "workspace_message_id" to "2",
                "steram_id" to "9",
                "topic" to "General",
            ),
        )

        assertEquals("channel:9", request?.providerChatKey)
    }

    @Test
    fun `private participant ids are normalized and sorted`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "private_chat_message",
                "workspace_message_id" to "5",
                "sender_id" to "20",
                "user_id" to "10",
            ),
        )

        assertEquals("direct:10,20", request?.providerChatKey)
        assertNull(request?.topicName)
    }

    @Test
    fun `group participant ids are de-duplicated`() {
        val request = PushNavigationRequest.fromMessageData(
            mapOf(
                "kind" to "group_chat_message",
                "workspace_message_id" to "6",
                "sender_id" to "3",
                "pm_users" to "1,2,3",
            ),
        )

        assertEquals("group_direct:1,2,3", request?.providerChatKey)
    }

    @Test
    fun `malformed and incomplete payloads are rejected`() {
        assertNull(
            PushNavigationRequest.fromMessageData(
                mapOf(
                    "kind" to "stream_chat_message",
                    "workspace_message_id" to "1",
                    "stream_id" to "42",
                ),
            ),
        )
        assertNull(
            PushNavigationRequest.fromMessageData(
                mapOf(
                    "kind" to "private_chat_message",
                    "workspace_message_id" to "1",
                    "sender_id" to "10",
                    "user_id" to "10",
                ),
            ),
        )
        assertNull(
            PushNavigationRequest.fromIntentFields(
                providerChatKey = "channel:42/../../other",
                topicName = "General",
                workspaceMessageId = 1,
            ),
        )
    }
}
