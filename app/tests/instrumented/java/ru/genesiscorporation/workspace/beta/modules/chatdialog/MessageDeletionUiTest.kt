package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

/** Exercises the production menu, confirmation and deletion state on an Android device. */
@RunWith(AndroidJUnit4::class)
class MessageDeletionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deleteIsVisibleOnlyForOwnPersistedMessages() {
        val currentMessage = mutableStateOf(message())
        setConversation(currentMessage)
        openMenu()
        compose.onNodeWithTag("delete-message-action").assertIsDisplayed()
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)

        compose.runOnIdle { currentMessage.value = message().copy(isOwn = false) }
        compose.onNodeWithTag("delete-message-action").assertDoesNotExist()
        compose.onNodeWithTag("select-message-action").assertIsDisplayed()
        compose.onNodeWithTag("forward-message-action").assertIsDisplayed()
        compose.runOnIdle { currentMessage.value = message().copy(uuid = "") }
        compose.onNodeWithTag("delete-message-action").assertDoesNotExist()
        compose.onNodeWithTag("select-message-action").assertDoesNotExist()
        compose.onNodeWithTag("forward-message-action").assertDoesNotExist()
        compose.runOnIdle { currentMessage.value = message().copy(topicUuid = "another-topic") }
        compose.onNodeWithTag("delete-message-action").assertDoesNotExist()
    }

    @Test
    fun cancelAndSystemBackNeverSendDelete() {
        var requests = 0
        setConversation(request = { requests++; ApiResult.Success("") })
        openConfirmation()
        compose.onNodeWithText(localizedString(R.string.message_delete_title)).assertIsDisplayed()
        compose.onNodeWithTag("cancel-message-deletion").performClick()
        compose.onNodeWithText(localizedString(R.string.message_delete_title)).assertDoesNotExist()
        openConfirmation()
        pressBack()
        compose.onNodeWithText(localizedString(R.string.message_delete_title)).assertDoesNotExist()
        compose.onNodeWithText(MESSAGE_TEXT).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test
    fun pendingRequestKeepsMessageAndDisablesRepeatedActionUntilSuccess() {
        val result = CompletableDeferred<ApiResult<String, ApiError>>()
        var requests = 0
        setConversation(request = { requests++; result.await() })
        openConfirmation()
        compose.onNodeWithTag("confirm-message-deletion").performClick()
        compose.onNodeWithText(MESSAGE_TEXT).assertIsDisplayed()
        openMenu()
        compose.onNodeWithTag("delete-message-action").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText(localizedString(R.string.message_deleting)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.runOnIdle { result.complete(ApiResult.Success("")) }
        compose.onNodeWithText(MESSAGE_TEXT).assertDoesNotExist()
        compose.onNodeWithTag("delete-message-action").assertDoesNotExist()
    }

    @Test
    fun failedRequestShowsErrorKeepsMessageAndAllowsRetry() {
        val firstResult = CompletableDeferred<ApiResult<String, ApiError>>()
        var requests = 0
        setConversation(request = {
            requests++
            if (requests == 1) firstResult.await() else ApiResult.Success("")
        })
        openConfirmation()
        compose.onNodeWithTag("confirm-message-deletion").performClick()
        compose.runOnIdle { firstResult.complete(ApiResult.Error(ApiError("Forbidden", "403"))) }
        compose.onNodeWithText(localizedString(R.string.message_delete_failed)).assertIsDisplayed()
        compose.onNodeWithText(MESSAGE_TEXT).assertIsDisplayed()
        openConfirmation()
        compose.onNodeWithTag("confirm-message-deletion").assertIsEnabled().performClick()
        compose.onNodeWithText(MESSAGE_TEXT).assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test
    fun pendingConfirmationCannotSubmitAgain() {
        var confirmations = 0
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                DeleteMessageConfirmationDialog(
                    isDeleting = true,
                    onDismiss = {},
                    onConfirm = { confirmations++ },
                )
            }
        }
        compose.onNodeWithTag("confirm-message-deletion").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText(localizedString(R.string.message_deleting)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, confirmations) }
    }

    @Test
    fun lightThemeKeepsDeletionActionReadableAndConfirmationUsable() {
        setConversation(darkTheme = false)
        openConfirmation()
        compose.onNodeWithText(localizedString(R.string.message_delete_title)).assertIsDisplayed()
        compose.onNodeWithTag("confirm-message-deletion").assertIsEnabled()
        compose.onNodeWithTag("cancel-message-deletion").performClick()
        compose.onNodeWithText(MESSAGE_TEXT).assertIsDisplayed()
    }

    private fun openMenu() {
        compose.onNodeWithText("Действия").performClick()
    }

    private fun openConfirmation() {
        openMenu()
        compose.onNodeWithTag("delete-message-action").performClick()
    }

    private fun setConversation(
        currentMessage: MutableState<MessageResponse> = mutableStateOf(message()),
        darkTheme: Boolean = true,
        request: suspend (String) -> ApiResult<String, ApiError> = { ApiResult.Success("") },
    ) {
        compose.setContent {
            WokspaceTheme(darkTheme = darkTheme, dynamicColor = false) {
                DeletionFixture(currentMessage.value, request)
            }
        }
    }

    @Composable
    private fun DeletionFixture(
        message: MessageResponse,
        request: suspend (String) -> ApiResult<String, ApiError>,
    ) {
        var deleted by remember { mutableStateOf(false) }
        var expanded by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val deletion = remember {
            MessageDeletion(
                canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
                request = request,
                onDeleted = { deleted = true },
            )
        }
        val pending by deletion.deletingMessageUuids.collectAsState()
        val error by deletion.actionError.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        MessageActionErrorEffect(error, snackbar, deletion::clearActionError)
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                if (!deleted) {
                    Text(message.payload.content)
                    Box {
                        Button(onClick = { expanded = true }) { Text("Действия") }
                        MessageDeletionControls(
                            messageUuid = message.uuid,
                            canDelete = canDeleteMessage(message, STREAM_UUID, TOPIC_UUID),
                            isDeleting = message.uuid in pending,
                            onDelete = { scope.launch { deletion.delete(message) } },
                        ) { requestDelete ->
                            MessageActionMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                canDelete = canDeleteMessage(message, STREAM_UUID, TOPIC_UUID),
                                isDeleting = message.uuid in pending,
                                onDelete = {
                                    expanded = false
                                    requestDelete()
                                },
                                onReaction = {},
                                onReply = {},
                                onEdit = if (message.isOwn) ({}) else null,
                                onSelect = if (canSelectMessage(message, STREAM_UUID, TOPIC_UUID)) ({}) else null,
                                onForward = if (canSelectMessage(message, STREAM_UUID, TOPIC_UUID)) ({}) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun localizedString(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun message() = MessageResponse(
        uuid = "11111111-2222-4333-8444-555555555555",
        updatedAt = "2026-09-01T10:00:00Z",
        createdAt = "2026-09-01T10:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = "test-author",
        authorUuid = "test-author",
        payload = MessageResponsePayload("markdown", MESSAGE_TEXT),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )

    companion object {
        private const val STREAM_UUID = "22222222-2222-4333-8444-555555555555"
        private const val TOPIC_UUID = "33333333-2222-4333-8444-555555555555"
        private const val MESSAGE_TEXT = "Сообщение для проверки удаления"
    }
}
