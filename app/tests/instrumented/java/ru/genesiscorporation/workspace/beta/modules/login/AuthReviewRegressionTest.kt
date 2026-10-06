package ru.genesiscorporation.workspace.beta.modules.login

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.ProjectResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerContent
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.projects.ProjectsContent
import ru.genesiscorporation.workspace.beta.modules.projects.returnToCredentials
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class AuthReviewRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun repeatedImeSubmissionDoesNotStartAnotherLookupWhileLoading() {
        var state: QueryState by mutableStateOf(QueryState.Idle)
        var lookups = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                ChooseServerContent("https://example.com", state, {}, {
                    lookups++
                    state = QueryState.Loading
                })
            }
        }
        val field = compose.onNodeWithContentDescription("Адрес организации")
        field.performImeAction()
        // Invoke an already queued IME action even if the field has since been disabled.
        val done = field.fetchSemanticsNode().config[SemanticsActions.OnImeAction].action!!
        compose.runOnIdle {
            done()
            assertEquals("The loading state must ignore a second IME submission", 1, lookups)
        }
        field.assertIsNotEnabled()
        compose.onNodeWithText("Войти").assertIsNotEnabled()
    }

    @Test
    fun largeProjectListComposesOnlyVisibleRowsAndCanSelectTheLastProject() {
        val projects = (0 until 200).map { ProjectResponseData("project-$it", "Project $it", "Description") }
        var selected: ProjectResponseData? by mutableStateOf(projects.first())
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                ProjectsContent(projects, selected, QueryState.Success, QueryState.Idle,
                    { selected = it }, {}, {}, {})
            }
        }
        val composedRows = compose.onAllNodes(hasText("Project ", substring = true)).fetchSemanticsNodes().size
        assertTrue("Expected a bounded visible row count, but composed $composedRows of 200 projects", composedRows < 25)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Project 199"))
        compose.onNodeWithText("Project 199").performClick()
        compose.runOnIdle { assertEquals("project-199", selected?.uuid) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Открыть проект"))
        compose.onNodeWithText("Открыть проект").assertIsDisplayed()
    }

    @Test
    fun projectsReturnDirectlyToCredentialsAfterOtp() = verifyReturnToCredentials(withOtp = true)

    @Test
    fun projectsReturnToCredentialsWithoutOtp() = verifyReturnToCredentials(withOtp = false)

    private fun verifyReturnToCredentials(withOtp: Boolean) {
        lateinit var navController: NavHostController
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                navController = rememberNavController()
                NavHost(navController, startDestination = LoginFlow.Login(false)) {
                    composable<LoginFlow.Login> { Text("Credentials destination") }
                    composable<LoginFlow.Otp> { Text("OTP destination") }
                    composable<LoginFlow.Projects> {
                        ProjectsContent(emptyList(), null, QueryState.Success, QueryState.Idle,
                            {}, {}, {}, { navController.returnToCredentials() })
                    }
                }
            }
        }
        compose.runOnIdle {
            if (withOtp) navController.navigate(LoginFlow.Otp("demo", "preview", false))
            navController.navigate(LoginFlow.Projects(false))
        }
        compose.onNodeWithText("Вернуться к логину").performScrollTo().performClick()
        compose.onNodeWithText("Credentials destination").assertIsDisplayed()
        compose.onNodeWithText("OTP destination").assertDoesNotExist()
    }
}
