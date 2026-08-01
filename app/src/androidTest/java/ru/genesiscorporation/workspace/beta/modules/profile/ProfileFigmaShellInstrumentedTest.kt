package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class ProfileFigmaShellInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topBarUsesTheFigmaHeightAndInvokesBack() {
        var backClicks = 0

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                ProfileFigmaTopBar(
                    title = "Мой профиль",
                    onBack = { backClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PROFILE_FIGMA_TOP_BAR_TAG)
            .assertHeightIsEqualTo(44.dp)
        composeRule.onNodeWithText("Мой профиль").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад").performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test
    fun settingRowKeepsTheCompactFigmaHeightAndRemainsActionable() {
        var clicks = 0

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                ProfileFigmaSettingRow(
                    icon = R.drawable.ic_notifications,
                    title = "Звук уведомления",
                    value = "Обычный",
                    testTag = PROFILE_FIGMA_SOUND_ROW_TAG,
                    onClick = { clicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PROFILE_FIGMA_SOUND_ROW_TAG)
            .assertHeightIsEqualTo(44.dp)
            .assertTextContains("Звук уведомления")
            .assertTextContains("Обычный")
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun organizationsExpandAndExposeRealAccountActions() {
        var expanded by mutableStateOf(false)
        val switches = mutableListOf<String>()
        var addClicks = 0
        val accounts = listOf(
            fakeAccount("first", "Exordos", "cassi@exordos.com"),
            fakeAccount("second", "Sandbox", "cassi@example.test"),
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                Column {
                    ProfileFigmaOrganizationSection(
                        accounts = accounts,
                        activeAccountId = "first",
                        expanded = expanded,
                        enabled = true,
                        onExpandedChange = { expanded = it },
                        onSwitchAccount = { switches += it },
                        onAddAccount = { addClicks += 1 },
                        accountAvatar = { Spacer(Modifier.size(38.dp)) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Exordos").assertDoesNotExist()
        composeRule.onNodeWithText("Организации").performClick()
        composeRule.onNodeWithText("Exordos").assertIsDisplayed()
        composeRule.onNodeWithText("Текущая").assertIsDisplayed()
        composeRule.onNodeWithText("Sandbox").performClick()
        composeRule.onNodeWithText("Добавить организацию").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("second"), switches)
            assertEquals(1, addClicks)
        }
    }

    @Test
    fun currentServerRowMatchesTheThreeLineFigmaLayout() {
        var clicks = 0

        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                ProfileFigmaServerRow(
                    server = "workspace.example.test",
                    accountLabel = "Exordos",
                    enabled = true,
                    onClick = { clicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PROFILE_FIGMA_SERVER_ROW_TAG)
            .assertHeightIsEqualTo(72.dp)
            .assertTextContains("Текущий сервер")
            .assertTextContains("workspace.example.test")
            .assertTextContains("Exordos")
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    private fun fakeAccount(
        id: String,
        organizationName: String,
        login: String,
    ) = WorkspaceAccount(
        accountId = id,
        baseUrl = "https://workspace.example.test",
        projectId = "project-$id",
        projectName = organizationName,
        organizationName = organizationName,
        userId = "user-$id",
        login = login,
        displayName = "Cassi $id",
    )
}
