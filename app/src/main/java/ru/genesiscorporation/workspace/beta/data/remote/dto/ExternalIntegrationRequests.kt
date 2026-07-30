package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod
import java.net.URI
import java.util.UUID

@Serializable
enum class ExternalAccountSelectionMode {
    @SerialName("explicit")
    EXPLICIT,

    @SerialName("all")
    ALL,
}

@Serializable
enum class ExternalHistoryDepth {
    @SerialName("new")
    NEW,

    @SerialName("7_days")
    SEVEN_DAYS,

    @SerialName("30_days")
    THIRTY_DAYS,

    @SerialName("90_days")
    NINETY_DAYS,

    @SerialName("all")
    ALL,
}

@Serializable
enum class ExternalAccountStatus {
    @SerialName("connecting")
    CONNECTING,

    @SerialName("backfill")
    BACKFILL,

    @SerialName("live")
    LIVE,

    @SerialName("degraded")
    DEGRADED,

    @SerialName("auth_required")
    AUTH_REQUIRED,

    @SerialName("disconnected")
    DISCONNECTED,

    @SerialName("suspended")
    SUSPENDED,
}

@Serializable
data class ZulipExternalAccountSettings(
    val kind: String,
    @SerialName("server_url") val serverUrl: String,
    val email: String,
    @SerialName("selection_mode")
    val selectionMode: ExternalAccountSelectionMode,
    @SerialName("history_depth")
    val historyDepth: ExternalHistoryDepth,
    @SerialName("default_project_id")
    val defaultProjectId: String,
)

@Serializable
data class ExternalAccountResponse(
    val uuid: String,
    val settings: ZulipExternalAccountSettings,
    @SerialName("credential_present")
    val credentialPresent: Boolean,
    val status: ExternalAccountStatus,
    @SerialName("live_ready")
    val liveReady: Boolean,
    val capabilities: JsonObject,
    @SerialName("safe_error")
    val safeError: String?,
    @SerialName("desired_generation")
    val desiredGeneration: Int,
    @SerialName("applied_generation")
    val appliedGeneration: Int,
    @SerialName("last_progress_at")
    val lastProgressAt: String?,
    val revision: Int,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

data class ValidatedExternalAccount(
    val response: ExternalAccountResponse,
    val entityTag: String,
)

fun validateExternalAccountResponse(
    response: ExternalAccountResponse,
    expectedUuid: String? = null,
    responseEntityTag: String? = null,
): ValidatedExternalAccount {
    val canonicalUuid = canonicalExternalIntegrationUuid(response.uuid)
    expectedUuid?.let {
        require(canonicalUuid == canonicalExternalIntegrationUuid(it)) {
            "External account response UUID does not match the request"
        }
    }
    require(response.settings.kind == ZULIP_PROVIDER_KIND) {
        "Unsupported external account provider"
    }
    val normalizedServerUrl =
        normalizeExternalIntegrationServerUrl(response.settings.serverUrl)
    val normalizedEmail = normalizeExternalIntegrationEmail(
        response.settings.email,
    )
    val canonicalProjectId =
        canonicalExternalIntegrationUuid(response.settings.defaultProjectId)
    require(response.desiredGeneration >= 1) {
        "External account desired generation must be positive"
    }
    require(response.appliedGeneration >= 0) {
        "External account applied generation must not be negative"
    }
    require(response.revision >= 1) {
        "External account revision must be positive"
    }
    require(response.createdAt.isNotBlank() && response.updatedAt.isNotBlank()) {
        "External account timestamps must not be blank"
    }
    require(response.capabilities.toString().length <= MAX_CAPABILITIES_CHARS) {
        "External account capabilities are too large"
    }
    response.safeError?.let {
        require(it.length <= MAX_SAFE_ERROR_CHARS) {
            "External account safe error is too large"
        }
    }
    val entityTag = responseEntityTag
        ?.let(::requireStrongExternalEntityTag)
        ?: "\"${response.revision}\""
    return ValidatedExternalAccount(
        response = response.copy(
            uuid = canonicalUuid,
            settings = response.settings.copy(
                serverUrl = normalizedServerUrl,
                email = normalizedEmail,
                defaultProjectId = canonicalProjectId,
            ),
        ),
        entityTag = entityTag,
    )
}

data class ExternalAccountsRequest(
    val pageLimit: Int = DEFAULT_EXTERNAL_PAGE_SIZE,
    val pageMarker: String? = null,
) : ApiRequest<ExternalAccountsRequestData, List<ExternalAccountResponse>, ApiError> {
    init {
        requireExternalPage(pageLimit, pageMarker)
    }

    override val method = HTTPMethod.GET
    override val url = EXTERNAL_ACCOUNTS_URL
    override val data = ExternalAccountsRequestData(
        pageLimit = pageLimit,
        pageMarker = pageMarker?.let(::canonicalExternalIntegrationUuid),
    )
}

class ExternalAccountRequest(
    accountUuid: String,
) : ApiRequest<EmptyRequestData, ExternalAccountResponse, ApiError> {
    override val method = HTTPMethod.GET
    override val url =
        "$EXTERNAL_ACCOUNTS_URL${canonicalExternalIntegrationUuid(accountUuid)}"
    override val data = EmptyRequestData()
}

class CreateExternalAccountRequest(
    accountUuid: String,
    serverUrl: String,
    email: String,
    apiKey: String,
    selectionMode: ExternalAccountSelectionMode,
    historyDepth: ExternalHistoryDepth,
    defaultProjectId: String,
) : ApiRequest<CreateExternalAccountRequestData, ExternalAccountResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url = EXTERNAL_ACCOUNTS_URL
    override val data = CreateExternalAccountRequestData(
        uuid = canonicalExternalIntegrationUuid(accountUuid),
        settings = CreateZulipExternalAccountSettings(
            kind = ZULIP_PROVIDER_KIND,
            serverUrl = normalizeExternalIntegrationServerUrl(serverUrl),
            email = normalizeExternalIntegrationEmail(email),
            apiKey = normalizeExternalIntegrationApiKey(apiKey),
            selectionMode = selectionMode,
            historyDepth = historyDepth,
            defaultProjectId =
                canonicalExternalIntegrationUuid(defaultProjectId),
        ),
    )
}

