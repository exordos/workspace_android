package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class ExternalProviderLimits(
    @SerialName("max_accounts")
    val maxAccounts: Int,
    @SerialName("max_selected_chats_per_account")
    val maxSelectedChatsPerAccount: Int,
    @SerialName("max_file_bytes")
    val maxFileBytes: Long,
)

@Serializable
data class ExternalProviderCustomCaBundle(
    val uuid: String,
    val generation: Int,
    val sha256: String,
    @SerialName("certificate_count")
    val certificateCount: Int,
)

@Serializable
data class ExternalProviderPolicyResponse(
    val provider: String,
    val enabled: Boolean,
    @SerialName("emergency_suspended")
    val emergencySuspended: Boolean,
    val limits: ExternalProviderLimits,
    @SerialName("custom_ca_bundle")
    val customCaBundle: ExternalProviderCustomCaBundle?,
    val revision: Int,
)

data class ValidatedExternalProviderPolicy(
    val response: ExternalProviderPolicyResponse,
    val entityTag: String,
)

fun validateExternalProviderPolicyResponse(
    response: ExternalProviderPolicyResponse,
    responseEntityTag: String? = null,
): ValidatedExternalProviderPolicy {
    require(response.provider == ZULIP_ADMIN_PROVIDER_KIND) {
        "Unsupported external provider policy"
    }
    validateExternalProviderLimits(response.limits)
    require(response.revision >= 1) {
        "External provider policy revision must be positive"
    }
    response.customCaBundle?.let { bundle ->
        canonicalExternalIntegrationUuid(bundle.uuid)
        require(bundle.generation >= 1) {
            "External provider CA generation must be positive"
        }
        require(
            bundle.sha256.matches(Regex("^[0-9a-f]{64}$")),
        ) {
            "External provider CA digest is invalid"
        }
        require(bundle.certificateCount in 1..MAX_EXTERNAL_CA_CERTIFICATES) {
            "External provider CA certificate count is invalid"
        }
    }
    val expectedEntityTag = "\"${response.revision}\""
    val entityTag = requireStrongExternalEntityTag(
        requireNotNull(responseEntityTag) {
            "External provider policy response is missing an ETag"
        },
    )
    require(entityTag == expectedEntityTag) {
        "External provider policy ETag does not match its revision"
    }
    return ValidatedExternalProviderPolicy(
        response = response.copy(
            customCaBundle = response.customCaBundle?.copy(
                uuid = canonicalExternalIntegrationUuid(
                    response.customCaBundle.uuid,
                ),
            ),
        ),
        entityTag = entityTag,
    )
}

class ExternalProviderPolicyRequest :
    ApiRequest<
        EmptyRequestData,
        ExternalProviderPolicyResponse,
        ApiError,
        > {
    override val method = HTTPMethod.GET
    override val url =
        "$EXTERNAL_PROVIDER_POLICIES_URL$ZULIP_ADMIN_PROVIDER_KIND"
    override val data = EmptyRequestData()
}

class UpdateExternalProviderPolicyRequest(
    enabled: Boolean,
    limits: ExternalProviderLimits,
    customCaCertificatesPem: List<String>?,
    entityTag: String,
) : ApiRequest<
    UpdateExternalProviderPolicyRequestData,
    ExternalProviderPolicyResponse,
    ApiError,
    > {
    override val method = HTTPMethod.PUT
    override val url =
        "$EXTERNAL_PROVIDER_POLICIES_URL$ZULIP_ADMIN_PROVIDER_KIND"
    override val data = UpdateExternalProviderPolicyRequestData(
        settings = ExternalProviderPolicySettingsRequest(
            kind = ZULIP_ADMIN_PROVIDER_KIND,
            enabled = enabled,
            limits = validateExternalProviderLimits(limits),
            customCaBundle = customCaCertificatesPem?.let {
                ExternalProviderCustomCaRequest(
                    certificatesPem =
                        validateExternalProviderCaCertificates(it),
                )
            },
        ),
    )
    override val additionalHeaders = mapOf(
        "If-Match" to requireStrongExternalEntityTag(entityTag),
    )
    override val encodeExplicitNulls = true
}

