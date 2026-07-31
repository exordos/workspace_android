package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class ComposerFormattingInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toolbarExposesLocalizedFunctionalActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val toolbarLabel = context.getString(
            R.string.message_composer_formatting_toolbar,
        )
        val boldLabel = context.getString(R.string.message_composer_bold)
        val linkLabel = context.getString(R.string.message_composer_link)
        val actions = mutableListOf<ComposerFormattingAction>()
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                ComposerFormattingToolbar(
                    enabled = true,
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription(boldLabel)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithContentDescription(toolbarLabel)
            .performScrollToNode(hasContentDescription(linkLabel))
        composeRule.onNodeWithContentDescription(linkLabel)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ComposerFormattingAction.BOLD,
                    ComposerFormattingAction.LINK,
                ),
                actions,
            )
        }
    }
}
