package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.exordos.workspace.data.remote.HTTPMethod

class AddStreamRequestContractTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun ordinaryStreamUsesTheCurrentCreationContract() {
        assertBody(AddStreamRequest("CASSI contract", "", null), """{
            "name":"CASSI contract","description":"","direct_user_uuid":null,
            "source_name":"native","encryption":false
        }""")
    }

    @Test
    fun encryptedStreamKeepsItsCanonicalIdentityAndKeyGeneration() {
        val stream = "00000000-0000-4000-8000-000000000001"
        val key = "00000000-0000-4000-8000-000000000002"
        assertBody(AddStreamRequest("CASSI contract", "", null, true, key, stream), """{
            "name":"CASSI contract","description":"","direct_user_uuid":null,
            "source_name":"native","encryption":true,
            "current_encryption_key_uuid":"$key","uuid":"$stream"
        }""")
    }

    @Test
    fun directStreamKeepsTheSelectedPeer() {
        val peer = "00000000-0000-4000-8000-000000000003"
        assertBody(AddStreamRequest("CASSI contract", "", peer), """{
            "name":"CASSI contract","description":"","direct_user_uuid":"$peer",
            "source_name":"native","encryption":false
        }""")
    }

    private fun assertBody(request: AddStreamRequest, expected: String) {
        assertEquals(json.parseToJsonElement(expected),
            json.parseToJsonElement(json.encodeToString(request.data)))
        assertEquals(HTTPMethod.POST, request.method)
        assertEquals("/api/workspace/v1/messenger/streams/", request.url)
        assertTrue(request.requiresApiKey)
    }
}
