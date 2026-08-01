package ru.genesiscorporation.workspace.beta.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ActiveCredentialSnapshot
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.Credential
import ru.genesiscorporation.workspace.beta.data.CredentialStore
import ru.genesiscorporation.workspace.beta.data.IsolatedAndroidTestContext
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferencesRepository

@RunWith(AndroidJUnit4::class)
class RefreshTokenEndpointInstrumentedTest {
    @Test
    fun terminalEndpointResponsesRemoveOnlyTheRejectedOwner() = runBlocking {
        val cases = listOf(
            TerminalCase(
                status = HttpStatusCode.BadRequest,
                body = """{"error":"invalid_grant"}""",
                expectedCode = "invalid_grant",
            ),
            TerminalCase(
                status = HttpStatusCode.Unauthorized,
                body = """{"message":"expired"}""",
                expectedCode = "401",
            ),
            TerminalCase(
                status = HttpStatusCode.Forbidden,
                body = """{"message":"forbidden"}""",
                expectedCode = "403",
            ),
        )

        cases.forEachIndexed { index, case ->
            withMockHarness("refresh-terminal-$index", handler = { request ->
                assertRefreshRequest(request)
                respond(
                    content = case.body,
                    status = case.status,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                val result = harness.client.refreshToken(
                    failedSession = harness.failedSession,
                    failedAccessToken = OLD_ACCESS_TOKEN,
                )

                assertTrue(result is ApiResult.Error)
                val error = (result as ApiResult.Error).error
                assertEquals(case.expectedCode, error.code)
                assertTrue(shouldRemoveAccountAfterRefresh(error))
                assertNull(harness.repository.activeAccountIdFlow.first())
                assertNull(
                    harness.credentials.read(
                        harness.ownerKey,
                        Credential.ACCESS_TOKEN,
                    ),
                )
                assertNull(
                    harness.credentials.read(
                        harness.ownerKey,
                        Credential.REFRESH_TOKEN,
                    ),
                )
                assertEquals(listOf(harness.ownerKey), harness.clearedOwners)
            }
        }
    }

    @Test
    fun transientAndMalformedEndpointFailuresKeepTheOwnerRetryable() =
        runBlocking {
            withMockHarness("refresh-rate-limited", handler = { request ->
                assertRefreshRequest(request)
                respond(
                    content = """{"message":"slow down"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                assertRetryableFailure(
                    harness = harness,
                    expectedKind = ApiErrorKind.RATE_LIMITED,
                    expectedCode = "429",
                )
            }
            withMockHarness("refresh-server-failure", handler = { request ->
                assertRefreshRequest(request)
                respond(
                    content = """{"message":"temporarily unavailable"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                assertRetryableFailure(
                    harness = harness,
                    expectedKind = ApiErrorKind.SERVER,
                    expectedCode = "503",
                )
            }
            withMockHarness("refresh-network-failure", handler = { request ->
                assertRefreshRequest(request)
                throw IOException("injected token-endpoint network failure")
            }) { harness ->
                assertRetryableFailure(
                    harness = harness,
                    expectedKind = ApiErrorKind.NETWORK,
                    expectedCode = "NETWORK",
                )
            }
            withMockHarness("refresh-malformed-response", handler = { request ->
                assertRefreshRequest(request)
                respond(
                    content = "{not-json",
                    status = HttpStatusCode.OK,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                assertRetryableFailure(
                    harness = harness,
                    expectedKind = ApiErrorKind.MALFORMED_RESPONSE,
                    expectedCode = "MALFORMED_RESPONSE",
                )
            }
            withMockHarness("refresh-identity-mismatch", handler = { request ->
                assertRefreshRequest(request)
                respond(
                    content = """{"access_token":"${jwt(OTHER_USER_ID, PROJECT_ID)}","refresh_token":"$ROTATED_REFRESH_TOKEN"}""",
                    status = HttpStatusCode.OK,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                assertRetryableFailure(
                    harness = harness,
                    expectedKind = ApiErrorKind.MALFORMED_RESPONSE,
                    expectedCode = "TOKEN_IDENTITY_MISMATCH",
                )
            }
        }

    @Test
    fun transientFailureKeepsOwnerAndLaterSuccessRotatesCredentials() =
        runBlocking {
            var requestCount = 0
            withMockHarness("refresh-transient", handler = { request ->
                assertRefreshRequest(request)
                requestCount += 1
                if (requestCount == 1) {
                    respond(
                        content = """{"message":"temporarily unavailable"}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = JSON_HEADERS,
                    )
                } else {
                    successfulRefreshResponse()
                }
            }) { harness ->
                val first = harness.client.refreshToken(
                    failedSession = harness.failedSession,
                    failedAccessToken = OLD_ACCESS_TOKEN,
                )

                assertTrue(first is ApiResult.Error)
                val firstError = (first as ApiResult.Error).error
                assertEquals(ApiErrorKind.SERVER, firstError.kind)
                assertTrue(shouldBackoffRefresh(firstError))
                assertEquals(harness.ownerKey, harness.repository.activeAccountIdFlow.first())
                assertEquals(
                    OLD_REFRESH_TOKEN,
                    harness.credentials.read(
                        harness.ownerKey,
                        Credential.REFRESH_TOKEN,
                    ),
                )

                delay(1_050L)
                val second = harness.client.refreshToken(
                    failedSession = harness.failedSession,
                    failedAccessToken = OLD_ACCESS_TOKEN,
                )

                assertTrue(second is ApiResult.Success)
                assertEquals(ROTATED_ACCESS_TOKEN, (second as ApiResult.Success).value)
                assertEquals(2, requestCount)
                val rotated = harness.repository.activeCredentialSnapshot()
                assertEquals(ROTATED_ACCESS_TOKEN, rotated.accessToken)
                assertEquals(ROTATED_REFRESH_TOKEN, rotated.refreshToken)
                assertTrue(harness.clearedOwners.isEmpty())

                val alreadyRotated = harness.client.refreshToken(
                    failedSession = harness.failedSession,
                    failedAccessToken = OLD_ACCESS_TOKEN,
                )
                assertTrue(alreadyRotated is ApiResult.Success)
                assertEquals(
                    ROTATED_ACCESS_TOKEN,
                    (alreadyRotated as ApiResult.Success).value,
                )
                assertEquals(2, requestCount)
            }
        }

    @Test
    fun concurrentRefreshesUseOneEndpointCallAndOneRotation() = runBlocking {
        var requestCount = 0
        withMockHarness("refresh-concurrent", handler = { request ->
            assertRefreshRequest(request)
            requestCount += 1
            delay(50L)
            successfulRefreshResponse()
        }) { harness ->
            val results = coroutineScope {
                List(4) {
                    async {
                        harness.client.refreshToken(
                            failedSession = harness.failedSession,
                            failedAccessToken = OLD_ACCESS_TOKEN,
                        )
                    }
                }.awaitAll()
            }

            assertEquals(1, requestCount)
            results.forEach { result ->
                assertTrue(result is ApiResult.Success)
                assertEquals(
                    ROTATED_ACCESS_TOKEN,
                    (result as ApiResult.Success).value,
                )
            }
            val rotated = harness.repository.activeCredentialSnapshot()
            assertEquals(ROTATED_ACCESS_TOKEN, rotated.accessToken)
            assertEquals(ROTATED_REFRESH_TOKEN, rotated.refreshToken)
        }
    }

    @Test
    fun invalidGeneratedRefreshTokenFailsClosedAgainstLiveEndpoint() =
        runBlocking {
            assumeTrue(
                "Run with refreshRemoteE2e=true on an explicitly authorized device",
                InstrumentationRegistry.getArguments()
                    .getString("refreshRemoteE2e") == "true",
            )
            val baseContext =
                ApplicationProvider.getApplicationContext<Context>()
                    .createDeviceProtectedStorageContext()
            val arguments = InstrumentationRegistry.getArguments()
            val baseUrl = arguments.getString("refreshRemoteBaseUrl")
            val projectId = arguments.getString("refreshRemoteProjectId")
            assumeTrue(!baseUrl.isNullOrBlank())
            assumeTrue(!projectId.isNullOrBlank())

            val context = IsolatedAndroidTestContext(
                baseContext,
                "refresh-live-invalid",
            )
            val credentials = InMemoryCredentialStore()
            val clearedOwners = mutableListOf<String>()
            val repository = ApiKeyRepository(
                context = context,
                credentialStore = credentials,
                clearAccountLocalData = clearedOwners::add,
            )
            val httpClient = HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000L
                    connectTimeoutMillis = 10_000L
                    socketTimeoutMillis = 20_000L
                }
                install(ContentNegotiation) {
                    json(REQUEST_JSON)
                }
            }

            try {
                val fakeAccessToken = jwt(USER_ID, projectId!!)
                repository.addBaseUrl(baseUrl!!)
                repository.saveSessionCredentials(
                    projectId = projectId,
                    projectName = "Isolated refresh endpoint acceptance",
                    organizationName = "Isolated refresh endpoint acceptance",
                    userId = USER_ID,
                    login = "cassi-refresh-endpoint-e2e",
                    accessToken = fakeAccessToken,
                    refreshToken = "cassi-invalid-refresh-${UUID.randomUUID()}",
                )
                val isolatedSession = repository.activeCredentialSnapshot()
                val isolatedOwner = checkNotNull(isolatedSession.ownerKey)
                val client = WorkspaceAPIClient(
                    client = httpClient,
                    userViewModel = createUserViewModel(context, repository),
                    sessionCookieStore = SessionCookieStore(),
                )

                val result = client.refreshToken(
                    failedSession = isolatedSession,
                    failedAccessToken = fakeAccessToken,
                )

                assertTrue(result is ApiResult.Error)
                val error = (result as ApiResult.Error).error
                assertTrue(error.code, shouldRemoveAccountAfterRefresh(error))
                assertNull(repository.activeAccountIdFlow.first())
                assertEquals(listOf(isolatedOwner), clearedOwners)
            } finally {
                httpClient.close()
                repository.clearAll()
                context.cleanUp()
            }
        }

    private suspend fun withMockHarness(
        label: String,
        handler: suspend MockRequestHandleScope.(HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
        block: suspend (RefreshHarness) -> Unit,
    ) {
        val baseContext =
            ApplicationProvider.getApplicationContext<Context>()
                .createDeviceProtectedStorageContext()
        val context = IsolatedAndroidTestContext(baseContext, label)
        val credentials = InMemoryCredentialStore()
        val clearedOwners = mutableListOf<String>()
        val repository = ApiKeyRepository(
            context = context,
            credentialStore = credentials,
            clearAccountLocalData = clearedOwners::add,
        )
        val httpClient = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(REQUEST_JSON)
            }
        }

        try {
            repository.addBaseUrl(EXPECTED_ORIGIN)
            repository.saveSessionCredentials(
                projectId = PROJECT_ID,
                projectName = "Project",
                organizationName = "Workspace",
                userId = USER_ID,
                login = "cassi",
                accessToken = OLD_ACCESS_TOKEN,
                refreshToken = OLD_REFRESH_TOKEN,
            )
            val failedSession = repository.activeCredentialSnapshot()
            val ownerKey = checkNotNull(failedSession.ownerKey)
            val client = WorkspaceAPIClient(
                client = httpClient,
                userViewModel = createUserViewModel(context, repository),
                sessionCookieStore = SessionCookieStore(),
            )
            block(
                RefreshHarness(
                    client = client,
                    repository = repository,
                    credentials = credentials,
                    failedSession = failedSession,
                    ownerKey = ownerKey,
                    clearedOwners = clearedOwners,
                ),
            )
        } finally {
            httpClient.close()
            repository.clearAll()
            context.cleanUp()
        }
    }

    private fun createUserViewModel(
        context: Context,
        repository: ApiKeyRepository,
    ) = UserViewModel(
        repo = repository,
        conversationStateStore = NoOpConversationStateStore,
        uiPreferencesRepository = WorkspaceUiPreferencesRepository(context),
    )

    private suspend fun assertRetryableFailure(
        harness: RefreshHarness,
        expectedKind: ApiErrorKind,
        expectedCode: String,
    ) {
        val result = harness.client.refreshToken(
            failedSession = harness.failedSession,
            failedAccessToken = OLD_ACCESS_TOKEN,
        )

        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).error
        assertEquals(expectedKind, error.kind)
        assertEquals(expectedCode, error.code)
        assertTrue(shouldBackoffRefresh(error))
        assertEquals(harness.ownerKey, harness.repository.activeAccountIdFlow.first())
        assertEquals(
            OLD_ACCESS_TOKEN,
            harness.credentials.read(harness.ownerKey, Credential.ACCESS_TOKEN),
        )
        assertEquals(
            OLD_REFRESH_TOKEN,
            harness.credentials.read(harness.ownerKey, Credential.REFRESH_TOKEN),
        )
        assertTrue(harness.clearedOwners.isEmpty())
    }

    private fun MockRequestHandleScope.successfulRefreshResponse() =
        respond(
            content = """{"access_token":"$ROTATED_ACCESS_TOKEN","refresh_token":"$ROTATED_REFRESH_TOKEN"}""",
            status = HttpStatusCode.OK,
            headers = JSON_HEADERS,
        )

    private fun assertRefreshRequest(request: HttpRequestData) {
        assertEquals(REFRESH_PATH, request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    private data class TerminalCase(
        val status: HttpStatusCode,
        val body: String,
        val expectedCode: String,
    )

    private data class RefreshHarness(
        val client: WorkspaceAPIClient,
        val repository: ApiKeyRepository,
        val credentials: InMemoryCredentialStore,
        val failedSession: ActiveCredentialSnapshot,
        val ownerKey: String,
        val clearedOwners: MutableList<String>,
    )

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
        const val EXPECTED_ORIGIN = "https://workspace.example.com"
        const val REFRESH_PATH =
            "/api/core/v1/iam/clients/default/actions/get_token/invoke"
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val OTHER_USER_ID = "33333333-3333-4333-8333-333333333333"
        const val PROJECT_ID = "22222222-2222-4222-8222-222222222222"
        const val OLD_ACCESS_TOKEN = "old-access-token"
        const val OLD_REFRESH_TOKEN = "old-refresh-token"
        const val ROTATED_REFRESH_TOKEN = "rotated-refresh-token"
        val ROTATED_ACCESS_TOKEN = jwt(USER_ID, PROJECT_ID)
        val JSON_HEADERS = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString(),
        )
        val REQUEST_JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun jwt(userId: String, projectId: String): String {
            val header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{}".toByteArray(StandardCharsets.UTF_8),
            )
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"sub":"$userId","project_id":"$projectId"}"""
                    .toByteArray(StandardCharsets.UTF_8),
            )
            return "$header.$payload.signature"
        }
    }
}
