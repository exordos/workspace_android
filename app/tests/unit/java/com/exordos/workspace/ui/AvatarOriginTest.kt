package com.exordos.workspace.ui

import org.junit.Assert.*
import org.junit.Test

class AvatarOriginTest {
    @Test fun trustedOriginIncludesSchemeHostAndEffectivePort() {
        assertTrue(isWorkspaceImageOrigin("https://WORKSPACE.invalid", "https://workspace.invalid:443/avatar"))
        assertTrue(isWorkspaceImageOrigin("http://localhost:8080", "http://localhost:8080/avatar"))
        assertTrue(isWorkspaceImageOrigin("https://[::1]:8443", "https://[::1]:8443/avatar"))
        assertTrue(isWorkspaceImageOrigin("http://workspace.invalid:80", "http://workspace.invalid/avatar"))
        for (url in listOf("http://workspace.invalid/avatar", "https://workspace.invalid:8443/avatar",
            "https://other.invalid/avatar", "https://workspace.invalid.attacker.invalid/avatar",
            "https://workspace.invalid@attacker.invalid/avatar", "https://user@workspace.invalid/avatar")) {
            assertFalse(url, isWorkspaceImageOrigin("https://workspace.invalid", url))
        }
    }

    @Test fun malformedOrNonHttpWorkspaceOriginsFailClosed() {
        for (base in listOf("", "/relative", "//workspace.invalid", "file:///tmp/avatar", "ftp://workspace.invalid",
            "https://workspace.invalid?next=", "https://workspace.invalid#fragment",
            "https://user@workspace.invalid", "https://workspace.invalid:65536", "https://work space.invalid")) {
            assertFalse(base, isWorkspaceImageOrigin(base, "$base/api/avatar"))
        }
    }
}
