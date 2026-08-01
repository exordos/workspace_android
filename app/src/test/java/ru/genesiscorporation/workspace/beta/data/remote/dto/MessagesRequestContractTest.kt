package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessagesRequestContractTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `latest message page uses stable descending keyset pagination`() {
        val request = MessagesRequest(
            streamId = STREAM_UUID,
            topicId = TOPIC_UUID,
            pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
            sortDirection = MessageSortDirection.DESCENDING,
        )

        val params = Properties.encodeToStringMap(request.data)
        assertEquals(STREAM_UUID, params["stream_uuid"])
        assertEquals(TOPIC_UUID, params["topic_uuid"])
        assertEquals(DEFAULT_MESSAGE_PAGE_SIZE.toString(), params["page_limit"])
        assertEquals("created_at", params["sort_key"])
        assertEquals("desc", params["sort_dir"])
        assertEquals(null, params["page_marker"])
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `feed page omits conversation filters`() {
        val request = MessagesRequest(
            pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
            sortDirection = MessageSortDirection.DESCENDING,
        )

        val params = Properties.encodeToStringMap(request.data)
        assertEquals(null, params["stream_uuid"])
        assertEquals(null, params["topic_uuid"])
        assertEquals(DEFAULT_MESSAGE_PAGE_SIZE.toString(), params["page_limit"])
        assertEquals("created_at", params["sort_key"])
        assertEquals("desc", params["sort_dir"])
        assertEquals(null, params["starred"])
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `starred activity uses the real server filter without a conversation`() {
        val request = MessagesRequest(
            pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
            sortDirection = MessageSortDirection.DESCENDING,
            starred = true,
        )

        val params = Properties.encodeToStringMap(request.data)
        assertEquals(null, params["stream_uuid"])
        assertEquals(null, params["topic_uuid"])
        assertEquals("true", params["starred"])
        assertEquals(DEFAULT_MESSAGE_PAGE_SIZE.toString(), params["page_limit"])
        assertEquals("created_at", params["sort_key"])
        assertEquals("desc", params["sort_dir"])
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `mentions and pinned activity use the real server filters`() {
        val mentions = Properties.encodeToStringMap(
            MessagesRequest(
                pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
                sortDirection = MessageSortDirection.DESCENDING,
                mentioned = true,
            ).data,
        )
        val pinned = Properties.encodeToStringMap(
            MessagesRequest(
                pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
                sortDirection = MessageSortDirection.DESCENDING,
                pinned = true,
            ).data,
        )

        assertEquals("true", mentions["mentioned"])
        assertEquals(null, mentions["pinned"])
        assertEquals("true", pinned["pinned"])
        assertEquals(null, pinned["mentioned"])
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `first unread lookup uses the exact incoming unread filter`() {
        val request = MessagesRequest(
            streamId = STREAM_UUID,
            topicId = TOPIC_UUID,
            pageLimit = 1,
            sortDirection = MessageSortDirection.ASCENDING,
            read = false,
            isOwn = false,
        )

        val params = Properties.encodeToStringMap(request.data)
        assertEquals(STREAM_UUID, params["stream_uuid"])
        assertEquals(TOPIC_UUID, params["topic_uuid"])
        assertEquals("1", params["page_limit"])
        assertEquals("asc", params["sort_dir"])
        assertEquals("false", params["read"])
        assertEquals("false", params["is_own"])
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `older message page sends a canonical continuation marker`() {
        val request = MessagesRequest(
            streamId = STREAM_UUID,
            topicId = TOPIC_UUID,
            pageLimit = 25,
            pageMarker = MESSAGE_UUID.uppercase(),
            sortDirection = MessageSortDirection.DESCENDING,
        )

        val params = Properties.encodeToStringMap(request.data)
        assertEquals("25", params["page_limit"])
        assertEquals(MESSAGE_UUID, params["page_marker"])
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `message anchor uses the canonical detail endpoint without query data`() {
        val request = MessageRequest(MESSAGE_UUID)

        assertEquals(
            "/api/workspace/v1/messenger/messages/$MESSAGE_UUID",
            request.url,
        )
        assertEquals(
            mapOf("emptyString" to ""),
            Properties.encodeToStringMap(request.data),
        )
    }

    @Test
    fun `message anchor rejects malformed identifiers before a request`() {
        assertThrows(IllegalArgumentException::class.java) {
            MessageRequest("$MESSAGE_UUID/path")
        }
    }

    @Test
    fun `read through action uses one canonical message boundary`() {
        val request = MarkMessagesReadRequest(MESSAGE_UUID.uppercase())

        assertEquals(
            "/api/workspace/v1/messenger/messages/" +
                "$MESSAGE_UUID/actions/read_up_to/invoke",
            request.url,
        )
        assertThrows(IllegalArgumentException::class.java) {
            MarkMessagesReadRequest("$MESSAGE_UUID/path")
        }
    }

    @Test
    fun `message page rejects unsafe limits and malformed markers`() {
        assertThrows(IllegalArgumentException::class.java) {
            MessagesRequest(
                STREAM_UUID,
                TOPIC_UUID,
                pageLimit = 0,
                sortDirection = MessageSortDirection.DESCENDING,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MessagesRequest(
                STREAM_UUID,
                TOPIC_UUID,
                pageMarker = "$MESSAGE_UUID/path",
                pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
                sortDirection = MessageSortDirection.DESCENDING,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MessagesRequest(
                streamId = null,
                topicId = TOPIC_UUID,
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `legacy message requests keep their unpaginated server behavior`() {
        val request = MessagesRequest(STREAM_UUID, TOPIC_UUID)

        val params = Properties.encodeToStringMap(request.data)
        assertEquals(null, params["page_limit"])
        assertEquals(null, params["page_marker"])
        assertEquals(null, params["sort_key"])
        assertEquals(null, params["sort_dir"])
    }

    private companion object {
        const val STREAM_UUID = "11111111-1111-4111-8111-111111111111"
        const val TOPIC_UUID = "22222222-2222-4222-8222-222222222222"
        const val MESSAGE_UUID = "33333333-3333-4333-8333-333333333333"
    }
}
