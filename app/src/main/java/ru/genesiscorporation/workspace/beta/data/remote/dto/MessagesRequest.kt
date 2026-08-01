package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class MessagesRequest(
    val streamId: String? = null,
    val topicId: String? = null,
    val pageLimit: Int? = null,
    val pageMarker: String? = null,
    val sortDirection: MessageSortDirection? = null,
    val read: Boolean? = null,
    val isOwn: Boolean? = null,
    val starred: Boolean? = null,
    val pinned: Boolean? = null,
    val mentioned: Boolean? = null,
): ApiRequest<MessagesRequestData, List<MessageResponse>, ApiError> {
    init {
        require(topicId == null || streamId != null) {
            "A topic filter requires a stream filter"
        }
        pageLimit?.let { limit ->
            require(limit in 1..MAX_MESSAGE_PAGE_SIZE) {
                "Message page size must be between 1 and $MAX_MESSAGE_PAGE_SIZE"
            }
        }
        pageMarker?.let(::parseCanonicalMessagePageMarker)
        require(
            (pageLimit == null && pageMarker == null && sortDirection == null) ||
                (pageLimit != null && sortDirection != null)
        ) {
            "Message keyset pagination requires a page size and sort direction"
        }
    }

    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MessagesRequestData(
        streamUuid = streamId,
        topicUuid = topicId,
        pageLimit = pageLimit,
        pageMarker = pageMarker?.let(::parseCanonicalMessagePageMarker),
        sortKey = sortDirection?.let { "created_at" },
        sortDirection = sortDirection?.wireValue,
        read = read,
        isOwn = isOwn,
        starred = starred,
        pinned = pinned,
        mentioned = mentioned,
    )
}

@Serializable
data class MessagesByIdsRequest(
    val messageIds: List<String>
): ApiRequest<MessagesByIdsRequestData, List<MessageResponse>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/"
    override val data = MessagesByIdsRequestData(
       messageIds
    )
}

class MessageRequest(
    messageUuid: String,
) : ApiRequest<EmptyRequestData, MessageResponse, ApiError> {
    private val canonicalMessageUuid = requireNotNull(
        parseCanonicalMessageUuid(messageUuid),
    ) {
        "messageUuid must be a canonical UUID"
    }

    override val method = HTTPMethod.GET
    override val url =
        "/api/workspace/v1/messenger/messages/$canonicalMessageUuid"
    override val data = EmptyRequestData()
}


@Serializable
data class MessagesRequestData(
    @SerialName("stream_uuid") val streamUuid: String?,
    @SerialName("topic_uuid") val topicUuid: String?,
    @SerialName("page_limit") val pageLimit: Int?,
    @SerialName("page_marker") val pageMarker: String?,
    @SerialName("sort_key") val sortKey: String?,
    @SerialName("sort_dir") val sortDirection: String?,
    val read: Boolean?,
    @SerialName("is_own") val isOwn: Boolean?,
    val starred: Boolean?,
    val pinned: Boolean?,
    val mentioned: Boolean?,
)

@Serializable
data class MessagesByIdsRequestData(
    val uuid: List<String>
) {
}
@Serializable
data class MessageResponse(
    var uuid: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") var topicUuid: String,
    @SerialName("user_uuid") var userUuid: String,
    @SerialName("author_uuid") var authorUuid: String,
    var payload: MessageResponsePayload,
    @SerialName("is_own") val isOwn: Boolean,
    var reactions: Map<String, Int>,
    val read: Boolean = true,
    val starred: Boolean = false,
    val pinned: Boolean = false,
    val mentioned: Boolean = false,
    var user: UserResponseData? = null,
    val provider: ProviderReference? = null,
)

@Serializable
data class MessageResponsePayload(
    val kind: String,
    var content: String
)

enum class MessageSortDirection(
    val wireValue: String,
) {
    ASCENDING("asc"),
    DESCENDING("desc"),
}

internal fun parseCanonicalMessagePageMarker(value: String): String =
    runCatching { java.util.UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { canonical ->
            canonical.equals(value.trim(), ignoreCase = true)
        }
        ?: throw IllegalArgumentException("Message page marker must be a canonical UUID")

const val DEFAULT_MESSAGE_PAGE_SIZE = 50
const val MAX_MESSAGE_PAGE_SIZE = 1_000
