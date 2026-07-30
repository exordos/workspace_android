package ru.genesiscorporation.workspace.beta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeTransportPolicyTest {
    @Test
    fun `heartbeat detects half-open sockets within a bounded window`() {
        assertEquals(20_000L, REALTIME_PING_INTERVAL_MILLIS)
        assertTrue(
            REALTIME_PING_INTERVAL_MILLIS in
                MIN_PING_INTERVAL_MILLIS..MAX_PING_INTERVAL_MILLIS,
        )

        // Ktor's default websocket session uses 2 * pingIntervalMillis as
        // its pong timeout. Keep the resulting detection window bounded.
        assertTrue(
            REALTIME_PING_INTERVAL_MILLIS * 2L <=
                MAX_HALF_OPEN_DETECTION_MILLIS,
        )
    }

    @Test
    fun `realtime frame allocation is bounded`() {
        assertEquals(2L * 1_024L * 1_024L, MAX_REALTIME_FRAME_BYTES)
        assertTrue(
            MAX_REALTIME_FRAME_BYTES <= MAX_ACCEPTABLE_FRAME_BYTES,
        )
    }

    private companion object {
        const val MIN_PING_INTERVAL_MILLIS = 10_000L
        const val MAX_PING_INTERVAL_MILLIS = 30_000L
        const val MAX_HALF_OPEN_DETECTION_MILLIS = 60_000L
        const val MAX_ACCEPTABLE_FRAME_BYTES = 4L * 1_024L * 1_024L
    }
}
