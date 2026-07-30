package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod
import java.time.Instant
import java.util.UUID

@Serializable
data class DraftsRequest(
    val streamUuid: String? = null,
    val topicUuid: String? = null,
    val pageLimit: Int = DEFAULT_DRAFT_PAGE_SIZE,
    val pageMarker: String? = null,
    val sortDirection: DraftSortDirection = DraftSortDirection.DESCENDING,
) : ApiRequest<DraftsRequestData, List<DraftResponse>, ApiError> {
    init {
        require(topicUuid == null || streamUuid != null) {
            "A draft topic filter requires a stream filter"
        }
        streamUuid?.let(::canonicalDraftUuid)
        topicUuid?.let(::canonicalDraftUuid)
        require(pageLimit in 1..MAX_DRAFT_PAGE_SIZE) {
            "Draft page size must be between 1 and $MAX_DRAFT_PAGE_SIZE"
        }
        pageMarker?.let(::canonicalDraftUuid)
    }

    override val method: HTTPMethod = HTTPMethod.GET
    override val url: String = DRAFTS_URL
    override val data = DraftsRequestData(
        streamUuid = streamUuid?.let(::canonicalDraftUuid),
        topicUuid = topicUuid?.let(::canonicalDraftUuid),
        pageLimit = pageLimit,
        pageMarker = pageMarker?.let(::canonicalDraftUuid),
        sortKey = "updated_at",
        sortDirection = sortDirection.wireValue,
    )
}

@Serializable
data class CreateDraftRequest(
    val draftUuid: String,
    val streamUuid: String,
    val topicUuid: String,
    val content: String,
) : ApiRequest<CreateDraftRequestData, DraftResponse, ApiError> {
    init {
        canonicalDraftUuid(draftUuid)
        canonicalDraftUuid(streamUuid)
        canonicalDraftUuid(topicUuid)
        normalizedDraftContent(content)
    }

    override val method: HTTPMethod = HTTPMethod.POST
    override val url: String = DRAFTS_URL
    override val data = CreateDraftRequestData(
        uuid = canonicalDraftUuid(draftUuid),
        streamUuid = canonicalDraftUuid(streamUuid),
        topicUuid = canonicalDraftUuid(topicUuid),
        payload = DraftPayload(
            kind = "markdown",
            content = normalizedDraftContent(content),
        ),
    )
}

@Serializable
data class UpdateDraftRequest(
    val draftUuid: String,
    val content: String,
    val entityTag: String,
) : ApiRequest<UpdateDraftRequestData, DraftResponse, ApiError> {
    init {
        canonicalDraftUuid(draftUuid)
        normalizedDraftContent(content)
        requireStrongDraftEntityTag(entityTag)
    }

    override val method: HTTPMethod = HTTPMethod.PUT
    override val url: String = "$DRAFTS_URL${canonicalDraftUuid(draftUuid)}"
    override val data = UpdateDraftRequestData(
        payload = DraftPayload(
            kind = "markdown",
            content = normalizedDraftContent(content),
        ),
    )
    override val additionalHeaders: Map<String, String> =
        mapOf("If-Match" to requireStrongDraftEntityTag(entityTag))
}

@Serializable
data class DeleteDraftRequest(
    val draftUuid: String,
    val entityTag: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    init {
        canonicalDraftUuid(draftUuid)
        requireStrongDraftEntityTag(entityTag)
    }

    override val method: HTTPMethod = HTTPMethod.DELETE
    override val url: String = "$DRAFTS_URL${canonicalDraftUuid(draftUuid)}"
    override val data = EmptyRequestData()
    override val additionalHeaders: Map<String, String> =
        mapOf("If-Match" to requireStrongDraftEntityTag(entityTag))
}

@Serializable
data class DraftsRequestData(
    @SerialName("stream_uuid") val streamUuid: String?,
    @SerialName("topic_uuid") val topicUuid: String?,
    @SerialName("page_limit") val pageLimit: Int,
    @SerialName("page_marker") val pageMarker: String?,
    @SerialName("sort_key") val sortKey: String,
    @SerialName("sort_dir") val sortDirection: String,
)

@Serializable
data class CreateDraftRequestData(
    val uuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    val payload: DraftPayload,
)

@Serializable
data class UpdateDraftRequestData(
    val payload: DraftPayload,
)

@Serializable
data class DraftPayload(
    val kind: String,
    val content: String,
)

