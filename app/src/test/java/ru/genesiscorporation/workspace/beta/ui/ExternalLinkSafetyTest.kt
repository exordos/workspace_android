package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkSafetyTest {
    @Test
    fun `web and mail links are accepted`() {
        assertTrue(isSafeExternalLink("https://example.com/path?q=1"))
        assertTrue(isSafeExternalLink("http://example.com"))
        assertTrue(isSafeExternalLink("mailto:person@example.com"))
    }

    @Test
    fun `local active and credentialed schemes are rejected`() {
        assertFalse(isSafeExternalLink("javascript:alert(1)"))
        assertFalse(isSafeExternalLink("file:///data/local/tmp/file"))
        assertFalse(isSafeExternalLink("intent://example.com"))
        assertFalse(isSafeExternalLink("https://user:secret@example.com"))
        assertFalse(isSafeExternalLink("https:///missing-host"))
        assertFalse(isSafeExternalLink("urn:message:uuid"))
    }

    @Test
    fun `canonical URL URNs preserve safe web targets exactly`() {
        val secure =
            "https://example.com/docs/path?first=one&second=two#section"
        val plain = "http://example.org/plain"

        assertEquals(secure, parseWorkspaceUrlUrn("urn:url:$secure"))
        assertEquals(
            secure,
            parseWorkspaceUrlUrn("  URN:URL:$secure  "),
        )
        assertEquals(
            "HTTPS://example.com/upper-scheme",
            parseWorkspaceUrlUrn(
                "urn:url:HTTPS://example.com/upper-scheme",
            ),
        )
        assertEquals(plain, parseWorkspaceUrlUrn("urn:url:$plain"))
        assertEquals(secure, normalizeSafeExternalLink("urn:url:$secure"))
        assertNull(parseWorkspaceUrlUrn(secure))
    }

    @Test
    fun `malformed credentialed and unsafe URL URNs fail closed`() {
        val invalidValues = listOf(
            "urn:url:",
            "urn:url:not-a-url",
            "urn:url:javascript:alert(1)",
            "urn:url:data:text/html,hello",
            "urn:url:file:///data/local/tmp/file",
            "urn:url://example.com/path",
            "urn:url:https://user@example.com/path",
            "urn:url:https://user:secret@example.com/path",
            "urn:url:urn:url:https://example.com/path",
            "urn:url:https://example.com/space here",
        )

        invalidValues.forEach { value ->
            assertNull(value, parseWorkspaceUrlUrn(value))
            assertNull(value, normalizeSafeExternalLink(value))
        }
    }

    @Test
    fun `ordinary safe links retain their existing behavior`() {
        assertEquals(
            "https://example.com/path?q=1",
            normalizeSafeExternalLink(" https://example.com/path?q=1 "),
        )
        assertEquals(
            "mailto:person@example.com",
            normalizeSafeExternalLink("mailto:person@example.com"),
        )
        assertNull(normalizeSafeExternalLink("urn:message:uuid"))
    }
}