class UpdateExternalAccountRequest(
    accountUuid: String,
    selectionMode: ExternalAccountSelectionMode,
    historyDepth: ExternalHistoryDepth,
    defaultProjectId: String,
    entityTag: String,
) : ApiRequest<UpdateExternalAccountRequestData, ExternalAccountResponse, ApiError> {
    override val method = HTTPMethod.PUT
    override val url =
        "$EXTERNAL_ACCOUNTS_URL${canonicalExternalIntegrationUuid(accountUuid)}"
    override val data = UpdateExternalAccountRequestData(
        settings = UpdateZulipExternalAccountSettings(
            kind = ZULIP_PROVIDER_KIND,
            selectionMode = selectionMode,
            historyDepth = historyDepth,
            defaultProjectId =
                canonicalExternalIntegrationUuid(defaultProjectId),
        ),
    )
    override val additionalHeaders = mapOf(
        "If-Match" to requireStrongExternalEntityTag(entityTag),
    )
}

class ReconnectExternalAccountRequest(
    accountUuid: String,
    serverUrl: String,
    email: String,
    apiKey: String,
    entityTag: String,
) : ApiRequest<ReconnectExternalAccountRequestData, ExternalAccountResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_ACCOUNTS_URL" +
            "${canonicalExternalIntegrationUuid(accountUuid)}/" +
            "actions/reconnect/invoke"
    override val data = ReconnectExternalAccountRequestData(
        settings = ReconnectZulipExternalAccountSettings(
            kind = ZULIP_PROVIDER_KIND,
            serverUrl = normalizeExternalIntegrationServerUrl(serverUrl),
            email = normalizeExternalIntegrationEmail(email),
            apiKey = normalizeExternalIntegrationApiKey(apiKey),
        ),
    )
    override val additionalHeaders = mapOf(
        "If-Match" to requireStrongExternalEntityTag(entityTag),
    )
}

