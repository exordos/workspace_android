package ru.genesiscorporation.workspace.beta.modules.login

import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import ru.genesiscorporation.workspace.beta.fixtures.WorkspaceTestAccount
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.otp.OtpScreen
import ru.genesiscorporation.workspace.beta.modules.otp.OtpViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Check the actual system Toast, including the HTTP -> ViewModel -> screen path. */
@RunWith(AndroidJUnit4::class)
class AuthenticationErrorUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginToastDisplaysTheStructuredServerMessage() = checkLoginError(
        """{"msg":"Synthetic login error","code":"LOGIN_FAILED"}""", "Synthetic login error",
    )

    @Test
    fun loginToastUsesFallbackForEmptyBody() = checkLoginError("", fallback)

    @Test
    fun loginToastUsesFallbackForWhitespaceBody() = checkLoginError(" \t\r\n", fallback)

    @Test
    fun loginToastUsesFallbackForEmptyStructuredMessage() = checkLoginError(
        """{"msg":"","code":"LOGIN_FAILED"}""", fallback,
    )

    @Test
    fun loginToastUsesFallbackForWhitespaceStructuredMessage() = checkLoginError(
        """{"msg":"  \t\n","code":"LOGIN_FAILED"}""", fallback,
    )

    @Test
    fun loginDisplaysEveryRepeatedFallbackError() = checkLoginError("", fallback, attempts = 2)

    private fun checkLoginError(body: String, message: String, attempts: Int = 1): Unit = runBlocking {
        val request = AtomicReference<LoopbackWorkspaceServer.Request>()
        val requests = AtomicInteger()
        val server = errorPeer(body, 400, request, requests)
        val owner = WorkspaceTestAccount.create(server.url)
        val mounted = mutableStateOf(true)
        val model = withContext(Dispatchers.Main) { LoginViewModel(owner.api, owner.user, false) }
        try {
            model.onLoginChange("cassi-fixture")
            model.onPasswordChange("synthetic-password")
            compose.setContent {
                if (mounted.value) WokspaceTheme { LoginScreen(model, rememberNavController()) }
            }
            val button = compose.onNodeWithText("Войти").performScrollTo()

            repeat(attempts) { attempt ->
                if (attempts > 1) compose.mainClock.autoAdvance = false
                assertToast(message, { requests.get() > attempt && model.queryState.value !is QueryState.Loading }) {
                    button.performClick()
                    if (attempts > 1) {
                        // Fast HTTP replies can finish before Compose renders Loading.
                        compose.waitUntil(5_000) { requests.get() > attempt && model.queryState.value is QueryState.Error }
                        compose.mainClock.advanceTimeBy(32)
                        compose.mainClock.autoAdvance = true
                    }
                }
            }

            assertEquals(QueryState.Idle, model.queryState.value)
            assertNull(requireNotNull(request.get()).headers["x-otp"])
            assertNull(requireNotNull(request.get()).headers["authorization"])
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
            model.viewModelScope.cancel()
            owner.close()
            server.close()
        }
    }

    @Test
    fun otpToastDisplaysTheStructuredServerMessage() = checkOtpError(
        """{"msg":"Synthetic OTP error","code":"OTP_REJECTED"}""", "Synthetic OTP error",
    )

    @Test
    fun otpToastUsesFallbackForEmptyBody() = checkOtpError("", fallback)

    @Test
    fun otpToastUsesFallbackForWhitespaceBody() = checkOtpError(" \t\r\n", fallback)

    @Test
    fun otpToastUsesFallbackForEmptyStructuredMessage() = checkOtpError(
        """{"msg":"","code":"OTP_REJECTED"}""", fallback,
    )

    @Test
    fun otpToastUsesFallbackForWhitespaceStructuredMessage() = checkOtpError(
        """{"msg":"  \t\n","code":"OTP_REJECTED"}""", fallback,
    )

    @Test
    fun otpDisplaysEveryRepeatedFallbackError() = checkOtpError("", fallback, attempts = 2)

    private fun checkOtpError(body: String, message: String, attempts: Int = 1): Unit = runBlocking {
        val request = AtomicReference<LoopbackWorkspaceServer.Request>()
        val requests = AtomicInteger()
        val server = errorPeer(body, 401, request, requests)
        val owner = WorkspaceTestAccount.create(server.url)
        val mounted = mutableStateOf(true)
        val model = withContext(Dispatchers.Main) {
            OtpViewModel(owner.api, owner.user, "cassi-fixture", "synthetic-password", false)
        }
        try {
            compose.setContent {
                if (mounted.value) WokspaceTheme { OtpScreen(model, rememberNavController()) }
            }

            repeat(attempts) { attempt ->
                if (attempts > 1) compose.mainClock.autoAdvance = false
                assertToast(message, { requests.get() > attempt && model.queryState.value !is QueryState.Loading }) {
                    val field = compose.onNodeWithContentDescription("6-значный код")
                    field.performTextReplacement("")
                    field.performTextReplacement("123456")
                    if (attempts > 1) {
                        compose.waitUntil(5_000) { requests.get() > attempt && model.queryState.value is QueryState.Error }
                        compose.mainClock.advanceTimeBy(32)
                        compose.mainClock.autoAdvance = true
                    }
                }
            }

            assertEquals(QueryState.Idle, model.queryState.value)
            assertEquals("123456", requireNotNull(request.get()).headers["x-otp"])
            assertNull(requireNotNull(request.get()).headers["authorization"])
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
            model.viewModelScope.cancel()
            owner.close()
            server.close()
        }
    }

    private fun errorPeer(
        body: String,
        status: Int,
        request: AtomicReference<LoopbackWorkspaceServer.Request>,
        requests: AtomicInteger,
    ) = LoopbackWorkspaceServer { incoming ->
        if (incoming.path.endsWith("/get_token/invoke")) {
            request.set(incoming)
            requests.incrementAndGet()
            LoopbackWorkspaceServer.Response(body, status)
        } else LoopbackWorkspaceServer.Response("[]")
    }

    private val fallback = "Произошла ошибка, повторите позднее"

    private fun assertToast(expected: String, errorReceived: () -> Boolean, action: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val event = instrumentation.uiAutomation.executeAndWaitForEvent(
            {
                action()
                // Advance Compose's test clock while the real HTTP request completes.
                compose.waitUntil(5_000, errorReceived)
                compose.waitForIdle()
            },
            { it.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                it.className == Toast::class.java.name &&
                it.packageName == instrumentation.targetContext.packageName },
            5_000,
        )
        try {
            assertTrue("Expected '$expected' in Toast, got ${event.text}", event.text.any { it.toString() == expected })
        } finally {
            event.recycle()
        }
    }
}
