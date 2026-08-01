package ru.genesiscorporation.workspace.beta.modules.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import ru.genesiscorporation.workspace.beta.data.WorkspaceAuthIdleTimeout
import ru.genesiscorporation.workspace.beta.data.WorkspaceNetworkStatus
import ru.genesiscorporation.workspace.beta.data.WorkspaceNotificationSound
import ru.genesiscorporation.workspace.beta.data.WorkspaceThemeMode
import ru.genesiscorporation.workspace.beta.data.collectWorkspaceDiagnostics
import ru.genesiscorporation.workspace.beta.data.renderWorkspaceDiagnostics
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.copyPlainProfileText
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URI

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBackToChats: () -> Unit,
    onOpenFolderDisplay: () -> Unit,
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
    var showAuthIdleTimeoutPicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationSoundPicker by rememberSaveable { mutableStateOf(false) }
    var showThemeModePicker by rememberSaveable { mutableStateOf(false) }
    var showChatDensityPicker by rememberSaveable { mutableStateOf(false) }
    var organizationsExpanded by rememberSaveable { mutableStateOf(false) }
    var showPersonalInformation by rememberSaveable { mutableStateOf(false) }
    var diagnosticsError by rememberSaveable { mutableStateOf(false) }
    var showStatusEditor by rememberSaveable { mutableStateOf(false) }
    var statusDraft by rememberSaveable { mutableStateOf("") }
    var awayDraft by rememberSaveable { mutableStateOf(false) }
    var statusSubmissionPending by rememberSaveable { mutableStateOf(false) }
    var pendingAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var avatarSubmissionPending by rememberSaveable { mutableStateOf(false) }
    var confirmAvatarRemoval by rememberSaveable { mutableStateOf(false) }
    var avatarRemovalPending by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPersonalInformation) {
        showPersonalInformation = false
    }
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
    val resolvedDisplayName = userData?.displayableName()
        ?: activeAccount?.displayName
        ?: activeAccount?.login
        ?: "Профиль"
    val resolvedAvatarUrn = userData?.avatar ?: activeAccount?.avatarUrn
    val profileReady = activeAccount != null &&
        userData?.uuid?.equals(activeAccount?.userId, ignoreCase = true) == true
    val profileActionsEnabled = profileReady &&
        !profileMutationInProgress &&
        !profileRefreshing
    val settingsEnabled = activeAccount != null && !settingsSaving
    val sessionActionsEnabled = activeAccount != null &&
        !operationInProgress &&
        !profileMutationInProgress
    val shareDiagnostics: () -> Unit = {
        val report = renderWorkspaceDiagnostics(
            collectWorkspaceDiagnostics(
                context = context,
                savedAccountCount = accounts.size,
                attachmentCacheBytes = attachmentCacheSizeBytes,
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
        Unit
    }
    val openStatusEditor = {
        viewModel.clearActionError()
        statusDraft = userData?.statusText.orEmpty()
        awayDraft = userData?.status == "idle"
        showStatusEditor = true
    }
    val changeAvatar = {
        viewModel.clearActionError()
        avatarPicker.launch("image/*")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .testTag(
                    if (showPersonalInformation) {
                        PROFILE_FIGMA_PERSONAL_INFO_TAG
                    } else {
                        PROFILE_FIGMA_ROOT_TAG
                    },
                ),
        ) {
            if (showPersonalInformation) {
                item(key = "personal-info-header") {
                    ProfileFigmaTopBar(
                        title = "Личная информация",
                        onBack = { showPersonalInformation = false },
                        loading = profileRefreshing,
                    )
                }
                activeAccount?.let { account ->
                    item(key = "personal-info-summary") {
                        ProfileFigmaSummary(
                            account = account,
                            displayName = resolvedDisplayName,
                            avatarUrn = resolvedAvatarUrn,
                            statusText = userData?.statusText,
                            presence = profilePresencePresentation(userData?.status),
                            enabled = false,
                            onOpen = null,
                        )
                    }
                    item(key = "personal-info-actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = openStatusEditor,
                                enabled = profileActionsEnabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Статус")
                            }
                            OutlinedButton(
                                onClick = changeAvatar,
                                enabled = profileActionsEnabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Сменить фото")
                            }
                        }
                    }
                    if (resolvedAvatarUrn?.let(::isResettableAvatar) == true) {
                        item(key = "personal-info-remove-avatar") {
                            TextButton(
                                onClick = {
                                    viewModel.clearActionError()
                                    confirmAvatarRemoval = true
                                },
                                enabled = profileActionsEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Удалить фото профиля",
                                    color = colors.indicatorRed,
                                )
                            }
                        }
                    }
                    item(key = "personal-info-id") {
                        ProfileFigmaInformationRow(
                            icon = R.drawable.ic_userid,
                            label = "ID пользователя",
                            value = account.userId,
                            copyable = profileReady,
                            onCopy = {
                                val copied = copyPlainProfileText(
                                    context,
                                    "ID пользователя",
                                    account.userId,
                                )
                                Toast.makeText(
                                    context,
                                    if (copied) {
                                        "ID пользователя скопирован"
                                    } else {
                                        "Не удалось скопировать ID"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                    (userData?.email ?: account.email)
                        ?.takeIf(String::isNotBlank)
                        ?.let { email ->
                            item(key = "personal-info-email") {
                                ProfileFigmaInformationRow(
                                    icon = R.drawable.ic_figma_profile_email,
                                    label = "Email",
                                    value = email,
                                    copyable = profileReady,
                                    onCopy = {
                                        val copied = copyPlainProfileText(
                                            context,
                                            "Email",
                                            email,
                                        )
                                        Toast.makeText(
                                            context,
                                            if (copied) {
                                                "Email скопирован"
                                            } else {
                                                "Не удалось скопировать Email"
                                            },
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                    item(key = "personal-info-username") {
                        ProfileFigmaInformationRow(
                            icon = R.drawable.ic_profile,
                            label = "Имя пользователя",
                            value = "@${userData?.username ?: account.login}",
                            copyable = profileReady,
                            onCopy = {
                                val username = userData?.username ?: account.login
                                val copied = copyPlainProfileText(
                                    context,
                                    "Имя пользователя",
                                    username,
                                )
                                Toast.makeText(
                                    context,
                                    if (copied) {
                                        "Имя пользователя скопировано"
                                    } else {
                                        "Не удалось скопировать имя пользователя"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                    userData?.identityKind
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { identityKind ->
                            item(key = "personal-info-account-kind") {
                                ProfileFigmaInformationRow(
                                    icon = R.drawable.ic_profile,
                                    label = "Тип аккаунта",
                                    value = identityKind,
                                    copyable = false,
                                    onCopy = {},
                                )
                            }
                        }
                }
            } else {
                item(key = "profile-header") {
                    ProfileFigmaTopBar(
                        title = "Мой профиль",
                        onBack = onBackToChats,
                        loading = profileRefreshing || settingsSaving,
                    )
                }
                activeAccount?.let { account ->
                    item(key = "profile-summary") {
                        ProfileFigmaSummary(
                            account = account,
                            displayName = resolvedDisplayName,
                            avatarUrn = resolvedAvatarUrn,
                            statusText = userData?.statusText,
                            presence = profilePresencePresentation(userData?.status),
                            enabled = sessionActionsEnabled,
                            onOpen = { showPersonalInformation = true },
                        )
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = colors.cardBackgroundActive,
                        )
                    }
                }
                actionError?.let { error ->
                    item(key = "profile-error") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    colors.infoCardBackground,
                                    RoundedCornerShape(8.dp),
                                )
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
                    }
                }
                item(key = "organizations") {
                    ProfileFigmaOrganizationSection(
                        accounts = accounts,
                        activeAccountId = activeAccountId,
                        expanded = organizationsExpanded,
                        enabled = sessionActionsEnabled,
                        onExpandedChange = { organizationsExpanded = it },
                        onSwitchAccount = { accountId ->
                            organizationsExpanded = false
                            viewModel.switchAccount(accountId)
                        },
                        onAddAccount = viewModel::addAccount,
                    )
                }
                item(key = "description-header") {
                    ProfileFigmaSectionHeader("Описание")
                }
                activeAccount?.let { account ->
                    item(key = "current-server") {
                        ProfileFigmaServerRow(
                            server = account.serverHost(),
                            accountLabel = userData?.email
                                ?: account.email
                                ?: account.login,
                            enabled = sessionActionsEnabled,
                            onClick = { organizationsExpanded = true },
                        )
                    }
                    item(key = "personal-information") {
                        ProfileFigmaSettingRow(
                            icon = R.drawable.ic_profile,
                            title = "Личная информация",
                            enabled = sessionActionsEnabled,
                            onClick = { showPersonalInformation = true },
                        )
                    }
                    item(key = "profile-status") {
                        ProfileFigmaSettingRow(
                            icon = R.drawable.ic_figma_profile_status,
                            title = "Статус",
                            subtitle = userData?.statusText
                                ?.trim()
                                ?.takeIf(String::isNotEmpty)
                                ?: "Укажите статус",
                            enabled = profileActionsEnabled,
                            minHeight = 64.dp,
                            onClick = openStatusEditor,
                        )
                    }
                }
                item(key = "settings-header") {
                    ProfileFigmaSectionHeader("Настройки")
                }
                item(key = "notification-sound") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_notifications,
                        title = "Звуки уведомлений",
                        value = uiPreferences.notificationSound.profileSoundLabel(),
                        enabled = settingsEnabled,
                        testTag = PROFILE_FIGMA_SOUND_ROW_TAG,
                        onClick = { showNotificationSoundPicker = true },
                    )
                }
                item(key = "language") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_figma_profile_schedule,
                        title = "Язык",
                        value = "Русский",
                    )
                }
                item(key = "auth-idle-timeout") {
                    AuthIdleTimeoutPreference(
                        selected = uiPreferences.authIdleTimeout,
                        enabled = settingsEnabled,
                        onOpen = { showAuthIdleTimeoutPicker = true },
                    )
                }
                item(key = "theme-mode") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_visibility,
                        title = "Настройка темы",
                        value = uiPreferences.themeMode.profileThemeLabel(),
                        enabled = settingsEnabled,
                        testTag = PROFILE_FIGMA_THEME_ROW_TAG,
                        onClick = { showThemeModePicker = true },
                    )
                }
                item(key = "folder-display") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_draft,
                        title = "Отображение папок",
                        subtitle = "Управление в списке чатов",
                        enabled = true,
                        testTag = PROFILE_FIGMA_FOLDER_ROW_TAG,
                        onClick = onOpenFolderDisplay,
                    )
                }
                item(key = "additional-header") {
                    ProfileFigmaSectionHeader("Дополнительно")
                }
                item(key = "chat-density") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_feed,
                        title = "Плотность списка чатов",
                        value = uiPreferences.chatListDensity.profileDensityLabel(),
                        enabled = settingsEnabled,
                        onClick = { showChatDensityPicker = true },
                    )
                }
                item(key = "personal-unread-priority") {
                    ProfileFigmaSwitchRow(
                        icon = R.drawable.ic_mail,
                        title = "Личные непрочитанные выше",
                        subtitle = "Сначала показывать личные диалоги",
                        checked = uiPreferences.prioritizePersonalUnread,
                        enabled = settingsEnabled,
                        onCheckedChange = viewModel::setPrioritizePersonalUnread,
                    )
                }
                item(key = "unmuted-unread-priority") {
                    ProfileFigmaSwitchRow(
                        icon = R.drawable.ic_notifications,
                        title = "Активные каналы выше",
                        subtitle = "Опускать заглушённые каналы ниже",
                        checked = uiPreferences.prioritizeUnmutedUnreadChannels,
                        enabled = settingsEnabled,
                        onCheckedChange = viewModel::setPrioritizeUnmutedUnreadChannels,
                    )
                }
                item(key = "attachment-cache") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.attach_file,
                        title = "Кэш вложений",
                        subtitle = if (cacheClearing) {
                            "Очищаем…"
                        } else {
                            "Только временные просмотренные файлы"
                        },
                        value = formatCacheSize(attachmentCacheSizeBytes),
                        enabled = attachmentCacheSizeBytes > 0L && !cacheClearing,
                        onClick = if (attachmentCacheSizeBytes > 0L) {
                            { confirmCacheClear = true }
                        } else {
                            null
                        },
                    )
                }
                item(key = "diagnostics") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_done_all,
                        title = "Диагностика",
                        subtitle = "Сеть: ${diagnostics.networkStatus.label()}",
                        value = if (
                            diagnostics.notificationPermissionGranted &&
                            diagnostics.notificationsEnabled
                        ) {
                            "Уведомления вкл."
                        } else {
                            "Уведомления выкл."
                        },
                        onClick = shareDiagnostics,
                    )
                }
                item(key = "external-integrations") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_handshake,
                        title = "Внешние интеграции",
                        subtitle = "Zulip и внешние чаты",
                        enabled = sessionActionsEnabled,
                        onClick = onOpenExternalIntegrations,
                    )
                }
                item(key = "about") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_profile,
                        title = "О приложении",
                        value = "Версия ${BuildConfig.VERSION_NAME}",
                        onClick = onOpenAbout,
                    )
                }
                item(key = "logout") {
                    ProfileFigmaSettingRow(
                        icon = R.drawable.ic_logout,
                        title = "Выйти из текущего аккаунта",
                        enabled = sessionActionsEnabled,
                        tint = colors.indicatorRed,
                        onClick = { confirmLogout = true },
                    )
                }
                item(key = "profile-bottom-space") {
                    Spacer(Modifier.height(12.dp))
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

    if (showAuthIdleTimeoutPicker) {
        AuthIdleTimeoutDialog(
            selected = uiPreferences.authIdleTimeout,
            enabled = !settingsSaving && activeAccount != null,
            onDismiss = { showAuthIdleTimeoutPicker = false },
            onSelected = { timeout ->
                showAuthIdleTimeoutPicker = false
                viewModel.setAuthIdleTimeout(timeout)
            },
        )
    }

    if (showNotificationSoundPicker) {
        ProfileFigmaChoiceDialog(
            title = "Звуки уведомлений",
            description =
                "После выбора прозвучит пример. Настройки канала Android " +
                    "могут переопределить звук.",
            selected = uiPreferences.notificationSound,
            choices = WorkspaceNotificationSound.entries.map { sound ->
                sound to sound.profileSoundLabel()
            },
            enabled = settingsEnabled,
            onDismiss = { showNotificationSoundPicker = false },
            onSelected = { sound ->
                showNotificationSoundPicker = false
                viewModel.setNotificationSound(sound)
            },
        )
    }

    if (showThemeModePicker) {
        ProfileFigmaChoiceDialog(
            title = "Настройка темы",
            selected = uiPreferences.themeMode,
            choices = WorkspaceThemeMode.entries.map { mode ->
                mode to mode.profileThemeLabel()
            },
            enabled = settingsEnabled,
            onDismiss = { showThemeModePicker = false },
            onSelected = { mode ->
                showThemeModePicker = false
                viewModel.setThemeMode(mode)
            },
        )
    }

    if (showChatDensityPicker) {
        ProfileFigmaChoiceDialog(
            title = "Плотность списка чатов",
            selected = uiPreferences.chatListDensity,
            choices = ChatListDensity.entries.map { density ->
                density to density.profileDensityLabel()
            },
            enabled = settingsEnabled,
            onDismiss = { showChatDensityPicker = false },
            onSelected = { density ->
                showChatDensityPicker = false
                viewModel.setChatListDensity(density)
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
internal fun AuthIdleTimeoutPreference(
    selected: WorkspaceAuthIdleTimeout,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    ProfileFigmaSettingRow(
        icon = R.drawable.ic_refresh,
        title = "Автовыход",
        subtitle = "После неактивности",
        value = selected.authIdleTimeoutLabel(),
        enabled = enabled,
        testTag = AUTH_IDLE_TIMEOUT_ROW_TAG,
        onClick = onOpen,
    )
}

@Composable
internal fun AuthIdleTimeoutDialog(
    selected: WorkspaceAuthIdleTimeout,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelected: (WorkspaceAuthIdleTimeout) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автовыход") },
        text = {
            Column(
                modifier = Modifier
                    .testTag(AUTH_IDLE_TIMEOUT_DIALOG_TAG)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Выйти из текущего аккаунта после периода неактивности",
                    color = LocalWorkspaceColorsPalette.current.textAdditional50,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                WorkspaceAuthIdleTimeout.entries.forEach { timeout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == timeout,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelected(timeout) },
                            )
                            .testTag("$AUTH_IDLE_TIMEOUT_OPTION_TAG_PREFIX${timeout.name}")
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == timeout,
                            enabled = enabled,
                            onClick = null,
                        )
                        Text(
                            text = timeout.authIdleTimeoutLabel(),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

private fun WorkspaceAuthIdleTimeout.authIdleTimeoutLabel(): String = when (this) {
    WorkspaceAuthIdleTimeout.SIX_HOURS -> "6 часов"
    WorkspaceAuthIdleTimeout.TWELVE_HOURS -> "12 часов"
    WorkspaceAuthIdleTimeout.ONE_DAY -> "24 часа"
    WorkspaceAuthIdleTimeout.THREE_DAYS -> "3 дня"
    WorkspaceAuthIdleTimeout.SEVEN_DAYS -> "7 дней"
    WorkspaceAuthIdleTimeout.NEVER -> "Никогда"
}

internal const val AUTH_IDLE_TIMEOUT_ROW_TAG = "profile.auth_idle_timeout"
internal const val AUTH_IDLE_TIMEOUT_DIALOG_TAG = "profile.auth_idle_timeout.dialog"
internal const val AUTH_IDLE_TIMEOUT_OPTION_TAG_PREFIX =
    "profile.auth_idle_timeout.option."

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

private fun WorkspaceAccount.serverHost(): String =
    runCatching { URI(baseUrl).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: baseUrl
