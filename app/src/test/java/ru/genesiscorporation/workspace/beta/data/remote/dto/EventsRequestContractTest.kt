package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EventsRequestContractTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `catch-up request uses strict epoch filtering and generation fencing`() {
        val request = EventsRequest(
            afterEpochVersion = 42,
            epochGeneration = "generation",
        )

        assertEquals("/api/workspace/v1/events/", request.url)
        val params = Properties.encodeToStringMap(request.data)
        assertEquals("42", params["epoch_version>"])
        assertEquals("generation", params["epoch_generation"])
        assertEquals(
            DEFAULT_EVENTS_PAGE_SIZE.toString(),
            params["page_limit"],
        )
    }

    @Test
    fun `catch-up request rejects unsafe cursors and page sizes`() {
        assertThrows(IllegalArgumentException::class.java) {
            EventsRequest(-1, "generation")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventsRequest(1, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventsRequest(1, "generation", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventsRequest(1, "generation", 501)
        }
    }
}
