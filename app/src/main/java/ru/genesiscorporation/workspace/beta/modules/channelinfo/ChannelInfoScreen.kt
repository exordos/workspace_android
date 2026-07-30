package ru.genesiscorporation.workspace.beta.modules.channelinfo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

@Composable
fun ChannelInfoScreen(
    viewModel: ChannelInfoViewModel,
    navController: NavHostController,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val muteInProgress by viewModel.muteInProgress.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val memberActionInProgress by viewModel.memberActionInProgress.collectAsStateWithLifecycle()
    val memberLoadError by viewModel.memberLoadError.collectAsStateWithLifecycle()
    val availableUsers by viewModel.availableUsers.collectAsStateWithLifecycle()
    val leftStream by viewModel.leftStream.collectAsStateWithLifecycle()
    val lastMemberActionResult by
        viewModel.lastMemberActionResult.collectAsStateWithLifecycle()
    val baseUrl by viewModel.client.userViewModel.baseUrl.collectAsState()
    val currentUserUuid by
        viewModel.client.userViewModel.userId.collectAsStateWithLifecycle()
    val onlineCount = members.count {
        it.user.status == "active" || it.user.status == "online"
    }
    var showAddMembers by rememberSaveable { mutableStateOf(false) }
    var memberToRemoveUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMemberActionRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    val memberToRemove = memberToRemoveUuid?.let { targetUuid ->
        members.firstOrNull { it.user.uuid == targetUuid }
    }

    LaunchedEffect(leftStream) {
        if (leftStream) navController.popBackStack()
    }
    LaunchedEffect(lastMemberActionResult, pendingMemberActionRequestId) {
        val result = lastMemberActionResult ?: return@LaunchedEffect
        if (result.requestId != pendingMemberActionRequestId) {
            return@LaunchedEffect
        }
        pendingMemberActionRequestId = null
        if (!result.success) return@LaunchedEffect
        when (result.kind) {
            MemberActionKind.ADD -> showAddMembers = false
            MemberActionKind.REMOVE -> memberToRemoveUuid = null
            else -> Unit
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            BackRow(onClick = navController::popBackStack)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarUrn = stream?.avatar,
                    baseUrl = baseUrl.orEmpty(),
                    color = stream?.color,
                    name = stream?.name.orEmpty(),
                    size = 64,
                    hasPadding = false,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stream?.name ?: "Информация о канале",
                        color = colors.textHeaders,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${members.size} участников, $onlineCount в сети",
                        color = colors.textAdditional30,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoActionButton(
                    icon = R.drawable.ic_figma_channel_mute,
                    description = if (stream?.notificationMode == "muted") {
                        "Включить уведомления"
                    } else {
                        "Отключить уведомления"
                    },
                    onClick = viewModel::toggleMuted,
                    enabled = !muteInProgress && stream != null,
                    modifier = Modifier.weight(1f),
                )
            }
            if (actionError != null) {
                Text(
                    text = actionError.orEmpty(),
                    color = colors.indicatorRed,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(
                        text = "Участники",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled =
                            !memberActionInProgress && memberLoadError == null,
                        onClick = { showAddMembers = true },
                    ) {
                        Text("Добавить")
                    }
                }
                memberLoadError?.let { error ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            color = colors.indicatorRed,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )
                        TextButton(
                            enabled = !memberActionInProgress,
                            onClick = viewModel::retryMembers,
                        ) {
                            Text("Повторить")
                        }
                    }
                }
                members.forEach { member ->
                    ChannelMemberCard(
                        member = member,
                        baseUrl = baseUrl.orEmpty(),
                        canRemove = canRemoveChannelMember(
                            memberUserUuid = member.user.uuid,
                            currentUserUuid = currentUserUuid,
                            ownerUuid = stream?.owner,
                        ),
                        isCurrentUser =
                            member.user.uuid == currentUserUuid,
                        removalInProgress = memberActionInProgress,
                        onRemove = { memberToRemoveUuid = member.user.uuid },
                    )
                }
            }
        }
    }

    if (showAddMembers) {
        AddChannelMembersDialog(
            streamName = stream?.name.orEmpty(),
            users = availableUsers,
            baseUrl = baseUrl.orEmpty(),
            busy = memberActionInProgress,
            onDismiss = { showAddMembers = false },
            onSubmit = { selected ->
                if (
                    !memberActionInProgress &&
                    pendingMemberActionRequestId == null
                ) {
                    pendingMemberActionRequestId = viewModel.addMembers(selected)
                }
            },
        )
    }
    memberToRemove?.let { member ->
        val isCurrentUser =
            member.user.uuid == currentUserUuid
        AlertDialog(
            onDismissRequest = {
                if (!memberActionInProgress) memberToRemoveUuid = null
            },
            title = {
                Text(if (isCurrentUser) "Покинуть канал?" else "Удалить участника?")
            },
            text = {
                Text(
                    if (isCurrentUser) {
                        "Канал исчезнет из вашего списка чатов."
                    } else {
                        "${member.user.displayableName()} будет удалён из канала."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !memberActionInProgress,
                    onClick = {
                        if (pendingMemberActionRequestId == null) {
                            pendingMemberActionRequestId =
                                viewModel.removeMember(member)
                        }
                    },
                ) {
                    Text(
                        text = if (memberActionInProgress) "Удаление…" else {
                            if (isCurrentUser) "Покинуть" else "Удалить"
                        },
                        color = colors.indicatorRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !memberActionInProgress,
                    onClick = { memberToRemoveUuid = null },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun BackRow(onClick: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .height(20.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Назад",
            color = colors.textHeaders.copy(alpha = 0.8f),
            fontFamily = NavigationFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun InfoActionButton(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.infoCardBackground)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (enabled) colors.iconBase else colors.iconDisable,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = LocalWorkspaceColorsPalette.current.textHeaders,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun ChannelMemberCard(
    member: ChannelMember,
    baseUrl: String,
    canRemove: Boolean,
    isCurrentUser: Boolean,
    removalInProgress: Boolean,
    onRemove: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.infoCardBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = member.user.avatar,
            baseUrl = baseUrl,
            color = null,
            name = member.user.displayableName(),
            size = 30,
            hasPadding = false,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = member.user.displayableName(),
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.user.statusText?.takeIf(String::isNotBlank)
                    ?: presenceLabel(member.user.status),
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (member.role != "member") {
            Text(
                text = roleLabel(member.role),
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 20.sp,
            )
        } else {
            Spacer(Modifier.size(1.dp))
        }
        if (canRemove) {
            TextButton(
                enabled = !removalInProgress,
                onClick = onRemove,
            ) {
                Text(
                    text = if (isCurrentUser) {
                        "Выйти"
                    } else {
                        "Удалить"
                    },
                    color = colors.indicatorRed,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AddChannelMembersDialog(
    streamName: String,
    users: List<UserResponseData>,
    baseUrl: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Set<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedUserUuids by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val colors = LocalWorkspaceColorsPalette.current
    val visibleUsers = remember(users, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            users
        } else {
            users.filter { user ->
                user.displayableName().contains(normalizedQuery, ignoreCase = true) ||
                    user.email?.contains(normalizedQuery, ignoreCase = true) == true ||
                    user.username.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = {
            Text(
                text = "Добавить в «$streamName»",
                color = colors.textHeaders,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Поиск") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (visibleUsers.isEmpty()) {
                    Text(
                        text = if (users.isEmpty()) {
                            "Все доступные пользователи уже в канале"
                        } else {
                            "Ничего не найдено"
                        },
                        color = colors.textAdditional50,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        items(
                            items = visibleUsers,
                            key = { it.uuid },
                        ) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        selectedUserUuids = if (
                                            user.uuid in selectedUserUuids
                                        ) {
                                            selectedUserUuids - user.uuid
                                        } else {
                                            selectedUserUuids + user.uuid
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = user.uuid in selectedUserUuids,
                                    onCheckedChange = null,
                                    enabled = !busy,
                                )
                                Avatar(
                                    avatarUrn = user.avatar,
                                    baseUrl = baseUrl,
                                    color = null,
                                    name = user.displayableName(),
                                    size = 32,
                                    hasPadding = false,
                                )
                                Text(
                                    text = user.displayableName(),
                                    color = colors.textHeaders,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedUserUuids.isNotEmpty() && !busy,
                onClick = { onSubmit(selectedUserUuids.toSet()) },
            ) {
                Text(
                    if (busy) {
                        "Добавление…"
                    } else {
                        "Добавить (${selectedUserUuids.size})"
                    },
                )
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

private fun roleLabel(role: String): String = when (role) {
    "owner" -> "Владелец"
    "administrator" -> "Администратор"
    "moderator" -> "Модератор"
    "guest" -> "Гость"
    else -> ""
}

private fun presenceLabel(status: String): String = when (status) {
    "active", "online" -> "В сети"
    "idle" -> "Нет на месте"
    "dnd", "do_not_disturb" -> "Не беспокоить"
    else -> "Не в сети"
}
