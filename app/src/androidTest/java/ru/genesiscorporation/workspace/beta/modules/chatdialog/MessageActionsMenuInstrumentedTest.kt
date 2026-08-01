package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class MessageActionsMenuInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandedMenuShowsActionsAndHighlightedMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val replyLabel = context.getString(R.string.workspace_reply_action)
        val highlightedText = "Предпросмотр выбранного сообщения"

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                MessageActionsMenu(
                    expanded = true,
                    item = message(),
                    onDismiss = {},
                    onReaction = {},
                    onOpenReactionPicker = {},
                    onEdit = {},
                    isDeleting = false,
                    onDelete = {},
                    onCopy = {},
                    onQuote = {},
                    onQuoteFragment = {},
                    canAddReply = false,
                    onAddQuote = {},
                    onAddQuoteFragment = {},
                    onForward = {},
                    isSelected = false,
                    onToggleSelection = {},
                    highlightedMessage = {
                        Text(highlightedText)
                    },
                )
            }
        }

        composeRule.onNodeWithText(replyLabel).assertIsDisplayed()
        composeRule.onNodeWithText("Изменить").assertIsDisplayed()
        composeRule.onNodeWithText(highlightedText).assertIsDisplayed()
    }

    private fun message() = MessageResponse(
        uuid = "40000000-0000-4000-8000-000000000001",
        updatedAt = "2026-08-02T00:00:00Z",
        createdAt = "2026-08-02T00:00:00Z",
        streamUuid = "20000000-0000-4000-8000-000000000001",
        topicUuid = "30000000-0000-4000-8000-000000000001",
        userUuid = "50000000-0000-4000-8000-000000000001",
        authorUuid = "50000000-0000-4000-8000-000000000001",
        payload = MessageResponsePayload(
            kind = "markdown",
            content = "Тестовое сообщение",
        ),
        isOwn = true,
        reactions = emptyMap(),
    )
}
