package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class ExternalProviderAdminRequestContractTest {
    @Test
    fun `policy requests use exact realm route revision and explicit CA intent`() {
        val read = ExternalProviderPolicyRequest()
        val update = UpdateExternalProviderPolicyRequest(
            enabled = true,
            limits = limits(),
            customCaCertificatesPem = listOf(CERTIFICATE),
            entityTag = "\"7\"",
        )
        val removeCa = UpdateExternalProviderPolicyRequest(
            enabled = false,
            limits = limits(),
            customCaCertificatesPem = null,
            entityTag = "\"8\"",
        )
        val suspend = ChangeExternalProviderSuspensionRequest(
            ExternalProviderSuspensionAction.SUSPEND,
        )

        assertEquals(
            "/api/workspace/v1/messenger/" +
                "external_provider_policies/zulip",
            read.url,
        )
        assertEquals(HTTPMethod.PUT, update.method)
        assertEquals(mapOf("If-Match" to "\"7\""), update.additionalHeaders)
        assertEquals("zulip", update.data.settings.kind)
        assertEquals(
            listOf("$CERTIFICATE\n"),
            update.data.settings.customCaBundle?.certificatesPem,
        )
        assertTrue(update.encodeExplicitNulls)
        assertTrue(
            Json.encodeToString(removeCa.data)
                .contains("\"custom_ca_bundle\":null"),
        )
        assertEquals(
            "/api/workspace/v1/messenger/" +
                "external_provider_policies/zulip/actions/suspend/invoke",
            suspend.url,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `bridge list and lifecycle actions use canonical resources`() {
        val list = ExternalBridgeInstancesRequest(
            pageLimit = 200,
            pageMarker = BRIDGE_UUID.uppercase(),
        )
        val revoke = ChangeExternalBridgeInstanceStatusRequest(
            instanceUuid = BRIDGE_UUID,
            action = ExternalBridgeInstanceAction.REVOKE,
        )

        val parameters = Properties.encodeToStringMap(list.data)
        assertEquals("200", parameters["page_limit"])
        assertEquals(BRIDGE_UUID, parameters["page_marker"])
        assertEquals(
            "/api/workspace/v1/messenger/external_bridge_instances/" +
                "$BRIDGE_UUID/actions/revoke/invoke",
            revoke.url,
        )
    }

    @Test
    fun `admin snapshots are validated and normalized fail closed`() {
        val policy = policy()
        val validated = validateExternalProviderPolicyResponse(
            response = policy,
            responseEntityTag = "\"4\"",
        )
        val health = health()
        val bridge = bridge()

        assertEquals("\"4\"", validated.entityTag)
        assertEquals(
            "healthy",
            validateExternalProviderHealthResponse(health).status,
        )
        assertEquals(
            BRIDGE_UUID,
            validateExternalBridgeInstanceResponse(
                bridge.copy(uuid = BRIDGE_UUID.uppercase()),
                expectedUuid = BRIDGE_UUID,
            ).uuid,
        )
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalProviderPolicyResponse(
                response = policy,
                responseEntityTag = "\"5\"",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalProviderPolicyResponse(
                response = policy,
                responseEntityTag = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalProviderHealthResponse(
                health.copy(accountCounts = mapOf("live" to -1)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalBridgeInstanceResponse(
                bridge.copy(safeError = "x".repeat(4_097)),
            )
        }
    }

    @Test
    fun `CA parser accepts certificate blocks only and rejects unsafe input`() {
        assertEquals(
            listOf("$CERTIFICATE\n", "$CERTIFICATE\n"),
            splitExternalProviderCaCertificates(
                "$CERTIFICATE\n\n$CERTIFICATE",
            ),
        )
        assertNull(
            splitExternalProviderCaCertificates(
                "prefix\n$CERTIFICATE",
            ),
        )
        assertNull(
            splitExternalProviderCaCertificates(
                "-----BEGIN PRIVATE KEY-----\nunsafe\n" +
                    "-----END PRIVATE KEY-----",
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            UpdateExternalProviderPolicyRequest(
                enabled = true,
                limits = limits().copy(maxFileBytes = 5_368_709_121L),
                customCaCertificatesPem = null,
                entityTag = "\"1\"",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExternalBridgeInstancesRequest(pageLimit = 501)
        }
    }

    private fun limits() = ExternalProviderLimits(
        maxAccounts = 10,
        maxSelectedChatsPerAccount = 100,
        maxFileBytes = 52_428_800,
    )

    private fun policy() = ExternalProviderPolicyResponse(
        provider = "zulip",
        enabled = true,
        emergencySuspended = false,
        limits = limits(),
        customCaBundle = ExternalProviderCustomCaBundle(
            uuid = CA_UUID,
            generation = 3,
            sha256 = "a".repeat(64),
            certificateCount = 1,
        ),
        revision = 4,
    )

    private fun health() = ExternalProviderHealthResponse(
        provider = "zulip",
        status = "healthy",
        accountCounts = mapOf("live" to 2),
        chatCounts = mapOf("live" to 5),
        bridgeCounts = mapOf("active" to 1),
        operationCounts = mapOf("queued" to 0),
        metrics = buildJsonObject {},
        updatedAt = "2026-07-30T12:00:00Z",
    )

    private fun bridge() = ExternalBridgeInstanceResponse(
        uuid = BRIDGE_UUID,
        provider = "zulip",
        identityGeneration = 2,
        status = ExternalBridgeInstanceStatus.ACTIVE,
        capabilities = buildJsonObject {},
        lastHeartbeatAt = "2026-07-30T12:00:00Z",
        certificateNotAfter = "2026-08-30T12:00:00Z",
        safeError = null,
        revision = 7,
    )

    private companion object {
        const val BRIDGE_UUID = "50000000-0000-4000-8000-000000000005"
        const val CA_UUID = "60000000-0000-4000-8000-000000000006"
        const val CERTIFICATE =
            "-----BEGIN CERTIFICATE-----\nZmFrZS1jYQ==\n" +
                "-----END CERTIFICATE-----"
    }
}
