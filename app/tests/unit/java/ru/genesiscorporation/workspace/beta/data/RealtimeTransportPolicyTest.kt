package ru.genesiscorporation.workspace.beta.data

import io.ktor.http.URLProtocol
import io.ktor.http.URLBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.appendGetQueryParameters
import ru.genesiscorporation.workspace.beta.data.remote.dto.EventsProbeRequest

class RealtimeTransportPolicyTest {

    @Test
    fun `https workspace uses secure websocket`() {
        val endpoint = resolveWebSocketEndpoint("https://workspace.example.com")

        assertEquals(URLProtocol.WSS, endpoint.protocol)
        assertEquals("workspace.example.com", endpoint.host)
        assertEquals(null, endpoint.port)
    }

    @Test
    fun `https workspace preserves explicit port`() {
        val endpoint = resolveWebSocketEndpoint("https://workspace.example.com:18443/")

        assertEquals(URLProtocol.WSS, endpoint.protocol)
        assertEquals("workspace.example.com", endpoint.host)
        assertEquals(18443, endpoint.port)
    }

    @Test
    fun `http workspace uses plain websocket`() {
        val endpoint = resolveWebSocketEndpoint("http://workspace.example.com")

        assertEquals(URLProtocol.WS, endpoint.protocol)
        assertEquals("workspace.example.com", endpoint.host)
        assertEquals(null, endpoint.port)
    }

    @Test
    fun `retry delay grows exponentially and is capped`() {
        assertEquals(2_000L, nextRetryDelayMillis(1_000L))
        assertEquals(30_000L, nextRetryDelayMillis(16_000L))
        assertEquals(30_000L, nextRetryDelayMillis(30_000L))
    }

    @Test
    fun `visible event after saved cursor requires reconnect`() {
        assertTrue(shouldReconnectForEventsProbe(41, listOf(42)))
    }

    @Test
    fun `empty visible event probe keeps the socket open`() {
        assertFalse(shouldReconnectForEventsProbe(41, emptyList()))
    }

    @Test
    fun `probe event already applied by websocket keeps the socket open`() {
        assertFalse(shouldReconnectForEventsProbe(42, listOf(42)))
    }

    @Test
    fun `stale probe response does not close a cursor that already advanced`() {
        assertFalse(shouldReconnectForEventsProbe(43, listOf(42)))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `visible event probe encodes the strict cursor in the request URL`() {
        val request = EventsProbeRequest(
            afterEpochVersion = 41,
            epochGeneration = "generation-a"
        )
        val query = Properties.encodeToStringMap(
            request.data
        )
        val url = URLBuilder("https://workspace.example.com${request.url}").apply {
            appendGetQueryParameters(query)
        }.build()

        assertEquals("41", query["epoch_version>"])
        assertEquals("generation-a", query["epoch_generation"])
        assertEquals("1", query["page_limit"])
        assertEquals("41", url.parameters["epoch_version>"])
        assertEquals(
            "https://workspace.example.com/api/workspace/v1/events/" +
                "?epoch_version%3E=41&epoch_generation=generation-a&page_limit=1",
            url.toString()
        )
    }

}
