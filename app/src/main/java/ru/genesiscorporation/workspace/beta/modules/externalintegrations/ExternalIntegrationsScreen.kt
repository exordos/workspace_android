package ru.genesiscorporation.workspace.beta.modules.externalintegrations

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountSelectionMode
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountStatus
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatStatus
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatType
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalHistoryDepth
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationReconciliationReason
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationStatus
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URI

@Composable
fun ExternalIntegrationsScreen(
    viewModel: ExternalIntegrationsViewModel,
    onBack: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val chatsByAccount by viewModel.chats.collectAsStateWithLifecycle()
    val operationsByAccount by
        viewModel.operations.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val loadStatus by viewModel.loadStatus.collectAsStateWithLifecycle()
    val activeAction by viewModel.activeAction.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val colors = LocalWorkspaceColorsPalette.current
    var connectOpen by rememberSaveable { mutableStateOf(false) }
    var settingsAccountUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var reconnectAccountUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var disconnectAccountUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var deleteAccountUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var deselectChatUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var retryOperationUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var discardOperationUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var platformError by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = activeAction != null
    val visibleChatsByAccount = remember(
        chatsByAccount,
        accounts,
        query,
    ) {
        accounts.associate { account ->
            account.uuid to chatsByAccount[account.uuid]
                .orEmpty()
                .filter {
                    query.isBlank() ||
                        it.displayName.contains(
                            query.trim(),
                            ignoreCase = true,
                        )
                }
                .sortedWith(
                    compareByDescending<ExternalChatResponse> {
                        it.selected
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) {
                        it.displayName
                    },
                )
        }
    }

    val visibleOperations = remember(operationsByAccount, accounts) {
        val accountUuids = accounts.mapTo(mutableSetOf()) { it.uuid }
        operationsByAccount
            .filterKeys { it in accountUuids }
            .values
            .flatten()
            .sortedWith(
                compareBy<ExternalOperationResponse>(
                    ::externalOperationSortRank,
                ).thenByDescending {
                    it.updatedAt.orEmpty()
                },
            )
    }
    val chatCatalogAccounts = remember(accounts) {
        accounts.filter {
            externalCapabilityAvailable(
                capabilities = it.capabilities,
                name = EXTERNAL_CHAT_CATALOG_CAPABILITY,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 10.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ExternalIntegrationsHeader(
                    refreshing =
                        loadStatus == ExternalIntegrationsLoadStatus.LOADING,
                    enabled = !busy,
                    onBack = onBack,
                    onRefresh = viewModel::refresh,
                )
            }
            error?.let { message ->
                item(key = "error") {
                    ExternalMessageCard(
                        text = message,
                        color = colors.indicatorRed,
                        primaryActionLabel = "Обновить",
                        onPrimaryAction = viewModel::refresh,
                        onDismiss = viewModel::clearError,
                    )
                }
            }
            notice?.let { message ->
                item(key = "notice") {
                    ExternalMessageCard(
                        text = message,
                        color = colors.indicatorGreen,
                        onDismiss = viewModel::clearNotice,
                    )
                }
            }
            platformError?.let { message ->
                item(key = "platform-error") {
                    ExternalMessageCard(
                        text = message,
                        color = colors.indicatorRed,
                        onDismiss = { platformError = null },
                    )
                }
            }
            if (
                loadStatus == ExternalIntegrationsLoadStatus.LOADING &&
                accounts.isEmpty()
            ) {
                item(key = "loading") {
                    ExternalLoadingCard()
                }
            } else if (
                loadStatus == ExternalIntegrationsLoadStatus.ERROR &&
                accounts.isEmpty()
            ) {
                item(key = "unavailable") {
                    ExternalIntegrationsUnavailableCard()
                }
            } else if (accounts.isEmpty()) {
                item(key = "empty") {
                    ExternalAccountEmptyCard(
                        enabled = !busy && activeAccount != null,
                        onConnect = { connectOpen = true },
                    )
                }
            } else {
                item(key = "accounts-heading") {
                    ExternalSectionHeading(
                        title = "Подключённые аккаунты",
                        subtitle =
                            "Workspace поддерживает один Zulip-аккаунт на профиль.",
                    )
                }
                items(
                    items = accounts,
                    key = ExternalAccountResponse::uuid,
                ) { account ->
                    ExternalAccountCard(
                        account = account,
                        busy = busy,
                        onSettings = {
                            settingsAccountUuid = account.uuid
                        },
                        onReconnect = {
                            reconnectAccountUuid = account.uuid
                        },
                        onDisconnect = {
                            disconnectAccountUuid = account.uuid
                        },
                        onDelete = {
                            deleteAccountUuid = account.uuid
                        },
                    )
                }
                if (chatCatalogAccounts.isEmpty()) {
                    item(key = "chats-unavailable") {
                        ExternalChatsUnavailableCard()
                    }
                } else {
                    item(key = "chats-heading") {
                        ExternalSectionHeading(
                            title = "Чаты Zulip",
                            subtitle = if (
                                chatCatalogAccounts.singleOrNull()
                                    ?.settings
                                    ?.selectionMode ==
                                ExternalAccountSelectionMode.ALL
                            ) {
                                "Новые чаты синхронизируются автоматически."
                            } else {
                                "Выберите, какие чаты появятся в Workspace."
                            },
                        )
                    }
                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it.take(MAX_SEARCH_CHARS)
                            },
                            label = { Text("Поиск чатов") },
                            singleLine = true,
                            enabled = !busy,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        R.drawable.ic_search,
                                    ),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            painter = painterResource(
                                                R.drawable.ic_close_small,
                                            ),
                                            contentDescription =
                                                "Очистить поиск",
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    chatCatalogAccounts.forEach { account ->
                        val visibleChats =
                            visibleChatsByAccount[account.uuid].orEmpty()
                        if (visibleChats.isEmpty()) {
                            item(key = "chats-empty-${account.uuid}") {
                                ExternalChatsEmptyCard(
                                    searching = query.isNotBlank(),
                                    preparing = !account.liveReady,
                                )
                            }
                        } else {
                            items(
                                items = visibleChats,
                                key = ExternalChatResponse::uuid,
                            ) { chat ->
                                ExternalChatCard(
                                    account = account,
                                    chat = chat,
                                    workspaceAccount = activeAccount,
                                    busy = busy,
                                    automatic =
                                        account.settings.selectionMode ==
                                            ExternalAccountSelectionMode.ALL,
                                    onSelect = {
                                        viewModel.selectChat(chat)
                                    },
                                    onDeselect = {
                                        deselectChatUuid = chat.uuid
                                    },
                                    onMoveHere = {
                                        viewModel.moveChatHere(chat)
                                    },
                                    onExternalOpenError = {
                                        platformError =
                                            "Не удалось открыть исходный чат в браузере."
                                    },
                                )
                            }
                        }
                    }
                }
                if (visibleOperations.isNotEmpty()) {
                    item(key = "operations-heading") {
                        ExternalSectionHeading(
                            title = "Операции интеграции",
                            subtitle =
                                "Восстановление незавершённых действий и безопасное удаление очереди.",
                        )
                    }
                    items(
                        items = visibleOperations,
                        key = ExternalOperationResponse::uuid,
                    ) { operation ->
                        val providerOrigin = accounts
                            .firstOrNull {
                                it.uuid == operation.externalAccountUuid
                            }
                            ?.settings
                            ?.serverUrl
                            .orEmpty()
                        ExternalOperationCard(
                            operation = operation,
                            providerOrigin = providerOrigin,
                            busy = busy,
                            onRetry = {
                                if (operation.retryRequiresConfirmation) {
                                    retryOperationUuid = operation.uuid
                                } else {
                                    viewModel.retryOperation(
                                        operation = operation,
                                        confirmDuplicateRisk = false,
                                    )
                                }
                            },
                            onDiscard = {
                                discardOperationUuid = operation.uuid
                            },
                            onExternalOpenError = {
                                platformError =
                                    "Не удалось открыть исходную операцию в браузере."
                            },
                        )
                    }
                }
            }
        }
    }
    if (connectOpen) {
        ExternalAccountCredentialDialog(
            title = "Подключить Zulip",
            description =
                "Ключ используется только для этого запроса и не отображается повторно.",
            initialServerUrl = "",
            initialEmail = "",
            showSyncSettings = true,
            busy = activeAction ==
                ExternalIntegrationAction.CREATE_ACCOUNT,
            successNotice = notice,
            error = error,
            onDismiss = { connectOpen = false },
            onSubmit = {
                    serverUrl,
                    email,
                    apiKey,
                    selectionMode,
                    historyDepth,
                ->
                viewModel.createAccount(
                    serverUrl = serverUrl,
                    email = email,
                    apiKey = apiKey,
                    selectionMode = selectionMode,
                    historyDepth = historyDepth,
                )
            },
        )
    }

    accounts.firstOrNull { it.uuid == settingsAccountUuid }?.let { account ->
        ExternalAccountSettingsDialog(
            account = account,
            busy = activeAction ==
                ExternalIntegrationAction.UPDATE_ACCOUNT,
            successNotice = notice,
            error = error,
            onDismiss = { settingsAccountUuid = null },
            onSubmit = { selectionMode, historyDepth ->
                viewModel.updateAccount(
                    account = account,
                    selectionMode = selectionMode,
                    historyDepth = historyDepth,
                )
            },
        )
    }

    accounts.firstOrNull { it.uuid == reconnectAccountUuid }?.let { account ->
        ExternalAccountCredentialDialog(
            title = "Переподключить Zulip",
            description =
                "Введите актуальный API key. Сохранённый ключ намеренно не подставляется.",
            initialServerUrl = account.settings.serverUrl,
            initialEmail = account.settings.email,
            showSyncSettings = false,
            busy = activeAction ==
                ExternalIntegrationAction.RECONNECT_ACCOUNT,
            successNotice = notice,
            error = error,
            onDismiss = { reconnectAccountUuid = null },
            onSubmit = {
                    serverUrl,
                    email,
                    apiKey,
                    _,
                    _,
                ->
                viewModel.reconnectAccount(
                    account = account,
                    serverUrl = serverUrl,
                    email = email,
                    apiKey = apiKey,
                )
            },
        )
    }

    accounts.firstOrNull { it.uuid == disconnectAccountUuid }?.let { account ->
        ExternalConfirmationDialog(
            title = "Остановить подключение?",
            text =
                "Новые сообщения перестанут синхронизироваться. Аккаунт и настройки останутся.",
            confirmLabel = "Отключить",
            destructive = false,
            busy = activeAction ==
                ExternalIntegrationAction.DISCONNECT_ACCOUNT,
            onDismiss = { disconnectAccountUuid = null },
            onConfirm = {
                if (viewModel.disconnectAccount(account)) {
                    disconnectAccountUuid = null
                }
            },
        )
    }

    accounts.firstOrNull { it.uuid == deleteAccountUuid }?.let { account ->
        ExternalConfirmationDialog(
            title = "Удалить внешний аккаунт?",
            text =
                "Проекции его чатов будут удалены из Workspace. Это действие нельзя отменить.",
            confirmLabel = "Удалить",
            destructive = true,
            busy = activeAction ==
                ExternalIntegrationAction.DELETE_ACCOUNT,
            onDismiss = { deleteAccountUuid = null },
            onConfirm = {
                if (viewModel.deleteAccount(account)) {
                    deleteAccountUuid = null
                }
            },
        )
    }

    val pendingDeselectChat = chatsByAccount.values
        .flatten()
        .firstOrNull { it.uuid == deselectChatUuid }
    if (pendingDeselectChat != null) {
        ExternalConfirmationDialog(
            title = "Убрать чат из Workspace?",
            text =
                "Исходный чат Zulip останется на месте, но его проекция и локальная история будут удалены.",
            confirmLabel = "Убрать",
            destructive = true,
            busy = activeAction ==
                ExternalIntegrationAction.DESELECT_CHAT,
            onDismiss = { deselectChatUuid = null },
            onConfirm = {
                if (viewModel.deselectChat(pendingDeselectChat)) {
                    deselectChatUuid = null
                }
            },
        )
    }

    val retryOperationCandidate = operationsByAccount.values
        .flatten()
        .firstOrNull { it.uuid == retryOperationUuid }
    LaunchedEffect(
        retryOperationUuid,
        retryOperationCandidate?.canRetry,
        retryOperationCandidate?.retryRequiresConfirmation,
    ) {
        if (
            retryOperationUuid != null &&
            (
                retryOperationCandidate?.canRetry != true ||
                    retryOperationCandidate.retryRequiresConfirmation != true
                )
        ) {
            retryOperationUuid = null
        }
    }
    val pendingRetryOperation = retryOperationCandidate
        ?.takeIf {
            it.canRetry && it.retryRequiresConfirmation
        }
    if (pendingRetryOperation != null) {
        ExternalConfirmationDialog(
            title = "Повторить с риском дубликата?",
            text =
                "Провайдер мог выполнить исходное действие без подтверждения. Повтор может создать дубликат.",
            confirmLabel = "Всё равно повторить",
            destructive = true,
            busy = activeAction ==
                ExternalIntegrationAction.RETRY_OPERATION,
            onDismiss = { retryOperationUuid = null },
            onConfirm = {
                if (
                    viewModel.retryOperation(
                        operation = pendingRetryOperation,
                        confirmDuplicateRisk = true,
                    )
                ) {
                    retryOperationUuid = null
                }
            },
        )
    }

    val discardOperationCandidate = operationsByAccount.values
        .flatten()
        .firstOrNull { it.uuid == discardOperationUuid }
    LaunchedEffect(
        discardOperationUuid,
        discardOperationCandidate?.canDiscard,
    ) {
        if (
            discardOperationUuid != null &&
            discardOperationCandidate?.canDiscard != true
        ) {
            discardOperationUuid = null
        }
    }
    val pendingDiscardOperation =
        discardOperationCandidate?.takeIf(ExternalOperationResponse::canDiscard)
    if (pendingDiscardOperation != null) {
        ExternalConfirmationDialog(
            title = "Удалить операцию из очереди?",
            text =
                "Workspace прекратит отслеживать и повторять эту операцию. Уже выполненное действие у провайдера не отменится.",
            confirmLabel = "Удалить из очереди",
            destructive = true,
            busy = activeAction ==
                ExternalIntegrationAction.DISCARD_OPERATION,
            onDismiss = { discardOperationUuid = null },
            onConfirm = {
                if (viewModel.discardOperation(pendingDiscardOperation)) {
                    discardOperationUuid = null
                }
            },
        )
    }
}

