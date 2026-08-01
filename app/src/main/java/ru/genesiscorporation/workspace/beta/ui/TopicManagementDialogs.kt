package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    topic: TopicsResponseData,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleDone: () -> Unit,
    onSetNotificationMode: (String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = {
            Text(
                text = topic.name,
                color = colors.textHeaders,
            )
        },
        text = {
            Column {
                Text(
                    text = "Уведомления",
                    color = colors.textAdditional50,
                )
                TOPIC_NOTIFICATION_MODES.forEach { mode ->
                    TextButton(
                        enabled = !busy,
                        onClick = { onSetNotificationMode(mode.value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = topic.notificationMode == mode.value,
                            onClick = null,
                            enabled = !busy,
                        )
                        Text(
                            text = mode.label,
                            color = colors.textHeaders,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (topic.unreadCount > 0) {
                    TextButton(
                        enabled = !busy,
                        onClick = onMarkRead,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Отметить прочитанным",
                            color = colors.textHeaders,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextButton(
                    enabled = !busy,
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Переименовать",
                        color = colors.textHeaders,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    enabled = !busy,
                    onClick = onToggleDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (topic.isDone) {
                            "Вернуть в работу"
                        } else {
                            "Отметить выполненным"
                        },
                        color = colors.textHeaders,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Закрыть")
            }
        },
    )
}

private data class TopicNotificationMode(
    val value: String,
    val label: String,
)

private val TOPIC_NOTIFICATION_MODES = listOf(
    TopicNotificationMode("default", "Как для всего чата"),
    TopicNotificationMode("follow", "Отслеживать"),
    TopicNotificationMode("unmute", "Всегда уведомлять"),
    TopicNotificationMode("mute", "Без уведомлений"),
)