class DisconnectExternalAccountRequest(
    accountUuid: String,
) : ApiRequest<EmptyRequestData, ExternalAccountResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_ACCOUNTS_URL" +
            "${canonicalExternalIntegrationUuid(accountUuid)}/" +
            "actions/disconnect/invoke"
    override val data = EmptyRequestData()
}

class DeleteExternalAccountRequest(
    accountUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url =
        "$EXTERNAL_ACCOUNTS_URL${canonicalExternalIntegrationUuid(accountUuid)}"
    override val data = EmptyRequestData()
}

@Serializable
data class ExternalAccountsRequestData(
    @SerialName("page_limit")
    val pageLimit: Int,
    @SerialName("page_marker")
    val pageMarker: String?,
)

@Serializable
data class CreateExternalAccountRequestData(
    val uuid: String,
    val settings: CreateZulipExternalAccountSettings,
)

@Serializable
data class CreateZulipExternalAccountSettings(
    val kind: String,
    @SerialName("server_url")
    val serverUrl: String,
    val email: String,
    @SerialName("api_key")
    val apiKey: String,
    @SerialName("selection_mode")
    val selectionMode: ExternalAccountSelectionMode,
    @SerialName("history_depth")
    val historyDepth: ExternalHistoryDepth,
    @SerialName("default_project_id")
    val defaultProjectId: String,
)

@Serializable
data class UpdateExternalAccountRequestData(
    val settings: UpdateZulipExternalAccountSettings,
)

@Serializable
data class UpdateZulipExternalAccountSettings(
    val kind: String,
    @SerialName("selection_mode")
    val selectionMode: ExternalAccountSelectionMode,
    @SerialName("history_depth")
    val historyDepth: ExternalHistoryDepth,
    @SerialName("default_project_id")
    val defaultProjectId: String,
)

@Serializable
data class ReconnectExternalAccountRequestData(
    val settings: ReconnectZulipExternalAccountSettings,
)

@Serializable
data class ReconnectZulipExternalAccountSettings(
    val kind: String,
    @SerialName("server_url")
    val serverUrl: String,
    val email: String,
    @SerialName("api_key")
    val apiKey: String,
)

@Serializable
enum class ExternalChatStatus {
    @SerialName("available")
    AVAILABLE,

    @SerialName("syncing")
    SYNCING,

    @SerialName("live")
    LIVE,

    @SerialName("degraded")
    DEGRADED,

    @SerialName("deselected")
    DESELECTED,
}

@Serializable
enum class ExternalChatType {
    @SerialName("channel")
    CHANNEL,

    @SerialName("personal")
    PERSONAL,

    @SerialName("group")
    GROUP,
}

@Serializable
data class ZulipExternalChatSource(
    val kind: String,
    @SerialName("chat_type")
    val chatType: ExternalChatType,
    @SerialName("original_url")
    val originalUrl: String? = null,
)