@Serializable
data class DraftResponse(
    val uuid: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    val payload: DraftPayload,
    val revision: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

enum class DraftSortDirection(
    val wireValue: String,
) {
    ASCENDING("asc"),
    DESCENDING("desc"),
}

data class ValidatedDraft(
    val response: DraftResponse,
    val entityTag: String,
)

fun validateDraftResponse(
    response: DraftResponse,
    expectedDraftUuid: String? = null,
    expectedProjectId: String? = null,
    expectedUserUuid: String? = null,
    expectedStreamUuid: String? = null,
    expectedTopicUuid: String? = null,
    responseEntityTag: String? = null,
): ValidatedDraft {
    val canonicalResponse = response.copy(
        uuid = canonicalDraftUuid(response.uuid),
        projectId = canonicalDraftUuid(response.projectId),
        userUuid = canonicalDraftUuid(response.userUuid),
        streamUuid = canonicalDraftUuid(response.streamUuid),
        topicUuid = canonicalDraftUuid(response.topicUuid),
        payload = response.payload.copy(
            content = normalizedDraftContent(response.payload.content),
        ),
    )
    require(response.payload.kind == "markdown") {
        "Draft payload must be markdown"
    }
    require(response.revision >= 1) {
        "Draft revision must be positive"
    }
    parseDraftTimestamp(response.createdAt)
    parseDraftTimestamp(response.updatedAt)
    expectedProjectId?.let {
        require(canonicalResponse.projectId == canonicalDraftUuid(it)) {
            "Draft belongs to another project"
        }
    }
    expectedDraftUuid?.let {
        require(canonicalResponse.uuid == canonicalDraftUuid(it)) {
            "Draft response UUID changed"
        }
    }
    expectedUserUuid?.let {
        require(canonicalResponse.userUuid == canonicalDraftUuid(it)) {
            "Draft belongs to another account"
        }
    }
    expectedStreamUuid?.let {
        require(canonicalResponse.streamUuid == canonicalDraftUuid(it)) {
            "Draft belongs to another stream"
        }
    }
    expectedTopicUuid?.let {
        require(canonicalResponse.topicUuid == canonicalDraftUuid(it)) {
            "Draft belongs to another topic"
        }
    }
    val fallbackEntityTag = "\"${canonicalResponse.revision}\""
    val entityTag = responseEntityTag
        ?.let(::requireStrongDraftEntityTag)
        ?: fallbackEntityTag
    require(entityTag == fallbackEntityTag) {
        "Draft ETag does not match its revision"
    }
    return ValidatedDraft(
        response = canonicalResponse,
        entityTag = entityTag,
    )
}

fun parseDraftConflictBody(
    body: String?,
    entityTag: String?,
    expectedDraftUuid: String,
    expectedProjectId: String,
    expectedUserUuid: String,
    expectedStreamUuid: String,
    expectedTopicUuid: String,
): ValidatedDraft? {
    if (body.isNullOrBlank()) return null
    val conflict = runCatching {
        DRAFT_JSON.decodeFromString<DraftConflictResponse>(body)
    }.getOrNull() ?: return null
    return runCatching {
        validateDraftResponse(
            response = conflict.current,
            expectedDraftUuid = expectedDraftUuid,
            expectedProjectId = expectedProjectId,
            expectedUserUuid = expectedUserUuid,
            expectedStreamUuid = expectedStreamUuid,
            expectedTopicUuid = expectedTopicUuid,
            responseEntityTag = entityTag,
        )
    }.getOrNull()
}

@Serializable
private data class DraftConflictResponse(
    val current: DraftResponse,
)

fun canonicalDraftUuid(value: String): String {
    val trimmed = value.trim()
    val canonical = runCatching { UUID.fromString(trimmed).toString() }
        .getOrNull()
        ?: throw IllegalArgumentException("Draft identifiers must be canonical UUIDs")
    require(canonical.equals(trimmed, ignoreCase = true)) {
        "Draft identifiers must be canonical UUIDs"
    }
    return canonical
}

fun normalizedDraftContent(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) {
        "Draft content must not be blank"
    }
    require(normalized.length <= MAX_DRAFT_CONTENT_CHARS) {
        "Draft content exceeds $MAX_DRAFT_CONTENT_CHARS characters"
    }
    return normalized
}

fun requireStrongDraftEntityTag(value: String): String {
    val trimmed = value.trim()
    require(trimmed.matches(Regex("""\"[1-9][0-9]*\""""))) {
        "Draft ETag must be a strong revision tag"
    }
    return trimmed
}

private fun parseDraftTimestamp(value: String): Instant =
    runCatching { Instant.parse(value) }
        .getOrNull()
        ?: throw IllegalArgumentException("Draft timestamp must be ISO-8601 UTC")

private val DRAFT_JSON = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
}

const val DEFAULT_DRAFT_PAGE_SIZE = 100
const val MAX_DRAFT_PAGE_SIZE = 1_000
const val MAX_DRAFT_CONTENT_CHARS = 40_000
private const val DRAFTS_URL = "/api/workspace/v1/messenger/drafts/"
