package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    busy: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleDone: () -> Unit,
    onSetNotificationMode: (String) -> Unit,
) {
    WorkspaceContextMenu(
        expanded = expanded,
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        width = 278.dp,
    ) {
        NotificationModeSelector(
            options = topicNotificationModeOptions(topic.notificationMode),
            selectedMode = topic.notificationMode,
            onModeSelected = onSetNotificationMode,
            enabled = !busy,
            modifier = Modifier.padding(4.dp),
        )
        if (topic.unreadCount > 0) {
            WorkspaceMenuActionRow(
                text = "Отметить всё как прочитанное",
                iconRes = R.drawable.ic_menu_check,
                onClick = onMarkRead,
                enabled = !busy,
            )
        }
        WorkspaceMenuActionRow(
            text = if (topic.isDone) {
                "Убрать отметку выполненной темы"
            } else {
                "Отметить тему как выполненную"
            },
            iconRes = R.drawable.ic_menu_flag,
            onClick = onToggleDone,
            enabled = !busy,
        )
        WorkspaceMenuActionRow(
            text = "Переименовать тему",
            iconRes = R.drawable.ic_menu_pen,
            onClick = onRename,
            enabled = !busy,
        )
    }
}
