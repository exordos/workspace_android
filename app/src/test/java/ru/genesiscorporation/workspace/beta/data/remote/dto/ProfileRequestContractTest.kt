package ru.genesiscorporation.workspace.beta.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod
import ru.genesiscorporation.workspace.beta.data.remote.workspaceRequestBuilder
import io.ktor.http.content.TextContent

class ProfileRequestContractTest {
    private val userUuid = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `presence update uses own user action contract`() {
        val request = UpdateOwnPresenceRequest(
            userUuid = userUuid,
            status = "idle",
            emoji = "coffee",
            text = "Focusing",
        )

        assertEquals(HTTPMethod.POST, request.method)
        assertEquals(
            "/api/workspace/v1/messenger/users/$userUuid/actions/presence/invoke",
            request.url,
        )
        assertEquals("idle", request.data.status)
        assertEquals("coffee", request.data.emoji)
        assertEquals("Focusing", request.data.text)
    }

    @Test
    fun `presence update can explicitly clear status fields`() {
        val request = UpdateOwnPresenceRequest(
            userUuid = userUuid,
            status = "active",
            emoji = null,
            text = null,
        )

        assertNull(request.data.emoji)
        assertNull(request.data.text)
        assertTrue(request.encodeExplicitNulls)
        val body = workspaceRequestBuilder(request, "https://example.test", "token")
            .body as TextContent
        assertEquals(
            """{"status":"active","emoji":null,"text":null}""",
            body.text,
        )
    }

    @Test
    fun `avatar reset uses own user action contract`() {
        val request = ResetOwnAvatarRequest(userUuid)

        assertEquals(HTTPMethod.POST, request.method)
        assertEquals(
            "/api/workspace/v1/messenger/users/$userUuid/actions/avatar_reset/invoke",
            request.url,
        )
    }
}
