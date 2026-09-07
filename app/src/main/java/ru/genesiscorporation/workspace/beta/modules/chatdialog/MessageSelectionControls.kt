package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

/** A full-row selection target also intercepts taps on links, attachments and calls. */
@Composable
internal fun SelectableMessageRow(
    messageUuid: String,
    description: String,
    selectionMode: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!selectionMode) {
        content()
        return
    }
    val selectionDescription = stringResource(R.string.message_selection_description, description)
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.width(40.dp).height(16.dp), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(if (selected) R.drawable.ic_checkbox_filled else R.drawable.ic_checkbox_empty),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(Modifier.weight(1f)) { content() }
        }
        Box(
            Modifier.matchParentSize()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("select-message-$messageUuid")
                .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { contentDescription = selectionDescription },
        )
    }
}

@Composable
internal fun MessageSelectionBar(
    selectedMessageUuids: Set<String>,
    canDelete: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onForward: () -> Unit,
    isForwarding: Boolean = false,
    canForward: Boolean = true,
) {
    val colors = LocalWorkspaceColorsPalette.current
    var confirmDeletion by remember(selectedMessageUuids) { mutableStateOf(false) }
    val busy = isDeleting || isForwarding
    Column(Modifier.fillMaxWidth().background(colors.background).testTag("message-selection-bar")) {
        HorizontalDivider(color = colors.divider)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                pluralStringResource(R.plurals.messages_selected_count, selectedMessageUuids.size, selectedMessageUuids.size),
                color = colors.textAdditional50,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            )
            SelectionActionsLayout(
                deleteButton = {
                    SelectionActionButton(
                        label = stringResource(if (isDeleting) R.string.message_deleting else R.string.message_delete),
                        enabled = canDelete && !busy,
                        tag = "delete-selected-messages",
                        icon = R.drawable.ic_item_delete,
                        iconTint = Color(0xFFF04C4C),
                        onClick = { confirmDeletion = true },
                    )
                },
                cancelButton = {
                    SelectionActionButton(
                        label = stringResource(R.string.message_delete_cancel),
                        enabled = !busy,
                        tag = "cancel-message-selection",
                        onClick = onCancel,
                    )
                },
                forwardButton = {
                    SelectionActionButton(
                        label = stringResource(R.string.messages_forward),
                        enabled = canForward && selectedMessageUuids.isNotEmpty() && !busy,
                        tag = "forward-selected-messages",
                        icon = R.drawable.ic_message_forward,
                        iconTint = colors.iconBase,
                        onClick = onForward,
                    )
                },
            )
            if (!canForward && selectedMessageUuids.size > MAX_FORWARD_SOURCE_MESSAGES) {
                Text(
                    stringResource(R.string.messages_forward_limit, MAX_FORWARD_SOURCE_MESSAGES),
                    color = colors.textAdditional50,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (!canDelete && !busy && selectedMessageUuids.isNotEmpty()) {
                Text(
                    stringResource(R.string.messages_selection_own_only),
                    color = colors.textAdditional50,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
    if (confirmDeletion && canDelete) {
        DeleteMessageConfirmationDialog(
            isDeleting = busy,
            onDismiss = { confirmDeletion = false },
            onConfirm = {
                if (!busy) {
                    confirmDeletion = false
                    onDelete()
                }
            },
            messageCount = selectedMessageUuids.size,
        )
    }
}

@Composable
private fun SelectionActionButton(
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    icon: Int? = null,
    iconTint: Color = Color.Unspecified,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.clip(shape)
                .background(colors.messageBackground)
                .border(BorderStroke(1.dp, colors.indicatorGrey), shape)
                .heightIn(min = 36.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (enabled) iconTint else colors.iconDisable,
                )
            }
            Text(
                label,
                color = if (enabled) colors.textHeaders else colors.textAdditional30,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SelectionActionsLayout(
    deleteButton: @Composable () -> Unit,
    cancelButton: @Composable () -> Unit,
    forwardButton: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep every action reachable on narrow screens and with larger system text.
        if (maxWidth < 340.dp || fontScale > 1.3f) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    deleteButton()
                    cancelButton()
                }
                forwardButton()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                deleteButton()
                Spacer(Modifier.weight(1f))
                cancelButton()
                forwardButton()
            }
        }
    }
}