class ChangeExternalProviderSuspensionRequest(
    action: ExternalProviderSuspensionAction,
) : ApiRequest<
    EmptyRequestData,
    ExternalProviderPolicyResponse,
    ApiError,
    > {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_PROVIDER_POLICIES_URL$ZULIP_ADMIN_PROVIDER_KIND/" +
            "actions/${action.path}/invoke"
    override val data = EmptyRequestData()
}

enum class ExternalProviderSuspensionAction(
    val path: String,
) {
    SUSPEND("suspend"),
    RESUME("resume"),
}

@Serializable
data class UpdateExternalProviderPolicyRequestData(
    val settings: ExternalProviderPolicySettingsRequest,
)

@Serializable
data class ExternalProviderPolicySettingsRequest(
    val kind: String,
    val enabled: Boolean,
    val limits: ExternalProviderLimits,
    @SerialName("custom_ca_bundle")
    val customCaBundle: ExternalProviderCustomCaRequest?,
)

@Serializable
data class ExternalProviderCustomCaRequest(
    @SerialName("certificates_pem")
    val certificatesPem: List<String>,
)

@Serializable
data class ExternalProviderHealthResponse(
    val provider: String,
    val status: String,
    @SerialName("account_counts")
    val accountCounts: Map<String, Long>,
    @SerialName("chat_counts")
    val chatCounts: Map<String, Long> = emptyMap(),
    @SerialName("bridge_counts")
    val bridgeCounts: Map<String, Long>,
    @SerialName("operation_counts")
    val operationCounts: Map<String, Long>,
    val metrics: JsonObject,
    @SerialName("updated_at")
    val updatedAt: String?,
)

fun validateExternalProviderHealthResponse(
    response: ExternalProviderHealthResponse,
): ExternalProviderHealthResponse {
    require(response.provider == ZULIP_ADMIN_PROVIDER_KIND) {
        "Unsupported external provider health snapshot"
    }
    val status = requireBoundedAdminText(
        response.status,
        field = "health status",
        maximum = MAX_EXTERNAL_ADMIN_NAME_CHARS,
    )
    validateExternalProviderCounts(response.accountCounts, "account")
    validateExternalProviderCounts(response.chatCounts, "chat")
    validateExternalProviderCounts(response.bridgeCounts, "bridge")
    validateExternalProviderCounts(response.operationCounts, "operation")
    require(response.metrics.toString().length <= MAX_EXTERNAL_ADMIN_JSON_CHARS) {
        "External provider metrics are too large"
    }
    val updatedAt = response.updatedAt?.let {
        requireBoundedAdminText(
            value = it,
            field = "health timestamp",
            maximum = MAX_EXTERNAL_ADMIN_TIMESTAMP_CHARS,
        )
    }
    return response.copy(status = status, updatedAt = updatedAt)
}

class ExternalProviderHealthRequest :
    ApiRequest<
        EmptyRequestData,
        ExternalProviderHealthResponse,
        ApiError,
        > {
    override val method = HTTPMethod.GET
    override val url =
        "$EXTERNAL_PROVIDER_HEALTH_URL$ZULIP_ADMIN_PROVIDER_KIND"
    override val data = EmptyRequestData()
}

@Serializable
enum class ExternalBridgeInstanceStatus {
    @SerialName("enrolling")
    ENROLLING,

    @SerialName("active")
    ACTIVE,

    @SerialName("degraded")
    DEGRADED,

    @SerialName("incompatible")
    INCOMPATIBLE,

    @SerialName("suspended")
    SUSPENDED,

    @SerialName("revoked")
    REVOKED,
}

@Serializable
data class ExternalBridgeInstanceResponse(
    val uuid: String,
    val provider: String,
    @SerialName("identity_generation")
    val identityGeneration: Int,
    val status: ExternalBridgeInstanceStatus,
    val capabilities: JsonObject,
    @SerialName("last_heartbeat_at")
    val lastHeartbeatAt: String?,
    @SerialName("certificate_not_after")
    val certificateNotAfter: String?,
    @SerialName("safe_error")
    val safeError: String?,
    val revision: Int,
)

