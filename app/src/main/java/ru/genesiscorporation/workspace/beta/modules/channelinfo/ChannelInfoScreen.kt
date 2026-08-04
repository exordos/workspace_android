package ru.genesiscorporation.workspace.beta.modules.channelinfo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.NotificationModeSelector
import ru.genesiscorporation.workspace.beta.ui.STREAM_NOTIFICATION_MODE_OPTIONS
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

private enum class ChannelInfoPanel {
    INFO,
    EDIT,
    TYPE,
    MEMBERS,
}

@Composable
fun ChannelInfoScreen(
    viewModel: ChannelInfoViewModel,
    navController: NavHostController,
    onBottomNavigationVisibilityChange: (Boolean) -> Unit,
) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val muteInProgress by viewModel.muteInProgress.collectAsStateWithLifecycle()
    val channelActionInProgress by
        viewModel.channelActionInProgress.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val memberActionInProgress by
        viewModel.memberActionInProgress.collectAsStateWithLifecycle()
    val memberLoadError by viewModel.memberLoadError.collectAsStateWithLifecycle()
    val availableUsers by viewModel.availableUsers.collectAsStateWithLifecycle()
    val leftStream by viewModel.leftStream.collectAsStateWithLifecycle()
    val deletedStream by viewModel.deletedStream.collectAsStateWithLifecycle()
    val lastMemberActionResult by
        viewModel.lastMemberActionResult.collectAsStateWithLifecycle()
    val baseUrl by viewModel.client.userViewModel.baseUrl.collectAsState()
    val currentUserUuid by
        viewModel.client.userViewModel.userId.collectAsStateWithLifecycle()

    var panel by rememberSaveable { mutableStateOf(ChannelInfoPanel.INFO) }
    var showAddMembers by rememberSaveable { mutableStateOf(false) }
    var selectedMemberUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var memberToRemoveUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteChannel by rememberSaveable { mutableStateOf(false) }
    var pendingMemberActionRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    val selectedMember = selectedMemberUuid?.let { targetUuid ->
        members.firstOrNull { it.user.uuid == targetUuid }
    }
    val memberToRemove = memberToRemoveUuid?.let { targetUuid ->
        members.firstOrNull { it.user.uuid == targetUuid }
    }
    val canManage = canManageChannel(stream?.role)
    val canDelete = canDeleteChannel(
        currentUserUuid = currentUserUuid,
        ownerUuid = stream?.owner,
    )

    LaunchedEffect(panel) {
        onBottomNavigationVisibilityChange(panel != ChannelInfoPanel.INFO)
    }
    LaunchedEffect(leftStream, deletedStream) {
        if (leftStream || deletedStream) navController.popBackStack()
    }
    LaunchedEffect(lastMemberActionResult, pendingMemberActionRequestId) {
        val result = lastMemberActionResult ?: return@LaunchedEffect
        if (result.requestId != pendingMemberActionRequestId) return@LaunchedEffect
        pendingMemberActionRequestId = null
        if (!result.success) return@LaunchedEffect
        when (result.kind) {
            MemberActionKind.ADD -> showAddMembers = false
            MemberActionKind.REMOVE -> {
                memberToRemoveUuid = null
                selectedMemberUuid = null
            }
        }
    }

    when (panel) {
        ChannelInfoPanel.INFO -> ChannelOverview(
            stream = stream,
            members = members,
            baseUrl = baseUrl.orEmpty(),
            canManage = canManage,
            muteInProgress = muteInProgress,
            memberLoadError = memberLoadError,
            actionError = actionError,
            onBack = navController::popBackStack,
            onNotificationModeSelected = viewModel::setNotificationMode,
            onOpenEdit = { panel = ChannelInfoPanel.EDIT },
            onOpenMembers = { panel = ChannelInfoPanel.MEMBERS },
            onAddMembers = { showAddMembers = true },
            onRetryMembers = viewModel::retryMembers,
        )

        ChannelInfoPanel.EDIT -> EditChannelPanel(
            stream = stream,
            membersCount = members.size,
            baseUrl = baseUrl.orEmpty(),
            busy = channelActionInProgress,
            error = actionError,
            canDelete = canDelete,
            onBack = { panel = ChannelInfoPanel.INFO },
            onClose = { panel = ChannelInfoPanel.INFO },
            onSave = viewModel::updateChannelDetails,
            onOpenType = { panel = ChannelInfoPanel.TYPE },
            onOpenMembers = { panel = ChannelInfoPanel.MEMBERS },
            onDelete = { showDeleteChannel = true },
        )

        ChannelInfoPanel.TYPE -> ChannelTypePanel(
            stream = stream,
            busy = channelActionInProgress,
            error = actionError,
            onBack = { panel = ChannelInfoPanel.EDIT },
            onClose = { panel = ChannelInfoPanel.INFO },
            onSelected = viewModel::updateChannelVisibility,
        )

        ChannelInfoPanel.MEMBERS -> ChannelMembersPanel(
            members = members,
            baseUrl = baseUrl.orEmpty(),
            canManage = canManage,
            currentUserUuid = currentUserUuid,
            selectedMember = selectedMember,
            busy = memberActionInProgress,
            loadError = memberLoadError,
            actionError = actionError,
            onBack = { panel = ChannelInfoPanel.EDIT },
            onClose = { panel = ChannelInfoPanel.INFO },
            onAdd = { showAddMembers = true },
            onRetry = viewModel::retryMembers,
            onSelectMember = { member ->
                if (viewModel.canRemoveMember(member)) {
                    selectedMemberUuid = if (selectedMemberUuid == member.user.uuid) {
                        null
                    } else {
                        member.user.uuid
                    }
                }
            },
            onRemove = { memberToRemoveUuid = it.user.uuid },
            onChangeRole = { member, role ->
                if (pendingMemberActionRequestId == null) {
                    pendingMemberActionRequestId =
                        viewModel.updateMemberRole(member, role)
                }
            },
        )
    }

    if (showAddMembers) {
        AddChannelMembersDialog(
            streamName = stream?.name.orEmpty(),
            users = availableUsers,
            baseUrl = baseUrl.orEmpty(),
            busy = memberActionInProgress,
            onDismiss = { showAddMembers = false },
            onSubmit = { selected ->
                if (!memberActionInProgress && pendingMemberActionRequestId == null) {
                    pendingMemberActionRequestId = viewModel.addMembers(selected)
                }
            },
        )
    }
    memberToRemove?.let { member ->
        val isCurrentUser = member.user.uuid == currentUserUuid
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
                        text = if (memberActionInProgress) {
                            if (isCurrentUser) "Выход…" else "Удаление…"
                        } else if (isCurrentUser) {
                            "Покинуть"
                        } else {
                            "Удалить"
                        },
                        color = LocalWorkspaceColorsPalette.current.indicatorRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !memberActionInProgress,
                    onClick = { memberToRemoveUuid = null },
                ) { Text("Отмена") }
            },
        )
    }
    if (showDeleteChannel) {
        AlertDialog(
            onDismissRequest = {
                if (!channelActionInProgress) showDeleteChannel = false
            },
            title = { Text("Удалить канал?") },
            text = {
                Text(
                    "Канал «${stream?.name.orEmpty()}» и его история будут удалены.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !channelActionInProgress,
                    onClick = {
                        showDeleteChannel = false
                        viewModel.deleteChannel()
                    },
                ) {
                    Text(
                        text = "Удалить",
                        color = LocalWorkspaceColorsPalette.current.indicatorRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !channelActionInProgress,
                    onClick = { showDeleteChannel = false },
                ) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ChannelOverview(
    stream: Stream?,
    members: List<ChannelMember>,
    baseUrl: String,
    canManage: Boolean,
    muteInProgress: Boolean,
    memberLoadError: String?,
    actionError: String?,
    onBack: () -> Unit,
    onNotificationModeSelected: (String) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenMembers: () -> Unit,
    onAddMembers: () -> Unit,
    onRetryMembers: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val onlineCount = members.count {
        it.user.status == "active" || it.user.status == "online"
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ChannelOverviewHeader(onBack = onBack)
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarUrn = stream?.avatar,
                    baseUrl = baseUrl,
                    color = stream?.color,
                    name = stream?.name.orEmpty(),
                    size = 58,
                    hasPadding = false,
                )
                Column(
                    modifier = Modifier.padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stream?.name ?: "Канал",
                        color = colors.textHeaders,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${members.size} участников, $onlineCount в сети",
                        color = colors.textAdditional50,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelActionTile(
                    icon = R.drawable.ic_figma_channel_call,
                    label = "Позвонить",
                    description = "Позвонить в канал",
                    enabled = false,
                    onClick = null,
                    modifier = Modifier.weight(1f),
                )
                ChannelActionTile(
                    icon = R.drawable.ic_figma_channel_settings,
                    label = "Настройки",
                    description = if (canManage) {
                        "Редактировать канал"
                    } else {
                        "Недостаточно прав для редактирования канала"
                    },
                    enabled = canManage,
                    onClick = onOpenEdit,
                    modifier = Modifier.weight(1f),
                )
                ChannelActionTile(
                    icon = R.drawable.ic_figma_channel_link,
                    label = "Поделиться",
                    description = "Поделиться каналом",
                    enabled = false,
                    onClick = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(text = "Уведомления")
                    Text(
                        text = "(Для всего чата)",
                        color = colors.textAdditional30,
                        fontSize = 12.sp,
                    )
                }
                NotificationModeSelector(
                    options = STREAM_NOTIFICATION_MODE_OPTIONS,
                    selectedMode = stream?.notificationMode ?: "all_messages",
                    onModeSelected = onNotificationModeSelected,
                    enabled = stream != null && !muteInProgress,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = colors.textAdditional50.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
                actionError?.let { InlineError(it) }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMembers)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(
                    text = "Участники",
                    modifier = Modifier.weight(1f),
                )
                if (canManage) {
                    Icon(
                        painter = painterResource(R.drawable.ic_figma_channel_add),
                        contentDescription = "Добавить участников",
                        tint = colors.iconBase,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAddMembers)
                            .padding(4.dp),
                    )
                }
            }
        }
        memberLoadError?.let { error ->
            item {
                ErrorWithRetry(
                    error = error,
                    busy = false,
                    onRetry = onRetryMembers,
                )
            }
        }
        items(members, key = { it.bindingUuid }) { member ->
            ChannelMemberRow(
                member = member,
                baseUrl = baseUrl,
                showChevron = false,
                enabled = false,
                onClick = null,
            )
        }
    }
}

@Composable
private fun EditChannelPanel(
    stream: Stream?,
    membersCount: Int,
    baseUrl: String,
    busy: Boolean,
    error: String?,
    canDelete: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSave: (String, String) -> Unit,
    onOpenType: () -> Unit,
    onOpenMembers: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    var name by rememberSaveable(stream?.uuid) {
        mutableStateOf(stream?.name.orEmpty())
    }
    var description by rememberSaveable(stream?.uuid) {
        mutableStateOf(stream?.description.orEmpty())
    }
    val normalizedName = name.trim()
    val normalizedDescription = description.trim()
    val changed = stream != null &&
        (stream.name != normalizedName || stream.description != normalizedDescription)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        ChannelTitleHeader(
            title = "Редактировать канал",
            onBack = onBack,
            onClose = onClose,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                avatarUrn = stream?.avatar,
                baseUrl = baseUrl,
                color = stream?.color,
                name = stream?.name.orEmpty(),
                size = 68,
                hasPadding = false,
            )
        }
        LabeledChannelField(
            label = "Название канала",
            value = name,
            placeholder = "Название",
            enabled = !busy,
            singleLine = true,
            onValueChange = { name = it.take(MAX_CHANNEL_NAME_CHARS) },
        )
        LabeledChannelField(
            label = "Описание (необязательно)",
            value = description,
            placeholder = "Описание",
            enabled = !busy,
            singleLine = false,
            onValueChange = { description = it.take(MAX_CHANNEL_DESCRIPTION_CHARS) },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (changed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    enabled = normalizedName.isNotEmpty() && !busy,
                    onClick = { onSave(normalizedName, normalizedDescription) },
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                    } else {
                        Text("Сохранить", color = colors.primary)
                    }
                }
            }
        }
        SettingsRow(
            icon = R.drawable.ic_figma_channel_settings,
            title = "Тип канала",
            value = stream?.let(::channelVisibilityLabel).orEmpty(),
            enabled = !busy,
            onClick = onOpenType,
        )
        SettingsRow(
            icon = R.drawable.ic_menu_group,
            title = "Участники",
            value = membersCount.toString(),
            enabled = !busy,
            onClick = onOpenMembers,
        )
        if (canDelete) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy, onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_message_delete),
                    contentDescription = null,
                    tint = colors.indicatorRed,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Удалить канал",
                    color = colors.indicatorRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
        error?.let { InlineError(it) }
    }
}

