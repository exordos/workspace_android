package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class MessageReactionPickerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchEmptyStateAndSelectionAreFunctional() {
        var pickerOpen by mutableStateOf(true)
        var selected: WorkspaceReactionSelection? = null
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                MessageReactionPicker(
                    open = pickerOpen,
                    onDismiss = { pickerOpen = false },
                    onReaction = { reaction ->
                        selected = reaction
                        pickerOpen = false
                    },
                )
            }
        }

        composeRule.onNode(hasSetTextAction())
            .performTextReplacement("definitely missing emoji")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("Ничего не найдено")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Ничего не найдено").assertExists()

        composeRule.onNode(hasSetTextAction())
            .performTextReplacement("smile")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText(":smile:")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(":smile:").performClick()

        composeRule.runOnIdle {
            assertEquals("smile", selected?.emojiName)
            assertEquals(
                setOf(
                    "smile",
                    "grinning_face_with_closed_eyes",
                    "grinning_face_with_smiling_eyes",
                ),
                selected?.equivalentEmojiNames,
            )
        }
        composeRule.onNodeWithText("Выберите реакцию").assertDoesNotExist()
    }
}
