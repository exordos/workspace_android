package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class PushDeviceRequestTest {
    @Test
    fun putRequestSerializesTheDocumentedWorkspacePayload() {
        val request = PutPushDeviceRequest(
            registrationUuid = "00000000-0000-4000-8000-000000000001",
            data = PushDeviceRequestData(
                transport = "fcm",
                platform = "android",
                registrationToken = "fcm-token",
                encryption = PushDeviceEncryptionData(
                    kind = PUSH_DEVICE_HPKE_KIND,
                    algorithm = PUSH_DEVICE_HPKE_ALGORITHM,
                    keyUuid = "00000000-0000-4000-8000-000000000002",
                    publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                ),
            ),
        )

        val payload = Json.encodeToString(request.data)

        assertEquals(HTTPMethod.PUT, request.method)
        assertEquals(
            "/api/workspace/v1/push_devices/00000000-0000-4000-8000-000000000001",
            request.url,
        )
        assertTrue(payload.contains("\"transport\":\"fcm\""))
        assertTrue(payload.contains("\"platform\":\"android\""))
        assertTrue(payload.contains("\"registration_token\":\"fcm-token\""))
        assertTrue(payload.contains("\"kind\":\"HPKE\""))
        assertTrue(payload.contains("\"algorithm\":\"$PUSH_DEVICE_HPKE_ALGORITHM\""))
        assertTrue(payload.contains("\"key_uuid\":\"00000000-0000-4000-8000-000000000002\""))
        assertTrue(payload.contains("\"public_key\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\""))
        assertFalse(payload.contains("registrationUuid"))
    }

    @Test
    fun deleteRequestIsBodylessAndUsesTheSameResourceUrl() {
        val request = DeletePushDeviceRequest(
            "00000000-0000-4000-8000-000000000001",
        )

        assertEquals(HTTPMethod.DELETE, request.method)
        assertEquals(
            "/api/workspace/v1/push_devices/00000000-0000-4000-8000-000000000001",
            request.url,
        )
    }

    @Test
    fun putResponseDecodesWorkspaceProjectUuid() {
        val response = Json.decodeFromString<PushDeviceResponse>(
            """
            {
              "uuid": "00000000-0000-4000-8000-000000000001",
              "project_id": "00000000-0000-4000-8000-000000000002",
              "user_uuid": "00000000-0000-4000-8000-000000000003",
              "transport": "fcm",
              "platform": "android",
              "registration_token": "fcm-token",
              "encryption": {
                "kind": "HPKE",
                "algorithm": "$PUSH_DEVICE_HPKE_ALGORITHM",
                "key_uuid": "00000000-0000-4000-8000-000000000004",
                "public_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
              },
              "created_at": "2026-07-26T05:30:00Z",
              "updated_at": "2026-07-26T05:40:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(
            "00000000-0000-4000-8000-000000000002",
            response.projectId,
        )
    }
}
