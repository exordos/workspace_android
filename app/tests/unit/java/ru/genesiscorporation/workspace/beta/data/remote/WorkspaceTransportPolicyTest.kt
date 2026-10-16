package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.http.Url
import org.junit.Assert.*
import org.junit.Test

class WorkspaceTransportPolicyTest {
    @Test fun remoteConnectionsUseTls() {
        for (url in listOf("https://workspace.invalid", "https://192.0.2.1:8443", "wss://workspace.invalid/events")) {
            assertTrue(url, permitsWorkspaceTransport(Url(url)))
        }
    }

    @Test fun httpAndWebSocketsRemainAvailableForExactLoopbackAddresses() {
        for (scheme in listOf("http", "ws")) for (host in listOf("localhost", "LOCALHOST", "127.0.0.1", "[::1]")) {
            val url = "$scheme://$host:18443/local"
            assertTrue(url, permitsWorkspaceTransport(Url(url)))
        }
    }

    @Test fun remoteAddressesAndLoopbackLookalikesCannotUseCleartext() {
        for (scheme in listOf("http", "ws")) for (host in listOf(
            "workspace.invalid", "localhost.invalid", "localhost.", "127.0.0.1.invalid",
            "192.0.2.1", "0.0.0.0", "127.1", "2130706433", "localhost@workspace.invalid",
        )) {
            val url = "$scheme://$host/private"
            assertFalse(url, permitsWorkspaceTransport(Url(url)))
        }
    }

    @Test fun localhostDoesNotAllowOtherProtocols() {
        assertFalse(permitsWorkspaceTransport(Url("ftp://localhost/private")))
    }
}