@Serializable
data class ExternalChatResponse(
    val uuid: String,
    @SerialName("external_account_uuid")
    val externalAccountUuid: String,
    val source: ZulipExternalChatSource,
    @SerialName("display_name")
    val displayName: String,
    val selected: Boolean,
    @SerialName("project_id")
    val projectId: String?,
    @SerialName("history_depth")
    val historyDepth: ExternalHistoryDepth,
    @SerialName("projection_stream_uuid")
    val projectionStreamUuid: String?,
    val status: ExternalChatStatus,
    val capabilities: JsonObject,
    @SerialName("safe_error")
    val safeError: String?,
    @SerialName("transition_pending")
    val transitionPending: Boolean,
    val revision: Int,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

fun validateExternalChatResponse(
    response: ExternalChatResponse,
    expectedUuid: String? = null,
    expectedExternalAccountUuid: String? = null,
): ExternalChatResponse {
    val canonicalUuid = canonicalExternalIntegrationUuid(response.uuid)
    val canonicalAccountUuid =
        canonicalExternalIntegrationUuid(response.externalAccountUuid)
    expectedUuid?.let {
        require(canonicalUuid == canonicalExternalIntegrationUuid(it)) {
            "External chat response UUID does not match the request"
        }
    }
    expectedExternalAccountUuid?.let {
        require(
            canonicalAccountUuid == canonicalExternalIntegrationUuid(it),
        ) {
            "External chat belongs to another external account"
        }
    }
    require(response.source.kind == ZULIP_PROVIDER_KIND) {
        "Unsupported external chat provider"
    }
    require(response.displayName.isNotBlank()) {
        "External chat display name must not be blank"
    }
    require(response.displayName.length <= MAX_EXTERNAL_DISPLAY_NAME_CHARS) {
        "External chat display name is too long"
    }
    require(response.revision >= 1) {
        "External chat revision must be positive"
    }
    require(response.createdAt.isNotBlank() && response.updatedAt.isNotBlank()) {
        "External chat timestamps must not be blank"
    }
    require(response.capabilities.toString().length <= MAX_CAPABILITIES_CHARS) {
        "External chat capabilities are too large"
    }
    response.safeError?.let {
        require(it.length <= MAX_SAFE_ERROR_CHARS) {
            "External chat safe error is too large"
        }
    }
    val canonicalProjectId =
        response.projectId?.let(::canonicalExternalIntegrationUuid)
    val canonicalProjectionStreamUuid =
        response.projectionStreamUuid?.let(::canonicalExternalIntegrationUuid)
    return response.copy(
        uuid = canonicalUuid,
        externalAccountUuid = canonicalAccountUuid,
        source = response.source.copy(
            originalUrl = response.source.originalUrl
                ?.trim()
                ?.takeIf(String::isNotBlank),
        ),
        displayName = response.displayName.trim(),
        projectId = canonicalProjectId,
        projectionStreamUuid = canonicalProjectionStreamUuid,
    )
}

data class ExternalChatsRequest(
    val externalAccountUuid: String,
    val pageLimit: Int = DEFAULT_EXTERNAL_PAGE_SIZE,
    val pageMarker: String? = null,
) : ApiRequest<ExternalChatsRequestData, List<ExternalChatResponse>, ApiError> {
    init {
        canonicalExternalIntegrationUuid(externalAccountUuid)
        requireExternalPage(pageLimit, pageMarker)
    }

    override val method = HTTPMethod.GET
    override val url = EXTERNAL_CHATS_URL
    override val data = ExternalChatsRequestData(
        externalAccountUuid =
            canonicalExternalIntegrationUuid(externalAccountUuid),
        pageLimit = pageLimit,
        pageMarker = pageMarker?.let(::canonicalExternalIntegrationUuid),
    )
}

class ExternalChatRequest(
    chatUuid: String,
) : ApiRequest<EmptyRequestData, ExternalChatResponse, ApiError> {
    override val method = HTTPMethod.GET
    override val url =
        "$EXTERNAL_CHATS_URL${canonicalExternalIntegrationUuid(chatUuid)}"
    override val data = EmptyRequestData()
}

class SelectExternalChatRequest(
    chatUuid: String,
    projectId: String,
) : ApiRequest<ExternalChatAssignmentRequestData, ExternalChatResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_CHATS_URL${canonicalExternalIntegrationUuid(chatUuid)}/" +
            "actions/select/invoke"
    override val data = ExternalChatAssignmentRequestData(
        projectId = canonicalExternalIntegrationUuid(projectId),
    )
}

class DeselectExternalChatRequest(
    chatUuid: String,
) : ApiRequest<EmptyRequestData, ExternalChatResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_CHATS_URL${canonicalExternalIntegrationUuid(chatUuid)}/" +
            "actions/deselect/invoke"
    override val data = EmptyRequestData()
}

