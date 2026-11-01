package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.data.remote.HTTPMethod

class ProtectedStreamMetadataContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `actual stream projection reads owner description private invite only and current key fields`() {
        val response = json.decodeFromString<Stream>(LIVE_RESPONSE)
        assertEquals(OWNER, response.ownerUuid)
        assertEquals("Protected description", response.description)
        assertFalse(response.isPrivate)
        assertTrue(response.inviteOnly)
        assertFalse(response.announce)
        assertTrue(response.encryption)
        assertEquals(KEY, response.currentEncryptionKeyUuid)
        assertEquals("owner", response.role)
        val encoded = Json.parseToJsonElement(json.encodeToString(response)).jsonObject
        assertTrue(encoded.containsKey("owner"))
        assertFalse(encoded.containsKey("owner_uuid"))
    }

    @Test fun `legacy owner uuid alias remains readable without changing canonical output`() {
        val legacy = LIVE_RESPONSE.replace("\"owner\":", "\"owner_uuid\":")
        assertEquals(OWNER, json.decodeFromString<Stream>(legacy).ownerUuid)
    }

    @Test fun `protected metadata edit uses actual PUT and sends only name and description`() {
        val request = UpdateProtectedStreamRequest(STREAM, "Updated", "New description")
        assertEquals(HTTPMethod.PUT, request.method)
        assertEquals("/api/workspace/v1/messenger/streams/$STREAM", request.url)
        assertTrue(request.requiresApiKey)
        val body = Json.parseToJsonElement(json.encodeToString(request.data)).jsonObject
        assertEquals(setOf("name", "description"), body.keys)
        val read = ProtectedStreamDetailsRequest(STREAM)
        assertEquals(HTTPMethod.GET, read.method)
        assertEquals(request.url, read.url)
        val delete = DeleteProtectedStreamRequest(STREAM)
        assertEquals(HTTPMethod.DELETE, delete.method)
        assertEquals(request.url, delete.url)
        assertTrue(delete.requiresApiKey)
    }

    @Test fun strictStreamIdentifiersReuseCanonicalParsingWithoutAcceptingWhitespace() {
        assertEquals(STREAM, parseCanonicalUuid(" $STREAM "))
        assertEquals("/api/workspace/v1/messenger/streams/$STREAM", ProtectedStreamDetailsRequest(STREAM.uppercase()).url)
        for (invalid in listOf(" $STREAM", "$STREAM ", "1-1-1-1-1", "$STREAM/path")) {
            assertThrows(IllegalArgumentException::class.java) { ProtectedStreamDetailsRequest(invalid) }
        }
    }

    private companion object {
        const val STREAM = "00000000-0000-0000-0000-000000000001"
        const val OWNER = "00000000-0000-0000-0000-000000000002"
        const val KEY = "00000000-0000-0000-0000-000000000003"
        const val LIVE_RESPONSE = """{
          "uuid":"00000000-0000-0000-0000-000000000001",
          "name":"Protected chat","description":"Protected description",
          "project_id":"00000000-0000-0000-0000-000000000004",
          "owner":"00000000-0000-0000-0000-000000000002",
          "user_uuid":"00000000-0000-0000-0000-000000000002","role":"owner",
          "notification_mode":"all_messages","unread_count":0,
          "active_unread_count":0,"passive_unread_count":0,
          "source_name":"native","source":{"kind":"native"},
          "invite_only":true,"announce":false,"private":false,"is_archived":false,
          "color":123,"direct_user_uuid":null,"last_message_uuid":null,
          "created_at":"2026-10-15T12:00:00Z","updated_at":"2026-10-15T12:00:00Z",
          "encryption":true,"current_encryption_key_uuid":"00000000-0000-0000-0000-000000000003"
        }"""
    }
}
