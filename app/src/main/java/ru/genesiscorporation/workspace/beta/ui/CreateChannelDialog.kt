package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

data class CreateChannelInput(
    val name: String,
    val description: String,
    val inviteOnly: Boolean,
    val announce: Boolean,
    val memberUserUuids: Set<String>,
)

@Composable
fun CreateChannelDialog(
    users: List<UserResponseData>,
    currentUserUuid: String?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (CreateChannelInput) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var inviteOnly by rememberSaveable { mutableStateOf(false) }
    var announce by rememberSaveable { mutableStateOf(false) }
    var selectedUserUuids by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val colors = LocalWorkspaceColorsPalette.current
    val selectableUserUuids = remember(users, currentUserUuid) {
        users
            .asSequence()
            .map { it.uuid }
            .filterNot { it == currentUserUuid }
            .toSet()
    }
    val candidateUsers = remember(users, currentUserUuid, query) {
        val normalizedQuery = query.trim()
        users
            .asSequence()
            .filterNot { it.uuid == currentUserUuid }
            .filter { user ->
                normalizedQuery.isEmpty() ||
                    user.displayableName().contains(normalizedQuery, ignoreCase = true) ||
                    user.username.contains(normalizedQuery, ignoreCase = true) ||
                    user.email?.contains(normalizedQuery, ignoreCase = true) == true
            }
            .sortedBy { it.displayableName().lowercase() }
            .toList()
    }
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = {
            Text(
                text = "Создать канал",
                color = colors.textHeaders,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !busy,
                        singleLine = true,
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!error.isNullOrBlank()) {
                    item {
                        Text(
                            text = error,
                            color = colors.indicatorRed,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        enabled = !busy,
                        label = { Text("Описание") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                item {
                    ToggleRow(
                        checked = inviteOnly,
                        enabled = !busy,
                        label = "Закрытый канал",
                        onToggle = { inviteOnly = !inviteOnly },
                    )
                }
                item {
                    ToggleRow(
                        checked = announce,
                        enabled = !busy,
                        label = "Сообщить участникам о создании",
                        onToggle = { announce = !announce },
                    )
                }
                item {
                    Text(
                        text = "Участники",
                        color = colors.textAdditional50,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        enabled = !busy,
                        singleLine = true,
                        label = { Text("Поиск пользователей") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (candidateUsers.isEmpty()) {
                    item {
                        Text(
                            text = if (query.isBlank()) {
                                "Нет доступных пользователей"
                            } else {
                                "Ничего не найдено"
                            },
                            color = colors.textAdditional50,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(
                        items = candidateUsers,
                        key = { it.uuid },
                    ) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    selectedUserUuids =
                                        if (user.uuid in selectedUserUuids) {
                                            selectedUserUuids - user.uuid
                                        } else {
                                            selectedUserUuids + user.uuid
                                        }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = user.uuid in selectedUserUuids,
                                enabled = !busy,
                                onCheckedChange = null,
                            )
                            Text(
                                text = user.displayableName(),
                                color = colors.textHeaders,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    onSubmit(
                        CreateChannelInput(
                            name = name.trim(),
                            description = description.trim(),
                            inviteOnly = inviteOnly,
                            announce = announce,
                            memberUserUuids = selectedUserUuids
                                .filterTo(mutableSetOf()) {
                                    it in selectableUserUuids
                                },
                        ),
                    )
                },
            ) {
                Text(if (busy) "Создание…" else "Создать")
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
private fun ToggleRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
        Text(
            text = label,
            color = LocalWorkspaceColorsPalette.current.textHeaders,
        )
    }
}