@Composable
private fun ChannelTypePanel(
    stream: Stream?,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSelected: (ChannelVisibility) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val serverVisibility = channelVisibility(
        inviteOnly = stream?.inviteOnly ?: false,
        isPrivate = stream?.isPrivate ?: false,
    )
    var selected by rememberSaveable(stream?.uuid) {
        mutableStateOf(serverVisibility)
    }
    LaunchedEffect(serverVisibility, busy) {
        if (!busy) selected = serverVisibility
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        ChannelTitleHeader(
            title = "Тип канала",
            onBack = onBack,
            onClose = onClose,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .border(
                    1.dp,
                    colors.textAdditional50.copy(alpha = 0.25f),
                    RoundedCornerShape(8.dp),
                )
                .padding(vertical = 4.dp),
        ) {
            ChannelTypeOption(
                title = "Открытый",
                description =
                    "Участники вашей организации могут просматривать сообщения " +
                        "и присоединяться",
                selected = selected == ChannelVisibility.OPEN,
                enabled = !busy,
                onClick = {
                    selected = ChannelVisibility.OPEN
                    onSelected(ChannelVisibility.OPEN)
                },
            )
            ChannelTypeOption(
                title = "Закрытый, открытая история переписки",
                description =
                    "Для присоединения и просмотра сообщений требуется приглашение",
                selected = selected == ChannelVisibility.CLOSED_OPEN_HISTORY,
                enabled = !busy,
                onClick = {
                    selected = ChannelVisibility.CLOSED_OPEN_HISTORY
                    onSelected(ChannelVisibility.CLOSED_OPEN_HISTORY)
                },
            )
            ChannelTypeOption(
                title = "Закрытый, защищенная история переписки",
                description =
                    "Для присоединения и просмотра сообщений требуется приглашение; " +
                        "пользователи могут видеть сообщения, отправленные в тот период, " +
                        "когда они были подписаны",
                selected = selected == ChannelVisibility.CLOSED_PROTECTED_HISTORY,
                enabled = !busy,
                onClick = {
                    selected = ChannelVisibility.CLOSED_PROTECTED_HISTORY
                    onSelected(ChannelVisibility.CLOSED_PROTECTED_HISTORY)
                },
            )
        }
        if (busy) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(18.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
        error?.let { InlineError(it) }
    }
}

@Composable
private fun ChannelMembersPanel(
    members: List<ChannelMember>,
    baseUrl: String,
    canManage: Boolean,
    currentUserUuid: String?,
    selectedMember: ChannelMember?,
    busy: Boolean,
    loadError: String?,
    actionError: String?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    onSelectMember: (ChannelMember) -> Unit,
    onRemove: (ChannelMember) -> Unit,
    onChangeRole: (ChannelMember, String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(12.dp),
    ) {
        item {
            ChannelTitleHeader(
                title = "Участники",
                onBack = onBack,
                onClose = onClose,
            )
        }
        item {
            ManagementActionRow(
                icon = R.drawable.ic_figma_channel_add,
                title = "Добавить участников",
                enabled = canManage && !busy,
                onClick = onAdd,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        item {
            ManagementActionRow(
                icon = R.drawable.ic_visibility_off,
                title = "Чёрный список/отключённые",
                description =
                    "Чёрный список и отключённые, только для просмотра; " +
                        "управление не поддерживается сервером",
                enabled = false,
                onClick = null,
            )
        }
        item {
            HorizontalDivider(
                color = colors.textAdditional50.copy(alpha = 0.2f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        loadError?.let { error ->
            item {
                ErrorWithRetry(error = error, busy = busy, onRetry = onRetry)
            }
        }
        items(members, key = { it.bindingUuid }) { member ->
            val isCurrentUser = member.user.uuid == currentUserUuid
            val manageable = isCurrentUser || (
                canManage && member.role != "owner"
            )
            ChannelMemberRow(
                member = member,
                baseUrl = baseUrl,
                showChevron = manageable,
                enabled = manageable && !busy,
                selected = selectedMember?.bindingUuid == member.bindingUuid,
                onClick = { onSelectMember(member) },
            )
            if (selectedMember?.bindingUuid == member.bindingUuid) {
                if (isCurrentUser) {
                    LeaveChannelEditor(
                        busy = busy,
                        onLeave = { onRemove(member) },
                    )
                } else {
                    MemberRoleEditor(
                        member = member,
                        busy = busy,
                        onRemove = { onRemove(member) },
                        onRoleSelected = { role -> onChangeRole(member, role) },
                    )
                }
            }
        }
        actionError?.let { error ->
            item { InlineError(error) }
        }
    }
}

@Composable
private fun ChannelOverviewHeader(onBack: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onBack)
            .padding(horizontal = 4.dp),
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
private fun ChannelTitleHeader(
    title: String,
    onBack: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = "Назад",
            tint = colors.iconBase,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(4.dp),
        )
        Text(
            text = title,
            color = colors.textHeaders,
            fontFamily = NavigationFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        onClose?.let { close ->
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = "Закрыть",
                tint = colors.iconBase,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = close)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun ChannelActionTile(
    @DrawableRes icon: Int,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.infoCardBackground)
            .then(
                onClick?.let { action ->
                    Modifier.clickable(enabled = enabled, onClick = action)
                } ?: Modifier,
            )
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) colors.iconBase else colors.iconDisable,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = label,
            color = if (enabled) colors.textAdditional50 else colors.textAdditional30,
            fontSize = 11.sp,
            lineHeight = 14.sp,
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
private fun LabeledChannelField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = colors.textAdditional50,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(colors.searchBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textAdditional30,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) colors.iconBase else colors.iconDisable,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Text(
            text = value,
            color = colors.textAdditional50,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_menu_chevron_right),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ChannelTypeOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primary,
                unselectedColor = colors.primary,
                disabledSelectedColor = colors.iconDisable,
                disabledUnselectedColor = colors.iconDisable,
            ),
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ManagementActionRow(
    @DrawableRes icon: Int,
    title: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                onClick?.let { action ->
                    Modifier.clickable(enabled = enabled, onClick = action)
                } ?: Modifier,
            )
            .semantics {
                description?.let { contentDescription = it }
                if (!enabled) disabled()
            }
            .alpha(if (enabled) 1f else 0.58f)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = colors.textAdditional50,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelMemberRow(
    member: ChannelMember,
    baseUrl: String,
    showChevron: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    selected: Boolean = false,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) colors.cardBackgroundActive else {
                    colors.background
                },
                shape = RoundedCornerShape(8.dp),
            )
            .then(
                onClick?.let { action ->
                    Modifier.clickable(enabled = enabled, onClick = action)
                } ?: Modifier,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
                .padding(start = 10.dp),
        ) {
            Text(
                text = member.user.displayableName(),
                color = colors.textHeaders,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.user.statusText?.takeIf(String::isNotBlank)
                    ?: presenceLabel(member.user.status),
                color = colors.textAdditional50,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        roleLabel(member.role).takeIf(String::isNotEmpty)?.let { role ->
            Text(
                text = role,
                color = colors.textAdditional50,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_menu_chevron_right),
                contentDescription = "Управление участником",
                tint = colors.iconBase,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
            )
        }
    }
    HorizontalDivider(
        color = colors.textAdditional50.copy(alpha = 0.16f),
        modifier = Modifier.padding(start = 48.dp),
    )
}

@Composable
private fun MemberRoleEditor(
    member: ChannelMember,
    busy: Boolean,
    onRemove: () -> Unit,
    onRoleSelected: (String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 4.dp),
    ) {
        EDITABLE_CHANNEL_MEMBER_ROLES.forEachIndexed { index, role ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !busy,
                        role = Role.RadioButton,
                        onClick = { onRoleSelected(role) },
                    )
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = member.role == role,
                    onClick = null,
                    enabled = !busy,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colors.primary,
                        unselectedColor = colors.primary,
                    ),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = roleAssignmentLabel(role),
                    color = colors.textHeaders,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (index == 0) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.ic_close_small),
                        contentDescription = "Удалить пользователя",
                        tint = colors.iconBase,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !busy, onClick = onRemove)
                            .padding(5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaveChannelEditor(
    busy: Boolean,
    onLeave: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = !busy,
            onClick = onLeave,
        ) {
            Text("Покинуть канал", color = colors.indicatorRed)
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
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                text = "Добавить в «$streamName»",
                color = colors.textHeaders,
            )
        },
        text = {
            Column {
                LabeledChannelField(
                    label = "Поиск",
                    value = query,
                    placeholder = "Имя или почта",
                    enabled = !busy,
                    singleLine = true,
                    onValueChange = { query = it.take(MAX_MEMBER_SEARCH_CHARS) },
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
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(visibleUsers, key = UserResponseData::uuid) { user ->
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
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.primary,
                                        checkmarkColor = colors.onPrimary,
                                    ),
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
                Text(if (busy) "Добавление…" else "Добавить (${selectedUserUuids.size})")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun InlineError(message: String) {
    Text(
        text = message,
        color = LocalWorkspaceColorsPalette.current.indicatorRed,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun ErrorWithRetry(
    error: String,
    busy: Boolean,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineError(error)
        Spacer(Modifier.weight(1f))
        TextButton(enabled = !busy, onClick = onRetry) { Text("Повторить") }
    }
}

private fun channelVisibilityLabel(stream: Stream): String = when (
    channelVisibility(stream.inviteOnly, stream.isPrivate)
) {
    ChannelVisibility.OPEN -> "Открытый"
    ChannelVisibility.CLOSED_OPEN_HISTORY -> "Закрытый, открытая история"
    ChannelVisibility.CLOSED_PROTECTED_HISTORY -> "Закрытый, защищенная история"
}

private fun roleLabel(role: String): String = when (role) {
    "owner" -> "Владелец"
    "administrator" -> "Администратор"
    "moderator" -> "Модератор"
    "guest" -> "Гость"
    "member" -> "Участник"
    else -> ""
}

private fun roleAssignmentLabel(role: String): String = when (role) {
    "administrator" -> "Назначить администратором"
    "moderator" -> "Назначить модератором"
    "member" -> "Назначить участником"
    else -> roleLabel(role)
}

private fun presenceLabel(status: String): String = when (status) {
    "active", "online" -> "В сети"
    "idle" -> "Нет на месте"
    "dnd", "do_not_disturb" -> "Не беспокоить"
    else -> "Не в сети"
}

private const val MAX_CHANNEL_NAME_CHARS = 255
private const val MAX_CHANNEL_DESCRIPTION_CHARS = 255
private const val MAX_MEMBER_SEARCH_CHARS = 200
