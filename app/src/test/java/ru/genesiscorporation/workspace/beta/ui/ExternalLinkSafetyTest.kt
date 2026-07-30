package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertFalse
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
}
