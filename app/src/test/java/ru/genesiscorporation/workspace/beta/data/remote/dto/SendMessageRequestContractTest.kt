package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageRequestContractTest {
    @Test
    fun `message create follows the canonical Workspace contract`() {
        val request = SendMessageRequest(
            streamUuid = "stream-uuid",
            topicUuid = "topic-uuid",
            content = "Hello",
        )

        assertEquals(
            "/api/workspace/v1/messenger/messages/",
            request.url,
        )
        val encoded = Json.encodeToString(request.data)
        assertTrue(encoded.contains("\"stream_uuid\":\"stream-uuid\""))
        assertTrue(encoded.contains("\"topic_uuid\":\"topic-uuid\""))
        assertTrue(encoded.contains("\"kind\":\"markdown\""))
        assertTrue(encoded.contains("\"content\":\"Hello\""))
        assertFalse(encoded.contains("\"uuid\""))
    }

    @Test
    fun `topic may be omitted to use the stream default`() {
        val request = SendMessageRequest(
            streamUuid = "stream-uuid",
            topicUuid = null,
            content = "Hello",
        )

        assertEquals(null, request.data.topicUuid)
    }
}
