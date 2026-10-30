package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedPayloadSerializationTest {
    @Test fun productionDefaultEncodingOmitsFieldsOutsideEachOpaqueKindContract() {
        // Ktor's json() default enables encodeDefaults, unlike Json {} in earlier fixtures.
        val json = Json { encodeDefaults = true }
        val key = "11111111-1111-4111-8111-111111111111"
        val cases = listOf(
            MessageResponsePayload("markdown", "hello") to setOf("kind", "content"),
            MessageResponsePayload("e2ee", "opaque", key) to setOf("kind", "content", "key_uuid"),
            MessageResponsePayload("key_request", keyUuid = key, deviceId = "device", deviceName = "Pixel 5", publicKey = "public") to
                setOf("kind", "key_uuid", "device_id", "device_name", "public_key"),
            MessageResponsePayload("key_grant", "opaque", key, requestMessageUuid = key, senderDeviceId = "a", recipientDeviceId = "b") to
                setOf("kind", "key_uuid", "request_message_uuid", "sender_device_id", "recipient_device_id", "content"),
            MessageResponsePayload("key_reject", keyUuid = key, requestMessageUuid = key, senderDeviceId = "a", recipientDeviceId = "b") to
                setOf("kind", "key_uuid", "request_message_uuid", "sender_device_id", "recipient_device_id"),
            MessageResponsePayload("key_announced", keyUuid = key) to setOf("kind", "key_uuid"),
        )
        cases.forEach { (payload, required) ->
            val request = MessageCreateData(key, key, key, payload)
            val encoded = json.parseToJsonElement(json.encodeToString(request)).jsonObject.getValue("payload").jsonObject
            assertEquals(payload.kind, required, encoded.keys)
            assertEquals(payload, json.decodeFromString<MessageResponsePayload>(encoded.toString()))
        }
    }
    @Test fun ordinaryCreationDoesNotSendNullGenerationOrCanonicalUuidWithKtorDefaults() {
        val json = Json { encodeDefaults = true }
        val data = AddStreamRequest("ordinary", "", null).data
        val encoded = json.parseToJsonElement(json.encodeToString(data)).jsonObject
        assertFalse(encoded.containsKey("current_encryption_key_uuid"))
        assertFalse(encoded.containsKey("uuid"))
        val key = "11111111-1111-4111-8111-111111111111"
        val encrypted = json.parseToJsonElement(json.encodeToString(AddStreamRequest("protected", "", null, true, key, key).data)).jsonObject
        assertTrue(encrypted.containsKey("current_encryption_key_uuid"))
        assertTrue(encrypted.containsKey("uuid"))
    }

    @Test fun sharedBodiesPreserveOrdinaryAndOpaqueWireContracts() {
        for (defaults in listOf(false, true)) {
            val json = Json { encodeDefaults = defaults }
            val id = "11111111-1111-4111-8111-111111111111"
            val ordinary = ForwardMessageRequest(id, id, id, "hello").data
            val protected = EncryptedSendMessageRequest(id, id, MessageResponsePayload("e2ee", "cipher", id), id).data
            assertEquals(setOf("uuid", "stream_uuid", "topic_uuid", "payload"), json.parseToJsonElement(json.encodeToString(ordinary)).jsonObject.keys)
            assertEquals("cipher", protected.payload.content)
            for (text in listOf("hello", "")) {
                val edit = json.parseToJsonElement(json.encodeToString(EditMessageRequest(id, text).data)).jsonObject["payload"]!!.jsonObject
                assertEquals("{\"kind\":\"markdown\",\"content\":\"$text\"}", edit.toString())
                val createDraft = json.parseToJsonElement(json.encodeToString(CreateDraftRequest(id, id, id, text).data)).jsonObject["payload"]
                val updateDraft = json.parseToJsonElement(json.encodeToString(UpdateDraftRequest(id, text, 1).data)).jsonObject["payload"]
                assertEquals(edit, createDraft)
                assertEquals(edit, updateDraft)
            }
            val opaque = json.parseToJsonElement(json.encodeToString(EncryptedEditMessageRequest(id, MessageResponsePayload("key_announced", keyUuid = id)).data)).jsonObject["payload"]!!.jsonObject
            assertEquals(setOf("kind", "key_uuid"), opaque.keys)
        }
    }

}