@Composable
private fun ExternalIntegrationsUnavailableCard() {
    val colors = LocalWorkspaceColorsPalette.current
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text =
                "Каталог недоступен. Проверьте соединение или права и используйте «Обновить».",
            color = colors.textAdditional50,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun ExternalChatsUnavailableCard() {
    val colors = LocalWorkspaceColorsPalette.current
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "Каталог внешних чатов недоступен",
                color = colors.textHeaders,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    "Текущий провайдер не разрешает просмотр каталога. Недоступные действия скрыты.",
                color = colors.textAdditional50,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ExternalIntegrationsHeader(
    refreshing: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Назад",
                tint = colors.textHeaders,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = "Внешние интеграции",
                color = colors.textHeaders,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Zulip и проекции внешних чатов",
                color = colors.textAdditional50,
                fontSize = 13.sp,
            )
        }
        if (refreshing) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp),
            )
        } else {
            IconButton(
                onClick = onRefresh,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Обновить",
                    tint = if (enabled) {
                        colors.iconActive
                    } else {
                        colors.iconDisable
                    },
                )
            }
        }
    }
}

@Composable
private fun ExternalSectionHeading(
    title: String,
    subtitle: String,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            color = colors.textAdditional50,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ExternalMessageCard(
    text: String,
    color: Color,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Surface(
        color = colors.infoCardBackground,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (
                    primaryActionLabel != null &&
                    onPrimaryAction != null
                ) {
                    TextButton(onClick = onPrimaryAction) {
                        Text(primaryActionLabel)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
private fun ExternalLoadingCard() {
    val colors = LocalWorkspaceColorsPalette.current
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Загружаем интеграции…",
                color = colors.textAdditional50,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ExternalAccountEmptyCard(
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_handshake),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = "Подключите рабочий Zulip",
                color = colors.textHeaders,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    "Workspace сможет показать выбранные внешние чаты рядом с обычными каналами.",
                color = colors.textAdditional50,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Button(
                onClick = onConnect,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Подключить Zulip")
            }
        }
    }
}

@Composable
private fun ExternalAccountCard(
    account: ExternalAccountResponse,
    busy: Boolean,
    onSettings: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val reconnectRecommended =
        account.status == ExternalAccountStatus.AUTH_REQUIRED ||
            account.status == ExternalAccountStatus.DEGRADED ||
            account.status == ExternalAccountStatus.DISCONNECTED
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            colors.iconHover,
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_handshake),
                        contentDescription = null,
                        tint = colors.primary,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = "Zulip",
                        color = colors.textHeaders,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = account.settings.serverUrl,
                        color = colors.textAdditional50,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = account.settings.email,
                        color = colors.textAdditional50,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExternalStatusBadge(
                    text = externalAccountStatusLabel(account),
                    color = externalAccountStatusColor(account),
                )
            }
            if (
                !account.liveReady &&
                (
                    account.status == ExternalAccountStatus.CONNECTING ||
                        account.status == ExternalAccountStatus.BACKFILL
                    )
            ) {
                Text(
                    text =
                        "Прогресс конфигурации: ${account.appliedGeneration} из ${account.desiredGeneration}",
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                )
            }
            account.safeError?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = colors.indicatorRed,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            externalCapabilityUnavailableReasons(account.capabilities)
                .forEach { reason ->
                    Text(
                        text = reason,
                        color = colors.indicatorRed,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            HorizontalDivider(
                color = colors.textAdditional30.copy(alpha = 0.35f),
            )
            OutlinedButton(
                onClick = onSettings,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Настроить синхронизацию")
            }
            if (reconnectRecommended) {
                Button(
                    onClick = onReconnect,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Переподключить")
                }
            }
            if (account.status != ExternalAccountStatus.DISCONNECTED) {
                TextButton(
                    onClick = onDisconnect,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Остановить подключение")
                }
            }
            TextButton(
                onClick = onDelete,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.indicatorRed,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить внешний аккаунт")
            }
        }
    }
}

@Composable
private fun ExternalChatsEmptyCard(
    searching: Boolean,
    preparing: Boolean,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = when {
                searching ->
                    "По этому запросу чаты не найдены."

                preparing ->
                    "Zulip ещё подготавливает каталог. Обновите экран немного позже."

                else ->
                    "Доступных внешних чатов пока нет."
            },
            color = colors.textAdditional50,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun ExternalChatCard(
    account: ExternalAccountResponse,
    chat: ExternalChatResponse,
    workspaceAccount: WorkspaceAccount?,
    busy: Boolean,
    automatic: Boolean,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    onMoveHere: () -> Unit,
    onExternalOpenError: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val context = LocalContext.current
    val originalUrl = remember(
        chat.source.originalUrl,
        account.settings.serverUrl,
    ) {
        safeExternalChatUrl(
            candidate = chat.source.originalUrl,
            providerOrigin = account.settings.serverUrl,
        )
    }
    val currentProjectId = workspaceAccount?.projectId
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.displayName,
                        color = colors.textHeaders,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = externalChatTypeLabel(chat.source.chatType),
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                    )
                }
                ExternalStatusBadge(
                    text = externalChatStatusLabel(chat),
                    color = externalChatStatusColor(chat.status),
                )
            }
            chat.safeError?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = colors.indicatorRed,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            externalCapabilityUnavailableReasons(chat.capabilities)
                .forEach { reason ->
                    Text(
                        text = reason,
                        color = colors.indicatorRed,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            if (chat.transitionPending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Workspace подтверждает изменение…",
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else if (automatic) {
                Text(
                    text = "Управляется автоматической синхронизацией",
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                )
            }
            originalUrl?.let { safeUrl ->
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    safeUrl.toUri(),
                                ),
                            )
                        }.onFailure {
                            onExternalOpenError()
                        }
                    },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text("Открыть исходный чат")
                }
            }
            if (!automatic && !chat.transitionPending) {
                if (!chat.selected) {
                    Button(
                        onClick = onSelect,
                        enabled = !busy && currentProjectId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Добавить в текущий проект")
                    }
                } else {
                    if (
                        currentProjectId != null &&
                        chat.projectId != currentProjectId
                    ) {
                        OutlinedButton(
                            onClick = onMoveHere,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Перенести в текущий проект")
                        }
                    }
                    TextButton(
                        onClick = onDeselect,
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colors.indicatorRed,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Убрать из Workspace")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalOperationCard(
    operation: ExternalOperationResponse,
    providerOrigin: String,
    busy: Boolean,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onExternalOpenError: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val context = LocalContext.current
    val originalUrl = remember(operation.originalUrl, providerOrigin) {
        safeExternalChatUrl(
            candidate = operation.originalUrl,
            providerOrigin = providerOrigin,
        )
    }
    Surface(
        color = colors.cardBackgroundBase,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = externalOperationActionLabel(operation.action),
                        color = colors.textHeaders,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            "Попытка ${operation.attempt} · ${operation.targetType}",
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExternalStatusBadge(
                    text = externalOperationStatusLabel(operation.status),
                    color = externalOperationStatusColor(operation.status),
                )
            }
            if (
                operation.status ==
                ExternalOperationStatus.MANUAL_RECONCILIATION_REQUIRED
            ) {
                Surface(
                    color = colors.indicatorRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text =
                            "Нужна ручная проверка: ${externalReconciliationReasonLabel(operation.reconciliationReason)}",
                        color = colors.indicatorRed,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            operation.safeError?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = colors.indicatorRed,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            originalUrl?.let { safeUrl ->
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    safeUrl.toUri(),
                                ),
                            )
                        }.onFailure {
                            onExternalOpenError()
                        }
                    },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text("Открыть у провайдера")
                }
            }
            if (operation.canRetry) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (operation.retryRequiresConfirmation) {
                            "Проверить и повторить"
                        } else {
                            "Повторить операцию"
                        },
                    )
                }
            }
            if (operation.canDiscard) {
                TextButton(
                    onClick = onDiscard,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.indicatorRed,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Удалить из очереди")
                }
            }
        }
    }
}

