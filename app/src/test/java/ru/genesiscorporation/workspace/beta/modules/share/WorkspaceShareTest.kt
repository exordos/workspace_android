package ru.genesiscorporation.workspace.beta.modules.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceShareTest {
    private val projectUuid = "11111111-1111-4111-8111-111111111111"
    private val streamUuid = "22222222-2222-4222-8222-222222222222"
    private val userUuid = "33333333-3333-4333-8333-333333333333"

    @Test
    fun streamLinkUsesTheSelectedServerProjectAndStream() {
        assertEquals(
            "https://workspace.example.com:8443/project/$projectUuid/stream/$streamUuid",
            workspaceStreamShareLink("https://workspace.example.com:8443/", projectUuid, streamUuid)
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
}