class MoveExternalChatRequest(
    chatUuid: String,
    projectId: String,
    entityTag: String,
) : ApiRequest<ExternalChatAssignmentRequestData, ExternalChatResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_CHATS_URL${canonicalExternalIntegrationUuid(chatUuid)}/" +
            "actions/move/invoke"
    override val data = ExternalChatAssignmentRequestData(
        projectId = canonicalExternalIntegrationUuid(projectId),
    )
    override val additionalHeaders = mapOf(
        "If-Match" to requireStrongExternalEntityTag(entityTag),
    )
}

@Serializable
data class ExternalChatsRequestData(
    @SerialName("external_account_uuid")
    val externalAccountUuid: String,
    @SerialName("page_limit")
    val pageLimit: Int,
    @SerialName("page_marker")
    val pageMarker: String?,
)

@Serializable
data class ExternalChatAssignmentRequestData(
    @SerialName("project_id")
    val projectId: String,
)

@Serializable
enum class ExternalOperationStatus {
    @SerialName("queued")
    QUEUED,

    @SerialName("running")
    RUNNING,

    @SerialName("succeeded")
    SUCCEEDED,

    @SerialName("failed")
    FAILED,

    @SerialName("manual_reconciliation_required")
    MANUAL_RECONCILIATION_REQUIRED,

    @SerialName("discarded")
    DISCARDED,
}

@Serializable
enum class ExternalOperationReconciliationState {
    @SerialName("not_required")
    NOT_REQUIRED,

    @SerialName("delayed_check")
    DELAYED_CHECK,

    @SerialName("committed_match")
    COMMITTED_MATCH,

    @SerialName("automatic_resend_queued")
    AUTOMATIC_RESEND_QUEUED,

    @SerialName("manual_required")
    MANUAL_REQUIRED,
}

@Serializable
enum class ExternalOperationReconciliationReason {
    @SerialName("provider_history_unavailable")
    PROVIDER_HISTORY_UNAVAILABLE,

    @SerialName("no_match_after_auto_resend")
    NO_MATCH_AFTER_AUTO_RESEND,

    @SerialName("unsafe_provider_state")
    UNSAFE_PROVIDER_STATE,
}

