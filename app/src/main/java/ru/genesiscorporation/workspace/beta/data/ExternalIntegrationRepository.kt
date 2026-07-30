package ru.genesiscorporation.workspace.beta.data

import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.accountChangedError
import ru.genesiscorporation.workspace.beta.data.remote.dto.CreateExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeselectExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DisconnectExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountSelectionMode
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalAccountsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalChatsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ExternalHistoryDepth
import ru.genesiscorporation.workspace.beta.data.remote.dto.MoveExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ReconnectExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.SelectExternalChatRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateExternalAccountRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ValidatedExternalAccount
import ru.genesiscorporation.workspace.beta.data.remote.dto.canonicalExternalIntegrationUuid
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalAccountResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.validateExternalChatResponse

class ExternalIntegrationRepository(
    private val client: WorkspaceAPIClient,
    private val eventsRepository: EventsRepository,
) {
    suspend fun listAccounts():
        ApiResult<List<ValidatedExternalAccount>, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
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
                        accounts.forEach {
                            eventsRepository.mergeExternalAccountSnapshot(
                                it.response,
                            )
                        }
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

    suspend fun getAccount(
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

    suspend fun createAccount(
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

    suspend fun updateAccount(
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

    suspend fun reconnectAccount(
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

    suspend fun disconnectAccount(
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

    suspend fun deleteAccount(
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

    suspend fun listChats(
        externalAccountUuid: String,
    ): ApiResult<List<ExternalChatResponse>, ApiError> {
        val ownerKey = activeOwnerKey()
            ?: return ApiResult.Error(authenticationRequiredError())
        val canonicalAccountUuid = runCatching {
            canonicalExternalIntegrationUuid(externalAccountUuid)
        }.getOrElse {
            return ApiResult.Error(invalidExternalInputError())
        }
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
                        chats.forEach(
                            eventsRepository::mergeExternalChatSnapshot,
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

    suspend fun getChat(
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

    suspend fun selectChat(
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

    suspend fun deselectChat(
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

    suspend fun moveChat(
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