fun validateExternalBridgeInstanceResponse(
    response: ExternalBridgeInstanceResponse,
    expectedUuid: String? = null,
): ExternalBridgeInstanceResponse {
    val canonicalUuid = canonicalExternalIntegrationUuid(response.uuid)
    expectedUuid?.let {
        require(canonicalUuid == canonicalExternalIntegrationUuid(it)) {
            "External bridge instance UUID does not match the request"
        }
    }
    require(response.provider == ZULIP_ADMIN_PROVIDER_KIND) {
        "Unsupported external bridge provider"
    }
    require(response.identityGeneration >= 1) {
        "External bridge identity generation must be positive"
    }
    require(response.revision >= 1) {
        "External bridge revision must be positive"
    }
    require(
        response.capabilities.toString().length <=
            MAX_EXTERNAL_ADMIN_JSON_CHARS,
    ) {
        "External bridge capabilities are too large"
    }
    val heartbeat = response.lastHeartbeatAt?.let {
        requireBoundedAdminText(
            it,
            "bridge heartbeat",
            MAX_EXTERNAL_ADMIN_TIMESTAMP_CHARS,
        )
    }
    val certificateNotAfter = response.certificateNotAfter?.let {
        requireBoundedAdminText(
            it,
            "bridge certificate expiry",
            MAX_EXTERNAL_ADMIN_TIMESTAMP_CHARS,
        )
    }
    val safeError = response.safeError?.let {
        requireBoundedAdminText(
            it,
            "bridge safe error",
            MAX_EXTERNAL_ADMIN_SAFE_ERROR_CHARS,
        )
    }
    return response.copy(
        uuid = canonicalUuid,
        lastHeartbeatAt = heartbeat,
        certificateNotAfter = certificateNotAfter,
        safeError = safeError,
    )
}

data class ExternalBridgeInstancesRequest(
    val pageLimit: Int = DEFAULT_EXTERNAL_PAGE_SIZE,
    val pageMarker: String? = null,
) : ApiRequest<
    ExternalBridgeInstancesRequestData,
    List<ExternalBridgeInstanceResponse>,
    ApiError,
    > {
    init {
        require(pageLimit in 1..MAX_EXTERNAL_ADMIN_PAGE_SIZE) {
            "External bridge page size is invalid"
        }
        pageMarker?.let(::canonicalExternalIntegrationUuid)
    }

    override val method = HTTPMethod.GET
    override val url = EXTERNAL_BRIDGE_INSTANCES_URL
    override val data = ExternalBridgeInstancesRequestData(
        pageLimit = pageLimit,
        pageMarker = pageMarker?.let(::canonicalExternalIntegrationUuid),
    )
}

class ChangeExternalBridgeInstanceStatusRequest(
    instanceUuid: String,
    action: ExternalBridgeInstanceAction,
) : ApiRequest<
    EmptyRequestData,
    ExternalBridgeInstanceResponse,
    ApiError,
    > {
    override val method = HTTPMethod.POST
    override val url =
        "$EXTERNAL_BRIDGE_INSTANCES_URL" +
            "${canonicalExternalIntegrationUuid(instanceUuid)}/" +
            "actions/${action.path}/invoke"
    override val data = EmptyRequestData()
}

enum class ExternalBridgeInstanceAction(
    val path: String,
) {
    SUSPEND("suspend"),
    RESUME("resume"),
    REVOKE("revoke"),
}

@Serializable
data class ExternalBridgeInstancesRequestData(
    @SerialName("page_limit")
    val pageLimit: Int,
    @SerialName("page_marker")
    val pageMarker: String?,
)

private fun validateExternalProviderLimits(
    limits: ExternalProviderLimits,
): ExternalProviderLimits {
    require(limits.maxAccounts in 0..MAX_EXTERNAL_ACCOUNTS) {
        "External provider account limit is invalid"
    }
    require(
        limits.maxSelectedChatsPerAccount in
            0..MAX_EXTERNAL_SELECTED_CHATS,
    ) {
        "External provider chat limit is invalid"
    }
    require(limits.maxFileBytes in 0..MAX_EXTERNAL_FILE_BYTES) {
        "External provider file limit is invalid"
    }
    return limits
}

