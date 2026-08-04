package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun TopicNameDialog(
    title: String,
    initialName: String,
    busy: Boolean,
    submitLabel: String = "Сохранить",
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val normalizedName = name.trim()
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = {
            Text(
                text = title,
                color = colors.textHeaders,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = { Text("Название темы") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotEmpty() && !busy,
                onClick = { onSubmit(normalizedName) },
            ) {
                Text(if (busy) "Сохранение…" else submitLabel)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Отмена")
            }
        },
    )
}

@Composable
fun TopicActionsDialog(
    expanded: Boolean,
    topic: TopicsResponseData,
    streamNotificationMode: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleDone: () -> Unit,
    onSetNotificationMode: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        modifier = Modifier.width(278.dp),
    ) {
        NotificationModeSelector(
            options = topicNotificationModeOptions(
                streamNotificationMode = streamNotificationMode,
                selectedMode = topic.notificationMode,
            ),
            selectedMode = topic.notificationMode,
            onModeSelected = onSetNotificationMode,
            enabled = !busy,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (topic.unreadCount > 0) {
            DropdownMenuItem(
                text = { Text("Отметить прочитанным") },
                onClick = onMarkRead,
                enabled = !busy,
                leadingIcon = {
                    MenuIcon(R.drawable.ic_done_all)
                },
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    if (topic.isDone) {
                        "Убрать отметку выполненной темы"
                    } else {
                        "Отметить тему как выполненную"
                    },
                )
            },
            onClick = onToggleDone,
            enabled = !busy,
            leadingIcon = {
                MenuIcon(R.drawable.ic_check)
            },
        )
        DropdownMenuItem(
            text = { Text("Переименовать тему") },
            onClick = onRename,
            enabled = !busy,
            leadingIcon = {
                MenuIcon(R.drawable.ic_message_edit)
            },
        )
    }
}

@Composable
private fun MenuIcon(
    drawable: Int,
) {
    Icon(
        painter = painterResource(drawable),
        contentDescription = null,
        tint = LocalWorkspaceColorsPalette.current.iconBase,
        modifier = Modifier.size(22.dp),
    )
}
