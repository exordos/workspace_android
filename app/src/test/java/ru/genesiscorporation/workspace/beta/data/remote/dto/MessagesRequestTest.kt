package ru.genesiscorporation.workspace.beta.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesRequestTest {
    @Test
    fun `requests recent mentioned messages for activity and badges`() {
        val request = MessagesRequest(
            pageLimit = DEFAULT_MESSAGE_PAGE_SIZE,
            sortDirection = MessageSortDirection.DESCENDING,
            mentioned = true,
        )

        assertEquals(DEFAULT_MESSAGE_PAGE_SIZE, request.data.pageLimit)
        assertEquals("created_at", request.data.sortKey)
        assertEquals("desc", request.data.sortDirection)
        assertEquals(null, request.data.read)
        assertEquals(true, request.data.mentioned)
    }
}
