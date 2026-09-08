package ru.genesiscorporation.workspace.beta.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class DeleteMessageRequestContractTest {
    @Test
    fun `delete message uses the canonical messenger endpoint`() {
        val request = DeleteMessageRequest(MESSAGE_UUID.uppercase())

        assertEquals(HTTPMethod.DELETE, request.method)
        assertEquals(
            "/api/workspace/v1/messenger/messages/$MESSAGE_UUID",
            request.url,
        )
    }

    @Test
    fun `delete message rejects malformed and noncanonical identifiers`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeleteMessageRequest("not-a-uuid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeleteMessageRequest("1-1-1-1-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeleteMessageRequest("$MESSAGE_UUID/path")
        }
    }

    private companion object {
        const val MESSAGE_UUID = "11111111-1111-4111-8111-111111111111"
    }
}
