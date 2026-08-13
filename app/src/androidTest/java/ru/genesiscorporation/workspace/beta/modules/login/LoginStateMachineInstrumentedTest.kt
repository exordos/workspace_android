package ru.genesiscorporation.workspace.beta.modules.login

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

@RunWith(AndroidJUnit4::class)
class LoginStateMachineInstrumentedTest {

    @Test
    fun nicknameAndEmailCompleteRetrySafeOtpProjectFlow() = runBlocking {
        listOf("cassi", "cassi@example.invalid").forEachIndexed { index, login ->
            var loginAttempts = 0
            var projectRequests = 0
            var projectTokenRequests = 0
            withHarness("login-success-$index", handler = { request ->
                when (request.url.encodedPath) {
                    TOKEN_PATH -> {
                        val body = requestJson(request)
                        when (body.string("grant_type")) {
                            "login+password" -> {
                                loginAttempts += 1
                                assertEquals(login, body.string("login"))
                                assertEquals(SYNTHETIC_PASSWORD, body.string("password"))
                                assertEquals("openid email profile", body.string("scope"))
                                assertEquals("3600", body.string("ttl"))
                                assertEquals("2592000", body.string("refresh_ttl"))
                                assertNull(request.headers[HttpHeaders.Authorization])
                                when (request.headers["X-OTP"]) {
                                    null -> otpError("OTP required")
                                    WRONG_OTP -> otpError("Invalid OTP")
                                    VALID_OTP -> respond(
                                        content =
                                            """{"access_token":"$LOGIN_ACCESS_TOKEN","refresh_token":"$PENDING_REFRESH_TOKEN"}""",
                                        status = HttpStatusCode.OK,
                                        headers = JSON_HEADERS,
                                    )
                                    else -> error("Unexpected OTP")
                                }
                            }
                            "refresh_token" -> {
                                projectTokenRequests += 1
                                assertEquals(
                                    PENDING_REFRESH_TOKEN,
                                    body.string("refresh_token"),
                                )
                                assertEquals(
                                    "openid email profile project:$PROJECT_ID",
                                    body.string("scope"),
                                )
                                assertNull(body.string("password"))
                                assertNull(request.headers[HttpHeaders.Authorization])
                                assertNull(request.headers["X-OTP"])
                                respond(
                                    content =
                                        """{"access_token":"$PROJECT_ACCESS_TOKEN","refresh_token":"$FINAL_REFRESH_TOKEN"}""",
                                    status = HttpStatusCode.OK,
                                    headers = JSON_HEADERS,
                                )
                            }
                            else -> error("Unexpected grant type")
                        }
                    }
                    PROJECTS_PATH -> {
                        projectRequests += 1
                        assertEquals(
                            "Bearer $LOGIN_ACCESS_TOKEN",
                            request.headers[HttpHeaders.Authorization],
                        )
                        respond(
                            content = PROJECTS_RESPONSE,
                            status = HttpStatusCode.OK,
                            headers = JSON_HEADERS,
                        )
                    }
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            }) { harness ->
                harness.viewModel.onLoginChange(login)
                harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)

                harness.viewModel.onLoginClick()

                assertTrue(harness.viewModel.needsOtp.value)
                assertEquals(QueryState.Idle, harness.viewModel.queryState.value)
                assertEquals(SYNTHETIC_PASSWORD, harness.viewModel.passwordText.value)
                assertEquals("OTP", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)

                harness.viewModel.onOtpTextChange(WRONG_OTP)
                harness.viewModel.onLoginClick()

                assertTrue(harness.viewModel.needsOtp.value)
                assertEquals(
                    "Неверный код OTP. Проверьте код в приложении-аутентификаторе",
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("OTP", harness.savedState["login.phase"])

                harness.viewModel.onOtpTextChange(VALID_OTP)
                harness.viewModel.onLoginClick()

                assertFalse(harness.viewModel.needsOtp.value)
                assertTrue(harness.viewModel.needsProject.value)
                assertEquals("", harness.viewModel.passwordText.value)
                assertEquals("", harness.viewModel.otpText.value)
                assertEquals(PROJECT_ID, harness.viewModel.selectedProjectId.value)
                assertEquals("PROJECT", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)

                harness.viewModel.onProjectConfirm()

                assertEquals(QueryState.Success, harness.viewModel.queryState.value)
                assertTrue(harness.savedState.keys().isEmpty())
                assertEquals(3, loginAttempts)
                assertEquals(1, projectRequests)
                assertEquals(1, projectTokenRequests)
                val session = harness.repository.activeCredentialSnapshot()
                assertEquals(PROJECT_ID, session.projectId)
                assertEquals(USER_ID, session.userId)
                assertEquals(PROJECT_ACCESS_TOKEN, session.accessToken)
                assertEquals(FINAL_REFRESH_TOKEN, session.refreshToken)
                assertEquals(
                    login,
                    harness.repository.activeAccountFlow.first()?.login,
                )
                assertEquals(1, harness.repository.accountsFlow.first().size)
            }
        }
    }

    @Test
    fun invalidCredentialsStayOnCredentialStepWithoutPersistingPassword() =
        runBlocking {
            withHarness("login-invalid", handler = { request ->
                assertEquals(TOKEN_PATH, request.url.encodedPath)
                assertEquals(SYNTHETIC_PASSWORD, requestJson(request).string("password"))
                respond(
                    content =
                        """{"code":"invalid_credentials","message":"Invalid credentials"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                harness.viewModel.onLoginChange("cassi@example.invalid")
                harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)

                harness.viewModel.onLoginClick()

                assertFalse(harness.viewModel.needsOtp.value)
                assertEquals(
                    "Неверное имя пользователя или пароль",
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals(SYNTHETIC_PASSWORD, harness.viewModel.passwordText.value)
                assertEquals("CREDENTIALS", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                val pendingServer = harness.repository.activeCredentialSnapshot()
                assertNull(pendingServer.accountId)
                assertNull(pendingServer.accessToken)
                assertNull(pendingServer.refreshToken)
            }
        }

    @Test
    fun recreatedOtpAndProjectStepsFailClosedWithoutSecrets() = runBlocking {
        withHarness("login-recreate-otp", handler = { request ->
            assertEquals(TOKEN_PATH, request.url.encodedPath)
            otpError("OTP required")
        }) { harness ->
            harness.viewModel.onLoginChange("cassi@example.invalid")
            harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)
            harness.viewModel.onLoginClick()
            assertTrue(harness.viewModel.needsOtp.value)

            val firstRecreated = harness.recreateViewModel()
            firstRecreated.onLoginChange(firstRecreated.loginText.value)
            firstRecreated.onPasswordChange(firstRecreated.passwordText.value)
            assertTrue(firstRecreated.queryState.value is QueryState.Error)

            val recreated = harness.recreateViewModel()
            recreated.onLoginChange(recreated.loginText.value)
            recreated.onPasswordChange(recreated.passwordText.value)

            assertFalse(recreated.needsOtp.value)
            assertFalse(recreated.needsProject.value)
            assertEquals("cassi@example.invalid", recreated.loginText.value)
            assertEquals("", recreated.passwordText.value)
            assertEquals("", recreated.otpText.value)
            assertEquals(
                "Вход был прерван. Введите пароль ещё раз",
                (recreated.queryState.value as QueryState.Error).message,
            )
            assertEquals("OTP", harness.savedState["login.phase"])
            assertSavedStateContainsNoSecrets(harness.savedState)

            recreated.onPasswordChange(SYNTHETIC_PASSWORD)
            assertEquals(QueryState.Idle, recreated.queryState.value)
            assertEquals("CREDENTIALS", harness.savedState["login.phase"])
            assertSavedStateContainsNoSecrets(harness.savedState)
        }

        withHarness("login-recreate-project", handler = { request ->
            when (request.url.encodedPath) {
                TOKEN_PATH -> respond(
                    content =
                        """{"access_token":"$LOGIN_ACCESS_TOKEN","refresh_token":"$PENDING_REFRESH_TOKEN"}""",
                    status = HttpStatusCode.OK,
                    headers = JSON_HEADERS,
                )
                PROJECTS_PATH -> respond(
                    content = PROJECTS_RESPONSE,
                    status = HttpStatusCode.OK,
                    headers = JSON_HEADERS,
                )
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }) { harness ->
            harness.viewModel.onLoginChange("cassi")
            harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)
            harness.viewModel.onLoginClick()
            assertTrue(harness.viewModel.needsProject.value)

            val firstRecreated = harness.recreateViewModel()
            firstRecreated.onLoginChange(firstRecreated.loginText.value)
            firstRecreated.onPasswordChange(firstRecreated.passwordText.value)
            assertTrue(firstRecreated.queryState.value is QueryState.Error)

            val recreated = harness.recreateViewModel()
            recreated.onLoginChange(recreated.loginText.value)
            recreated.onPasswordChange(recreated.passwordText.value)

            assertFalse(recreated.needsOtp.value)
            assertFalse(recreated.needsProject.value)
            assertEquals("cassi", recreated.loginText.value)
            assertEquals("", recreated.passwordText.value)
            assertEquals(
                "Вход был прерван. Введите пароль ещё раз",
                (recreated.queryState.value as QueryState.Error).message,
            )
            assertEquals("PROJECT", harness.savedState["login.phase"])
            assertSavedStateContainsNoSecrets(harness.savedState)

            recreated.onPasswordChange(SYNTHETIC_PASSWORD)
            assertEquals(QueryState.Idle, recreated.queryState.value)
            assertEquals("CREDENTIALS", harness.savedState["login.phase"])
            assertSavedStateContainsNoSecrets(harness.savedState)
        }
    }

    @Test
    fun malformedMissingTimeoutAndServerFailuresStayRetryableWithoutSession() =
        runBlocking {
            val cases = listOf(
                ResponseFaultCase(
                    label = "malformed-json",
                    body = "{not-json",
                    status = HttpStatusCode.OK,
                    expectedMessage = GENERIC_LOGIN_ERROR,
                ),
                ResponseFaultCase(
                    label = "missing-access-token",
                    body = "{}",
                    status = HttpStatusCode.OK,
                    expectedMessage = GENERIC_LOGIN_ERROR,
                ),
                ResponseFaultCase(
                    label = "missing-refresh-token",
                    body = """{"access_token":"$LOGIN_ACCESS_TOKEN"}""",
                    status = HttpStatusCode.OK,
                    expectedMessage = "Сервер не вернул refresh token",
                ),
                ResponseFaultCase(
                    label = "server-503",
                    body = """{"code":"temporarily_unavailable"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    expectedMessage = GENERIC_LOGIN_ERROR,
                ),
            )

            cases.forEach { case ->
                withHarness("login-fault-${case.label}", handler = { request ->
                    assertEquals(TOKEN_PATH, request.url.encodedPath)
                    respond(
                        content = case.body,
                        status = case.status,
                        headers = JSON_HEADERS,
                    )
                }) { harness ->
                    submitSyntheticCredentials(harness)

                    assertEquals(
                        case.expectedMessage,
                        (harness.viewModel.queryState.value as QueryState.Error).message,
                    )
                    assertFalse(harness.viewModel.needsOtp.value)
                    assertFalse(harness.viewModel.needsProject.value)
                    assertEquals(SYNTHETIC_PASSWORD, harness.viewModel.passwordText.value)
                    assertEquals("CREDENTIALS", harness.savedState["login.phase"])
                    assertSavedStateContainsNoSecrets(harness.savedState)
                    assertNoSession(harness)
                }
            }

            withHarness(
                label = "login-fault-timeout",
                timeoutMillis = 50,
                handler = { request ->
                    assertEquals(TOKEN_PATH, request.url.encodedPath)
                    delay(500)
                    respond(
                        content = """{"access_token":"$LOGIN_ACCESS_TOKEN"}""",
                        status = HttpStatusCode.OK,
                        headers = JSON_HEADERS,
                    )
                },
            ) { harness ->
                submitSyntheticCredentials(harness)

                assertEquals(
                    "Сервер не ответил вовремя. Попробуйте ещё раз",
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("CREDENTIALS", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)
            }
        }

    @Test
    fun otpAndProjectFaultsPreserveOnlyRetryableInMemoryState() = runBlocking {
        listOf(
            ResponseFaultCase(
                label = "otp-missing-refresh",
                body = """{"access_token":"$LOGIN_ACCESS_TOKEN"}""",
                status = HttpStatusCode.OK,
                expectedMessage = "Сервер не вернул refresh token",
            ),
            ResponseFaultCase(
                label = "otp-malformed-token",
                body = "{not-json",
                status = HttpStatusCode.OK,
                expectedMessage = GENERIC_LOGIN_ERROR,
            ),
            ResponseFaultCase(
                label = "otp-server-503",
                body = """{"message":"temporary backend detail"}""",
                status = HttpStatusCode.ServiceUnavailable,
                expectedMessage = GENERIC_LOGIN_ERROR,
            ),
        ).forEach { case ->
            var attempts = 0
            withHarness("login-fault-${case.label}", handler = { request ->
                assertEquals(TOKEN_PATH, request.url.encodedPath)
                attempts += 1
                if (attempts == 1) {
                    otpError("OTP required")
                } else {
                    assertEquals(VALID_OTP, request.headers["X-OTP"])
                    respond(
                        content = case.body,
                        status = case.status,
                        headers = JSON_HEADERS,
                    )
                }
            }) { harness ->
                harness.viewModel.onLoginChange("cassi@example.invalid")
                harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)
                harness.viewModel.onLoginClick()
                harness.viewModel.onOtpTextChange(VALID_OTP)
                harness.viewModel.onLoginClick()

                assertEquals(2, attempts)
                assertTrue(harness.viewModel.needsOtp.value)
                assertFalse(harness.viewModel.needsProject.value)
                assertEquals(
                    case.expectedMessage,
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("OTP", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)
            }
        }

        listOf(
            ResponseFaultCase(
                label = "malformed-projects",
                body = "{}",
                status = HttpStatusCode.OK,
                expectedMessage = "Сервер вернул некорректный список проектов",
            ),
            ResponseFaultCase(
                label = "projects-503",
                body = """{"message":"temporary backend detail"}""",
                status = HttpStatusCode.ServiceUnavailable,
                expectedMessage = "Не удалось загрузить доступные проекты",
            ),
        ).forEach { case ->
            withHarness("login-fault-${case.label}", handler = { request ->
                when (request.url.encodedPath) {
                    TOKEN_PATH -> when (request.headers["X-OTP"]) {
                        null -> otpError("OTP required")
                        VALID_OTP -> respond(
                            content =
                                """{"access_token":"$LOGIN_ACCESS_TOKEN","refresh_token":"$PENDING_REFRESH_TOKEN"}""",
                            status = HttpStatusCode.OK,
                            headers = JSON_HEADERS,
                        )
                        else -> error("Unexpected OTP")
                    }
                    PROJECTS_PATH -> respond(
                        content = case.body,
                        status = case.status,
                        headers = JSON_HEADERS,
                    )
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            }) { harness ->
                harness.viewModel.onLoginChange("cassi")
                harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)
                harness.viewModel.onLoginClick()
                harness.viewModel.onOtpTextChange(VALID_OTP)
                harness.viewModel.onLoginClick()

                assertTrue(harness.viewModel.needsOtp.value)
                assertFalse(harness.viewModel.needsProject.value)
                assertEquals(
                    case.expectedMessage,
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("OTP", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)
            }
        }
    }

    @Test
    fun delayedLoginCompletionKeepsLoadingCheckpointUntilResultArrives() =
        runBlocking {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            withHarness("login-delayed-completion", handler = { request ->
                assertEquals(TOKEN_PATH, request.url.encodedPath)
                requestStarted.complete(Unit)
                releaseResponse.await()
                respond(
                    content = """{"code":"temporarily_unavailable"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = JSON_HEADERS,
                )
            }) { harness ->
                harness.viewModel.onLoginChange("cassi")
                harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)
                val request = async { harness.viewModel.onLoginClick() }
                requestStarted.await()

                assertEquals(QueryState.Loading, harness.viewModel.queryState.value)
                assertEquals("AUTHENTICATING", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)

                releaseResponse.complete(Unit)
                request.await()

                assertEquals(
                    GENERIC_LOGIN_ERROR,
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("CREDENTIALS", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)
            }
        }

    @Test
    fun projectTokenFaultsKeepPickerRetryableWithoutPersistingSession() =
        runBlocking {
            listOf(
                ResponseFaultCase(
                    label = "project-token-malformed",
                    body = "{not-json",
                    status = HttpStatusCode.OK,
                    expectedMessage = "Не удалось открыть выбранный проект",
                ),
                ResponseFaultCase(
                    label = "project-token-missing-access",
                    body = "{}",
                    status = HttpStatusCode.OK,
                    expectedMessage = "Не удалось открыть выбранный проект",
                ),
                ResponseFaultCase(
                    label = "project-token-503",
                    body = """{"message":"temporary backend detail"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    expectedMessage = "Не удалось открыть выбранный проект",
                ),
            ).forEach { case ->
                withHarness("login-fault-${case.label}", handler = { request ->
                    when (request.url.encodedPath) {
                        TOKEN_PATH -> when (requestJson(request).string("grant_type")) {
                            "login+password" -> respondLoginSuccess()
                            "refresh_token" -> respond(
                                content = case.body,
                                status = case.status,
                                headers = JSON_HEADERS,
                            )
                            else -> error("Unexpected grant type")
                        }
                        PROJECTS_PATH -> respondProjectsSuccess()
                        else -> error("Unexpected path ${request.url.encodedPath}")
                    }
                }) { harness ->
                    submitSyntheticCredentials(harness)
                    assertTrue(harness.viewModel.needsProject.value)

                    harness.viewModel.onProjectConfirm()

                    assertTrue(harness.viewModel.needsProject.value)
                    assertEquals(PROJECT_ID, harness.viewModel.selectedProjectId.value)
                    assertEquals(
                        case.expectedMessage,
                        (harness.viewModel.queryState.value as QueryState.Error).message,
                    )
                    assertEquals("PROJECT", harness.savedState["login.phase"])
                    assertSavedStateContainsNoSecrets(harness.savedState)
                    assertNoSession(harness)
                }
            }

            withHarness(
                label = "login-fault-project-token-timeout",
                timeoutMillis = 50,
                handler = { request ->
                    when (request.url.encodedPath) {
                        TOKEN_PATH -> when (requestJson(request).string("grant_type")) {
                            "login+password" -> respondLoginSuccess()
                            "refresh_token" -> {
                                delay(500)
                                respond(
                                    content = "{}",
                                    status = HttpStatusCode.OK,
                                    headers = JSON_HEADERS,
                                )
                            }
                            else -> error("Unexpected grant type")
                        }
                        PROJECTS_PATH -> respondProjectsSuccess()
                        else -> error("Unexpected path ${request.url.encodedPath}")
                    }
                },
            ) { harness ->
                submitSyntheticCredentials(harness)
                harness.viewModel.onProjectConfirm()

                assertTrue(harness.viewModel.needsProject.value)
                assertEquals(
                    "Не удалось открыть выбранный проект",
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("PROJECT", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)
            }
        }

    @Test
    fun delayedProjectTokenCompletionKeepsPickerAndCheckpointStable() =
        runBlocking {
            val requestStarted = CompletableDeferred<Unit>()
            val releaseResponse = CompletableDeferred<Unit>()
            withHarness("login-delayed-project-token", handler = { request ->
                when (request.url.encodedPath) {
                    TOKEN_PATH -> when (requestJson(request).string("grant_type")) {
                        "login+password" -> respondLoginSuccess()
                        "refresh_token" -> {
                            requestStarted.complete(Unit)
                            releaseResponse.await()
                            respond(
                                content = """{"message":"temporary backend detail"}""",
                                status = HttpStatusCode.ServiceUnavailable,
                                headers = JSON_HEADERS,
                            )
                        }
                        else -> error("Unexpected grant type")
                    }
                    PROJECTS_PATH -> respondProjectsSuccess()
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            }) { harness ->
                submitSyntheticCredentials(harness)
                val request = async { harness.viewModel.onProjectConfirm() }
                requestStarted.await()

                assertEquals(QueryState.Loading, harness.viewModel.queryState.value)
                assertTrue(harness.viewModel.needsProject.value)
                assertEquals(PROJECT_ID, harness.viewModel.selectedProjectId.value)
                assertEquals("PROJECT", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)

                releaseResponse.complete(Unit)
                request.await()

                assertTrue(harness.viewModel.needsProject.value)
                assertEquals(
                    "Не удалось открыть выбранный проект",
                    (harness.viewModel.queryState.value as QueryState.Error).message,
                )
                assertEquals("PROJECT", harness.savedState["login.phase"])
                assertSavedStateContainsNoSecrets(harness.savedState)
                assertNoSession(harness)
            }
        }

    private suspend fun withHarness(
        label: String,
        timeoutMillis: Long? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
        block: suspend (LoginHarness) -> Unit,
    ) {
        val baseContext =
            ApplicationProvider.getApplicationContext<Context>()
                .createDeviceProtectedStorageContext()
        val context = IsolatedAndroidTestContext(baseContext, label)
        val credentials = InMemoryCredentialStore()
        val repository = ApiKeyRepository(
            context = context,
            credentialStore = credentials,
        )
        val httpClient = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(REQUEST_JSON)
            }
            if (timeoutMillis != null) {
                install(HttpTimeout) {
                    requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
            }
        }

        try {
            repository.addBaseUrl(EXPECTED_ORIGIN)
            val userViewModel = UserViewModel(
                repo = repository,
                conversationStateStore = NoOpConversationStateStore,
                uiPreferencesRepository = WorkspaceUiPreferencesRepository(context),
            )
            val client = WorkspaceAPIClient(
                client = httpClient,
                userViewModel = userViewModel,
                sessionCookieStore = SessionCookieStore(),
            )
            val savedState = SavedStateHandle()
            block(
                LoginHarness(
                    client = client,
                    userViewModel = userViewModel,
                    repository = repository,
                    savedState = savedState,
                    viewModel = LoginViewModel(
                        client,
                        userViewModel,
                        LoginProcessState(savedState),
                    ),
                ),
            )
        } finally {
            httpClient.close()
            repository.clearAll()
            context.cleanUp()
        }
    }

    private fun MockRequestHandleScope.otpError(message: String) = respond(
        content = """{"code":"OTPInvalidCodeError","message":"$message"}""",
        status = HttpStatusCode.Unauthorized,
        headers = JSON_HEADERS,
    )

    private fun MockRequestHandleScope.respondLoginSuccess() = respond(
        content =
            """{"access_token":"$LOGIN_ACCESS_TOKEN","refresh_token":"$PENDING_REFRESH_TOKEN"}""",
        status = HttpStatusCode.OK,
        headers = JSON_HEADERS,
    )

    private fun MockRequestHandleScope.respondProjectsSuccess() = respond(
        content = PROJECTS_RESPONSE,
        status = HttpStatusCode.OK,
        headers = JSON_HEADERS,
    )

    private fun requestJson(request: HttpRequestData): JsonObject {
        val text = (request.body as TextContent).text
        return REQUEST_JSON.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.content

    private suspend fun submitSyntheticCredentials(harness: LoginHarness) {
        harness.viewModel.onLoginChange("cassi@example.invalid")
        harness.viewModel.onPasswordChange(SYNTHETIC_PASSWORD)
        harness.viewModel.onLoginClick()
    }

    private suspend fun assertNoSession(harness: LoginHarness) {
        val session = harness.repository.activeCredentialSnapshot()
        assertNull(session.accountId)
        assertNull(session.accessToken)
        assertNull(session.refreshToken)
    }

    private fun assertSavedStateContainsNoSecrets(savedState: SavedStateHandle) {
        assertEquals(
            setOf("login.safe_login", "login.phase"),
            savedState.keys(),
        )
        val values = savedState.keys().mapNotNull { key -> savedState.get<String>(key) }
        listOf(
            SYNTHETIC_PASSWORD,
            WRONG_OTP,
            VALID_OTP,
            LOGIN_ACCESS_TOKEN,
            PROJECT_ACCESS_TOKEN,
            PENDING_REFRESH_TOKEN,
            FINAL_REFRESH_TOKEN,
        ).forEach { secret ->
            assertFalse(
                "Saved state contains a secret",
                values.any { value -> secret in value },
            )
        }
    }

    private data class LoginHarness(
        val client: WorkspaceAPIClient,
        val userViewModel: UserViewModel,
        val repository: ApiKeyRepository,
        val savedState: SavedStateHandle,
        val viewModel: LoginViewModel,
    ) {
        fun recreateViewModel() = LoginViewModel(
            client,
            userViewModel,
            LoginProcessState(savedState),
        )
    }

    private data class ResponseFaultCase(
        val label: String,
        val body: String,
        val status: HttpStatusCode,
        val expectedMessage: String,
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
        const val TOKEN_PATH =
            "/api/core/v1/iam/clients/default/actions/get_token/invoke"
        const val PROJECTS_PATH = "/api/core/v1/iam/projects/"
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val PROJECT_ID = "22222222-2222-4222-8222-222222222222"
        const val SYNTHETIC_PASSWORD = "synthetic-password"
        const val WRONG_OTP = "000000"
        const val VALID_OTP = "123456"
        const val PENDING_REFRESH_TOKEN = "pending-refresh-token"
        const val FINAL_REFRESH_TOKEN = "final-refresh-token"
        const val GENERIC_LOGIN_ERROR = "Не удалось выполнить вход. Попробуйте ещё раз"
        val LOGIN_ACCESS_TOKEN = jwt(USER_ID)
        val PROJECT_ACCESS_TOKEN = jwt(USER_ID, PROJECT_ID)
        val JSON_HEADERS = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString(),
        )
        val REQUEST_JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val PROJECTS_RESPONSE =
            """{"items":[{"uuid":"$PROJECT_ID","name":"Sandbox Project","status":"active","organization":{"name":"Workspace"}}]}"""

        fun jwt(userId: String, projectId: String? = null): String {
            val header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{}".toByteArray(StandardCharsets.UTF_8),
            )
            val projectClaim = projectId?.let { ",\"project_id\":\"$it\"" }.orEmpty()
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"sub":"$userId"$projectClaim}"""
                    .toByteArray(StandardCharsets.UTF_8),
            )
            return "$header.$payload.signature"
        }
    }
}
