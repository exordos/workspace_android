package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

/** Message actions shared by text, attachment and call bubbles. */
@Composable
internal fun MessageActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    canDelete: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onReaction: ((String) -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onAddQuote: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(252.dp),
        shape = RoundedCornerShape(8.dp),
        containerColor = LocalWorkspaceColorsPalette.current.messageBackground,
    ) {
        if (onReaction != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("👍", "❤️", "😂", "😮", "😢").forEach { emoji ->
                    TextButton(
                        onClick = {
                            onReaction(emoji)
                            onDismissRequest()
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
            }
        }
        if (onReply != null) {
            HorizontalDivider()
            MessageActionMenuItem(
                label = "Ответить",
                iconId = R.drawable.ic_quote,
                onClick = {
                    onReply()
                    onDismissRequest()
                },
            )
            if (onAddQuote != null) {
                MessageActionMenuItem(
                    label = "Добавить цитату",
                    iconId = R.drawable.ic_add_quote,
                    onClick = {
                        onAddQuote()
                        onDismissRequest()
                    },
                )
            }
            if (onEdit != null) {
                MessageActionMenuItem(
                    label = "Изменить",
                    iconId = R.drawable.ic_edit,
                    onClick = {
                        onEdit()
                        onDismissRequest()
                    },
                )
            }
        }
        if (onForward != null) {
            MessageActionMenuItem(
                label = stringResource(R.string.messages_forward),
                iconId = R.drawable.ic_message_forward,
                modifier = Modifier.testTag("forward-message-action"),
                enabled = !isDeleting,
                onClick = {
                    onForward()
                    onDismissRequest()
                },
            )
        }
        if (canDelete) {
            DeleteMessageMenuItem(isDeleting = isDeleting, onClick = onDelete)
        }
        if (onSelect != null) {
            MessageActionMenuItem(
                label = stringResource(R.string.message_select),
                iconId = R.drawable.ic_message_select,
                modifier = Modifier.testTag("select-message-action"),
                enabled = !isDeleting,
                onClick = {
                    onSelect()
                    onDismissRequest()
                },
            )
        }
    }
}

/** Figma's common 12dp inset, 28dp icon slot and 12dp text gap. */
@Composable
internal fun MessageActionMenuItem(
    label: String,
    iconId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalWorkspaceColorsPalette.current
    DropdownMenuItem(
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp),
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(iconId),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (enabled) colors.iconBase else colors.iconDisable,
                )
                Text(
                    text = label,
                    color = if (enabled) colors.textHeaders else colors.textAdditional30,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        onClick = onClick,
    )
}
