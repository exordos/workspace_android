package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun DeleteMessageMenuItem(isDeleting: Boolean, onClick: () -> Unit) {
    MessageActionMenuItem(
        label = stringResource(if (isDeleting) R.string.message_deleting else R.string.message_delete),
        iconId = R.drawable.ic_item_delete,
        modifier = Modifier.testTag("delete-message-action"),
        enabled = !isDeleting,
        onClick = onClick,
    )
}

/** Keeps confirmation separate from the long-press action and the network request. */
@Composable
internal fun MessageDeletionControls(
    messageUuid: String,
    canDelete: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    content: @Composable (requestDelete: () -> Unit) -> Unit,
) {
    var confirmDeletion by remember(messageUuid) { mutableStateOf(false) }
    content {
        if (canDelete && !isDeleting) confirmDeletion = true
    }
    if (confirmDeletion && canDelete) {
        DeleteMessageConfirmationDialog(
            isDeleting = isDeleting,
            onDismiss = { confirmDeletion = false },
            onConfirm = {
                if (!isDeleting) {
                    confirmDeletion = false
                    onDelete()
                }
            },
        )
    }
}

@Composable
internal fun DeleteMessageConfirmationDialog(
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    messageCount: Int = 1,
) {
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.messageBackground,
        titleContentColor = colors.textHeaders,
        textContentColor = colors.textHeaders,
        title = {
            Text(
                if (messageCount == 1) stringResource(R.string.message_delete_title)
                else pluralStringResource(R.plurals.messages_delete_title, messageCount, messageCount),
                fontFamily = InterFontFamily,
            )
        },
        text = {
            Text(
                stringResource(if (messageCount == 1) R.string.message_delete_confirmation else R.string.messages_delete_confirmation),
                fontFamily = InterFontFamily,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-message-deletion")) {
                Text(stringResource(R.string.message_delete_cancel), color = colors.textHeaders, fontFamily = InterFontFamily)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isDeleting,
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-message-deletion"),
            ) {
                Text(
                    stringResource(if (isDeleting) R.string.message_deleting else R.string.message_delete),
                    color = if (isDeleting) colors.textAdditional30 else colors.textHeaders,
                    fontFamily = InterFontFamily,
                )
            }
        },
    )
}

@Composable
internal fun MessageDeletionMenu(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onForwardMessage: ((MessageResponse) -> Unit)? = null,
) {
    val deletingMessageUuids by viewModel.deletingMessageUuids.collectAsStateWithLifecycle()
    val canDelete = viewModel.canDeleteMessage(item)
    val isDeleting = item.uuid in deletingMessageUuids
    MessageDeletionControls(
        messageUuid = item.uuid,
        canDelete = canDelete,
        isDeleting = isDeleting,
        onDelete = { viewModel.deleteMessage(item) },
    ) { requestDelete ->
        MessageActionMenu(
            expanded = expanded && viewModel.canSelectMessage(item),
            onDismissRequest = onDismissRequest,
            canDelete = canDelete,
            isDeleting = isDeleting,
            onDelete = {
                onDismissRequest()
                requestDelete()
            },
            onForward = if (viewModel.canSelectMessage(item) && onForwardMessage != null) {
                { onForwardMessage(item) }
            } else null,
            onSelect = if (viewModel.canSelectMessage(item)) {
                { viewModel.startMessageSelection(item) }
            } else null,
        )
    }
}

@Composable
internal fun MessageActionErrorEffect(
    error: MessageActionError?,
    snackbarHostState: SnackbarHostState,
    onErrorShown: (MessageActionError) -> Unit,
) {
    val message = error?.let { stringResource(it.resourceId, *it.formatArgs.toTypedArray()) }
    LaunchedEffect(error?.token, message) {
        if (error != null && message != null) {
            snackbarHostState.showSnackbar(message)
            onErrorShown(error)
        }
    }
}
