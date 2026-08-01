package ru.genesiscorporation.workspace.beta.modules.chooseserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsResponseData

class ChooseServerUrlTest {
    @Test
    fun `bare host is normalized to a canonical https origin`() {
        assertEquals(
            "https://workspace.example.com",
            normalizeWorkspaceServerUrl(" WORKSPACE.Example.com/ "),
        )
        assertEquals(
            "https://workspace.example.com:8443",
            normalizeWorkspaceServerUrl("https://workspace.example.com:8443"),
        )
    }

    @Test
    fun `unsafe or non-origin server inputs are rejected`() {
        assertNull(normalizeWorkspaceServerUrl("http://workspace.example.com"))
        assertNull(normalizeWorkspaceServerUrl("https://user@workspace.example.com"))
        assertNull(normalizeWorkspaceServerUrl("https://workspace.example.com/path"))
        assertNull(normalizeWorkspaceServerUrl("https://workspace.example.com?next=elsewhere"))
        assertNull(normalizeWorkspaceServerUrl("https://workspace.example.com#fragment"))
        assertNull(normalizeWorkspaceServerUrl("https://http://workspace.example.com"))
    }

    @Test
    fun `workspace settings require bounded safe public metadata`() {
        assertTrue(
            isUsableWorkspaceServerSettings(
                settings(
                    realmUrl = "https://workspace.example.com",
                    meetUrl = "https://meet.example.com/room/",
                    realmIcon = "urn:url:https://workspace.example.com/logo.png",
                ),
            ),
        )
        assertTrue(isUsableWorkspaceServerSettings(settings(meetUrl = "")))

        assertFalse(isUsableWorkspaceServerSettings(settings(realmName = "  ")))
        assertFalse(isUsableWorkspaceServerSettings(settings(realmName = "Bad\u0000Realm")))
        assertFalse(isUsableWorkspaceServerSettings(settings(realmName = "R".repeat(257))))
        assertFalse(
            isUsableWorkspaceServerSettings(
                settings(meetUrl = "http://meet.example.com"),
            ),
        )
        assertFalse(
            isUsableWorkspaceServerSettings(
                settings(meetUrl = "https://meet.example.com/?token=secret"),
            ),
        )
        assertFalse(
            isUsableWorkspaceServerSettings(
                settings(realmUrl = "https://user@workspace.example.com"),
            ),
        )
        assertFalse(
            isUsableWorkspaceServerSettings(
                settings(realmIcon = "https://example.com/icon\n.png"),
            ),
        )
        assertFalse(
            isUsableWorkspaceServerSettings(
                settings(realmIcon = "urn:url:javascript:alert(1)"),
            ),
        )
        assertFalse(
            isUsableWorkspaceServerSettings(
                settings(realmIcon = "//assets.example.com/icon.png"),
            ),
        )
        assertTrue(
            isUsableWorkspaceServerSettings(
                settings(realmIcon = "/logo-512x512.png"),
            ),
        )
    }

    @Test
    fun `discovery failures expose actionable safe categories`() {
        assertEquals(
            "Сервер не ответил вовремя. Повторите попытку",
            discoveryMessage(ApiErrorKind.TIMEOUT),
        )
        assertEquals(
            "Не удалось найти сервер или установить защищённое соединение",
            discoveryMessage(ApiErrorKind.NETWORK),
        )
        assertEquals(
            "По этому адресу не найден Workspace",
            discoveryMessage(ApiErrorKind.NOT_FOUND),
        )
        assertEquals(
            "Сервер временно недоступен. Повторите попытку позже",
            discoveryMessage(ApiErrorKind.SERVER),
        )
        assertEquals(
            "Сервер вернул некорректный ответ Workspace",
            discoveryMessage(ApiErrorKind.MALFORMED_RESPONSE),
        )
    }

    private fun settings(
        realmName: String = "Example Workspace",
        meetUrl: String = "https://meet.example.com",
        realmUrl: String? = null,
        realmIcon: String? = null,
    ) = ServerSettingsResponseData(
        email_auth_enabled = true,
        realmName = realmName,
        meetUrl = meetUrl,
        realmUrl = realmUrl,
        realmIcon = realmIcon,
    )

    private fun discoveryMessage(kind: ApiErrorKind): String =
        serverDiscoveryErrorMessage(
            ApiError(
                errorMessage = "sensitive upstream detail",
                code = "TEST",
                kind = kind,
            ),
        )
}
