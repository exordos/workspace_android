package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class CompactMessageComposerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun followsFigmaOrderWithoutDesktopToolbarOrModeTabs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var value by mutableStateOf(TextFieldValue("Текст сообщения"))
        val invoked = mutableListOf<String>()

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                CompactMessageComposer(
                    value = value,
                    onValueChange = { value = it },
                    editorEnabled = true,
                    actionsEnabled = true,
                    attachmentEnabled = true,
                    canSend = true,
                    editing = false,
                    onAttach = { invoked += "attach" },
                    onEmoji = { invoked += "emoji" },
                    onOpenDrafts = { invoked += "history" },
                    onSend = { invoked += "send" },
                )
            }
        }

        composeRule.onNodeWithTag(COMPACT_COMPOSER_ROW_TAG)
            .assertHeightIsEqualTo(48.dp)
        val orderedTags = listOf(
            COMPACT_COMPOSER_ATTACH_TAG,
            COMPACT_COMPOSER_EDITOR_TAG,
            COMPACT_COMPOSER_EMOJI_TAG,
            COMPACT_COMPOSER_HISTORY_TAG,
            COMPACT_COMPOSER_SEND_TAG,
        )
        val leftEdges = orderedTags.map { tag ->
            composeRule.onNodeWithTag(tag)
                .fetchSemanticsNode()
                .boundsInRoot
                .left
        }
        assertTrue(leftEdges.zipWithNext().all { (left, right) -> left < right })

        composeRule.onNodeWithText(
            context.getString(R.string.message_composer_write),
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.message_composer_preview),
        ).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.message_composer_formatting_toolbar),
        ).assertDoesNotExist()

        orderedTags.filterNot { it == COMPACT_COMPOSER_EDITOR_TAG }
            .forEach { tag ->
                composeRule.onNodeWithTag(tag).assertIsEnabled().performClick()
            }
        composeRule.runOnIdle {
            assertEquals(listOf("attach", "emoji", "history", "send"), invoked)
        }
    }

    @Test
    fun disablesActionsWhileConversationIsUnavailable() {
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                CompactMessageComposer(
                    value = TextFieldValue(),
                    onValueChange = {},
                    editorEnabled = false,
                    actionsEnabled = false,
                    attachmentEnabled = false,
                    canSend = false,
                    editing = false,
                    onAttach = {},
                    onEmoji = {},
                    onOpenDrafts = {},
                    onSend = {},
                )
            }
        }

        composeRule.onNodeWithTag(COMPACT_COMPOSER_ATTACH_TAG)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(COMPACT_COMPOSER_EMOJI_TAG)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(COMPACT_COMPOSER_HISTORY_TAG)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(COMPACT_COMPOSER_SEND_TAG)
            .assertIsNotEnabled()
    }
}
