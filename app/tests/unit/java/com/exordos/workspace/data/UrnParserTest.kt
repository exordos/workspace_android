package com.exordos.workspace.data

import org.junit.Assert.*
import org.junit.Test

class UrnParserTest {
    @Test fun namespaceIsParsedBeforeTheUrlIdentifier() {
        for (url in listOf(
            "https://example.invalid/path/:image:/avatar.png",
            "https://example.invalid/avatar.png?next=:image:",
            "https://example.invalid/avatar.png#:image:",
        )) assertEquals("url", UrnParser.namespace("urn:url:$url"))
        assertEquals("image", UrnParser.namespace("urn:image:avatar-id"))
        assertEquals("gravatar", UrnParser.namespace("urn:gravatar:hash"))
    }

    @Test fun malformedNamespacesAreRejected() {
        for (urn in listOf(null, "", "https://example.invalid/:image:/avatar", "urn:image", "urn::id", "urn:image:")) {
            assertNull(urn, UrnParser.namespace(urn))
            assertNull(urn, UrnParser.parseUrl(urn, "https://workspace.invalid"))
        }
        assertNull(UrnParser.parseUrl("urn:other:image:id", "https://workspace.invalid"))
        assertNull(UrnParser.parseUrl("urn:Image:id", "https://workspace.invalid"))
    }

    @Test fun supportedNamespacesKeepTheirDownloadUrls() {
        val origin = "https://workspace.invalid"
        assertEquals("$origin/api/workspace/v1/messenger/files/file-id/actions/download",
            UrnParser.parseUrl("urn:image:file-id", origin))
        assertEquals("https://secure.gravatar.com/avatar/hash?d=identicon",
            UrnParser.parseUrl("urn:gravatar:hash", origin))
        assertEquals("https://example.invalid/path/:image:/avatar.png",
            UrnParser.parseUrl("urn:url:https://example.invalid/path/:image:/avatar.png", origin))
    }
}
