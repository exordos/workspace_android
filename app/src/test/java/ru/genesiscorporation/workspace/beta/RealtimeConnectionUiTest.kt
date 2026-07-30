package ru.genesiscorporation.workspace.beta

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.RealtimeConnectionState
import ru.genesiscorporation.workspace.beta.data.awaitRealtimeRetryOrTimeout
import ru.genesiscorporation.workspace.beta.data.shouldAcceptRealtimeReconnect

class RealtimeConnectionUiTest {

    @Test
    fun `healthy and intentionally paused realtime stay silent`() {
        assertNull(
            realtimeConnectionBannerState(
                RealtimeConnectionState.CONNECTED,
            ),
        )
        assertNull(
            realtimeConnectionBannerState(
                RealtimeConnectionState.PAUSED,
            ),
        )
    }

    @Test
    fun `initial connection is informative but not manually retryable`() {
        assertEquals(
            RealtimeConnectionBannerState(
                kind = RealtimeConnectionBannerKind.CONNECTING,
                canRetryNow = false,
            ),
            realtimeConnectionBannerState(
                RealtimeConnectionState.CONNECTING,
            ),
        )
    }

    @Test
    fun `backoff exposes an immediate real retry action`() {
        assertEquals(
            RealtimeConnectionBannerState(
                kind = RealtimeConnectionBannerKind.RECOVERING,
                canRetryNow = true,
            ),
            realtimeConnectionBannerState(
                RealtimeConnectionState.BACKING_OFF,
            ),
        )
        assertTrue(
            shouldAcceptRealtimeReconnect(
                state = RealtimeConnectionState.BACKING_OFF,
                appForeground = true,
            ),
        )
    }

    @Test
    fun `retry is rejected outside foreground backoff`() {
        RealtimeConnectionState.entries
            .filterNot { it == RealtimeConnectionState.BACKING_OFF }
            .forEach { state ->
                assertFalse(
                    shouldAcceptRealtimeReconnect(
                        state = state,
                        appForeground = true,
                    ),
                )
            }
        assertFalse(
            shouldAcceptRealtimeReconnect(
                state = RealtimeConnectionState.BACKING_OFF,
                appForeground = false,
            ),
        )
    }

    @Test
    fun `manual retry signal interrupts the current bounded wait`() =
        runBlocking {
            val requests = Channel<Unit>(capacity = Channel.CONFLATED)
            val wait = async {
                awaitRealtimeRetryOrTimeout(
                    requests = requests,
                    timeoutMillis = 5_000L,
                )
            }
            yield()

            requests.send(Unit)

            assertTrue(wait.await())
        }
}
