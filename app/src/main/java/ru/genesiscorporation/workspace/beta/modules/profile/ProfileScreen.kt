package ru.genesiscorporation.workspace.beta.modules.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.ChatListDensity
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.WorkspaceNetworkStatus
import ru.genesiscorporation.workspace.beta.data.WorkspaceNotificationSound
import ru.genesiscorporation.workspace.beta.data.WorkspaceThemeMode
import ru.genesiscorporation.workspace.beta.data.collectWorkspaceDiagnostics
import ru.genesiscorporation.workspace.beta.data.renderWorkspaceDiagnostics
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URI

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenAbout: () -> Unit,
    onOpenExternalIntegrations: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccountId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val userData by viewModel.userData.collectAsStateWithLifecycle()
    val operationInProgress by viewModel.operationInProgress.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val uiPreferences by viewModel.uiPreferences.collectAsStateWithLifecycle()
    val settingsSaving by viewModel.settingsSaving.collectAsStateWithLifecycle()
    val attachmentCacheSizeBytes by
        viewModel.attachmentCacheSizeBytes.collectAsStateWithLifecycle()
    val cacheClearing by viewModel.cacheClearing.collectAsStateWithLifecycle()
    val profileRefreshing by viewModel.profileRefreshing.collectAsStateWithLifecycle()
    val profileMutationInProgress by
        viewModel.profileMutationInProgress.collectAsStateWithLifecycle()
    val profileMutationSucceeded by
        viewModel.profileMutationSucceeded.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmCacheClear by rememberSaveable { mutableStateOf(false) }
    var diagnosticsError by rememberSaveable { mutableStateOf(false) }
    var showStatusEditor by rememberSaveable { mutableStateOf(false) }
    var statusDraft by rememberSaveable { mutableStateOf("") }
    var awayDraft by rememberSaveable { mutableStateOf(false) }
    var statusSubmissionPending by rememberSaveable { mutableStateOf(false) }
    var pendingAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var avatarSubmissionPending by rememberSaveable { mutableStateOf(false) }
    var confirmAvatarRemoval by rememberSaveable { mutableStateOf(false) }
    var avatarRemovalPending by rememberSaveable { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            pendingAvatarUri = uri.toString()
        }
    }
    val diagnostics = remember(
        context,
        accounts.size,
        attachmentCacheSizeBytes,
        uiPreferences,
    ) {
        collectWorkspaceDiagnostics(
            context = context,
            savedAccountCount = accounts.size,
            attachmentCacheBytes = attachmentCacheSizeBytes,
            preferences = uiPreferences,
        )
    }
    LaunchedEffect(activeAccountId) {
        viewModel.refreshAttachmentCacheSize()
    }
    LaunchedEffect(
        profileMutationInProgress,
        profileMutationSucceeded,
        statusSubmissionPending,
        avatarSubmissionPending,
        avatarRemovalPending,
    ) {
        if (profileMutationInProgress || profileMutationSucceeded == null) {
            return@LaunchedEffect
        }
        if (statusSubmissionPending) {
            statusSubmissionPending = false
            if (profileMutationSucceeded == true) {
                showStatusEditor = false
            }
        }
        if (avatarSubmissionPending) {
            avatarSubmissionPending = false
            if (profileMutationSucceeded == true) {
                pendingAvatarUri = null
            }
        }
        if (avatarRemovalPending) {
            avatarRemovalPending = false
            if (profileMutationSucceeded == true) {
                confirmAvatarRemoval = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Профиль",
                color = colors.textHeaders,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
            )
            actionError?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.infoCardBackground, RoundedCornerShape(10.dp))
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error,
                        color = colors.indicatorRed,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                    )
                    TextButton(onClick = viewModel::clearActionError) {
                        Text("Закрыть")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                activeAccount?.let { account ->
                    item(key = "profile") {
                        ProfileIdentityCard(
                            account = account,
                            displayName = userData?.displayableName()
                                ?: account.displayName
                                ?: account.login,
                            email = userData?.email ?: account.email,
                            username = userData?.username ?: account.login,
                            avatarUrn = userData?.avatar ?: account.avatarUrn,
                            statusLabel = profileStatusLabel(
                                text = userData?.statusText,
                                status = userData?.status,
                            ),
                            customAvatar =
                                (userData?.avatar ?: account.avatarUrn)
                                    ?.let(::isResettableAvatar) == true,
                            profileReady = userData?.uuid
                                ?.equals(account.userId, ignoreCase = true) == true,
                            refreshing = profileRefreshing,
                            actionInProgress = profileMutationInProgress,
                            onRefresh = viewModel::refreshProfile,
                            onEditStatus = {
                                viewModel.clearActionError()
                                statusDraft = userData?.statusText.orEmpty()
                                awayDraft = userData?.status == "idle"
                                showStatusEditor = true
                            },
                            onChangeAvatar = {
                                viewModel.clearActionError()
                                avatarPicker.launch("image/*")
                            },
                            onRemoveAvatar = {
                                viewModel.clearActionError()
                                confirmAvatarRemoval = true
                            },
                        )
                    }
                }

                item(key = "settings-title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Настройки",
                            color = colors.textHeaders,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (settingsSaving) {
                            CircularProgressIndicator(
                                color = colors.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                item(key = "theme-mode") {
                    ThemeModePreference(
                        selected = uiPreferences.themeMode,
                        enabled = !settingsSaving && activeAccount != null,
                        onSelected = viewModel::setThemeMode,
                    )
                }
                item(key = "chat-density") {
                    ChatDensityPreference(
                        selected = uiPreferences.chatListDensity,
                        enabled = !settingsSaving && activeAccount != null,
                        onSelected = viewModel::setChatListDensity,
                    )
                }
                item(key = "notification-sound") {
                    NotificationSoundPreference(
                        selected = uiPreferences.notificationSound,
                        enabled = !settingsSaving && activeAccount != null,
                        onSelected = viewModel::setNotificationSound,
                    )
                }
                item(key = "personal-unread-priority") {
                    BooleanPreference(
                        title = "Личные непрочитанные выше",
                        description =
                            "Среди непрочитанных чатов сначала показывать личные диалоги",
                        checked = uiPreferences.prioritizePersonalUnread,
                        enabled = !settingsSaving && activeAccount != null,
                        onCheckedChange = viewModel::setPrioritizePersonalUnread,
                    )
                }
                item(key = "unmuted-unread-priority") {
                    BooleanPreference(
                        title = "Активные каналы выше",
                        description =
                            "Среди непрочитанных каналов опускать заглушённые ниже",
                        checked = uiPreferences.prioritizeUnmutedUnreadChannels,
                        enabled = !settingsSaving && activeAccount != null,
                        onCheckedChange =
                            viewModel::setPrioritizeUnmutedUnreadChannels,
                    )
                }
                item(key = "attachment-cache") {
                    AttachmentCachePreference(
                        sizeBytes = attachmentCacheSizeBytes,
                        clearing = cacheClearing,
                        onClear = { confirmCacheClear = true },
                    )
                }
                item(key = "diagnostics") {
                    DiagnosticsPreference(
                        networkStatus = diagnostics.networkStatus,
                        notificationsEnabled =
                            diagnostics.notificationPermissionGranted &&
                                diagnostics.notificationsEnabled,
                        onShare = {
                            val report = renderWorkspaceDiagnostics(
                                collectWorkspaceDiagnostics(
                                    context = context,
                                    savedAccountCount = accounts.size,
                                    attachmentCacheBytes =
                                        attachmentCacheSizeBytes,
                                    preferences = uiPreferences,
                                ),
                            )
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                "CASSI Workspace diagnostics",
                                            )
                                            .putExtra(Intent.EXTRA_TEXT, report),
                                        "Поделиться диагностикой",
                                    ),
                                )
                            }.onFailure {
                                diagnosticsError = true
                            }
                        },
                    )
                }
                item(key = "external-integrations") {
                    ExternalIntegrationsPreference(
                        enabled =
                            activeAccount != null &&
                                !operationInProgress &&
                                !profileMutationInProgress,
                        onOpen = onOpenExternalIntegrations,
                    )
                }

                item(key = "accounts-title") {
                    Text(
                        text = "Учётные записи",
                        color = colors.textHeaders,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(
                    items = accounts,
                    key = WorkspaceAccount::accountId,
                ) { account ->
                    AccountRow(
                        account = account,
                        selected = account.accountId == activeAccountId,
                        enabled =
                            !operationInProgress && !profileMutationInProgress,
                        onClick = { viewModel.switchAccount(account.accountId) },
                    )
                }
                item(key = "add-account") {
                    OutlinedButton(
                        onClick = viewModel::addAccount,
                        enabled =
                            !operationInProgress && !profileMutationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "Добавить организацию или аккаунт",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                item(key = "logout") {
                    Button(
                        onClick = { confirmLogout = true },
                        enabled =
                            !operationInProgress &&
                                !profileMutationInProgress &&
                                activeAccount != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = colors.indicatorRed,
                            disabledContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_logout),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "Выйти из текущего аккаунта",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                item(key = "version") {
                    OutlinedButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = "О приложении",
                                color = colors.textHeaders,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                text = "Версия ${BuildConfig.VERSION_NAME}",
                                color = colors.textAdditional50,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                            tint = colors.textAdditional30,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer(rotationZ = 180f),
                        )
                    }
                }
            }
        }

        if (operationInProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = {
                Text(
                    "Локальная сессия этого аккаунта будет удалена. Остальные сохранённые аккаунты останутся.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    },
                ) {
                    Text(
                        text = "Выйти",
                        color = colors.indicatorRed,
                    )
                }
            },
        )
    }

    if (confirmCacheClear) {
        AlertDialog(
            onDismissRequest = { confirmCacheClear = false },
            title = { Text("Очистить кэш вложений?") },
            text = {
                Text(
                    "Будет удалено ${formatCacheSize(attachmentCacheSizeBytes)} " +
                        "временных файлов текущего аккаунта. Сессия, сообщения, " +
                        "черновики и неподтверждённые отправки сохранятся.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmCacheClear = false }) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCacheClear = false
                        viewModel.clearCachedAttachments()
                    },
                ) {
                    Text("Очистить")
                }
            },
        )
    }

    if (diagnosticsError) {
        AlertDialog(
            onDismissRequest = { diagnosticsError = false },
            title = { Text("Не удалось открыть отправку") },
            text = {
                Text("На устройстве не найдено приложение для передачи отчёта.")
            },
            confirmButton = {
                TextButton(onClick = { diagnosticsError = false }) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (showStatusEditor) {
        AlertDialog(
            onDismissRequest = {
                if (!profileMutationInProgress) {
                    showStatusEditor = false
                }
            },
            title = { Text("Статус") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = statusDraft,
                        onValueChange = { value ->
                            statusDraft = value.take(PROFILE_STATUS_MAX_LENGTH)
                        },
                        enabled = !profileMutationInProgress,
                        label = { Text("Текст статуса") },
                        supportingText = {
                            Text(
                                "${statusDraft.length}/$PROFILE_STATUS_MAX_LENGTH",
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = awayDraft,
                                enabled = !profileMutationInProgress,
                                role = Role.Switch,
                                onValueChange = { awayDraft = it },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Нет на месте")
                            Text(
                                "Другие участники увидят, что вы отошли",
                                color = colors.textAdditional50,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = awayDraft,
                            enabled = !profileMutationInProgress,
                            onCheckedChange = null,
                        )
                    }
                    if (profileMutationInProgress && statusSubmissionPending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Сохраняем…",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (
                        !profileMutationInProgress &&
                        profileMutationSucceeded == false
                    ) {
                        actionError?.let { error ->
                            Text(
                                text = error,
                                color = colors.indicatorRed,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !profileMutationInProgress,
                        onClick = {
                            if (
                                viewModel.updateStatus(
                                    text = "",
                                    away = false,
                                    clear = true,
                                )
                            ) {
                                statusSubmissionPending = true
                            }
                        },
                    ) {
                        Text("Очистить")
                    }
                    TextButton(
                        enabled = !profileMutationInProgress,
                        onClick = { showStatusEditor = false },
                    ) {
                        Text("Отмена")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !profileMutationInProgress,
                    onClick = {
                        if (
                            viewModel.updateStatus(
                                text = statusDraft,
                                away = awayDraft,
                            )
                        ) {
                            statusSubmissionPending = true
                        }
                    },
                ) {
                    Text("Сохранить")
                }
            },
        )
    }

    pendingAvatarUri?.let { uriText ->
        AlertDialog(
            onDismissRequest = {
                if (!profileMutationInProgress) {
                    pendingAvatarUri = null
                }
            },
            title = { Text("Новое фото профиля") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = Uri.parse(uriText),
                        contentDescription = "Предпросмотр фото профиля",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        text = "PNG, JPEG, GIF или WebP, не больше 25 МБ",
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (profileMutationInProgress && avatarSubmissionPending) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Загружаем…",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (
                        !profileMutationInProgress &&
                        profileMutationSucceeded == false
                    ) {
                        actionError?.let { error ->
                            Text(
                                text = error,
                                color = colors.indicatorRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !profileMutationInProgress,
                    onClick = { pendingAvatarUri = null },
                ) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !profileMutationInProgress,
                    onClick = {
                        if (
                            viewModel.uploadAvatar(
                                context = context,
                                uri = Uri.parse(uriText),
                            )
                        ) {
                            avatarSubmissionPending = true
                        }
                    },
                ) {
                    Text("Использовать")
                }
            },
        )
    }

    if (confirmAvatarRemoval) {
        AlertDialog(
            onDismissRequest = {
                if (!profileMutationInProgress) {
                    confirmAvatarRemoval = false
                }
            },
            title = { Text("Удалить фото профиля?") },
            text = {
                Column {
                    Text("Вместо фотографии будет показан стандартный аватар.")
                    if (profileMutationInProgress && avatarRemovalPending) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Удаляем…",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (
                        !profileMutationInProgress &&
                        profileMutationSucceeded == false
                    ) {
                        actionError?.let { error ->
                            Text(
                                text = error,
                                color = colors.indicatorRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !profileMutationInProgress,
                    onClick = { confirmAvatarRemoval = false },
                ) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !profileMutationInProgress,
                    onClick = {
                        if (viewModel.removeAvatar()) {
                            avatarRemovalPending = true
                        }
                    },
                ) {
                    Text(
                        text = "Удалить",
                        color = colors.indicatorRed,
                    )
                }
            },
        )
    }
}

@Composable
private fun ExternalIntegrationsPreference(
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    OutlinedButton(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_handshake),
            contentDescription = null,
            tint = if (enabled) colors.primary else colors.iconDisable,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Внешние интеграции",
                color = if (enabled) {
                    colors.textHeaders
                } else {
                    colors.textAdditional30
                },
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Подключение Zulip и выбор внешних чатов",
                color = colors.textAdditional50,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ThemeModePreference(
    selected: WorkspaceThemeMode,
    enabled: Boolean,
    onSelected: (WorkspaceThemeMode) -> Unit,
) {
    PreferenceCard(title = "Оформление") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PreferenceChoiceChip(
                label = "Система",
                selected = selected == WorkspaceThemeMode.SYSTEM,
                enabled = enabled,
                onClick = { onSelected(WorkspaceThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f),
            )
            PreferenceChoiceChip(
                label = "Светлая",
                selected = selected == WorkspaceThemeMode.LIGHT,
                enabled = enabled,
                onClick = { onSelected(WorkspaceThemeMode.LIGHT) },
                modifier = Modifier.weight(1f),
            )
            PreferenceChoiceChip(
                label = "Тёмная",
                selected = selected == WorkspaceThemeMode.DARK,
                enabled = enabled,
                onClick = { onSelected(WorkspaceThemeMode.DARK) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChatDensityPreference(
    selected: ChatListDensity,
    enabled: Boolean,
    onSelected: (ChatListDensity) -> Unit,
) {
    PreferenceCard(title = "Плотность списка чатов") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PreferenceChoiceChip(
                label = "Стандартная",
                selected = selected == ChatListDensity.STANDARD,
                enabled = enabled,
                onClick = { onSelected(ChatListDensity.STANDARD) },
                modifier = Modifier.weight(1f),
            )
            PreferenceChoiceChip(
                label = "Компактная",
                selected = selected == ChatListDensity.COMPACT,
                enabled = enabled,
                onClick = { onSelected(ChatListDensity.COMPACT) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotificationSoundPreference(
    selected: WorkspaceNotificationSound,
    enabled: Boolean,
    onSelected: (WorkspaceNotificationSound) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    PreferenceCard(title = "Звук уведомлений") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NotificationSoundChoice(
                    label = "Обычный",
                    sound = WorkspaceNotificationSound.DEFAULT,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
                NotificationSoundChoice(
                    label = "Мягкий",
                    sound = WorkspaceNotificationSound.SUBTLE,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
                NotificationSoundChoice(
                    label = "Цифровой",
                    sound = WorkspaceNotificationSound.DIGITAL,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NotificationSoundChoice(
                    label = "Стекло",
                    sound = WorkspaceNotificationSound.GLASS,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
                NotificationSoundChoice(
                    label = "Импульс",
                    sound = WorkspaceNotificationSound.PULSE,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
                NotificationSoundChoice(
                    label = "Без звука",
                    sound = WorkspaceNotificationSound.NONE,
                    selected = selected,
                    enabled = enabled,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text =
                    "После выбора прозвучит пример. Системные настройки Android " +
                        "могут переопределить звук канала.",
                color = colors.textAdditional50,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun NotificationSoundChoice(
    label: String,
    sound: WorkspaceNotificationSound,
    selected: WorkspaceNotificationSound,
    enabled: Boolean,
    onSelected: (WorkspaceNotificationSound) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceChoiceChip(
        label = label,
        selected = selected == sound,
        enabled = enabled,
        onClick = { onSelected(sound) },
        modifier = modifier,
    )
}

@Composable
private fun AttachmentCachePreference(
    sizeBytes: Long,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    PreferenceCard(title = "Кэш вложений") {
        Text(
            text =
                "Временные просмотренные файлы: ${formatCacheSize(sizeBytes)}. " +
                    "Черновики и неподтверждённые отправки не удаляются.",
            color = colors.textAdditional50,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        OutlinedButton(
            onClick = onClear,
            enabled = sizeBytes > 0L && !clearing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            if (clearing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (sizeBytes > 0L) {
                    "Очистить кэш вложений"
                } else {
                    "Кэш вложений пуст"
                },
            )
        }
    }
}

@Composable
private fun DiagnosticsPreference(
    networkStatus: WorkspaceNetworkStatus,
    notificationsEnabled: Boolean,
    onShare: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    PreferenceCard(title = "Диагностика") {
        Text(
            text = "Сеть: ${networkStatus.label()}. " +
                "Уведомления: ${if (notificationsEnabled) "включены" else "отключены"}. " +
                "Отчёт не содержит токены, адрес сервера, проект, пользователя " +
                "или сообщения.",
            color = colors.textAdditional50,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Поделиться диагностикой")
        }
    }
}

private fun WorkspaceNetworkStatus.label(): String = when (this) {
    WorkspaceNetworkStatus.ONLINE -> "доступна"
    WorkspaceNetworkStatus.LIMITED -> "без подтверждённого доступа"
    WorkspaceNetworkStatus.OFFLINE -> "нет подключения"
    WorkspaceNetworkStatus.UNKNOWN -> "состояние неизвестно"
}

internal fun formatCacheSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes < 1_024L -> "$safeBytes Б"
        safeBytes < 1_024L * 1_024L ->
            "${safeBytes / 1_024L} КБ"
        else -> {
            val megabyte = 1_024L * 1_024L
            val whole = safeBytes / megabyte
            val decimal = (safeBytes % megabyte) * 10L / megabyte
            "$whole,$decimal МБ"
        }
    }
}

@Composable
private fun PreferenceChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun BooleanPreference(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.infoCardBackground, RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.infoCardBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        content()
    }
}

@Composable
private fun ProfileIdentityCard(
    account: WorkspaceAccount,
    displayName: String,
    email: String?,
    username: String,
    avatarUrn: String?,
    statusLabel: String,
    customAvatar: Boolean,
    profileReady: Boolean,
    refreshing: Boolean,
    actionInProgress: Boolean,
    onRefresh: () -> Unit,
    onEditStatus: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.infoCardBackground, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                avatarUrn = avatarUrn,
                baseUrl = account.baseUrl,
                color = null,
                name = displayName,
                size = 56,
                hasPadding = false,
                ownerAccountId = account.accountId,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = displayName,
                    color = colors.textHeaders,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@$username",
                    color = colors.textAdditional50,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                email?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        color = colors.textAdditional50,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = statusLabel,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (refreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    enabled = !actionInProgress && !refreshing,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = "Обновить профиль",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onEditStatus,
                enabled = profileReady && !actionInProgress && !refreshing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Статус")
            }
            OutlinedButton(
                onClick = onChangeAvatar,
                enabled = profileReady && !actionInProgress && !refreshing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Сменить фото")
            }
        }
        if (customAvatar) {
            TextButton(
                onClick = onRemoveAvatar,
                enabled = profileReady && !actionInProgress && !refreshing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Удалить фото профиля",
                    color = colors.indicatorRed,
                )
            }
        }
    }
}

internal fun profileStatusLabel(
    text: String?,
    status: String?,
): String {
    val presence = when (status?.lowercase()) {
        "active" -> "В сети"
        "idle" -> "Нет на месте"
        "do_not_disturb" -> "Не беспокоить"
        "offline" -> "Не в сети"
        else -> "Статус неизвестен"
    }
    val trimmedText = text?.trim().orEmpty()
    return if (trimmedText.isEmpty()) presence else "$trimmedText · $presence"
}

internal fun isResettableAvatar(avatarUrn: String): Boolean =
    avatarUrn.startsWith("urn:image:") || avatarUrn.startsWith("urn:url:")

private const val PROFILE_STATUS_MAX_LENGTH = 256

@Composable
private fun AccountRow(
    account: WorkspaceAccount,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) colors.cardBackgroundActive else colors.cardBackgroundBase,
                RoundedCornerShape(11.dp),
            )
            .clickable(
                enabled = enabled && !selected,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = account.avatarUrn,
            baseUrl = account.baseUrl,
            color = null,
            name = account.displayName ?: account.login,
            size = 40,
            hasPadding = false,
            ownerAccountId = account.accountId,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = account.displayName ?: account.login,
                color = colors.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${account.serverHost()} · ${account.projectLabel()}",
                color = colors.textAdditional50,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text(
                text = "Текущий",
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun WorkspaceAccount.serverHost(): String =
    runCatching { URI(baseUrl).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: baseUrl

private fun WorkspaceAccount.projectLabel(): String =
    if (projectName == projectId) {
        "Проект ${projectId.take(8)}…"
    } else {
        projectName
    }
