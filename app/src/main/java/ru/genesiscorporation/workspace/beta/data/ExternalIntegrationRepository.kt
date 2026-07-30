package ru.genesiscorporation.workspace.beta.data

import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.accountChangedError
import ru.genesiscorporation.workspace.beta.data.remote.dto.CreateExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DiscardExternalOperationRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeselectExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DisconnectExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountSelectionMode
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalBridgeInstanceAction
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalBridgeInstanceResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalBridgeInstancesRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalHistoryDepth
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalOperationsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderHealthRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderHealthResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderLimits
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderPolicyRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderPolicyResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalProviderSuspensionAction
import ru.genesiscorporation.workspace.beta.data.remote.dto.MoveExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ReconnectExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RetryExternalOperationRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SelectExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateExternalProviderPolicyRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedExternalAccount
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedExternalProviderPolicy
import ru.genesiscorporation.workspace.beta.data.remote.dto.ChangeExternalBridgeInstanceStatusRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ChangeExternalProviderSuspensionRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalExternalIntegrationUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalBridgeInstanceResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalOperationResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalProviderHealthResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalProviderPolicyResponse

interface ExternalIntegrationDataSource {
    val accounts: StateFlow<List<ExternalAccountResponse>>
    val chats: StateFlow<Map<String, List<ExternalChatResponse>>>
    val operations: StateFlow<Map<String, List<ExternalOperationResponse>>>

    suspend fun listAccounts():
        ApiResult<List<ValidatedExternalAccount>, ApiError>

    suspend fun getAccount(
        accountUuid: String,
    ): ApiResult<ValidatedExternalAccount, ApiError>

    suspend fun createAccount(
        accountUuid: String,
        serverUrl: String,
        email: String,
        apiKey: String,
        selectionMode: ExternalAccountSelectionMode,
        historyDepth: ExternalHistoryDepth,
        defaultProjectId: String,
    ): ApiResult<ValidatedExternalAccount, ApiError>

    suspend fun updateAccount(
        accountUuid: String,
        selectionMode: ExternalAccountSelectionMode,
        historyDepth: ExternalHistoryDepth,
        defaultProjectId: String,
        entityTag: String,
    ): ApiResult<ValidatedExternalAccount, ApiError>

    suspend fun reconnectAccount(
        accountUuid: String,
        serverUrl: String,
        email: String,
        apiKey: String,
        entityTag: String,
    ): ApiResult<ValidatedExternalAccount, ApiError>

    suspend fun disconnectAccount(
        accountUuid: String,
    ): ApiResult<ValidatedExternalAccount, ApiError>

    suspend fun deleteAccount(
        accountUuid: String,
    ): ApiResult<Unit, ApiError>

    suspend fun listChats(
        externalAccountUuid: String,
    ): ApiResult<List<ExternalChatResponse>, ApiError>

    suspend fun getChat(
        chatUuid: String,
    ): ApiResult<ExternalChatResponse, ApiError>

    suspend fun selectChat(
        chatUuid: String,
        projectId: String,
    ): ApiResult<ExternalChatResponse, ApiError>

    suspend fun deselectChat(
        chatUuid: String,
    ): ApiResult<ExternalChatResponse, ApiError>

    suspend fun moveChat(
        chatUuid: String,
        projectId: String,
        entityTag: String,
    ): ApiResult<ExternalChatResponse, ApiError>

    suspend fun listOperations(
        externalAccountUuid: String,
    ): ApiResult<List<ExternalOperationResponse>, ApiError>

    suspend fun retryOperation(
        operationUuid: String,
        externalAccountUuid: String,
        confirmDuplicateRisk: Boolean,
    ): ApiResult<ExternalOperationResponse, ApiError>

    suspend fun discardOperation(
        operationUuid: String,
        externalAccountUuid: String,
    ): ApiResult<Unit, ApiError>

    suspend fun getProviderPolicy():
        ApiResult<ValidatedExternalProviderPolicy?, ApiError>

    suspend fun updateProviderPolicy(
        enabled: Boolean,
        limits: ExternalProviderLimits,
        customCaCertificatesPem: List<String>?,
        entityTag: String,
    ): ApiResult<ValidatedExternalProviderPolicy, ApiError>