@Serializable
data class ExternalOperationResponse(
    val uuid: String,
    @SerialName("external_account_uuid")
    val externalAccountUuid: String,
    val action: String,
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_uuid")
    val targetUuid: String? = null,
    val status: ExternalOperationStatus,
    @SerialName("safe_error")
    val safeError: String? = null,
    @SerialName("can_retry")
    val canRetry: Boolean,
    @SerialName("can_discard")
    val canDiscard: Boolean,
    @SerialName("duplicate_risk")
    val duplicateRisk: Boolean,
    @SerialName("retry_requires_confirmation")
    val retryRequiresConfirmation: Boolean,
    @SerialName("original_url")
    val originalUrl: String? = null,
    @SerialName("reconciliation_state")
    val reconciliationState: ExternalOperationReconciliationState,
    @SerialName("reconciliation_reason")
    val reconciliationReason: ExternalOperationReconciliationReason? = null,
    @SerialName("reconciliation_evidence")
    val reconciliationEvidence: JsonObject,
    val attempt: Int,
    @SerialName("attempt_history")
    val attemptHistory: JsonArray,
    val details: JsonObject,
    val revision: Int,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

fun validateExternalOperationResponse(
    response: ExternalOperationResponse,
    expectedUuid: String? = null,
    expectedExternalAccountUuid: String? = null,
): ExternalOperationResponse {
    val canonicalUuid = canonicalExternalIntegrationUuid(response.uuid)
    val canonicalAccountUuid =
        canonicalExternalIntegrationUuid(response.externalAccountUuid)
    expectedUuid?.let {
        require(canonicalUuid == canonicalExternalIntegrationUuid(it)) {
            "External operation response UUID does not match the request"
        }
    }
    expectedExternalAccountUuid?.let {
        require(
            canonicalAccountUuid == canonicalExternalIntegrationUuid(it),
        ) {
            "External operation belongs to another external account"
        }
    }
    val action = requireExternalOperationName(response.action, "action")
    val targetType =
        requireExternalOperationName(response.targetType, "target type")
    val canonicalTargetUuid =
        response.targetUuid?.let(::canonicalExternalIntegrationUuid)
    require(response.attempt >= 0) {
        "External operation attempt must not be negative"
    }
    require(response.revision >= 1) {
        "External operation revision must be positive"
    }
    require(
        response.attemptHistory.size <= MAX_EXTERNAL_OPERATION_HISTORY_ITEMS,
    ) {
        "External operation attempt history is too large"
    }
    require(
        response.details.toString().length +
            response.attemptHistory.toString().length +
            response.reconciliationEvidence.toString().length <=
            MAX_EXTERNAL_OPERATION_METADATA_CHARS,
    ) {
        "External operation metadata is too large"
    }
    response.safeError?.let {
        require(it.length <= MAX_SAFE_ERROR_CHARS) {
            "External operation safe error is too large"
        }
    }
    val originalUrl = response.originalUrl
        ?.trim()
        ?.takeIf(String::isNotBlank)
    require(originalUrl == null || originalUrl.length <= MAX_EXTERNAL_URL_CHARS) {
        "External operation original URL is too large"
    }
    require(!response.retryRequiresConfirmation || response.canRetry) {
        "External operation retry confirmation requires a retry action"
    }
    require(!response.retryRequiresConfirmation || response.duplicateRisk) {
        "External operation retry confirmation requires duplicate risk"
    }
    response.createdAt?.let {
        require(it.isNotBlank()) {
            "External operation creation timestamp must not be blank"
        }
    }
    response.updatedAt?.let {
        require(it.isNotBlank()) {
            "External operation update timestamp must not be blank"
        }
    }
    return response.copy(
        uuid = canonicalUuid,
        externalAccountUuid = canonicalAccountUuid,
        action = action,
        targetType = targetType,
        targetUuid = canonicalTargetUuid,
        originalUrl = originalUrl,
    )
}

data class ExternalOperationsRequest(
    val externalAccountUuid: String,
    val pageLimit: Int = DEFAULT_EXTERNAL_PAGE_SIZE,
    val pageMarker: String? = null,
) : ApiRequest<ExternalOperationsRequestData, List<ExternalOperationResponse>, ApiError> {
    init {
        canonicalExternalIntegrationUuid(externalAccountUuid)
        requireExternalPage(pageLimit, pageMarker)
    }

    override val method = HTTPMethod.GET
    override val url = EXTERNAL_OPERATIONS_URL
    override val data = ExternalOperationsRequestData(
        externalAccountUuid =
            canonicalExternalIntegrationUuid(externalAccountUuid),
        pageLimit = pageLimit,
        pageMarker = pageMarker?.let(::canonicalExternalIntegrationUuid),
    )
}

class RetryExternalOperationRequest(
    operationUuid: String,
    confirmDuplicateRisk: Boolean,
) : ApiRequest<RetryExternalOperationRequestData, ExternalOperationResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_OPERATIONS_URL" +
            "${canonicalExternalIntegrationUuid(operationUuid)}/" +
            "actions/retry/invoke"
    override val data = RetryExternalOperationRequestData(
        confirmDuplicateRisk = confirmDuplicateRisk,
    )
}

class DiscardExternalOperationRequest(
    operationUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url =
        "$EXTERNAL_OPERATIONS_URL" +
            canonicalExternalIntegrationUuid(operationUuid)
    override val data = EmptyRequestData()
}

@Serializable
data class ExternalOperationsRequestData(
    @SerialName("external_account_uuid")
    val externalAccountUuid: String,
    @SerialName("page_limit")
    val pageLimit: Int,
    @SerialName("page_marker")
    val pageMarker: String?,
)

@Serializable
data class RetryExternalOperationRequestData(
    @SerialName("confirm_duplicate_risk")
    val confirmDuplicateRisk: Boolean,
)

