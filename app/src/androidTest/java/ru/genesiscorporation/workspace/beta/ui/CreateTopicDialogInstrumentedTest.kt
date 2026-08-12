package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class CreateTopicDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyDarkDialogMatchesFigmaSizeAndKeepsSubmitDisabled() {
        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                CreateTopicDialog(
                    busy = false,
                    onSubmit = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(CREATE_TOPIC_DIALOG_TAG)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(366.dp)
            .assertHeightIsEqualTo(160.dp)
        composeRule.onAllNodesWithText("Название темы").assertCountEquals(2)
        composeRule.onNodeWithTag(CREATE_TOPIC_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun nonBlankNameEnablesCreateAndSubmitsTrimmedValue() {
        var submittedName: String? = null
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                CreateTopicDialog(
                    busy = false,
                    onSubmit = { submittedName = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(CREATE_TOPIC_NAME_FIELD_TAG)
            .performTextInput("  Релиз Android  ")
        composeRule.onNodeWithTag(CREATE_TOPIC_SUBMIT_TAG)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Релиз Android", submittedName)
        }
    }

    @Test
    fun cancelDismissesAndBusyStateLocksBothActions() {
        var dismissals = 0
        var busy by mutableStateOf(false)
        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                CreateTopicDialog(
                    busy = busy,
                    onSubmit = {},
                    onDismiss = { dismissals += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(CREATE_TOPIC_CANCEL_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            busy = true
        }
        composeRule.onNodeWithTag(CREATE_TOPIC_CANCEL_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(CREATE_TOPIC_SUBMIT_TAG).assertIsNotEnabled()
    }
}
