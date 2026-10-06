package ru.genesiscorporation.workspace.beta.modules.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.modules.otp.OtpContent
import ru.genesiscorporation.workspace.beta.ui.AuthScreen
import ru.genesiscorporation.workspace.beta.ui.authColors
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class AuthenticationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun credentialsRequireBothFieldsAndKeepPasswordVisibilityAndLogoutActions() {
        var logins = 0
        var logouts = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                var login by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                val colors = authColors()
                AuthScreen(colors) {
                    CredentialsContent(
                        login, password, "Example", "example.com", null, false, colors,
                        { login = it }, { password = it }, { logins++ }, { logouts++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Войти").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Имя пользователя или email").performTextInput("demo")
        compose.onNodeWithText("Войти").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Пароль").performTextInput("preview")
        compose.onNodeWithContentDescription("Показать пароль", useUnmergedTree = true).onParent().performScrollTo().performClick()
        compose.onNodeWithContentDescription("Скрыть пароль", useUnmergedTree = true).onParent().assertIsDisplayed().performClick()
        compose.onNodeWithText("Войти").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("Выйти из организации").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, logins)
            assertEquals(1, logouts)
        }
    }

    @Test
    fun otpUsesOneEditableCodeAndAllowsReturningWithoutAConfirmationButton() {
        var entered = ""
        var backs = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                var otp by remember { mutableStateOf("") }
                val colors = authColors()
                AuthScreen(colors) {
                    OtpContent(otp, true, colors, { otp = it; entered = it }, { backs++ })
                }
            }
        }
        compose.onNodeWithText("Подтвердить").assertDoesNotExist()
        compose.onNodeWithContentDescription("6-значный код").performTextReplacement("12345")
        compose.runOnIdle { assertEquals("12345", entered) }
        compose.onNodeWithContentDescription("6-значный код").performTextInput("6")
        compose.runOnIdle { assertEquals("123456", entered) }
        compose.onNodeWithText("Вернуться к логину").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun loadingDisablesCredentialsAndOrganizationExit() {
        compose.setContent {
            WokspaceTheme(darkTheme = false, dynamicColor = false) {
                val colors = authColors()
                AuthScreen(colors) {
                    CredentialsContent(
                        "demo", "preview", "Example", "example.com", null, true, colors,
                        {}, {}, {}, {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Имя пользователя или email").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Пароль").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Показать пароль", useUnmergedTree = true).onParent().assertIsNotEnabled()
        compose.onNodeWithText("Войти").assertIsNotEnabled()
        compose.onNodeWithText("Выйти из организации").assertIsNotEnabled()
    }
}
