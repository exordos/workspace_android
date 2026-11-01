package com.exordos.workspace.modules.projects

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.LoginFlow
import com.exordos.workspace.data.remote.dto.ProjectResponseData
import com.exordos.workspace.fixtures.LoopbackWorkspaceServer
import com.exordos.workspace.fixtures.WorkspaceTestAccount
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.login.LoginScreen
import com.exordos.workspace.modules.login.LoginViewModel
import com.exordos.workspace.modules.otp.OtpScreen
import com.exordos.workspace.modules.otp.OtpViewModel
import com.exordos.workspace.ui.theme.WokspaceTheme


/** Duplicate API records must not crash the project picker after authentication. */
@RunWith(AndroidJUnit4::class)
class ProjectsDuplicateIdentityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun repeatedProjectUuidDoesNotCrashThePicker() {
        val first = ProjectResponseData("cassi-project-a", "CASSI project A", "")
        val second = ProjectResponseData("cassi-project-b", "CASSI project B", "")
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                ProjectsContent(
                    projects = listOf(first, first.copy(name = "Ignored duplicate metadata"), second),
                    selectedProject = first,
                    state = QueryState.Success,
                    loginState = QueryState.Idle,
                    onProjectSelected = {},
                    onRetry = {},
                    onOpenProject = {},
                    onReturnToLogin = {},
                )
            }
        }
        compose.onAllNodesWithText(first.name).assertCountEquals(1)
        compose.onAllNodesWithText("Ignored duplicate metadata").assertCountEquals(0)
        compose.onNodeWithText(second.name).assertIsDisplayed()
    }

    @Test
    fun loginWithDuplicatedProjectsCanOpenTheSelectedProject(): Unit = checkLogin(initialError = false)

    @Test
    fun projectLookupRetryCanRenderDuplicatesAndOpenTheSelectedProject(): Unit = checkLogin(initialError = true)

    @Test
    fun otpLoginWithDuplicatedProjectsCanOpenTheSelectedProject(): Unit =
        checkLogin(initialError = false, requireOtp = true)

    @Test
    fun passwordLoginWithUniqueProjectsCanOpenTheSelectedProject(): Unit =
        checkLogin(initialError = false, duplicates = false)

    @Test
    fun otpLoginWithUniqueProjectsCanOpenTheSelectedProject(): Unit =
        checkLogin(initialError = false, requireOtp = true, duplicates = false)

    private fun checkLogin(
        initialError: Boolean,
        requireOtp: Boolean = false,
        duplicates: Boolean = true,
    ): Unit = runBlocking {
        val first = ProjectResponseData("cassi-project-a", "CASSI project A", "")
        val second = ProjectResponseData("cassi-project-b", "CASSI project B", "")
        val requests = AtomicInteger()
        val refreshScope = AtomicReference<String>()
        val otpLogins = AtomicInteger()
        val server = LoopbackWorkspaceServer { request ->
            when {
                request.path.endsWith("/get_token/invoke") -> {
                    val body = Json.parseToJsonElement(request.body).jsonObject
                    if (body["grant_type"]?.jsonPrimitive?.content == "refresh_token") {
                        refreshScope.set(body["scope"]?.jsonPrimitive?.content)
                    }
                    if (requireOtp && body["grant_type"]?.jsonPrimitive?.content != "refresh_token" &&
                        request.headers["x-otp"] != "123456") {
                        LoopbackWorkspaceServer.Response("""{"msg":"OTP required","code":"OTP_REQUIRED"}""", 401)
                    } else {
                        if (request.headers["x-otp"] == "123456") otpLogins.incrementAndGet()
                        LoopbackWorkspaceServer.Response(
                            """{"access_token":"cassi-fixture-0","refresh_token":"cassi-fixture-refresh"}""",
                        )
                    }
                }
                request.path.endsWith("/iam/projects/") -> {
                    val attempt = requests.incrementAndGet()
                    if (initialError && attempt == 1) {
                        LoopbackWorkspaceServer.Response("""{"msg":"Synthetic retry","code":"RETRY"}""", 503)
                    } else {
                        LoopbackWorkspaceServer.Response(Json.encodeToString(
                            if (duplicates) listOf(first, first.copy(), second) else listOf(first, second),
                        ))
                    }
                }
                else -> LoopbackWorkspaceServer.Response("[]")
            }
        }
        val owner = WorkspaceTestAccount.create(server.url)
        val mounted = mutableStateOf(true)
        val login = withContext(Dispatchers.Main) { LoginViewModel(owner.api, owner.user, false) }
        var projects: ProjectsViewModel? = null
        var otp: OtpViewModel? = null
        try {
            login.onLoginChange("cassi-fixture")
            login.onPasswordChange("synthetic-password")
            compose.setContent {
                if (mounted.value) WokspaceTheme(darkTheme = false, dynamicColor = false) {
                    val navigation = rememberNavController()
                    NavHost(navigation, startDestination = LoginFlow.Login(false)) {
                        composable<LoginFlow.Login> { LoginScreen(login, navigation) }
                        composable<LoginFlow.Otp> { entry ->
                            val route = entry.toRoute<LoginFlow.Otp>()
                            val model = remember {
                                OtpViewModel(owner.api, owner.user, route.login, route.password, false)
                                    .also { otp = it }
                            }
                            OtpScreen(model, navigation)
                        }
                        composable<LoginFlow.Projects> {
                            val model = remember {
                                ProjectsViewModel(owner.api, owner.user, false).also { projects = it }
                            }
                            ProjectsScreen(model, navigation)
                        }
                    }
                }
            }
            compose.onNodeWithText("Войти").performScrollTo().performClick()
            if (requireOtp) {
                compose.waitUntil(15_000) { otp != null }
                compose.onNodeWithContentDescription("6-значный код").performTextInput("123456")
            }
            if (initialError) {
                compose.waitUntil(15_000) { projects?.projectsQueryState?.value is QueryState.Error }
                compose.onNodeWithText("Повторить").performScrollTo().performClick()
            }
            compose.waitUntil(15_000) { projects?.projectsQueryState?.value is QueryState.Success }
            compose.onAllNodesWithText(first.name).assertCountEquals(1)
            compose.onNodeWithText(second.name).performScrollTo().performClick()
            compose.onNodeWithText("Открыть проект").performScrollTo().performClick()
            compose.waitUntil(15_000) {
                projects?.queryState?.value is QueryState.Success &&
                    owner.user.selectedServer.value?.projectUuid == second.uuid
            }
            assertEquals("openid email profile project:${second.uuid}", refreshScope.get())
            assertEquals(second.uuid, owner.user.selectedServer.value?.projectUuid)
            assertEquals(if (initialError) 2 else 1, requests.get())
            assertEquals(if (requireOtp) 1 else 0, otpLogins.get())

            compose.onNodeWithText("Вернуться к логину").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Имя пользователя или email").assertIsDisplayed()
        } finally {
            try {
                compose.runOnIdle { mounted.value = false }
                compose.waitForIdle()
            } finally {
                login.viewModelScope.cancel()
                projects?.viewModelScope?.cancel()
                otp?.viewModelScope?.cancel()
                try { owner.close() } finally { server.close() }
            }
        }
    }
}
