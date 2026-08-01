package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun expandedMenuShowsActionsWithoutDuplicatingSelectedMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val replyLabel = context.getString(R.string.workspace_reply_action)
        val selectedText = "Сообщение остаётся на исходной позиции"
        val menuExpanded = mutableStateOf(false)

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    Text(selectedText)
                    MessageActionsMenu(
                        expanded = menuExpanded.value,
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
                    )
                }
            }
        }

        val originalBounds = composeRule
            .onNodeWithText(selectedText)
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { menuExpanded.value = true }
        composeRule.onNodeWithText(replyLabel).assertIsDisplayed()
        composeRule.onNodeWithText("Изменить").assertIsDisplayed()
        composeRule.onAllNodesWithText(selectedText).assertCountEquals(1)
        val menuBounds = composeRule
            .onNodeWithText(selectedText)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(originalBounds, menuBounds)
    }

    @Test
    fun selectionTargetTogglesAndDoesNotBlockListScrolling() {
        val listState = LazyListState()
        composeRule.setContent {
            var selectedIndexes by remember { mutableStateOf(emptySet<Int>()) }
            WokspaceTheme(darkTheme = true) {
                LazyColumn(
                    modifier = Modifier
                        .height(240.dp)
                        .testTag("messages"),
                    state = listState,
                ) {
                    items((0..20).toList()) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .messageSelectionTarget(
                                    enabled = true,
                                    isSelected = index in selectedIndexes,
                                    selectionLabel = "Выбрать сообщение $index",
                                    onToggleSelection = {
                                        selectedIndexes = if (
                                            index in selectedIndexes
                                        ) {
                                            selectedIndexes - index
                                        } else {
                                            selectedIndexes + index
                                        }
                                    },
                                ),
                        ) {
                            Text("Сообщение $index")
                        }
                    }
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Выбрать сообщение 0")
            .performClick()
            .assertIsSelected()
        composeRule
            .onNodeWithContentDescription("Выбрать сообщение 1")
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag("messages").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertTrue(
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0,
            )
        }
    }

    @Test
    fun blurIsAppliedOnlyToMessagesOutsideCurrentSelection() {
        val selected = setOf("selected")

        assertFalse(
            shouldBlurMessage(
                messageUuid = "active",
                activeMessageMenuUuid = "active",
                selectedMessageUuids = emptySet(),
            ),
        )
        assertFalse(
            shouldBlurMessage(
                messageUuid = "selected",
                activeMessageMenuUuid = null,
                selectedMessageUuids = selected,
            ),
        )
        assertTrue(
            shouldBlurMessage(
                messageUuid = "other",
                activeMessageMenuUuid = null,
                selectedMessageUuids = selected,
            ),
        )
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
