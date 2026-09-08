package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
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

@RunWith(AndroidJUnit4::class)
class MessageSelectionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectTwoOwnMessagesAndCancelConfirmationOrSelectionWithBack() {
        var requests = 0
        setConversation(request = { requests++; ApiResult.Success("") })
        selectFirstOwnMessage()
        compose.onNodeWithTag("select-message-$SECOND_UUID").performClick()
        compose.onNodeWithTag("select-message-$FIRST_UUID").assertIsOn()
        compose.onNodeWithTag("select-message-$SECOND_UUID").assertIsOn()
        compose.onNodeWithText(localizedQuantity(R.plurals.messages_selected_count, 2)).assertIsDisplayed()
        compose.onNodeWithTag("delete-selected-messages").assertIsEnabled().performClick()
        compose.onNodeWithText(localizedQuantity(R.plurals.messages_delete_title, 2)).assertIsDisplayed()
        compose.onNodeWithTag("cancel-message-deletion").performClick()
        compose.onNodeWithTag("message-selection-bar").assertIsDisplayed()
        compose.onNodeWithTag("delete-selected-messages").performClick()
        pressBack()
        compose.onNodeWithText(localizedQuantity(R.plurals.messages_delete_title, 2)).assertDoesNotExist()
        compose.onNodeWithTag("message-selection-bar").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("message-selection-bar").assertDoesNotExist()
        compose.onNodeWithText("Первое своё сообщение").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test
    fun mixedAndForeignSelectionDisableDeleteButCanBeForwarded() {
        var forwarded = 0
        setConversation(onForward = { forwarded++ })
        selectFirstOwnMessage()
        compose.onNodeWithTag("select-message-$FOREIGN_UUID").performClick()
        compose.onNodeWithTag("delete-selected-messages").assertIsNotEnabled()
        compose.onNodeWithTag("forward-selected-messages").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, forwarded) }
        compose.onNodeWithTag("select-message-$FIRST_UUID").performClick()
        compose.onNodeWithTag("select-message-$FOREIGN_UUID").assertIsOn()
        compose.onNodeWithTag("delete-selected-messages").assertIsNotEnabled()
        compose.onNodeWithTag("forward-selected-messages").assertIsEnabled()
        compose.onNodeWithTag("cancel-message-selection").performClick()
        compose.onNodeWithTag("message-selection-bar").assertDoesNotExist()
    }

    @Test
    fun pendingBatchDisablesActionsAndPartialFailureKeepsOnlyFailedMessageForRetry() {
        val first = CompletableDeferred<ApiResult<String, ApiError>>()
        val requests = mutableListOf<String>()
        setConversation(request = { uuid ->
            requests += uuid
            when (requests.size) {
                1 -> first.await()
                2 -> ApiResult.Error(ApiError("Temporarily unavailable", "503"))
                else -> ApiResult.Success("")
            }
        })
        selectFirstOwnMessage()
        compose.onNodeWithTag("select-message-$SECOND_UUID").performClick()
        compose.onNodeWithTag("delete-selected-messages").performClick()
        compose.onNodeWithTag("confirm-message-deletion").performClick()
        compose.onNodeWithTag("delete-selected-messages").assertIsNotEnabled()
        compose.onNodeWithTag("cancel-message-selection").assertIsNotEnabled()
        compose.onNodeWithTag("forward-selected-messages").assertIsNotEnabled()
        compose.onNodeWithTag("select-message-$SECOND_UUID").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(FIRST_UUID), requests) }
        compose.runOnIdle { first.complete(ApiResult.Success("")) }
        compose.onNodeWithTag("select-message-$FIRST_UUID").assertDoesNotExist()
        compose.onNodeWithTag("select-message-$SECOND_UUID").assertIsOn()
        compose.onNodeWithText(localizedQuantity(R.plurals.messages_selected_count, 1)).assertIsDisplayed()
        compose.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.messages_delete_partial_failure, 1, 2, 1),
        ).assertIsDisplayed()
        val snackbarBounds = compose.onNodeWithTag("message-action-snackbar").fetchSemanticsNode().boundsInRoot
        val toolbarBounds = compose.onNodeWithTag("message-selection-bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Error snackbar must not cover selection actions", snackbarBounds.bottom <= toolbarBounds.top + 1f)
        compose.onNodeWithTag("delete-selected-messages").assertIsEnabled().performClick()
        compose.onNodeWithTag("confirm-message-deletion").performClick()
        compose.onNodeWithTag("message-selection-bar").assertDoesNotExist()
        compose.onNodeWithText("Второе своё сообщение").assertDoesNotExist()
        compose.onNodeWithText("Чужое сообщение").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(FIRST_UUID, SECOND_UUID, SECOND_UUID), requests) }
    }

    @Test
    fun confirmingSelectionDeletesExactlySelectedOwnMessages() {
        val requests = mutableListOf<String>()
        setConversation(request = { requests += it; ApiResult.Success("") })
        selectFirstOwnMessage()
        compose.onNodeWithTag("select-message-$SECOND_UUID").performClick()
        compose.onNodeWithTag("delete-selected-messages").performClick()
        compose.onNodeWithTag("confirm-message-deletion").performClick()
        compose.onNodeWithText("Первое своё сообщение").assertDoesNotExist()
        compose.onNodeWithText("Второе своё сообщение").assertDoesNotExist()
        compose.onNodeWithText("Чужое сообщение").assertIsDisplayed()
        compose.onNodeWithTag("message-selection-bar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(FIRST_UUID, SECOND_UUID), requests) }
    }

    @Test
    fun narrowLayoutWithLargeTextKeepsEveryToolbarActionReachable() {
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                    Box(Modifier.width(320.dp)) {
                        MessageSelectionBar(
                            selectedMessageUuids = setOf(FIRST_UUID, SECOND_UUID),
                            canDelete = true,
                            isDeleting = false,
                            onDelete = {},
                            onCancel = {},
                            onForward = {},
                        )
                    }
                }
            }
        }
        listOf("delete-selected-messages", "cancel-message-selection", "forward-selected-messages").forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        }
    }

    private fun selectFirstOwnMessage() {
        compose.onNodeWithText("Действия 1").performClick()
        compose.onNodeWithTag("select-message-action").performClick()
    }

    private fun setConversation(
        request: suspend (String) -> ApiResult<String, ApiError> = { ApiResult.Success("") },
        onForward: () -> Unit = {},
    ) {
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                SelectionFixture(request, onForward)
            }
        }
    }

    @Composable
    private fun SelectionFixture(
        request: suspend (String) -> ApiResult<String, ApiError>,
        onForward: () -> Unit,
    ) {
        val messages = remember {
            mutableStateListOf(
                message(FIRST_UUID, "Первое своё сообщение"),
                message(SECOND_UUID, "Второе своё сообщение"),
                message(FOREIGN_UUID, "Чужое сообщение").copy(isOwn = false),
            )
        }
        val deletion = remember {
            MessageDeletion(
                canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
                request = request,
                onDeleted = { uuid -> messages.removeAll { it.uuid == uuid } },
            )
        }
        val selection = remember {
            MessageSelection(
                canSelect = { canSelectMessage(it, STREAM_UUID, TOPIC_UUID) },
                canDelete = { canDeleteMessage(it, STREAM_UUID, TOPIC_UUID) },
                deletion = deletion,
            )
        }
        val selected by selection.selectedMessageUuids.collectAsState()
        val selecting by selection.isSelectionMode.collectAsState()
        val canDelete by selection.canDeleteSelectedMessages.collectAsState()
        val pending by selection.deletingSelectedMessages.collectAsState()
        val error by deletion.actionError.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var expandedMessage by remember { mutableStateOf<String?>(null) }
        BackHandler(enabled = selecting) { selection.clearMessageSelection() }
        MessageActionErrorEffect(error, snackbar, deletion::clearActionError)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("message-action-snackbar")) },
            bottomBar = {
                if (selecting) {
                    MessageSelectionBar(
                        selectedMessageUuids = selected,
                        canDelete = canDelete,
                        isDeleting = pending,
                        onDelete = { scope.launch { selection.deleteSelectedMessages() } },
                        onCancel = selection::clearMessageSelection,
                        onForward = onForward,
                    )
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    messages.forEachIndexed { index, message ->
                        SelectableMessageRow(
                            messageUuid = message.uuid,
                            description = message.description(),
                            selectionMode = selecting,
                            selected = message.uuid in selected,
                            enabled = !pending,
                            onToggle = { selection.toggleMessageSelection(message) },
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(message.payload.content)
                                Box {
                                    Button(onClick = { expandedMessage = message.uuid }) {
                                        Text("Действия ${index + 1}")
                                    }
                                    MessageActionMenu(
                                        expanded = expandedMessage == message.uuid,
                                        onDismissRequest = { expandedMessage = null },
                                        canDelete = canDeleteMessage(message, STREAM_UUID, TOPIC_UUID),
                                        isDeleting = pending,
                                        onDelete = {},
                                        onSelect = { selection.startMessageSelection(message) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun localizedQuantity(id: Int, count: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, count, count)

    private fun message(uuid: String, content: String) = MessageResponse(
        uuid = uuid,
        updatedAt = "2026-09-01T10:00:00Z",
        createdAt = "2026-09-01T10:00:00Z",
        streamUuid = STREAM_UUID,
        topicUuid = TOPIC_UUID,
        userUuid = "test-author",
        authorUuid = "test-author",
        payload = MessageResponsePayload("markdown", content),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )

    companion object {
        private const val FIRST_UUID = "11111111-2222-4333-8444-555555555555"
        private const val SECOND_UUID = "22222222-2222-4333-8444-555555555555"
        private const val FOREIGN_UUID = "33333333-2222-4333-8444-555555555555"
        private const val STREAM_UUID = "44444444-2222-4333-8444-555555555555"
        private const val TOPIC_UUID = "55555555-2222-4333-8444-555555555555"
    }
}
