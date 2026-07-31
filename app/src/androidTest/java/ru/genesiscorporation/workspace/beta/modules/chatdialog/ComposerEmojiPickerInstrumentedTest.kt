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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class ComposerEmojiPickerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchAndSelectionInsertARealCatalogGlyph() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(
            R.string.message_composer_emoji_picker_title,
        )
        var pickerOpen by mutableStateOf(true)
        var selected: String? = null
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                ComposerEmojiPicker(
                    open = pickerOpen,
                    onDismiss = { pickerOpen = false },
                    onEmoji = { glyph ->
                        selected = glyph
                        pickerOpen = false
                    },
                )
            }
        }

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
            assertEquals("😄", selected)
        }
        composeRule.onNodeWithText(title).assertDoesNotExist()
    }
}
