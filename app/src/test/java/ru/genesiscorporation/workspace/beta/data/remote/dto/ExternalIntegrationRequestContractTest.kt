package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.encodeToStringMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class ExternalIntegrationRequestContractTest {
    @Test
    fun `create account sends one write-only zulip credential body`() {
        val request = CreateExternalAccountRequest(
            accountUuid = ACCOUNT_UUID.uppercase(),
            serverUrl = "zulip.example.com/",
            email = " user@example.com ",
            apiKey = " secret-key ",
            selectionMode = ExternalAccountSelectionMode.EXPLICIT,
            historyDepth = ExternalHistoryDepth.THIRTY_DAYS,
            defaultProjectId = PROJECT_UUID,
        )

        assertEquals(HTTPMethod.POST, request.method)
        assertEquals(
            "/api/workspace/v1/messenger/external_accounts/",
            request.url,
        )
        assertEquals(ACCOUNT_UUID, request.data.uuid)
        assertEquals("https://zulip.example.com", request.data.settings.serverUrl)
        assertEquals("user@example.com", request.data.settings.email)
        assertEquals("secret-key", request.data.settings.apiKey)
        assertEquals(PROJECT_UUID, request.data.settings.defaultProjectId)
        val body = Json.encodeToString(request.data)
        assertTrue(body.contains("\"api_key\":\"secret-key\""))
        assertFalse(body.contains("credential_present"))
    }

    @Test
    fun `account lifecycle actions use canonical endpoints and strong revisions`() {
        val update = UpdateExternalAccountRequest(
            accountUuid = ACCOUNT_UUID,
            selectionMode = ExternalAccountSelectionMode.ALL,
            historyDepth = ExternalHistoryDepth.SEVEN_DAYS,
            defaultProjectId = PROJECT_UUID,
            entityTag = "\"7\"",
        )
        val reconnect = ReconnectExternalAccountRequest(
            accountUuid = ACCOUNT_UUID,
            serverUrl = "https://zulip.example.com",
            email = "user@example.com",
            apiKey = "replacement",
            entityTag = "\"8\"",
        )
        val disconnect = DisconnectExternalAccountRequest(ACCOUNT_UUID)
        val delete = DeleteExternalAccountRequest(ACCOUNT_UUID)

        assertEquals(
            "/api/workspace/v1/messenger/external_accounts/$ACCOUNT_UUID",
            update.url,
        )
        assertEquals(mapOf("If-Match" to "\"7\""), update.additionalHeaders)
        assertEquals(
            "/api/workspace/v1/messenger/external_accounts/$ACCOUNT_UUID/" +
                "actions/reconnect/invoke",
            reconnect.url,
        )
        assertEquals(mapOf("If-Match" to "\"8\""), reconnect.additionalHeaders)
        assertEquals(
            "/api/workspace/v1/messenger/external_accounts/$ACCOUNT_UUID/" +
                "actions/disconnect/invoke",
            disconnect.url,
        )
        assertEquals(HTTPMethod.DELETE, delete.method)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `external chat catalog and assignments preserve owner and project scope`() {
        val list = ExternalChatsRequest(
            externalAccountUuid = ACCOUNT_UUID,
            pageLimit = 100,
            pageMarker = CHAT_UUID,
        )
        val select = SelectExternalChatRequest(CHAT_UUID, PROJECT_UUID)
        val deselect = DeselectExternalChatRequest(CHAT_UUID)
        val move = MoveExternalChatRequest(
            CHAT_UUID,
            PROJECT_UUID,
            "\"9\"",
        )

        val params = Properties.encodeToStringMap(list.data)
        assertEquals(ACCOUNT_UUID, params["external_account_uuid"])
        assertEquals("100", params["page_limit"])
        assertEquals(CHAT_UUID, params["page_marker"])
        assertEquals(PROJECT_UUID, select.data.projectId)
        assertEquals(
            "/api/workspace/v1/messenger/external_chats/$CHAT_UUID/" +
                "actions/deselect/invoke",
            deselect.url,
        )
        assertEquals(mapOf("If-Match" to "\"9\""), move.additionalHeaders)
    }

    @Test
    fun `external integration requests fail closed on unsafe input`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreateExternalAccountRequest(
                accountUuid = "not-a-uuid",
                serverUrl = "https://zulip.example.com",
                email = "user@example.com",
                apiKey = "secret",
                selectionMode = ExternalAccountSelectionMode.EXPLICIT,
                historyDepth = ExternalHistoryDepth.NEW,
                defaultProjectId = PROJECT_UUID,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CreateExternalAccountRequest(
                accountUuid = ACCOUNT_UUID,
                serverUrl = "http://zulip.example.com",
                email = "user@example.com",
                apiKey = "secret",
                selectionMode = ExternalAccountSelectionMode.EXPLICIT,
                historyDepth = ExternalHistoryDepth.NEW,
                defaultProjectId = PROJECT_UUID,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectExternalAccountRequest(
                accountUuid = ACCOUNT_UUID,
                serverUrl = "https://user@zulip.example.com",
                email = "invalid",
                apiKey = "",
                entityTag = "W/\"2\"",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExternalAccountsRequest(pageLimit = 501)
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeExternalIntegrationServerUrl(
                "https://zulip.example.com:65536",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeExternalIntegrationEmail("user name@example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeExternalIntegrationApiKey("secret\nkey")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `operation queue uses scoped paging and explicit duplicate consent`() {
        val list = ExternalOperationsRequest(
            externalAccountUuid = ACCOUNT_UUID,
            pageLimit = 200,
            pageMarker = OPERATION_UUID,
        )
        val retry = RetryExternalOperationRequest(
            operationUuid = OPERATION_UUID,
            confirmDuplicateRisk = true,
        )
        val discard = DiscardExternalOperationRequest(OPERATION_UUID)

        val params = Properties.encodeToStringMap(list.data)
        assertEquals(ACCOUNT_UUID, params["external_account_uuid"])
        assertEquals("200", params["page_limit"])
        assertEquals(OPERATION_UUID, params["page_marker"])
        assertEquals(
            "/api/workspace/v1/messenger/external_operations/" +
                "$OPERATION_UUID/actions/retry/invoke",
            retry.url,
        )
        assertTrue(retry.data.confirmDuplicateRisk)
        assertEquals(HTTPMethod.DELETE, discard.method)
        assertEquals(
            "/api/workspace/v1/messenger/external_operations/$OPERATION_UUID",
            discard.url,
        )
    }

    @Test
    fun `operation response validation enforces scope and retry risk`() {
        val valid = externalOperation()

        assertEquals(
            valid,
            validateExternalOperationResponse(
                response = valid,
                expectedUuid = OPERATION_UUID,
                expectedExternalAccountUuid = ACCOUNT_UUID,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalOperationResponse(
                response = valid.copy(
                    externalAccountUuid = CHAT_UUID,
                ),
                expectedExternalAccountUuid = ACCOUNT_UUID,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalOperationResponse(
                valid.copy(
                    duplicateRisk = false,
                    retryRequiresConfirmation = true,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateExternalOperationResponse(
                valid.copy(action = "message.send\nunsafe"),
            )
        }
    }

    private fun externalOperation() = ExternalOperationResponse(
        uuid = OPERATION_UUID,
        externalAccountUuid = ACCOUNT_UUID,
        action = "message.send",
        targetType = "message",
        targetUuid = CHAT_UUID,
        status = ExternalOperationStatus.FAILED,
        safeError = "Provider confirmation timed out",
        canRetry = true,
        canDiscard = true,
        duplicateRisk = true,
        retryRequiresConfirmation = true,
        originalUrl = "https://zulip.example.com/#narrow/channel/42",
        reconciliationState =
            ExternalOperationReconciliationState.MANUAL_REQUIRED,
        reconciliationReason =
            ExternalOperationReconciliationReason.UNSAFE_PROVIDER_STATE,
        reconciliationEvidence = buildJsonObject {},
        attempt = 1,
        attemptHistory = buildJsonArray {},
        details = buildJsonObject {},
        revision = 4,
        createdAt = "2026-07-30T10:00:00Z",
        updatedAt = "2026-07-30T10:01:00Z",
    )

    private companion object {
        const val ACCOUNT_UUID = "10000000-0000-4000-8000-000000000001"
        const val CHAT_UUID = "20000000-0000-4000-8000-000000000002"
        const val PROJECT_UUID = "30000000-0000-4000-8000-000000000003"
        const val OPERATION_UUID = "40000000-0000-4000-8000-000000000004"
    }
}
