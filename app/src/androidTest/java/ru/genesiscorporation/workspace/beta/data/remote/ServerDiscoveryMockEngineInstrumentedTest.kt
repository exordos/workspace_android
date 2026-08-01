package ru.genesiscorporation.workspace.beta.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.Credential
import ru.genesiscorporation.workspace.beta.data.CredentialStore
import ru.genesiscorporation.workspace.beta.data.IsolatedAndroidTestContext
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferencesRepository
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsResponseData

@RunWith(AndroidJUnit4::class)
class ServerDiscoveryMockEngineInstrumentedTest {
    @Test
    fun sameOriginRedirectKeepsDiscoveryResponseUsable() = runBlocking {
        var requestCount = 0
        val result = performDiscovery { request ->
            requestCount += 1
            if (request.url.encodedPath == SETTINGS_PATH) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, REDIRECTED_PATH),
                )
            } else {
                assertEquals(REDIRECTED_PATH, request.url.encodedPath)
                validSettingsResponse()
            }
        }

        assertEquals(2, requestCount)
        val success = result as ApiResult.Success
        assertEquals("Example Workspace", success.value.realmName)
    }

    @Test
    fun crossOriginRedirectIsRejectedBeforeSettingsAreAccepted() = runBlocking {
        var requestCount = 0
        val result = performDiscovery { request ->
            requestCount += 1
            if (request.url.host == EXPECTED_HOST) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "$REDIRECTED_ORIGIN$REDIRECTED_PATH",
                    ),
                )
            } else {
                validSettingsResponse()
            }
        }

        assertEquals(2, requestCount)
        assertDiscoveryError(
            result,
            expectedKind = ApiErrorKind.MALFORMED_RESPONSE,
            expectedCode = "UNEXPECTED_RESPONSE_ORIGIN",
        )
    }

    @Test
    fun statusMatrixUsesActionableDiscoveryCategories() = runBlocking {
        val cases = listOf(
            HttpStatusCode.Forbidden to ApiErrorKind.FORBIDDEN,
            HttpStatusCode.NotFound to ApiErrorKind.NOT_FOUND,
            HttpStatusCode.TooManyRequests to ApiErrorKind.RATE_LIMITED,
            HttpStatusCode.ServiceUnavailable to ApiErrorKind.SERVER,
        )

        cases.forEach { (status, expectedKind) ->
            val result = performDiscovery {
                respond(
                    content = "{}",
                    status = status,
                    headers = JSON_HEADERS,
                )
            }
            assertDiscoveryError(
                result,
                expectedKind = expectedKind,
                expectedCode = status.value.toString(),
            )
        }
    }

    @Test
    fun malformedOversizedAndTlsFailuresFailClosed() = runBlocking {
        val malformed = performDiscovery {
            respond(
                content = "{not-json",
                status = HttpStatusCode.OK,
                headers = JSON_HEADERS,
            )
        }
        assertDiscoveryError(
            malformed,
            expectedKind = ApiErrorKind.MALFORMED_RESPONSE,
            expectedCode = "MALFORMED_RESPONSE",
        )

        val oversized = performDiscovery {
            respond(
                content = "x".repeat(MAX_API_RESPONSE_BYTES + 1),
                status = HttpStatusCode.OK,
            )
        }
        assertDiscoveryError(
            oversized,
            expectedKind = ApiErrorKind.MALFORMED_RESPONSE,
            expectedCode = "RESPONSE_TOO_LARGE",
        )

        val tlsFailure = performDiscovery {
            throw SSLHandshakeException("injected certificate failure")
        }
        assertDiscoveryError(
            tlsFailure,
            expectedKind = ApiErrorKind.NETWORK,
            expectedCode = "NETWORK",
        )
    }

    private suspend fun performDiscovery(
        handler: suspend MockRequestHandleScope.(HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
    ): ApiResult<ServerSettingsResponseData, ApiError> {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedAndroidTestContext(baseContext, "discovery-mock")
        val repository = ApiKeyRepository(
            context = context,
            credentialStore = InMemoryCredentialStore(),
        )
        val userViewModel = UserViewModel(
            repo = repository,
            conversationStateStore = NoOpConversationStateStore,
            uiPreferencesRepository = WorkspaceUiPreferencesRepository(context),
        )
        val httpClient = HttpClient(MockEngine(handler)) {
            followRedirects = true
        }
        return try {
            WorkspaceAPIClient(
                client = httpClient,
                userViewModel = userViewModel,
                sessionCookieStore = SessionCookieStore(),
            ).performRequest(ServerSettingsRequest(EXPECTED_ORIGIN))
        } finally {
            httpClient.close()
            repository.clearAll()
            context.cleanUp()
        }
    }

    private fun MockRequestHandleScope.validSettingsResponse() =
        respond(
            content = VALID_SETTINGS_JSON,
            status = HttpStatusCode.OK,
            headers = JSON_HEADERS,
        )

    private fun assertDiscoveryError(
        result: ApiResult<ServerSettingsResponseData, ApiError>,
        expectedKind: ApiErrorKind,
        expectedCode: String,
    ) {
        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).error
        assertEquals(expectedKind, error.kind)
        assertEquals(expectedCode, error.code)
    }

    private class InMemoryCredentialStore : CredentialStore {
        private val values = mutableMapOf<Pair<String, Credential>, String>()

        override fun read(accountKey: String, credential: Credential): String? =
            values[accountKey to credential]

        override fun write(
            accountKey: String,
            credential: Credential,
            value: String,
        ) {
            values[accountKey to credential] = value
        }

        override fun remove(accountKey: String, credential: Credential) {
            values.remove(accountKey to credential)
        }

        override fun clear() {
            values.clear()
        }
    }

    private object NoOpConversationStateStore : ConversationStateStore {
        override suspend fun read(
            ownerKey: String,
            streamUuid: String,
            topicUuid: String,
            draftStorageSlot: String?,
        ): PersistedConversationState? = null

        override suspend fun write(
            ownerKey: String,
            streamUuid: String,
            topicUuid: String,
            state: PersistedConversationState,
            draftStorageSlot: String?,
        ) = Unit

        override suspend fun list(ownerKey: String) =
            emptyList<PersistedConversationState>()

        override suspend fun remove(
            ownerKey: String,
            streamUuid: String,
            topicUuid: String,
            draftStorageSlot: String?,
        ) = Unit

        override suspend fun clearAccount(ownerKey: String) = Unit
    }

    private companion object {
        const val EXPECTED_HOST = "workspace.example.com"
        const val EXPECTED_ORIGIN = "https://$EXPECTED_HOST"
        const val REDIRECTED_ORIGIN = "https://redirected.example.com"
        const val SETTINGS_PATH = "/api/workspace/v1/messenger/server_settings/"
        const val REDIRECTED_PATH = "/canonical/server_settings"
        val JSON_HEADERS = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString(),
        )
        const val VALID_SETTINGS_JSON =
            """{"email_auth_enabled":true,"realm_name":"Example Workspace","meet_url":"https://meet.example.com"}"""
    }
}
