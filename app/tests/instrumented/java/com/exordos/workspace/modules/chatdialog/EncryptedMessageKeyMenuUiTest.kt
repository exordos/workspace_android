package com.exordos.workspace.modules.chatdialog

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class EncryptedMessageKeyMenuUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lockedBubbleRealLongPressOpensOnlyTheMessageKeyAction() {
        var requested = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                LockedEncryptedMessageActions("historical-message", false, { requested++ }) { Text("Locked message") }
            }
        }
        compose.onNodeWithTag("request-message-key-action").assertDoesNotExist()
        compose.onNodeWithTag("message-bubble-historical-message").performTouchInput { longClick() }
        compose.onNodeWithTag("request-message-key-action").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, requested) }
        compose.onNodeWithTag("request-message-key-action").assertDoesNotExist()
        compose.onNodeWithText("Ответить").assertDoesNotExist()
        compose.onNodeWithText("Изменить").assertDoesNotExist()
        compose.onNodeWithTag("forward-message-action").assertDoesNotExist()
    }

    @Test fun lockedMenuCannotLeakPlaintextActionsEvenIfOtherCallbacksAreAccidentallySupplied() {
        var plaintextActions = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                MessageActionMenu(true, {}, true, false, { plaintextActions++ },
                    onReply = { plaintextActions++ }, onEdit = { plaintextActions++ },
                    onForward = { plaintextActions++ }, onSelect = { plaintextActions++ },
                    onReaction = { plaintextActions++ }, onRequestEncryptionKey = {}, requestEncryptionKeyPending = true)
            }
        }
        compose.onNodeWithTag("request-message-key-action").assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithText("Ответить").assertDoesNotExist()
        compose.onNodeWithText("Изменить").assertDoesNotExist()
        compose.onNodeWithTag("forward-message-action").assertDoesNotExist()
        compose.onNodeWithTag("select-message-action").assertDoesNotExist()
        compose.onNodeWithText("👍").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, plaintextActions) }
    }

    @Test fun decryptedOrdinaryMenuRetainsReplyWithoutAnExtraKeyAction() {
        var replies = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                MessageActionMenu(true, {}, false, false, {}, onReply = { replies++ })
            }
        }
        compose.onNodeWithTag("request-message-key-action").assertDoesNotExist()
        compose.onNodeWithText("Ответить").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, replies) }
    }
}
