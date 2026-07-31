package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class WorkspaceReplyComposerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabsSelectReorderRemoveAndClearWithoutDeadControls() {
        var session by mutableStateOf(
            WorkspaceReplySession(
                tabs = listOf(
                    tab("alice", MESSAGE_A, "Alice", "answer A"),
                    tab("bob", MESSAGE_B, "Bob", "answer B"),
                ),
                activeTabId = "alice",
            ),
        )
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                WorkspaceReplyComposer(
                    session = session,
                    enabled = true,
                    onSelect = { session = selectWorkspaceReplyTab(session, it) },
                    onRemove = { session = removeWorkspaceReplyTab(session, it) },
                    onMove = { id, offset ->
                        session = moveWorkspaceReplyTab(session, id, offset)
                    },
                    onClearAll = { session = WorkspaceReplySession() },
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Переместить ответ влево",
        ).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            "Переместить ответ вправо",
        ).assertExists()
        composeRule.onNodeWithText("Bob").performClick()
        composeRule.runOnIdle {
            assertEquals("bob", session.activeTabId)
        }
        composeRule.onNodeWithContentDescription(
            "Переместить ответ вправо",
        ).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            "Переместить ответ влево",
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("bob", "alice"), session.tabs.map { it.id })
        }
        composeRule.onNodeWithContentDescription(
            "Убрать ответ для Bob",
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("alice"), session.tabs.map { it.id })
            assertEquals("alice", session.activeTabId)
        }
        composeRule.onNodeWithContentDescription(
            "Очистить все ответы",
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(WorkspaceReplySession(), session)
        }
    }

    private fun tab(
        id: String,
        messageUuid: String,
        senderName: String,
        answer: String,
    ) = WorkspaceReplyTab(
        id = id,
        messageUuid = messageUuid,
        senderUuid = SENDER,
        senderName = senderName,
        quotedContent = "quoted $senderName",
        createdAt = "2026-07-31T12:00:00Z",
        answer = answer,
    )

    private companion object {
        const val MESSAGE_A = "00000000-0000-4000-8000-000000000001"
        const val MESSAGE_B = "00000000-0000-4000-8000-000000000002"
        const val SENDER = "00000000-0000-4000-8000-000000000010"
    }
}
