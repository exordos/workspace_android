package ru.genesiscorporation.workspace.beta.modules.externalintegrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalBridgeInstanceAction
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalBridgeInstanceResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalBridgeInstanceStatus
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalHistoryDepth
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderHealthResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderLimits
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderSuspensionAction
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedExternalProviderPolicy
import java.util.UUID

class ExternalIntegrationsViewModel(
    private val userViewModel: UserViewModel,
    private val dataSource: ExternalIntegrationDataSource,
) : ViewModel() {
    val accounts = dataSource.accounts
    val chats = dataSource.chats
    val operations = dataSource.operations
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
    private val _ownerAccess =
        MutableStateFlow(ExternalOwnerIntegrationAccess.UNKNOWN)
    val ownerAccess: StateFlow<ExternalOwnerIntegrationAccess> =
        _ownerAccess.asStateFlow()
    private val _adminAccess =
        MutableStateFlow(ExternalProviderAdminAccess.UNKNOWN)
    val adminAccess: StateFlow<ExternalProviderAdminAccess> =
        _adminAccess.asStateFlow()
    private val _providerPolicy =
        MutableStateFlow<ValidatedExternalProviderPolicy?>(null)
    val providerPolicy: StateFlow<ValidatedExternalProviderPolicy?> =
        _providerPolicy.asStateFlow()
    private val _providerHealth =
        MutableStateFlow<ExternalProviderHealthResponse?>(null)
    val providerHealth: StateFlow<ExternalProviderHealthResponse?> =
        _providerHealth.asStateFlow()
    private val _bridgeInstances =
        MutableStateFlow<List<ExternalBridgeInstanceResponse>?>(null)
    val bridgeInstances:
        StateFlow<List<ExternalBridgeInstanceResponse>?> =
        _bridgeInstances.asStateFlow()
    private val _adminError = MutableStateFlow<String?>(null)
    val adminError: StateFlow<String?> = _adminError.asStateFlow()

    init {
        viewModelScope.launch {
            userViewModel.activeAccountId.collectLatest { ownerKey ->
                _error.value = null
                _notice.value = null
                _activeAction.value = null
                _ownerAccess.value =
                    ExternalOwnerIntegrationAccess.UNKNOWN
                _adminAccess.value = ExternalProviderAdminAccess.UNKNOWN
                _providerPolicy.value = null
                _providerHealth.value = null
                _bridgeInstances.value = null
                _adminError.value = null
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
        _adminError.value = null
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

    fun clearAdminError() {
        _adminError.value = null
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

    fun retryOperation(
        operation: ExternalOperationResponse,
        confirmDuplicateRisk: Boolean,
    ): Boolean {
        if (!operation.canRetry) return false
        if (
            operation.retryRequiresConfirmation &&
            !confirmDuplicateRisk
        ) {
            return false
        }
        return runMutation(
            action = ExternalIntegrationAction.RETRY_OPERATION,
            successNotice = "Повторная отправка поставлена в очередь.",
        ) {
            dataSource.retryOperation(
                operationUuid = operation.uuid,
                externalAccountUuid = operation.externalAccountUuid,
                confirmDuplicateRisk = confirmDuplicateRisk,
            )
        }
    }

    fun discardOperation(
        operation: ExternalOperationResponse,
    ): Boolean {
        if (!operation.canDiscard) return false
        return runMutation(
            action = ExternalIntegrationAction.DISCARD_OPERATION,
            successNotice = "Операция удалена из очереди.",
        ) {
            dataSource.discardOperation(
                operationUuid = operation.uuid,
                externalAccountUuid = operation.externalAccountUuid,
            )
        }
    }

    fun updateProviderPolicy(
        policy: ValidatedExternalProviderPolicy,
        enabled: Boolean,
        limits: ExternalProviderLimits,
        customCaCertificatesPem: List<String>?,
        removeCustomCa: Boolean,
    ): Boolean {
        val current = _providerPolicy.value
        if (current?.entityTag != policy.entityTag) return false
        if (removeCustomCa && customCaCertificatesPem != null) return false
        if (
            !removeCustomCa &&
            customCaCertificatesPem == null &&
            current.response.customCaBundle != null
        ) {
            return false
        }
        val changed =
            enabled != current.response.enabled ||
                limits != current.response.limits ||
                removeCustomCa ||
                customCaCertificatesPem != null
        if (!changed) return false
        return runMutation(
            action = ExternalIntegrationAction.UPDATE_PROVIDER_POLICY,
            successNotice = "Политика Zulip обновлена.",
            onSuccess = { _providerPolicy.value = it },
        ) {
            dataSource.updateProviderPolicy(
                enabled = enabled,
                limits = limits,
                customCaCertificatesPem =
                    if (removeCustomCa) null else customCaCertificatesPem,
                entityTag = policy.entityTag,
            )
        }
    }

    fun changeProviderSuspension(
        policy: ValidatedExternalProviderPolicy,
        action: ExternalProviderSuspensionAction,
    ): Boolean {
        val current = _providerPolicy.value
        if (current?.entityTag != policy.entityTag) return false
        if (
            (
                action == ExternalProviderSuspensionAction.SUSPEND &&
                    current.response.emergencySuspended
                ) ||
            (
                action == ExternalProviderSuspensionAction.RESUME &&
                    !current.response.emergencySuspended
                )
        ) {
            return false
        }
        return runMutation(
            action = if (
                action == ExternalProviderSuspensionAction.SUSPEND
            ) {
                ExternalIntegrationAction.SUSPEND_PROVIDER
            } else {
                ExternalIntegrationAction.RESUME_PROVIDER
            },
            successNotice = if (
                action == ExternalProviderSuspensionAction.SUSPEND
            ) {
                "Провайдер аварийно приостановлен."
            } else {
                "Работа провайдера возобновлена."
            },
            onSuccess = { _providerPolicy.value = it },
        ) {
            dataSource.changeProviderSuspension(action)
        }
    }

    fun changeBridgeInstanceStatus(
        instance: ExternalBridgeInstanceResponse,
        action: ExternalBridgeInstanceAction,
    ): Boolean {
        val current = _bridgeInstances.value
            ?.firstOrNull { it.uuid == instance.uuid }
            ?: return false
        if (
            current.revision != instance.revision ||
            !bridgeActionAllowed(current.status, action)
        ) {
            return false
        }
        return runMutation(
            action = when (action) {
                ExternalBridgeInstanceAction.SUSPEND ->
                    ExternalIntegrationAction.SUSPEND_BRIDGE

                ExternalBridgeInstanceAction.RESUME ->
                    ExternalIntegrationAction.RESUME_BRIDGE

                ExternalBridgeInstanceAction.REVOKE ->
                    ExternalIntegrationAction.REVOKE_BRIDGE
            },
            successNotice = when (action) {
                ExternalBridgeInstanceAction.SUSPEND ->
                    "Bridge-инстанс приостановлен."

                ExternalBridgeInstanceAction.RESUME ->
                    "Bridge-инстанс возобновлён."

                ExternalBridgeInstanceAction.REVOKE ->
                    "Сертификат bridge-инстанса отозван."
            },
            onSuccess = { updated ->
                _bridgeInstances.value = _bridgeInstances.value?.map {
                    if (it.uuid == updated.uuid) updated else it
                }
            },
        ) {
            dataSource.changeBridgeInstanceStatus(
                instanceUuid = current.uuid,
                action = action,
            )
        }
    }

    private suspend fun refreshScope(
        ownerKey: String,
        showLoading: Boolean,
    ) {
        if (showLoading) {
            _loadStatus.value = ExternalIntegrationsLoadStatus.LOADING
        }
        try {
            val (ownerOutcome, adminOutcome) = coroutineScope {
                val owner = async { refreshOwnerIntegrations() }
                val admin = async { refreshProviderAdmin() }
                owner.await() to admin.await()
            }
            if (!isOwnerCurrent(ownerKey)) return
            _ownerAccess.value = ownerOutcome.access
            _adminAccess.value = adminOutcome.access
            if (adminOutcome.policyWasRead) {
                _providerPolicy.value = adminOutcome.policyUpdate
            }
            if (adminOutcome.healthWasRead) {
                _providerHealth.value = adminOutcome.healthUpdate
            }
            if (adminOutcome.instancesWereRead) {
                _bridgeInstances.value = adminOutcome.instancesUpdate
            }
            _adminError.value = adminOutcome.error?.let(
                ::externalIntegrationErrorText,
            )
            ownerOutcome.error?.let {
                _error.value = externalIntegrationErrorText(it)
            }
            _loadStatus.value = if (
                ownerOutcome.error != null &&
                ownerOutcome.access !=
                ExternalOwnerIntegrationAccess.ALLOWED &&
                adminOutcome.access != ExternalProviderAdminAccess.VISIBLE
            ) {
                ExternalIntegrationsLoadStatus.ERROR
            } else {
                ExternalIntegrationsLoadStatus.READY
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

    private suspend fun refreshOwnerIntegrations():
        OwnerIntegrationRefreshOutcome {
        return when (val accountsResult = dataSource.listAccounts()) {
            is ApiResult.Error -> if (
                accountsResult.error.kind == ApiErrorKind.FORBIDDEN
            ) {
                OwnerIntegrationRefreshOutcome(
                    access = ExternalOwnerIntegrationAccess.DENIED,
                )
            } else {
                OwnerIntegrationRefreshOutcome(
                    access = if (accounts.value.isEmpty()) {
                        ExternalOwnerIntegrationAccess.ERROR
                    } else {
                        ExternalOwnerIntegrationAccess.ALLOWED
                    },
                    error = accountsResult.error,
                )
            }

            is ApiResult.Success -> {
                for (account in accountsResult.value) {
                    when (
                        val chatsResult =
                            dataSource.listChats(account.response.uuid)
                    ) {
                        is ApiResult.Success -> Unit
                        is ApiResult.Error ->
                            return OwnerIntegrationRefreshOutcome(
                                access =
                                    ExternalOwnerIntegrationAccess.ALLOWED,
                                error = chatsResult.error,
                            )
                    }
                    when (
                        val operationsResult =
                            dataSource.listOperations(
                                account.response.uuid,
                            )
                    ) {
                        is ApiResult.Success -> Unit
                        is ApiResult.Error ->
                            return OwnerIntegrationRefreshOutcome(
                                access =
                                    ExternalOwnerIntegrationAccess.ALLOWED,
                                error = operationsResult.error,
                            )
                    }
                }
                OwnerIntegrationRefreshOutcome(
                    access = ExternalOwnerIntegrationAccess.ALLOWED,
                )
            }
        }
    }

    private suspend fun refreshProviderAdmin():
        ProviderAdminRefreshOutcome = coroutineScope {
        val policyDeferred = async { dataSource.getProviderPolicy() }
        val healthDeferred = async { dataSource.getProviderHealth() }
        val instancesDeferred = async { dataSource.listBridgeInstances() }
        val policyResult = policyDeferred.await()
        val healthResult = healthDeferred.await()
        val instancesResult = instancesDeferred.await()
        val errors = listOfNotNull(
            (policyResult as? ApiResult.Error)?.error,
            (healthResult as? ApiResult.Error)?.error,
            (instancesResult as? ApiResult.Error)?.error,
        )
        val policy = (policyResult as? ApiResult.Success)?.value
        val health = (healthResult as? ApiResult.Success)?.value
        val instances = (instancesResult as? ApiResult.Success)?.value
        val authorized =
            policy != null ||
                health != null ||
                instances != null ||
                (
                    errors.isNotEmpty() &&
                        (
                            _providerPolicy.value != null ||
                                _providerHealth.value != null ||
                                _bridgeInstances.value != null
                            )
                    )
        ProviderAdminRefreshOutcome(
            access = when {
                authorized -> ExternalProviderAdminAccess.VISIBLE
                errors.isNotEmpty() -> ExternalProviderAdminAccess.ERROR
                else -> ExternalProviderAdminAccess.HIDDEN
            },
            policyWasRead = policyResult is ApiResult.Success,
            policyUpdate = policy,
            healthWasRead = healthResult is ApiResult.Success,
            healthUpdate = health,
            instancesWereRead = instancesResult is ApiResult.Success,
            instancesUpdate = instances,
            error = errors.firstOrNull(),
        )
    }

    private fun <T> runMutation(
        action: ExternalIntegrationAction,
        successNotice: String,
        onSuccess: (T) -> Unit = {},
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
                        onSuccess(result.value)
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

enum class ExternalOwnerIntegrationAccess {
    UNKNOWN,
    ALLOWED,
    DENIED,
    ERROR,
}

enum class ExternalProviderAdminAccess {
    UNKNOWN,
    HIDDEN,
    VISIBLE,
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
    RETRY_OPERATION,
    DISCARD_OPERATION,
    UPDATE_PROVIDER_POLICY,
    SUSPEND_PROVIDER,
    RESUME_PROVIDER,
    SUSPEND_BRIDGE,
    RESUME_BRIDGE,
    REVOKE_BRIDGE,
}

private data class ExternalIntegrationScope(
    val ownerKey: String,
    val projectId: String,
)

private data class OwnerIntegrationRefreshOutcome(
    val access: ExternalOwnerIntegrationAccess,
    val error: ApiError? = null,
)

private data class ProviderAdminRefreshOutcome(
    val access: ExternalProviderAdminAccess,
    val policyWasRead: Boolean,
    val policyUpdate: ValidatedExternalProviderPolicy?,
    val healthWasRead: Boolean,
    val healthUpdate: ExternalProviderHealthResponse?,
    val instancesWereRead: Boolean,
    val instancesUpdate: List<ExternalBridgeInstanceResponse>?,
    val error: ApiError?,
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

internal fun bridgeActionAllowed(
    status: ExternalBridgeInstanceStatus,
    action: ExternalBridgeInstanceAction,
): Boolean = when (action) {
    ExternalBridgeInstanceAction.SUSPEND ->
        status != ExternalBridgeInstanceStatus.SUSPENDED &&
            status != ExternalBridgeInstanceStatus.REVOKED

    ExternalBridgeInstanceAction.RESUME ->
        status == ExternalBridgeInstanceStatus.SUSPENDED

    ExternalBridgeInstanceAction.REVOKE ->
        status != ExternalBridgeInstanceStatus.REVOKED
}
