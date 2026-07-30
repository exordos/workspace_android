package ru.genesiscorporation.workspace.beta.modules.externalintegrations

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountSelectionMode
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountStatus
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalHistoryDepth
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationStatus
import ru.genesiscorporation.workspace.beta.data.remote.dto.ZulipExternalAccountSettings

class ExternalIntegrationsModelTest {
    @Test
    fun `provider links stay on the configured https origin`() {
        assertEquals(
            "https://zulip.example.com/#narrow/channel/42",
            safeExternalChatUrl(
                candidate =
                    "https://zulip.example.com/#narrow/channel/42",
                providerOrigin = "https://zulip.example.com",
            ),
        )
        assertNull(
            safeExternalChatUrl(
                candidate =
                    "https://evil.example/#narrow/channel/42",
                providerOrigin = "https://zulip.example.com",
            ),
        )
        assertNull(
            safeExternalChatUrl(
                candidate =
                    "http://zulip.example.com/#narrow/channel/42",
                providerOrigin = "https://zulip.example.com",
            ),
        )
        assertNull(
            safeExternalChatUrl(
                candidate =
                    "https://user@zulip.example.com/#narrow/channel/42",
                providerOrigin = "https://zulip.example.com",
            ),
        )
        assertNull(
            safeExternalChatUrl(
                candidate =
                    "https://zulip.example.com/#narrow/channel/42",
                providerOrigin = "http://zulip.example.com",
            ),
        )
        assertNull(
            safeExternalChatUrl(
                candidate =
                    "https://zulip.example.com:8443/#narrow/channel/42",
                providerOrigin = "https://zulip.example.com",
            ),
        )
        assertEquals(
            "https://zulip.example.com:8443/#narrow/channel/42",
            safeExternalChatUrl(
                candidate =
                    "https://zulip.example.com:8443/#narrow/channel/42",
                providerOrigin = "https://zulip.example.com:8443",
            ),
        )
    }

    @Test
    fun `live readiness is the authoritative connected label`() {
        assertEquals(
            "Подключён",
            externalAccountStatusLabel(
                account(
                    status = ExternalAccountStatus.BACKFILL,
                    liveReady = true,
                ),
            ),
        )
        assertEquals(
            "Нужен ключ",
            externalAccountStatusLabel(
                account(
                    status = ExternalAccountStatus.AUTH_REQUIRED,
                    liveReady = false,
                ),
            ),
        )
    }

    @Test
    fun `transport errors map to actionable bounded messages`() {
        val conflict = externalIntegrationErrorText(
            ApiError(
                errorMessage = "raw server conflict",
                code = "412",
                kind = ApiErrorKind.CONFLICT,
            ),
        )
        val ownerChanged = externalIntegrationErrorText(
            ApiError(
                errorMessage = "raw owner identifier",
                code = "ACCOUNT_CHANGED",
                kind = ApiErrorKind.CONFLICT,
            ),
        )

        assertTrue(conflict.contains("Обновите"))
        assertTrue(ownerChanged.contains("Аккаунт сменился"))
        assertTrue("raw" !in conflict)
        assertTrue("identifier" !in ownerChanged)
    }

    @Test
    fun `capability descriptors hide unavailable surfaces and explain why`() {
        val capabilities = buildJsonObject {
            put(
                "messenger.chat_catalog",
                buildJsonObject {
                    put("available", JsonPrimitive(false))
                    put(
                        "unavailable_reason",
                        buildJsonObject {
                            put(
                                "message",
                                JsonPrimitive(
                                    "Provider catalog is temporarily unavailable",
                                ),
                            )
                        },
                    )
                },
            )
            put(
                "messenger.message.send",
                buildJsonObject {
                    put("available", JsonPrimitive(true))
                },
            )
        }

        assertTrue(
            !externalCapabilityAvailable(
                capabilities,
                "messenger.chat_catalog",
            ),
        )
        assertTrue(
            externalCapabilityAvailable(
                capabilities,
                "messenger.message.send",
            ),
        )
        assertEquals(
            listOf("Provider catalog is temporarily unavailable"),
            externalCapabilityUnavailableReasons(capabilities),
        )
    }

    @Test
    fun `operation statuses use user facing labels`() {
        assertEquals(
            "Нужна проверка",
            externalOperationStatusLabel(
                ExternalOperationStatus.MANUAL_RECONCILIATION_REQUIRED,
            ),
        )
        assertEquals(
            "В очереди",
            externalOperationStatusLabel(ExternalOperationStatus.QUEUED),
        )
    }

    private fun account(
        status: ExternalAccountStatus,
        liveReady: Boolean,
    ) = ExternalAccountResponse(
        uuid = "10000000-0000-4000-8000-000000000001",
        settings = ZulipExternalAccountSettings(
            kind = "zulip",
            serverUrl = "https://zulip.example.com",
            email = "user@example.com",
            selectionMode = ExternalAccountSelectionMode.EXPLICIT,
            historyDepth = ExternalHistoryDepth.THIRTY_DAYS,
            defaultProjectId =
                "20000000-0000-4000-8000-000000000002",
        ),
        credentialPresent = true,
        status = status,
        liveReady = liveReady,
        capabilities = buildJsonObject {},
        safeError = null,
        desiredGeneration = 1,
        appliedGeneration = 1,
        lastProgressAt = null,
        revision = 1,
        createdAt = "2026-07-30T10:00:00Z",
        updatedAt = "2026-07-30T10:00:00Z",
    )
}
