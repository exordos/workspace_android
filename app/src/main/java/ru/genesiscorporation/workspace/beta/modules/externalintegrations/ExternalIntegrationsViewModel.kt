package ru.genesiscorporation.workspace.beta.modules.externalintegrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ExternalIntegrationDataSource
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountSelectionMode
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalHistoryDepth
import java.util.UUID

class ExternalIntegrationsViewModel(
    private val userViewModel: UserViewModel,
    private val dataSource: ExternalIntegrationDataSource,
) : ViewModel() {
    val accounts = dataSource.accounts
    val chats = dataSource.chats
    val activeAccount = userViewModel.activeAccount

    private val _loadStatus =
        MutableStateFlow(ExternalIntegrationsLoadStatus.IDLE)
    val loadStatus: StateFlow<ExternalIntegrationsLoadStatus> =
        _loadStatus.asStateFlow()
    private val _activeAction =
        MutableStateFlow<ExternalIntegrationAction?>(null)
    val activeAction: StateFlow<ExternalIntegrationAction?> =
        _activeAction.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            userViewModel.activeAccountId.collectLatest { ownerKey ->
                _error.value = null
                _notice.value = null
                _activeAction.value = null
                if (ownerKey == null) {
                    _loadStatus.value = ExternalIntegrationsLoadStatus.IDLE
                } else {
                    refreshScope(ownerKey, showLoading = true)
                }
            }
        }
    }

    fun refresh(): Boolean {
        if (
            _loadStatus.value == ExternalIntegrationsLoadStatus.LOADING ||
            _activeAction.value != null
        ) {
            return false
        }
        val ownerKey = userViewModel.activeAccountId.value ?: run {
            _error.value = "Текущий аккаунт недоступен"
            return false
        }
        _error.value = null
        _loadStatus.value = ExternalIntegrationsLoadStatus.LOADING
        viewModelScope.launch {
            refreshScope(ownerKey, showLoading = false)
        }
        return true
    }

    fun clearError() {
        _error.value = null
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun createAccount(
        serverUrl: String,
        email: String,
        apiKey: String,
        selectionMode: ExternalAccountSelectionMode,
        historyDepth: ExternalHistoryDepth,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.CREATE_ACCOUNT,
        successNotice = "Zulip подключён. Синхронизация запускается.",
    ) { scope ->
        dataSource.createAccount(
            accountUuid = UUID.randomUUID().toString(),
            serverUrl = serverUrl,
            email = email,
            apiKey = apiKey,
            selectionMode = selectionMode,
            historyDepth = historyDepth,
            defaultProjectId = scope.projectId,
        )
    }

    fun updateAccount(
        account: ExternalAccountResponse,
        selectionMode: ExternalAccountSelectionMode,
        historyDepth: ExternalHistoryDepth,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.UPDATE_ACCOUNT,
        successNotice = "Настройки синхронизации сохранены.",
    ) { scope ->
        dataSource.updateAccount(
            accountUuid = account.uuid,
            selectionMode = selectionMode,
            historyDepth = historyDepth,
            defaultProjectId = scope.projectId,
            entityTag = entityTag(account.revision),
        )
    }

    fun reconnectAccount(
        account: ExternalAccountResponse,
        serverUrl: String,
        email: String,
        apiKey: String,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.RECONNECT_ACCOUNT,
        successNotice = "Новые учётные данные приняты.",
    ) {
        dataSource.reconnectAccount(
            accountUuid = account.uuid,
            serverUrl = serverUrl,
            email = email,
            apiKey = apiKey,
            entityTag = entityTag(account.revision),
        )
    }

    fun disconnectAccount(
        account: ExternalAccountResponse,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.DISCONNECT_ACCOUNT,
        successNotice = "Подключение остановлено.",
    ) {
        dataSource.disconnectAccount(account.uuid)
    }

    fun deleteAccount(
        account: ExternalAccountResponse,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.DELETE_ACCOUNT,
        successNotice = "Внешний аккаунт удалён.",
    ) {
        dataSource.deleteAccount(account.uuid)
    }

    fun selectChat(
        chat: ExternalChatResponse,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.SELECT_CHAT,
        successNotice = "Чат добавлен в текущий проект.",
    ) { scope ->
        dataSource.selectChat(chat.uuid, scope.projectId)
    }

    fun deselectChat(
        chat: ExternalChatResponse,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.DESELECT_CHAT,
        successNotice = "Проекция чата удаляется.",
    ) {
        dataSource.deselectChat(chat.uuid)
    }

    fun moveChatHere(
        chat: ExternalChatResponse,
    ): Boolean = runMutation(
        action = ExternalIntegrationAction.MOVE_CHAT,
        successNotice = "Чат переносится в текущий проект.",
    ) { scope ->
        dataSource.moveChat(
            chatUuid = chat.uuid,
            projectId = scope.projectId,
            entityTag = entityTag(chat.revision),
        )
    }

    private suspend fun refreshScope(
        ownerKey: String,
        showLoading: Boolean,
    ) {
        if (showLoading) {
            _loadStatus.value = ExternalIntegrationsLoadStatus.LOADING
        }
        try {
            when (val accountsResult = dataSource.listAccounts()) {
                is ApiResult.Error -> {
                    if (isOwnerCurrent(ownerKey)) {
                        _error.value =
                            externalIntegrationErrorText(accountsResult.error)
                        _loadStatus.value =
                            ExternalIntegrationsLoadStatus.ERROR
                    }
                    return
                }

                is ApiResult.Success -> {
                    for (account in accountsResult.value) {
                        when (
                            val chatsResult =
                                dataSource.listChats(account.response.uuid)
                        ) {
                            is ApiResult.Success -> Unit
                            is ApiResult.Error -> {
                                if (isOwnerCurrent(ownerKey)) {
                                    _error.value = externalIntegrationErrorText(
                                        chatsResult.error,
                                    )
                                    _loadStatus.value =
                                        ExternalIntegrationsLoadStatus.ERROR
                                }
                                return
                            }
                        }
                    }
                }
            }
            if (isOwnerCurrent(ownerKey)) {
                _loadStatus.value = ExternalIntegrationsLoadStatus.READY
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (isOwnerCurrent(ownerKey)) {
                _error.value =
                    "Не удалось обновить внешние интеграции. Повторите попытку."
                _loadStatus.value = ExternalIntegrationsLoadStatus.ERROR
            }
        }
    }

    private fun <T> runMutation(
        action: ExternalIntegrationAction,
        successNotice: String,
        request: suspend (ExternalIntegrationScope) -> ApiResult<T, ApiError>,
    ): Boolean {
        if (_activeAction.value != null) return false
        val scope = currentScope() ?: run {
            _error.value = "Не удалось определить текущий проект"
            return false
        }
        _activeAction.value = action
        _error.value = null
        _notice.value = null
        viewModelScope.launch {
            try {
                when (val result = request(scope)) {
                    is ApiResult.Success -> {
                        if (!isOwnerCurrent(scope.ownerKey)) return@launch
                        _notice.value = successNotice
                        refreshScope(
                            ownerKey = scope.ownerKey,
                            showLoading = false,
                        )
                    }

                    is ApiResult.Error -> {
                        if (!isOwnerCurrent(scope.ownerKey)) return@launch
                        _error.value =
                            externalIntegrationErrorText(result.error)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (isOwnerCurrent(scope.ownerKey)) {
                    _error.value =
                        "Операция не завершена. Проверьте соединение и повторите."
                }
            } finally {
                if (isOwnerCurrent(scope.ownerKey)) {
                    _activeAction.value = null
                }
            }
        }
        return true
    }

    private fun currentScope(): ExternalIntegrationScope? {
        val account = userViewModel.activeAccount.value ?: return null
        if (account.accountId.isBlank() || account.projectId.isBlank()) {
            return null
        }
        return ExternalIntegrationScope(
            ownerKey = account.accountId,
            projectId = account.projectId,
        )
    }

    private fun isOwnerCurrent(ownerKey: String): Boolean =
        userViewModel.activeAccountId.value == ownerKey
}

enum class ExternalIntegrationsLoadStatus {
    IDLE,
    LOADING,
    READY,
    ERROR,
}

enum class ExternalIntegrationAction {
    CREATE_ACCOUNT,
    UPDATE_ACCOUNT,
    RECONNECT_ACCOUNT,
    DISCONNECT_ACCOUNT,
    DELETE_ACCOUNT,
    SELECT_CHAT,
    DESELECT_CHAT,
    MOVE_CHAT,
}

private data class ExternalIntegrationScope(
    val ownerKey: String,
    val projectId: String,
)

internal fun externalIntegrationErrorText(error: ApiError): String =
    when {
        error.code == "ACCOUNT_CHANGED" ->
            "Аккаунт сменился. Данные не были применены."

        error.kind == ApiErrorKind.UNAUTHORIZED ->
            "Сеанс завершён. Войдите снова."

        error.kind == ApiErrorKind.FORBIDDEN ->
            "Недостаточно прав для этой операции."

        error.kind == ApiErrorKind.CONFLICT ->
            "Данные уже изменились. Обновите экран и повторите."

        error.kind == ApiErrorKind.RATE_LIMITED ->
            "Слишком много запросов. Повторите немного позже."

        error.kind == ApiErrorKind.TIMEOUT ||
            error.kind == ApiErrorKind.NETWORK ->
            "Нет устойчивого соединения. Изменения не подтверждены."

        error.kind == ApiErrorKind.VALIDATION ->
            "Проверьте введённые данные."

        error.kind == ApiErrorKind.SERVER ->
            "Сервис временно недоступен. Повторите попытку."

        else ->
            "Не удалось выполнить операцию. Обновите экран и повторите."
    }

private fun entityTag(revision: Int): String = "\"$revision\""
