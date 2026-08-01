package ru.genesiscorporation.workspace.beta.modules.comingsoon

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class WorkspaceComingSoonScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarUsesTheApprovedComingSoonCopy() {
        show(ComingSoonDestination.CALENDAR)

        composeRule.onNodeWithContentDescription("Календарь").assertIsDisplayed()
        composeRule.onNodeWithText("КАЛЕНДАРЬ").assertIsDisplayed()
        assertSharedCopy()
    }

    @Test
    fun mailUsesTheApprovedComingSoonCopy() {
        show(ComingSoonDestination.MAIL)

        composeRule.onNodeWithContentDescription("Почта").assertIsDisplayed()
        composeRule.onNodeWithText("ПОЧТА").assertIsDisplayed()
        assertSharedCopy()
    }

    private fun show(destination: ComingSoonDestination) {
        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                WorkspaceComingSoonScreen(destination)
            }
        }
    }

    private fun assertSharedCopy() {
        composeRule.onNodeWithText("Уже скоро!").assertIsDisplayed()
        composeRule.onNodeWithText("В разработке!").assertIsDisplayed()
    }
}
