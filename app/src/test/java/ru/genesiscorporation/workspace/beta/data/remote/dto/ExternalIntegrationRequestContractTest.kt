package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
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

    private companion object {
        const val ACCOUNT_UUID = "10000000-0000-4000-8000-000000000001"
        const val CHAT_UUID = "20000000-0000-4000-8000-000000000002"
        const val PROJECT_UUID = "30000000-0000-4000-8000-000000000003"
    }
}