    suspend fun changeProviderSuspension(
        action: ExternalProviderSuspensionAction,
    ): ApiResult<ValidatedExternalProviderPolicy, ApiError>

    suspend fun getProviderHealth():
        ApiResult<ExternalProviderHealthResponse?, ApiError>

    suspend fun listBridgeInstances():
        ApiResult<List<ExternalBridgeInstanceResponse>?, ApiError>

    suspend fun changeBridgeInstanceStatus(
        instanceUuid: String,
        action: ExternalBridgeInstanceAction,
    ): ApiResult<ExternalBridgeInstanceResponse, ApiError>
}

class ExternalIntegrationRepository(
    private val client: WorkspaceAPIClient,
    private val eventsRepository: EventsRepository,
) : ExternalIntegrationDataSource {
    override val accounts = eventsRepository.externalAccounts
    override val chats = eventsRepository.externalChats
    override val operations = eventsRepository.externalOperations

    override suspend fun listAccounts():
        ApiResult<List<ValidatedExternalAccount>, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val baselineRevisions = eventsRepository.externalAccounts.value
            .associate { it.uuid to it.revision }
        val accounts = mutableListOf<ValidatedExternalAccount>()
        val seenAccountUuids = mutableSetOf<String>()
        val seenMarkers = mutableSetOf<String>()
        var pageMarker: String? = null
        repeat(MAX_EXTERNAL_PAGES) {
            when (
                val result = performOwned(
                    ownerKey = ownerKey,
                    request = ExternalAccountsRequest(
                        pageLimit = EXTERNAL_PAGE_SIZE,
                        pageMarker = pageMarker,
                    ),
                )
            ) {
                is ApiResult.Error -> return result
                is ApiResult.Success -> {
                    val validated = validateAccounts(result.value)
                        ?: return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    if (
                        validated.any {
                            !seenAccountUuids.add(it.response.uuid)
                        }
                    ) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    accounts += validated
                    val nextMarker = runCatching {
                        canonicalPageMarker(result.metadata.nextPageMarker)
                    }.getOrElse {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    } ?: run {
                        if (!isOwnerCurrent(ownerKey)) {
                            return ApiResult.Error(accountChangedError())
                        }
                        eventsRepository.reconcileExternalAccountSnapshots(
                            responses = accounts.map { it.response },
                            baselineRevisions = baselineRevisions,
                        )
                        return ApiResult.Success(accounts)
                    }
                    if (!seenMarkers.add(nextMarker)) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    pageMarker = nextMarker
                }
            }
        }
        return ApiResult.Error(externalPaginationLimitError())
    }

    override suspend fun getAccount(
        accountUuid: String,
    ): ApiResult<ValidatedExternalAccount, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalUuid = runCatching {
            canonicalExternalIntegrationUuid(accountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (
            val result = performOwned(
                ownerKey,
                ExternalAccountRequest(canonicalUuid),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedAccountResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = canonicalUuid,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun createAccount(
        accountUuid: String,
        serverUrl: String,
        email: String,
        apiKey: String,
        selectionMode: ExternalAccountSelectionMode,
        historyDepth: ExternalHistoryDepth,
        defaultProjectId: String,
    ): ApiResult<ValidatedExternalAccount, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val request = runCatching {
            CreateExternalAccountRequest(
                accountUuid = accountUuid,
                serverUrl = serverUrl,
                email = email,
                apiKey = apiKey,
                selectionMode = selectionMode,
                historyDepth = historyDepth,
                defaultProjectId = defaultProjectId,
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (val result = performOwned(ownerKey, request)) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedAccountResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = request.data.uuid,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun updateAccount(
        accountUuid: String,
        selectionMode: ExternalAccountSelectionMode,
        historyDepth: ExternalHistoryDepth,
        defaultProjectId: String,
        entityTag: String,
    ): ApiResult<ValidatedExternalAccount, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val input = runCatching {
            val canonicalUuid =
                canonicalExternalIntegrationUuid(accountUuid)
            canonicalUuid to UpdateExternalAccountRequest(
                accountUuid = canonicalUuid,
                selectionMode = selectionMode,
                historyDepth = historyDepth,
                defaultProjectId = defaultProjectId,
                entityTag = entityTag,
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val (canonicalUuid, request) = input
        return when (
            val result = performOwned(
                ownerKey,
                request,
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedAccountResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = canonicalUuid,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun reconnectAccount(
        accountUuid: String,
        serverUrl: String,
        email: String,
        apiKey: String,
        entityTag: String,
    ): ApiResult<ValidatedExternalAccount, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalUuid = runCatching {
            canonicalExternalIntegrationUuid(accountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val request = runCatching {
            ReconnectExternalAccountRequest(
                accountUuid = canonicalUuid,
                serverUrl = serverUrl,
                email = email,
                apiKey = apiKey,
                entityTag = entityTag,
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (
            val result = performOwned(
                ownerKey,
                request,
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedAccountResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = canonicalUuid,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun disconnectAccount(
        accountUuid: String,
    ): ApiResult<ValidatedExternalAccount, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalUuid = runCatching {
            canonicalExternalIntegrationUuid(accountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (
            val result = performOwned(
                ownerKey,
                DisconnectExternalAccountRequest(canonicalUuid),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedAccountResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = canonicalUuid,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun deleteAccount(
        accountUuid: String,
    ): ApiResult<Unit, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalUuid = runCatching {
            canonicalExternalIntegrationUuid(accountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val current = eventsRepository.externalAccounts.value
            .singleOrNull { it.uuid == canonicalUuid }
        return when (
            val result = performOwned(
                ownerKey,
                DeleteExternalAccountRequest(canonicalUuid),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> {
                if (!isOwnerCurrent(ownerKey)) {
                    ApiResult.Error(accountChangedError())
                } else {
                    current?.let(
                        eventsRepository::removeExternalAccountSnapshot,
                    )
                    ApiResult.Success(Unit)
                }
            }
        }
    }

    override suspend fun listChats(
        externalAccountUuid: String,
    ): ApiResult<List<ExternalChatResponse>, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalAccountUuid = runCatching {
            canonicalExternalIntegrationUuid(externalAccountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val baselineRevisions =
            eventsRepository.externalChats.value[canonicalAccountUuid]
                .orEmpty()
                .associate { it.uuid to it.revision }
        val chats = mutableListOf<ExternalChatResponse>()
        val seenChatUuids = mutableSetOf<String>()
        val seenMarkers = mutableSetOf<String>()
        var pageMarker: String? = null
        repeat(MAX_EXTERNAL_PAGES) {
            when (
                val result = performOwned(
                    ownerKey = ownerKey,
                    request = ExternalChatsRequest(
                        externalAccountUuid = canonicalAccountUuid,
                        pageLimit = EXTERNAL_PAGE_SIZE,
                        pageMarker = pageMarker,
                    ),
                )
            ) {
                is ApiResult.Error -> return result
                is ApiResult.Success -> {
                    val validated = validateChats(
                        responses = result.value,
                        expectedExternalAccountUuid = canonicalAccountUuid,
                    ) ?: return ApiResult.Error(
                        malformedExternalResponseError(),
                    )
                    if (
                        validated.any {
                            !seenChatUuids.add(it.uuid)
                        }
                    ) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    chats += validated
                    val nextMarker = runCatching {
                        canonicalPageMarker(result.metadata.nextPageMarker)
                    }.getOrElse {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    } ?: run {
                        if (!isOwnerCurrent(ownerKey)) {
                            return ApiResult.Error(accountChangedError())
                        }
                        eventsRepository.reconcileExternalChatSnapshots(
                            externalAccountUuid = canonicalAccountUuid,
                            responses = chats,
                            baselineRevisions = baselineRevisions,
                        )
                        return ApiResult.Success(chats)
                    }
                    if (!seenMarkers.add(nextMarker)) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    pageMarker = nextMarker
                }
            }
        }
        return ApiResult.Error(externalPaginationLimitError())
    }

    override suspend fun getChat(
        chatUuid: String,
    ): ApiResult<ExternalChatResponse, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalUuid = runCatching {
            canonicalExternalIntegrationUuid(chatUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (
            val result = performOwned(
                ownerKey,
                ExternalChatRequest(canonicalUuid),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedChatResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = canonicalUuid,
            )
        }
    }

    override suspend fun selectChat(
        chatUuid: String,
        projectId: String,
    ): ApiResult<ExternalChatResponse, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val input = runCatching {
            val canonicalUuid = canonicalExternalIntegrationUuid(chatUuid)
            canonicalUuid to SelectExternalChatRequest(
                canonicalUuid,
                projectId,
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val (canonicalUuid, request) = input
        return mutateChat(
            ownerKey = ownerKey,
            expectedChatUuid = canonicalUuid,
            request = request,
        )
    }

    override suspend fun deselectChat(
        chatUuid: String,
    ): ApiResult<ExternalChatResponse, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val input = runCatching {
            val canonicalUuid = canonicalExternalIntegrationUuid(chatUuid)
            canonicalUuid to DeselectExternalChatRequest(canonicalUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val (canonicalUuid, request) = input
        return mutateChat(
            ownerKey = ownerKey,
            expectedChatUuid = canonicalUuid,
            request = request,
        )
    }

    override suspend fun moveChat(
        chatUuid: String,
        projectId: String,
        entityTag: String,
    ): ApiResult<ExternalChatResponse, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val input = runCatching {
            val canonicalUuid = canonicalExternalIntegrationUuid(chatUuid)
            canonicalUuid to MoveExternalChatRequest(
                chatUuid = canonicalUuid,
                projectId = projectId,
                entityTag = entityTag,
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val (canonicalUuid, request) = input
        return mutateChat(
            ownerKey = ownerKey,
            expectedChatUuid = canonicalUuid,
            request = request,
        )
    }

    override suspend fun listOperations(
        externalAccountUuid: String,
    ): ApiResult<List<ExternalOperationResponse>, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalAccountUuid = runCatching {
            canonicalExternalIntegrationUuid(externalAccountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val baselineRevisions =
            eventsRepository.externalOperations.value[canonicalAccountUuid]
                .orEmpty()
                .associate { it.uuid to it.revision }
        val operations = mutableListOf<ExternalOperationResponse>()
        val seenOperationUuids = mutableSetOf<String>()
        val seenMarkers = mutableSetOf<String>()
        var pageMarker: String? = null
        repeat(MAX_EXTERNAL_PAGES) {
            when (
                val result = performOwned(
                    ownerKey = ownerKey,
                    request = ExternalOperationsRequest(
                        externalAccountUuid = canonicalAccountUuid,
                        pageLimit = EXTERNAL_PAGE_SIZE,
                        pageMarker = pageMarker,
                    ),
                )
            ) {
                is ApiResult.Error -> return result
                is ApiResult.Success -> {
                    val validated = validateOperations(
                        responses = result.value,
                        expectedExternalAccountUuid = canonicalAccountUuid,
                    ) ?: return ApiResult.Error(
                        malformedExternalResponseError(),
                    )
                    if (
                        validated.any {
                            !seenOperationUuids.add(it.uuid)
                        }
                    ) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    operations += validated
                    val nextMarker = runCatching {
                        canonicalPageMarker(result.metadata.nextPageMarker)
                    }.getOrElse {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    } ?: run {
                        if (!isOwnerCurrent(ownerKey)) {
                            return ApiResult.Error(accountChangedError())
                        }
                        eventsRepository.reconcileExternalOperationSnapshots(
                            externalAccountUuid = canonicalAccountUuid,
                            responses = operations,
                            baselineRevisions = baselineRevisions,
                        )
                        return ApiResult.Success(operations)
                    }
                    if (!seenMarkers.add(nextMarker)) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    pageMarker = nextMarker
                }
            }
        }
        return ApiResult.Error(externalPaginationLimitError())
    }

    override suspend fun retryOperation(
        operationUuid: String,
        externalAccountUuid: String,
        confirmDuplicateRisk: Boolean,
    ): ApiResult<ExternalOperationResponse, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val input = runCatching {
            val canonicalOperationUuid =
                canonicalExternalIntegrationUuid(operationUuid)
            val canonicalAccountUuid =
                canonicalExternalIntegrationUuid(externalAccountUuid)
            Triple(
                canonicalOperationUuid,
                canonicalAccountUuid,
                RetryExternalOperationRequest(
                    operationUuid = canonicalOperationUuid,
                    confirmDuplicateRisk = confirmDuplicateRisk,
                ),
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val (canonicalOperationUuid, canonicalAccountUuid, request) = input
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request = request,
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedOperationResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = canonicalOperationUuid,
                expectedExternalAccountUuid = canonicalAccountUuid,
            )
        }
    }

    override suspend fun discardOperation(
        operationUuid: String,
        externalAccountUuid: String,
    ): ApiResult<Unit, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val input = runCatching {
            canonicalExternalIntegrationUuid(operationUuid) to
                canonicalExternalIntegrationUuid(externalAccountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        val (canonicalOperationUuid, canonicalAccountUuid) = input
        val current =
            eventsRepository.externalOperations.value[canonicalAccountUuid]
                .orEmpty()
                .singleOrNull { it.uuid == canonicalOperationUuid }
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request =
                    DiscardExternalOperationRequest(canonicalOperationUuid),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> {
                if (!isOwnerCurrent(ownerKey)) {
                    ApiResult.Error(accountChangedError())
                } else {
                    current?.let(
                        eventsRepository::removeExternalOperationSnapshot,
                    )
                    ApiResult.Success(Unit)
                }
            }
        }
    }

    override suspend fun getProviderPolicy():
        ApiResult<ValidatedExternalProviderPolicy?, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request = ExternalProviderPolicyRequest(),
            )
        ) {
            is ApiResult.Error -> if (
                result.error.kind == ApiErrorKind.FORBIDDEN
            ) {
                ApiResult.Success(null)
            } else {
                result
            }
            is ApiResult.Success -> {
                val validated = runCatching {
                    validateExternalProviderPolicyResponse(
                        response = result.value,
                        responseEntityTag = result.metadata.entityTag,
                    )
                }.getOrNull() ?: return ApiResult.Error(
                    malformedExternalResponseError(),
                )
                ApiResult.Success(validated)
            }
        }
    }

    override suspend fun updateProviderPolicy(
        enabled: Boolean,
        limits: ExternalProviderLimits,
        customCaCertificatesPem: List<String>?,
        entityTag: String,
    ): ApiResult<ValidatedExternalProviderPolicy, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val request = runCatching {
            UpdateExternalProviderPolicyRequest(
                enabled = enabled,
                limits = limits,
                customCaCertificatesPem = customCaCertificatesPem,
                entityTag = entityTag,
            )
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (val result = performOwned(ownerKey, request)) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedProviderPolicyResult(
                ownerKey = ownerKey,
                response = result.value,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun changeProviderSuspension(
        action: ExternalProviderSuspensionAction,
    ): ApiResult<ValidatedExternalProviderPolicy, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request = ChangeExternalProviderSuspensionRequest(action),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedProviderPolicyResult(
                ownerKey = ownerKey,
                response = result.value,
                entityTag = result.metadata.entityTag,
            )
        }
    }

    override suspend fun getProviderHealth():
        ApiResult<ExternalProviderHealthResponse?, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request = ExternalProviderHealthRequest(),
            )
        ) {
            is ApiResult.Error -> if (
                result.error.kind == ApiErrorKind.FORBIDDEN
            ) {
                ApiResult.Success(null)
            } else {
                result
            }
            is ApiResult.Success -> {
                val validated = runCatching {
                    validateExternalProviderHealthResponse(result.value)
                }.getOrNull() ?: return ApiResult.Error(
                    malformedExternalResponseError(),
                )
                ApiResult.Success(validated)
            }
        }
    }

    override suspend fun listBridgeInstances():
        ApiResult<List<ExternalBridgeInstanceResponse>?, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val instances = mutableListOf<ExternalBridgeInstanceResponse>()
        val seenUuids = mutableSetOf<String>()
        val seenMarkers = mutableSetOf<String>()
        var pageMarker: String? = null
        repeat(MAX_EXTERNAL_PAGES) { pageIndex ->
            when (
                val result = performOwned(
                    ownerKey = ownerKey,
                    request = ExternalBridgeInstancesRequest(
                        pageLimit = EXTERNAL_PAGE_SIZE,
                        pageMarker = pageMarker,
                    ),
                )
            ) {
                is ApiResult.Error -> {
                    if (
                        pageIndex == 0 &&
                        result.error.kind == ApiErrorKind.FORBIDDEN
                    ) {
                        return ApiResult.Success(null)
                    }
                    return result
                }

                is ApiResult.Success -> {
                    val validated = runCatching {
                        result.value
                            .filter { it.provider == "zulip" }
                            .map(::validateExternalBridgeInstanceResponse)
                    }.getOrNull() ?: return ApiResult.Error(
                        malformedExternalResponseError(),
                    )
                    if (validated.any { !seenUuids.add(it.uuid) }) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    instances += validated
                    val nextMarker = runCatching {
                        canonicalPageMarker(result.metadata.nextPageMarker)
                    }.getOrElse {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    } ?: run {
                        if (!isOwnerCurrent(ownerKey)) {
                            return ApiResult.Error(accountChangedError())
                        }
                        return ApiResult.Success(instances)
                    }
                    if (!seenMarkers.add(nextMarker)) {
                        return ApiResult.Error(
                            malformedExternalResponseError(),
                        )
                    }
                    pageMarker = nextMarker
                }
            }
        }
        return ApiResult.Error(externalPaginationLimitError())
    }

    override suspend fun changeBridgeInstanceStatus(
        instanceUuid: String,
        action: ExternalBridgeInstanceAction,
    ): ApiResult<ExternalBridgeInstanceResponse, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalUuid = runCatching {
            canonicalExternalIntegrationUuid(instanceUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request = ChangeExternalBridgeInstanceStatusRequest(
                    instanceUuid = canonicalUuid,
                    action = action,
                ),
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> {
                val validated = runCatching {
                    validateExternalBridgeInstanceResponse(
                        response = result.value,
                        expectedUuid = canonicalUuid,
                    )
                }.getOrNull() ?: return ApiResult.Error(
                    malformedExternalResponseError(),
                )
                ApiResult.Success(validated)
            }
        }
    }

    private suspend inline fun <
        reified RequestData : Any,
        reified Response : Any,
    > performOwned(
        ownerKey: String,
        request: ApiRequest<RequestData, Response, ApiError>,
    ): ApiResult<Response, ApiError> {
        val result = client.performRequest(
            request = request,
            expectedOwnerKey = ownerKey,
        )
        return if (
            result is ApiResult.Success &&
            !isOwnerCurrent(ownerKey)
        ) {
            ApiResult.Error(accountChangedError())
        } else {
            result
        }
    }

    private suspend inline fun <
        reified RequestData : Any,
    > mutateChat(
        ownerKey: String,
        expectedChatUuid: String,
        request: ApiRequest<RequestData, ExternalChatResponse, ApiError>,
    ): ApiResult<ExternalChatResponse, ApiError> {
        return when (
            val result = performOwned(
                ownerKey = ownerKey,
                request = request,
            )
        ) {
            is ApiResult.Error -> result
            is ApiResult.Success -> validatedChatResult(
                ownerKey = ownerKey,
                response = result.value,
                expectedUuid = expectedChatUuid,
            )
        }
    }

    private suspend fun validatedAccountResult(
        ownerKey: String,
        response: ExternalAccountResponse,
        expectedUuid: String,
        entityTag: String?,
    ): ApiResult<ValidatedExternalAccount, ApiError> {
        val validated = runCatching {
            validateExternalAccountResponse(
                response = response,
                expectedUuid = expectedUuid,
                responseEntityTag = entityTag,
            )
        }.getOrNull() ?: return ApiResult.Error(
            malformedExternalResponseError(),
        )
        if (!isOwnerCurrent(ownerKey)) {
            return ApiResult.Error(accountChangedError())
        }
        eventsRepository.mergeExternalAccountSnapshot(validated.response)
        return ApiResult.Success(validated)
    }

    private suspend fun validatedChatResult(
        ownerKey: String,
        response: ExternalChatResponse,
        expectedUuid: String,
    ): ApiResult<ExternalChatResponse, ApiError> {
        val validated = runCatching {
            validateExternalChatResponse(
                response = response,
                expectedUuid = expectedUuid,
            )
        }.getOrNull() ?: return ApiResult.Error(
            malformedExternalResponseError(),
        )
        if (!isOwnerCurrent(ownerKey)) {
            return ApiResult.Error(accountChangedError())
        }
        eventsRepository.mergeExternalChatSnapshot(validated)
        return ApiResult.Success(validated)
    }

    private suspend fun validatedOperationResult(
        ownerKey: String,
        response: ExternalOperationResponse,
        expectedUuid: String,
        expectedExternalAccountUuid: String,
    ): ApiResult<ExternalOperationResponse, ApiError> {
        val validated = runCatching {
            validateExternalOperationResponse(
                response = response,
                expectedUuid = expectedUuid,
                expectedExternalAccountUuid = expectedExternalAccountUuid,
            )
        }.getOrNull() ?: return ApiResult.Error(
            malformedExternalResponseError(),
        )
        if (!isOwnerCurrent(ownerKey)) {
            return ApiResult.Error(accountChangedError())
        }
        eventsRepository.mergeExternalOperationSnapshot(validated)
        return ApiResult.Success(validated)
    }

    private suspend fun validatedProviderPolicyResult(
        ownerKey: String,
        response: ExternalProviderPolicyResponse,
        entityTag: String?,
    ): ApiResult<ValidatedExternalProviderPolicy, ApiError> {
        val validated = runCatching {
            validateExternalProviderPolicyResponse(
                response = response,
                responseEntityTag = entityTag,
            )
        }.getOrNull() ?: return ApiResult.Error(
            malformedExternalResponseError(),
        )
        if (!isOwnerCurrent(ownerKey)) {
            return ApiResult.Error(accountChangedError())
        }
        return ApiResult.Success(validated)
    }

    private fun validateAccounts(
        responses: List<ExternalAccountResponse>,
    ): List<ValidatedExternalAccount>? = runCatching {
        responses.map(::validateExternalAccountResponse)
    }.getOrNull()

    private fun validateChats(
        responses: List<ExternalChatResponse>,
        expectedExternalAccountUuid: String,
    ): List<ExternalChatResponse>? = runCatching {
        responses.map {
            validateExternalChatResponse(
                response = it,
                expectedExternalAccountUuid = expectedExternalAccountUuid,
            )
        }
    }.getOrNull()

    private fun validateOperations(
        responses: List<ExternalOperationResponse>,
        expectedExternalAccountUuid: String,
    ): List<ExternalOperationResponse>? = runCatching {
        responses.map {
            validateExternalOperationResponse(
                response = it,
                expectedExternalAccountUuid = expectedExternalAccountUuid,
            )
        }
    }.getOrNull()

    private fun canonicalPageMarker(value: String?): String? {
        val normalized = value?.trim()?.takeIf(String::isNotBlank)
            ?: return null
        return canonicalExternalIntegrationUuid(normalized)
    }

    private suspend fun activeOwnerKey(): String? =
        client.userViewModel.repo
            .activeCredentialSnapshot()
            .ownerKey
            ?.takeIf(String::isNotBlank)

    private suspend fun isOwnerCurrent(ownerKey: String): Boolean =
        client.userViewModel.repo.isActiveCredentialOwner(ownerKey)

    private companion object {
        const val EXTERNAL_PAGE_SIZE = 500
        const val MAX_EXTERNAL_PAGES = 50
    }
}

private fun authenticationRequiredError() = ApiError(
    errorMessage = "Authentication is required",
    code = "AUTHENTICATION_REQUIRED",
    kind = ApiErrorKind.UNAUTHORIZED,
)

private fun invalidExternalInputError() = ApiError(
    errorMessage = "External integration input is invalid",
    code = "INVALID_EXTERNAL_INTEGRATION_INPUT",
    kind = ApiErrorKind.VALIDATION,
)

private fun malformedExternalResponseError() = ApiError(
    errorMessage = "Workspace returned an invalid external integration response",
    code = "MALFORMED_EXTERNAL_INTEGRATION_RESPONSE",
    kind = ApiErrorKind.MALFORMED_RESPONSE,
)

private fun externalPaginationLimitError() = ApiError(
    errorMessage = "External integration pagination exceeded the safety limit",
    code = "EXTERNAL_PAGINATION_LIMIT",
    kind = ApiErrorKind.MALFORMED_RESPONSE,
)
