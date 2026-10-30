package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AddUsersToStreamRequestContractTest {
    @Test fun `native invitations encode the singular member role accepted by the V2 API`() {
        val stream = "33333333-3333-4333-8333-333333333333"
        val member = "22222222-2222-4222-8222-222222222222"
        val request = AddUsersToStreamRequest(stream, listOf(member))
        val body = Json.parseToJsonElement(Json.encodeToString(request.data)).jsonObject
        assertEquals("/api/workspace/v1/messenger/streams/$stream/actions/add_users/invoke", request.url)
        assertEquals(setOf("member"), body.keys)
        assertEquals(listOf(member), body.getValue("member").jsonArray.map { it.jsonPrimitive.content })
    }
}