private fun requireExternalOperationName(
    value: String,
    field: String,
): String {
    val normalized = value.trim()
    require(
        normalized.isNotEmpty() &&
            normalized.length <= MAX_EXTERNAL_OPERATION_NAME_CHARS &&
            normalized.none(Char::isISOControl),
    ) {
        "External operation $field is invalid"
    }
    return normalized
}

fun canonicalExternalIntegrationUuid(value: String): String {
    val trimmed = value.trim()
    val canonical = runCatching { UUID.fromString(trimmed).toString() }
        .getOrNull()
        ?: throw IllegalArgumentException(
            "External integration identifiers must be canonical UUIDs",
        )
    require(canonical.equals(trimmed, ignoreCase = true)) {
        "External integration identifiers must be canonical UUIDs"
    }
    return canonical
}

fun normalizeExternalIntegrationServerUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull()
        ?: throw IllegalArgumentException(
            "External provider server must be a valid HTTPS URL",
        )
    require(
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.path.orEmpty().isBlank() &&
            (uri.port == -1 || uri.port in 1..65_535),
    ) {
        "External provider server must be an HTTPS origin"
    }
    return URI(
        "https",
        null,
        uri.host.lowercase(),
        uri.port,
        null,
        null,
        null,
    ).toString()
}

fun normalizeExternalIntegrationEmail(value: String): String {
    val normalized = value.trim()
    require(
        normalized.length in 3..MAX_EXTERNAL_EMAIL_CHARS &&
            normalized.count { it == '@' } == 1 &&
            !normalized.startsWith('@') &&
            !normalized.endsWith('@') &&
            normalized.none {
                it.isWhitespace() || it.isISOControl()
            },
    ) {
        "External provider email is invalid"
    }
    return normalized
}

fun normalizeExternalIntegrationApiKey(value: String): String {
    val normalized = value.trim()
    require(
        normalized.length in 1..MAX_EXTERNAL_API_KEY_CHARS &&
            normalized.none {
                it.isWhitespace() || it.isISOControl()
            },
    ) {
        "External provider API key is invalid"
    }
    return normalized
}

fun requireStrongExternalEntityTag(value: String): String {
    val normalized = value.trim()
    require(normalized.matches(Regex("""\"[1-9][0-9]*\""""))) {
        "External integration ETag must be a strong revision tag"
    }
    return normalized
}

private fun requireExternalPage(
    pageLimit: Int,
    pageMarker: String?,
) {
    require(pageLimit in 1..MAX_EXTERNAL_PAGE_SIZE) {
        "External integration page size is invalid"
    }
    pageMarker?.let(::canonicalExternalIntegrationUuid)
}

const val DEFAULT_EXTERNAL_PAGE_SIZE = 100
private const val MAX_EXTERNAL_PAGE_SIZE = 500
private const val MAX_EXTERNAL_EMAIL_CHARS = 320
private const val MAX_EXTERNAL_API_KEY_CHARS = 4_096
private const val MAX_EXTERNAL_DISPLAY_NAME_CHARS = 512
private const val MAX_CAPABILITIES_CHARS = 65_536
private const val MAX_SAFE_ERROR_CHARS = 4_096
private const val MAX_EXTERNAL_URL_CHARS = 2_048
private const val MAX_EXTERNAL_OPERATION_NAME_CHARS = 128
private const val MAX_EXTERNAL_OPERATION_HISTORY_ITEMS = 256
private const val MAX_EXTERNAL_OPERATION_METADATA_CHARS = 131_072
private const val ZULIP_PROVIDER_KIND = "zulip"
private const val EXTERNAL_ACCOUNTS_URL =
    "/api/workspace/v1/messenger/external_accounts/"
private const val EXTERNAL_CHATS_URL =
    "/api/workspace/v1/messenger/external_chats/"
private const val EXTERNAL_OPERATIONS_URL =
    "/api/workspace/v1/messenger/external_operations/"