fun splitExternalProviderCaCertificates(
    value: String,
): List<String>? {
    if (
        value.isBlank() ||
        value.length > MAX_EXTERNAL_CA_INPUT_CHARS ||
        value.any { it.code > 0x7f } ||
        "PRIVATE KEY" in value.uppercase()
    ) {
        return null
    }
    val matches = EXTERNAL_CA_CERTIFICATE_PATTERN.findAll(value).toList()
    if (matches.size !in 1..MAX_EXTERNAL_CA_CERTIFICATES) return null
    var consumedUntil = 0
    matches.forEach { match ->
        if (value.substring(consumedUntil, match.range.first).isNotBlank()) {
            return null
        }
        consumedUntil = match.range.last + 1
    }
    if (value.substring(consumedUntil).isNotBlank()) return null
    return validateExternalProviderCaCertificates(
        matches.map { "${it.value.trim()}\n" },
    )
}

private fun validateExternalProviderCaCertificates(
    certificates: List<String>,
): List<String> {
    require(certificates.size in 1..MAX_EXTERNAL_CA_CERTIFICATES) {
        "External provider CA certificate count is invalid"
    }
    return certificates.map { certificate ->
        val normalized = certificate.trim()
        require(
            normalized.length <= MAX_EXTERNAL_CA_CERTIFICATE_CHARS &&
                normalized.startsWith("-----BEGIN CERTIFICATE-----") &&
                normalized.endsWith("-----END CERTIFICATE-----") &&
                normalized.none { it.code > 0x7f } &&
                "PRIVATE KEY" !in normalized.uppercase(),
        ) {
            "External provider CA certificate is invalid"
        }
        "$normalized\n"
    }
}

private fun validateExternalProviderCounts(
    counts: Map<String, Long>,
    field: String,
) {
    require(counts.size <= MAX_EXTERNAL_ADMIN_COUNT_KEYS) {
        "External provider $field counts are too large"
    }
    counts.forEach { (name, count) ->
        requireBoundedAdminText(
            name,
            "$field count name",
            MAX_EXTERNAL_ADMIN_NAME_CHARS,
        )
        require(count >= 0) {
            "External provider $field count must not be negative"
        }
    }
}

private fun requireBoundedAdminText(
    value: String,
    field: String,
    maximum: Int,
): String {
    val normalized = value.trim()
    require(
        normalized.isNotEmpty() &&
            normalized.length <= maximum &&
            normalized.none(Char::isISOControl),
    ) {
        "External provider $field is invalid"
    }
    return normalized
}

private val EXTERNAL_CA_CERTIFICATE_PATTERN = Regex(
    """-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----""",
)

private const val ZULIP_ADMIN_PROVIDER_KIND = "zulip"
private const val EXTERNAL_PROVIDER_POLICIES_URL =
    "/api/workspace/v1/messenger/external_provider_policies/"
private const val EXTERNAL_PROVIDER_HEALTH_URL =
    "/api/workspace/v1/messenger/external_provider_health/"
private const val EXTERNAL_BRIDGE_INSTANCES_URL =
    "/api/workspace/v1/messenger/external_bridge_instances/"
private const val MAX_EXTERNAL_ACCOUNTS = 100_000
private const val MAX_EXTERNAL_SELECTED_CHATS = 1_000_000
private const val MAX_EXTERNAL_FILE_BYTES = 5_368_709_120L
private const val MAX_EXTERNAL_ADMIN_PAGE_SIZE = 500
private const val MAX_EXTERNAL_ADMIN_COUNT_KEYS = 64
private const val MAX_EXTERNAL_ADMIN_NAME_CHARS = 128
private const val MAX_EXTERNAL_ADMIN_TIMESTAMP_CHARS = 128
private const val MAX_EXTERNAL_ADMIN_SAFE_ERROR_CHARS = 4_096
private const val MAX_EXTERNAL_ADMIN_JSON_CHARS = 131_072
private const val MAX_EXTERNAL_CA_CERTIFICATES = 32
private const val MAX_EXTERNAL_CA_CERTIFICATE_CHARS = 64 * 1_024
private const val MAX_EXTERNAL_CA_INPUT_CHARS =
    MAX_EXTERNAL_CA_CERTIFICATES * MAX_EXTERNAL_CA_CERTIFICATE_CHARS
