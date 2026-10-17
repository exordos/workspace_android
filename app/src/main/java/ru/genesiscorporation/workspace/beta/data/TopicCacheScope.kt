package ru.genesiscorporation.workspace.beta.data

import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.ProtectedRequestAuthentication
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

/** Capture before IO. Only this repository generation and credential may publish its result. */
class TopicCacheScope internal constructor(
    internal val lineage: Any,
    internal val generation: Long,
    internal val ownerUuid: String?,
    internal val authentication: ProtectedRequestAuthentication?,
    internal val server: ServerConfig,
) {
    internal fun sameOwner(other: TopicCacheScope): Boolean = ownerUuid != null &&
        ownerUuid == other.ownerUuid && lineage === other.lineage && generation == other.generation &&
        server.id == other.server.id && server.baseUrl == other.server.baseUrl && server.projectUuid == other.server.projectUuid

    internal fun matchesCredential(token: String?): Boolean = authentication?.matchesAccessToken(token) ?: token.isNullOrBlank()
    internal fun <D, R, E> pin(request: ApiRequest<D, R, E>) = requireNotNull(authentication).pin(request)
    internal fun <D, R, E> recover(request: ApiRequest<D, R, E>) = requireNotNull(authentication).recoverSession(request)
    override fun toString(): String = "TopicCacheScope([REDACTED])"
}

internal class TopicCacheRead<R>(val scope: TopicCacheScope, val result: ApiResult<R, ApiError>, val stale: Boolean = false)

/** A complete read/enrichment attempt shares one owner and one bounded recovery budget. */
internal suspend fun <R> WorkspaceAPIClient.withTopicCacheRead(
    repository: EventsRepository,
    read: suspend (TopicCacheScope, Boolean) -> ApiResult<R, ApiError>,
): TopicCacheRead<R> {
    var scope = repository.captureTopicCacheScope()
    if (scope.ownerUuid == null || !repository.isTopicCacheScopeCurrent(scope)) {
        val initial = scope
        repository.verifyCurrentAccount()
        scope = repository.captureTopicCacheScope()
        if (scope.ownerUuid == null || !repository.isTopicCacheScopeCurrent(scope)) {
            return TopicCacheRead(scope, ApiResult.Error(ApiError("Account owner unavailable", "OWNER_UNAVAILABLE")),
                stale = !repository.isTopicCacheGenerationCurrent(initial))
        }
    }
    val result = read(scope, true)
    if (repository.isTopicCacheScopeCurrent(scope)) return TopicCacheRead(scope, result)
    if (!repository.isTopicCacheGenerationCurrent(scope))
        return TopicCacheRead(scope, ApiResult.Error(ApiError("Account changed", "AUTHENTICATION_CHANGED")), stale = true)
    var renewed = repository.captureTopicCacheScope()
    if (renewed.ownerUuid == null || !repository.isTopicCacheScopeCurrent(renewed)) {
        repository.verifyCurrentAccount()
        renewed = repository.captureTopicCacheScope()
    }
    if (!scope.sameOwner(renewed) || !repository.isTopicCacheScopeCurrent(renewed))
        return TopicCacheRead(scope, ApiResult.Error(ApiError("Account changed", "AUTHENTICATION_CHANGED")), stale = true)
    // Discard the entire old snapshot, including enrichment. The fresh attempt cannot refresh again.
    val fresh = read(renewed, false)
    return if (repository.isTopicCacheScopeCurrent(renewed)) TopicCacheRead(renewed, fresh)
    else TopicCacheRead(renewed, ApiResult.Error(ApiError("Account changed", "AUTHENTICATION_CHANGED")), stale = true)
}

internal suspend inline fun <reified D : Any, reified R : Any, reified E : Any> WorkspaceAPIClient.readTopicCache(
    repository: EventsRepository, request: ApiRequest<D, R, E>, expected: TopicCacheScope? = null,
): TopicCacheRead<R> = withTopicCacheRead(repository) { scope, recovery ->
    if (expected != null && !expected.sameOwner(scope))
        ApiResult.Error(ApiError("Account changed", "AUTHENTICATION_CHANGED"))
    else performRequest(if (recovery) scope.recover(request) else scope.pin(request), repository.serverId, scope.server)
}

internal class TopicSnapshot(
    val topics: List<ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData>,
    val messages: List<ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse>,
)

internal suspend inline fun <reified D : Any, reified E : Any> WorkspaceAPIClient.readTopicSnapshot(
    repository: EventsRepository,
    request: ApiRequest<D, List<ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData>, E>,
): TopicCacheRead<TopicSnapshot> = withTopicCacheRead(repository) { scope, recovery ->
    when (val topics = performRequest(if (recovery) scope.recover(request) else scope.pin(request), repository.serverId, scope.server)) {
        is ApiResult.Error -> ApiResult.Error(topics.error)
        is ApiResult.Success -> {
            val ids = topics.value.mapNotNull { it.lastMessageUuid }.distinct()
            val messages = if (ids.isEmpty()) emptyList() else {
                val messageRequest = ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest(ids)
                when (val response = performRequest(if (recovery) scope.recover(messageRequest) else scope.pin(messageRequest), repository.serverId, scope.server)) {
                    is ApiResult.Success -> response.value
                    is ApiResult.Error -> emptyList()
                }
            }
            ApiResult.Success(TopicSnapshot(topics.value, messages))
        }
    }
}
