package ru.genesiscorporation.workspace.beta.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount

class WorkspaceDeepLinkTest {
    @Test
    fun parsesDesktopTopicPermalinkAndMatchesExactAccount() {
        val link = parseWorkspaceDeepLink(
            "https://workspace.example.com/org/workspace.example.com/project/$PROJECT/stream/$STREAM/topic/$TOPIC",
        ) ?: error("Link was rejected")

        assertEquals("https://workspace.example.com", link.baseUrl)
        assertEquals(PROJECT, link.projectId)
        assertEquals(
            WorkspaceDeepLinkTarget.Topic(STREAM, TOPIC),
            link.target,
        )
        assertTrue(
            link.matches(
                account(
                    baseUrl = "https://WORKSPACE.example.com/",
                    projectId = PROJECT.uppercase(),
                ),
            ),
        )
    }

    @Test
    fun parsesMessageAndCustomSchemeLinks() {
        val httpsLink = parseWorkspaceDeepLink(
            "https://workspace.example.com/org/workspace.example.com/project/$PROJECT/message/$MESSAGE",
        )
        val customLink = parseWorkspaceDeepLink(
            "ew://open/org/workspace.example.com/project/$PROJECT/stream/$STREAM",
        )

        assertEquals(
            WorkspaceDeepLinkTarget.Message(MESSAGE),
            httpsLink?.target,
        )
        assertEquals(
            WorkspaceDeepLinkTarget.Stream(STREAM),
            customLink?.target,
        )
        assertNull(customLink?.baseUrl)
    }

    @Test
    fun rejectsUntrustedOrAmbiguousRoutes() {
        listOf(
            "http://workspace.example.com/org/workspace.example.com/project/$PROJECT/stream/$STREAM",
            "https://user@workspace.example.com/org/workspace.example.com/project/$PROJECT/stream/$STREAM",
            "https://workspace.example.com/org/workspace.example.com/project/$PROJECT/stream/$STREAM?next=evil",
            "https://workspace.example.com/org/../project/$PROJECT/stream/$STREAM",
            "https://workspace.example.com/org/workspace.example.com/project/not-a-uuid/stream/$STREAM",
            "https://workspace.example.com/org/workspace.example.com/project/$PROJECT/message/$MESSAGE/topic/$TOPIC",
            "ew://workspace.example.com/org/workspace.example.com/project/$PROJECT/stream/$STREAM",
            "ew://open:443/org/workspace.example.com/project/$PROJECT/stream/$STREAM",
        ).forEach { value ->
            assertNull(value, parseWorkspaceDeepLink(value))
        }
    }

    private fun account(
        baseUrl: String,
        projectId: String,
    ) = WorkspaceAccount(
        accountId = "account",
        baseUrl = baseUrl,
        projectId = projectId,
        projectName = "Project",
        userId = "55555555-5555-4555-8555-555555555555",
        login = "cassi",
    )

    private companion object {
        const val PROJECT = "11111111-1111-4111-8111-111111111111"
        const val STREAM = "22222222-2222-4222-8222-222222222222"
        const val TOPIC = "33333333-3333-4333-8333-333333333333"
        const val MESSAGE = "44444444-4444-4444-8444-444444444444"
    }
}
