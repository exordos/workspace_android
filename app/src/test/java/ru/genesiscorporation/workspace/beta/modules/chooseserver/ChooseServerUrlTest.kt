package ru.genesiscorporation.workspace.beta.modules.chooseserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChooseServerUrlTest {
    @Test
    fun `bare host is normalized to a canonical https origin`() {
        assertEquals(
            "https://workspace.example.com",
            normalizeWorkspaceServerUrl(" WORKSPACE.Example.com/ "),
        )
        assertEquals(
            "https://workspace.example.com:8443",
            normalizeWorkspaceServerUrl("https://workspace.example.com:8443"),
        )
    }

    @Test
    fun `unsafe or non-origin server inputs are rejected`() {
        assertNull(normalizeWorkspaceServerUrl("http://workspace.example.com"))
        assertNull(normalizeWorkspaceServerUrl("https://user@workspace.example.com"))
        assertNull(normalizeWorkspaceServerUrl("https://workspace.example.com/path"))
        assertNull(normalizeWorkspaceServerUrl("https://workspace.example.com?next=elsewhere"))
        assertNull(normalizeWorkspaceServerUrl("https://workspace.example.com#fragment"))
        assertNull(normalizeWorkspaceServerUrl("https://http://workspace.example.com"))
    }
}
