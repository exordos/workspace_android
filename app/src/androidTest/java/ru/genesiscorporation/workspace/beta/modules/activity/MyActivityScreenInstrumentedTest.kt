package ru.genesiscorporation.workspace.beta.modules.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class MyActivityScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheFullFigmaHubWithoutTheLegacyBurger() {
        show()

        composeRule.onNodeWithText("Моя активность")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Входящие, непрочитанных: 24",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Избранное").assertIsDisplayed()
        composeRule.onNodeWithText("Черновики").assertIsDisplayed()
        composeRule.onNodeWithText("Лента").assertIsDisplayed()
        composeRule.onNodeWithText("Упоминания").assertIsDisplayed()
        composeRule.onNodeWithText("Реакции").assertIsDisplayed()
        composeRule.onNodeWithText("Отмеченные сообщения")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Меню")
            .assertDoesNotExist()
    }

    @Test
    fun routesRowsAndFiltersTheSupportedDestinations() {
        val clicks = mutableListOf<MyActivityDestination>()
        show(onDestinationSelected = clicks::add)

        composeRule.onNodeWithText("Избранное").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(MyActivityDestination.STARRED), clicks)
        }

        composeRule.onNodeWithContentDescription(
            "Поиск по моей активности",
        ).performTextInput("черн")
        composeRule.onNodeWithText("Черновики").assertIsDisplayed()
        composeRule.onNodeWithText("Входящие").assertDoesNotExist()
        composeRule.onNodeWithText("Избранное").assertDoesNotExist()
        composeRule.onNodeWithText("Лента").assertDoesNotExist()
    }

    private fun show(
        onDestinationSelected: (MyActivityDestination) -> Unit = {},
    ) {
        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                MyActivityContent(
                    folders = emptyList(),
                    selectedFolder = null,
                    inboxUnreadCount = 24,
                    onOpenChats = {},
                    onFolderSelected = {},
                    onManageFolders = {},
                    onDestinationSelected = onDestinationSelected,
                )
            }
        }
    }
}