@Composable
private fun ExternalStatusBadge(
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ExternalAccountCredentialDialog(
    title: String,
    description: String,
    initialServerUrl: String,
    initialEmail: String,
    showSyncSettings: Boolean,
    busy: Boolean,
    successNotice: String?,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (
        String,
        String,
        String,
        ExternalAccountSelectionMode,
        ExternalHistoryDepth,
    ) -> Boolean,
) {
    SecureWindowWhileVisible()
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var email by remember { mutableStateOf(initialEmail) }
    // Deliberately not saveable: a provider credential must not enter saved
    // instance state or process-restoration storage.
    var apiKey by remember { mutableStateOf("") }
    var selectionMode by remember {
        mutableStateOf(ExternalAccountSelectionMode.EXPLICIT)
    }
    var historyDepth by remember {
        mutableStateOf(ExternalHistoryDepth.THIRTY_DAYS)
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val canSubmit =
        !busy &&
            serverUrl.isNotBlank() &&
            email.isNotBlank() &&
            apiKey.isNotBlank()
    val submit = {
        submitted = onSubmit(
            serverUrl,
            email,
            apiKey,
            selectionMode,
            historyDepth,
        )
    }
    LaunchedEffect(busy, successNotice) {
        if (submitted && !busy && successNotice != null) {
            apiKey = ""
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                apiKey = ""
                onDismiss()
            }
        },
        title = { Text(title) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(description)
                }
                item {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it.take(MAX_SERVER_URL_CHARS)
                        },
                        label = { Text("Адрес сервера") },
                        placeholder = { Text("zulip.example.com") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.take(MAX_EMAIL_CHARS)
                        },
                        label = { Text("Email Zulip") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it.take(MAX_API_KEY_CHARS)
                        },
                        label = { Text("API key") },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (canSubmit) submit()
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showSyncSettings) {
                    item {
                        ExternalSelectionModePicker(
                            selected = selectionMode,
                            enabled = !busy,
                            onSelected = { selectionMode = it },
                        )
                    }
                    item {
                        ExternalHistoryDepthPicker(
                            selected = historyDepth,
                            enabled = !busy,
                            onSelected = { historyDepth = it },
                        )
                    }
                }
                error?.let {
                    item {
                        Text(
                            text = it,
                            color = LocalWorkspaceColorsPalette
                                .current
                                .indicatorRed,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = canSubmit,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("Продолжить")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    apiKey = ""
                    onDismiss()
                },
                enabled = !busy,
            ) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun SecureWindowWhileVisible() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val wasSecure =
            activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_SECURE != 0
        if (!wasSecure) {
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose {
            if (!wasSecure) {
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun ExternalAccountSettingsDialog(
    account: ExternalAccountResponse,
    busy: Boolean,
    successNotice: String?,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (
        ExternalAccountSelectionMode,
        ExternalHistoryDepth,
    ) -> Boolean,
) {
    var selectionMode by rememberSaveable(account.uuid, account.revision) {
        mutableStateOf(account.settings.selectionMode)
    }
    var historyDepth by rememberSaveable(account.uuid, account.revision) {
        mutableStateOf(account.settings.historyDepth)
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val dirty =
        selectionMode != account.settings.selectionMode ||
            historyDepth != account.settings.historyDepth
    LaunchedEffect(busy, successNotice) {
        if (submitted && !busy && successNotice != null) {
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Настройки синхронизации") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ExternalSelectionModePicker(
                        selected = selectionMode,
                        enabled = !busy,
                        onSelected = { selectionMode = it },
                    )
                }
                item {
                    ExternalHistoryDepthPicker(
                        selected = historyDepth,
                        enabled = !busy,
                        onSelected = { historyDepth = it },
                    )
                }
                error?.let {
                    item {
                        Text(
                            text = it,
                            color = LocalWorkspaceColorsPalette
                                .current
                                .indicatorRed,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submitted = onSubmit(selectionMode, historyDepth)
                },
                enabled = !busy && dirty,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
            ) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun ExternalSelectionModePicker(
    selected: ExternalAccountSelectionMode,
    enabled: Boolean,
    onSelected: (ExternalAccountSelectionMode) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Выбор чатов",
            color = colors.textHeaders,
            fontWeight = FontWeight.Medium,
        )
        ExternalAccountSelectionMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                enabled = enabled,
                label = {
                    Text(
                        if (mode == ExternalAccountSelectionMode.EXPLICIT) {
                            "Вручную"
                        } else {
                            "Все автоматически"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExternalHistoryDepthPicker(
    selected: ExternalHistoryDepth,
    enabled: Boolean,
    onSelected: (ExternalHistoryDepth) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Глубина истории",
            color = colors.textHeaders,
            fontWeight = FontWeight.Medium,
        )
        ExternalHistoryDepth.entries.forEach { depth ->
            FilterChip(
                selected = selected == depth,
                onClick = { onSelected(depth) },
                enabled = enabled,
                label = { Text(externalHistoryDepthLabel(depth)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExternalConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) {
                        colors.indicatorRed
                    } else {
                        colors.primary
                    },
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
            ) {
                Text("Отмена")
            }
        },
    )
}

internal fun safeExternalChatUrl(
    candidate: String?,
    providerOrigin: String,
): String? {
    val raw = candidate?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (raw.length > MAX_EXTERNAL_LINK_CHARS) return null
    val link = runCatching { URI(raw) }.getOrNull() ?: return null
    val origin = runCatching { URI(providerOrigin) }.getOrNull() ?: return null
    if (
        !link.scheme.equals("https", ignoreCase = true) ||
        link.host.isNullOrBlank() ||
        link.userInfo != null ||
        !origin.scheme.equals("https", ignoreCase = true) ||
        origin.host.isNullOrBlank() ||
        origin.userInfo != null ||
        origin.path.orEmpty().isNotBlank() ||
        origin.rawQuery != null ||
        origin.rawFragment != null ||
        origin.port !in -1..65_535 ||
        !link.host.equals(origin.host, ignoreCase = true) ||
        effectiveHttpsPort(link) != effectiveHttpsPort(origin)
    ) {
        return null
    }
    return link.toASCIIString()
}

private fun effectiveHttpsPort(uri: URI): Int =
    if (uri.port == -1) 443 else uri.port

internal fun externalAccountStatusLabel(
    account: ExternalAccountResponse,
): String = if (account.liveReady) {
    "Подключён"
} else {
    when (account.status) {
        ExternalAccountStatus.CONNECTING -> "Подключение"
        ExternalAccountStatus.BACKFILL -> "Загрузка"
        ExternalAccountStatus.LIVE -> "Онлайн"
        ExternalAccountStatus.DEGRADED -> "Сбой"
        ExternalAccountStatus.AUTH_REQUIRED -> "Нужен ключ"
        ExternalAccountStatus.DISCONNECTED -> "Отключён"
        ExternalAccountStatus.SUSPENDED -> "Приостановлен"
    }
}

@Composable
private fun externalAccountStatusColor(
    account: ExternalAccountResponse,
): Color {
    val colors = LocalWorkspaceColorsPalette.current
    if (account.liveReady) return colors.indicatorGreen
    return when (account.status) {
        ExternalAccountStatus.LIVE -> colors.indicatorGreen
        ExternalAccountStatus.CONNECTING,
        ExternalAccountStatus.BACKFILL,
        -> colors.indicatorBlue
        ExternalAccountStatus.DEGRADED,
        ExternalAccountStatus.AUTH_REQUIRED,
        -> colors.indicatorOrange
        ExternalAccountStatus.DISCONNECTED,
        ExternalAccountStatus.SUSPENDED,
        -> colors.indicatorGrey
    }
}

private fun externalChatStatusLabel(chat: ExternalChatResponse): String =
    when {
        chat.transitionPending -> "Изменение"
        chat.status == ExternalChatStatus.AVAILABLE -> "Доступен"
        chat.status == ExternalChatStatus.SYNCING -> "Синхронизация"
        chat.status == ExternalChatStatus.LIVE -> "Онлайн"
        chat.status == ExternalChatStatus.DEGRADED -> "Сбой"
        else -> "Не выбран"
    }

@Composable
private fun externalChatStatusColor(status: ExternalChatStatus): Color {
    val colors = LocalWorkspaceColorsPalette.current
    return when (status) {
        ExternalChatStatus.LIVE -> colors.indicatorGreen
        ExternalChatStatus.SYNCING -> colors.indicatorBlue
        ExternalChatStatus.DEGRADED -> colors.indicatorOrange
        ExternalChatStatus.AVAILABLE,
        ExternalChatStatus.DESELECTED,
        -> colors.indicatorGrey
    }
}

private fun externalChatTypeLabel(type: ExternalChatType): String =
    when (type) {
        ExternalChatType.CHANNEL -> "Канал"
        ExternalChatType.PERSONAL -> "Личный чат"
        ExternalChatType.GROUP -> "Групповой чат"
    }

private fun externalHistoryDepthLabel(depth: ExternalHistoryDepth): String =
    when (depth) {
        ExternalHistoryDepth.NEW -> "Только новые сообщения"
        ExternalHistoryDepth.SEVEN_DAYS -> "Последние 7 дней"
        ExternalHistoryDepth.THIRTY_DAYS -> "Последние 30 дней"
        ExternalHistoryDepth.NINETY_DAYS -> "Последние 90 дней"
        ExternalHistoryDepth.ALL -> "Вся доступная история"
    }

internal fun externalCapabilityAvailable(
    capabilities: JsonObject,
    name: String,
): Boolean = runCatching {
    capabilities[name]
        ?.jsonObject
        ?.get("available")
        ?.jsonPrimitive
        ?.booleanOrNull == true
}.getOrDefault(false)

internal fun externalCapabilityUnavailableReasons(
    capabilities: JsonObject,
): List<String> = capabilities.entries
    .sortedBy { it.key }
    .mapNotNull { (name, descriptor) ->
        val unavailable = runCatching {
            descriptor.jsonObject["available"]
                ?.jsonPrimitive
                ?.booleanOrNull == false
        }.getOrDefault(false)
        if (!unavailable) return@mapNotNull null
        val message = runCatching {
            val messageValue = descriptor.jsonObject["unavailable_reason"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
            messageValue
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
        (message ?: externalCapabilityLabel(name))
            .take(MAX_CAPABILITY_REASON_CHARS)
    }
    .distinct()
    .take(MAX_VISIBLE_CAPABILITY_REASONS)

private fun externalCapabilityLabel(name: String): String = when (name) {
    EXTERNAL_CHAT_CATALOG_CAPABILITY ->
        "Каталог внешних чатов недоступен"

    "messenger.message.send" ->
        "Отправка сообщений провайдеру недоступна"

    "messenger.message.edit" ->
        "Редактирование сообщений провайдера недоступно"

    "messenger.message.delete" ->
        "Удаление сообщений провайдера недоступно"

    "messenger.message.read" ->
        "Синхронизация прочтения недоступна"

    "messenger.reaction.write" ->
        "Изменение реакций у провайдера недоступно"

    "messenger.file.transfer" ->
        "Передача файлов провайдеру недоступна"

    else -> name.take(MAX_CAPABILITY_REASON_CHARS)
}

internal fun externalOperationSortRank(
    operation: ExternalOperationResponse,
): Int = when (operation.status) {
    ExternalOperationStatus.MANUAL_RECONCILIATION_REQUIRED -> 0
    ExternalOperationStatus.FAILED -> 1
    ExternalOperationStatus.RUNNING -> 2
    ExternalOperationStatus.QUEUED -> 3
    ExternalOperationStatus.SUCCEEDED -> 4
    ExternalOperationStatus.DISCARDED -> 5
}

internal fun externalOperationStatusLabel(
    status: ExternalOperationStatus,
): String = when (status) {
    ExternalOperationStatus.QUEUED -> "В очереди"
    ExternalOperationStatus.RUNNING -> "Выполняется"
    ExternalOperationStatus.SUCCEEDED -> "Выполнено"
    ExternalOperationStatus.FAILED -> "Ошибка"
    ExternalOperationStatus.MANUAL_RECONCILIATION_REQUIRED ->
        "Нужна проверка"

    ExternalOperationStatus.DISCARDED -> "Удалено"
}

@Composable
private fun externalOperationStatusColor(
    status: ExternalOperationStatus,
): Color {
    val colors = LocalWorkspaceColorsPalette.current
    return when (status) {
        ExternalOperationStatus.SUCCEEDED -> colors.indicatorGreen
        ExternalOperationStatus.RUNNING,
        ExternalOperationStatus.QUEUED,
        -> colors.indicatorBlue

        ExternalOperationStatus.FAILED,
        ExternalOperationStatus.MANUAL_RECONCILIATION_REQUIRED,
        -> colors.indicatorOrange

        ExternalOperationStatus.DISCARDED -> colors.indicatorGrey
    }
}

private fun externalOperationActionLabel(action: String): String =
    when (action) {
        "message.send" -> "Отправка сообщения"
        "message.edit" -> "Редактирование сообщения"
        "message.delete" -> "Удаление сообщения"
        "message.read" -> "Синхронизация прочтения"
        "reaction.add" -> "Добавление реакции"
        "reaction.remove" -> "Удаление реакции"
        "stream.rename" -> "Переименование канала"
        "topic.rename" -> "Переименование темы"
        "topic.move" -> "Перенос темы"
        "file.upload" -> "Передача файла"
        else -> action
    }

private fun externalReconciliationReasonLabel(
    reason: ExternalOperationReconciliationReason?,
): String = when (reason) {
    ExternalOperationReconciliationReason.PROVIDER_HISTORY_UNAVAILABLE ->
        "история провайдера недоступна"

    ExternalOperationReconciliationReason.NO_MATCH_AFTER_AUTO_RESEND ->
        "после автоматического повтора подтверждение не найдено"

    ExternalOperationReconciliationReason.UNSAFE_PROVIDER_STATE ->
        "состояние провайдера нельзя определить безопасно"

    null -> "проверьте результат у провайдера"
}

private const val MAX_SEARCH_CHARS = 256
private const val MAX_SERVER_URL_CHARS = 2_048
private const val MAX_EMAIL_CHARS = 320
private const val MAX_API_KEY_CHARS = 4_096
private const val MAX_EXTERNAL_LINK_CHARS = 4_096
private const val MAX_CAPABILITY_REASON_CHARS = 512
private const val MAX_VISIBLE_CAPABILITY_REASONS = 8
private const val EXTERNAL_CHAT_CATALOG_CAPABILITY =
    "messenger.chat_catalog"
