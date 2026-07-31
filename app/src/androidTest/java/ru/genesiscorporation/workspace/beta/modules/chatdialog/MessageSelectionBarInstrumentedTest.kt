package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class MessageSelectionBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionBarExposesOnlyFunctionalForwardAndCancelActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedLabel = context.resources.getQuantityString(
            R.plurals.message_selected_count,
            2,
            2,
        )
        val forwardLabel = context.getString(R.string.message_selection_forward)
        val cancelLabel = context.getString(R.string.message_selection_cancel)
        var selectedCount by mutableIntStateOf(2)
        var forwardCalls = 0
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                MessageSelectionBar(
                    selectedCount = selectedCount,
                    onForward = { forwardCalls += 1 },
                    onCancel = { selectedCount = 0 },
                )
            }
        }

        composeRule.onNodeWithText(selectedLabel).assertExists()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        composeRule.onNodeWithText("Удалить").assertDoesNotExist()
        composeRule.onNodeWithText(forwardLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(1, forwardCalls)
        }
        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.onNodeWithText(forwardLabel).assertDoesNotExist()
    }
}
