package com.exordos.workspace.modules.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WorkspaceShareTest {
    private val projectUuid = "11111111-1111-4111-8111-111111111111"
    private val streamUuid = "22222222-2222-4222-8222-222222222222"
    private val userUuid = "33333333-3333-4333-8333-333333333333"

    @Test
    fun streamLinkUsesTheSelectedServerProjectAndStream() {
        assertEquals(
            "https://workspace.example.com:8443/org/workspace.example.com-8443/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("https://workspace.example.com:8443/", projectUuid, streamUuid)
        )
        assertEquals(
            "HTTPS://Workspace.Example.COM:8443/org/workspace.example.com-8443/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("HTTPS://Workspace.Example.COM:8443/", projectUuid, streamUuid)
        )
        assertEquals(
            "https://workspace.example.com:443/org/workspace.example.com/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("https://workspace.example.com:443/", projectUuid, streamUuid)
        )
    }

    @Test
    fun contactLinkUsesTheWebProfileShareFormat() {
        assertEquals(
            "https://workspace.example.com/#user/$userUuid",
            workspaceUserShareLink("https://workspace.example.com/", userUuid)
        )
        assertEquals(
            "HTTPS://workspace.example.com/#user/$userUuid",
            workspaceUserShareLink("HTTPS://workspace.example.com/", userUuid)
        )
    }

    @Test
    fun linksNeverFallBackToAnotherOrganization() {
        for (baseUrl in listOf(null, "", "workspace.example.com")) {
            assertNull(workspaceStreamShareLink(baseUrl, projectUuid, streamUuid))
            assertNull(workspaceUserShareLink(baseUrl, userUuid))
        }
    }

    @Test
    fun rejectsCredentialsQueriesFragmentsAndNonOriginPaths() {
        for (baseUrl in listOf(
            "https://name:password@workspace.example.com",
            "https://workspace.example.com/?token=private",
            "https://workspace.example.com/#private",
            "https://workspace.example.com/api",
            "https://workspace.example.com/%2F",
            "javascript:alert(1)"
        )) {
            assertNull(workspaceStreamShareLink(baseUrl, projectUuid, streamUuid))
            assertNull(workspaceUserShareLink(baseUrl, userUuid))
        }
    }

    @Test
    fun rejectsMissingAndNonCanonicalResourceIds() {
        for (uuid in listOf("", "1-1-1-1-1", "../another-stream", "$userUuid?token=private")) {
            assertNull(workspaceStreamShareLink("https://workspace.example.com", projectUuid, uuid))
            assertNull(workspaceStreamShareLink("https://workspace.example.com", uuid, streamUuid))
            assertNull(workspaceUserShareLink("https://workspace.example.com", uuid))
        }
    }

    @Test
    fun httpDefaultPortIsNotPartOfTheOrganizationRoute() {
        assertEquals(
            "http://workspace.example.com:80/org/workspace.example.com/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("http://workspace.example.com:80", projectUuid, streamUuid),
        )
        assertEquals(
            "http://workspace.example.com:443/org/workspace.example.com-443/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("http://workspace.example.com:443", projectUuid, streamUuid),
        )
    }

    @Test
    fun ipv6OriginRemainsValidWhileOrganizationRouteIsSanitized() {
        assertEquals(
            "https://[2001:db8::1]:8443/org/2001-db8-1-8443/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("https://[2001:db8::1]:8443/", projectUuid, streamUuid),
        )
        assertEquals("https://[2001:db8::1]/#user/$userUuid",
            workspaceUserShareLink("https://[2001:db8::1]/", userUuid))
    }

    @Test
    fun surroundingWhitespaceAndRootSlashesDoNotEnterSharedLinks() {
        assertEquals("https://workspace.example.com/#user/$userUuid",
            workspaceUserShareLink(" \nhttps://workspace.example.com///\t", userUuid))
    }

    @Test
    fun canonicalUppercaseIdsAreAcceptedButWhitespaceAndUuidShorthandAreRejected() {
        val uppercase = "ABCDEFAB-1234-4ABC-8ABC-ABCDEFABCDEF"
        assertEquals("https://workspace.example.com/#user/$uppercase",
            workspaceUserShareLink("https://workspace.example.com", uppercase))
        for (id in listOf(" $userUuid", "$userUuid ", "1-1-1-1-1", "{$userUuid}", "urn:uuid:$userUuid")) {
            assertNull(workspaceUserShareLink("https://workspace.example.com", id))
            assertNull(workspaceStreamShareLink("https://workspace.example.com", id, streamUuid))
            assertNull(workspaceStreamShareLink("https://workspace.example.com", projectUuid, id))
        }
    }

    @Test
    fun malformedAndNonHttpOriginsNeverProduceShareLinks() {
        for (origin in listOf("https://", "//workspace.example.com", "ftp://workspace.example.com",
            "file:///fixture", "https://bad host", "https://workspace.example.com/%ZZ")) {
            assertNull(origin, workspaceUserShareLink(origin, userUuid))
            assertNull(origin, workspaceStreamShareLink(origin, projectUuid, streamUuid))
        }
    }

    @Test
    fun organizationRouteDoesNotDependOnTheDeviceLanguage() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(
                "https://IDENTITY.example.com/org/identity.example.com/project/$projectUuid/stream/$streamUuid",
                workspaceStreamShareLink("https://IDENTITY.example.com/", projectUuid, streamUuid),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
