package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarSourceTest {
    @Test
    fun workspaceImageIsResolvedOnSelectedServerAndRequiresAuthentication() {
        val source = resolveWorkspaceAvatarSource(
            avatarUrn = "urn:image:369741bd-270f-4ab1-bb30-940a41753754",
            baseUrl = "https://workspace.example/",
        )

        assertEquals(
            "https://workspace.example/api/workspace/v1/messenger/files/" +
                "369741bd-270f-4ab1-bb30-940a41753754/actions/download",
            source?.url,
        )
        assertTrue(source?.requiresAuthentication == true)
    }

    @Test
    fun publicHttpsAvatarDoesNotReceiveWorkspaceAuthentication() {
        val source = resolveWorkspaceAvatarSource(
            avatarUrn = "urn:url:https://cdn.example/avatar.png",
            baseUrl = "https://workspace.example",
        )

        assertEquals("https://cdn.example/avatar.png", source?.url)
        assertFalse(source?.requiresAuthentication ?: true)
    }

    @Test
    fun localAndNonHttpAvatarUrlsAreRejected() {
        assertNull(
            resolveWorkspaceAvatarSource(
                avatarUrn = "urn:url:file:///data/user/0/private",
                baseUrl = "https://workspace.example",
            ),
        )
        assertNull(
            resolveWorkspaceAvatarSource(
                avatarUrn = "urn:url:content://media/external/images/1",
                baseUrl = "https://workspace.example",
            ),
        )
    }
}
