package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class ChatUserInfoViewModel(
    val userName: String,
    val userId: String,
    val avatarUrl: String,
    val email: String,
    val client: WorkspaceAPIClient,
    val repo: EventsRepository,
) : ViewModel() {
    private val bindings = MutableStateFlow<List<StreamBindingResponseData>?>(
        repo.streamBindings.value
            .takeIf(List<StreamBindingResponseData>::isNotEmpty),
    )
    private val refreshRequestId = AtomicLong(0L)

    private val _profileLoading = MutableStateFlow(false)
    val profileLoading = _profileLoading.asStateFlow()
    private val _profileLoaded = MutableStateFlow(
        resolveTargetUser(userId, repo.users.value) != null,
    )
    val profileLoaded = _profileLoaded.asStateFlow()
    private val _sharedChannelsLoaded = MutableStateFlow(
        bindings.value != null && repo.streams.value.isNotEmpty(),
    )
    val sharedChannelsLoaded = _sharedChannelsLoaded.asStateFlow()
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError = _profileError.asStateFlow()
    private val _directChatOpening = MutableStateFlow(false)
    val directChatOpening = _directChatOpening.asStateFlow()
    private val _directChatError = MutableStateFlow<String?>(null)
    val directChatError = _directChatError.asStateFlow()
    private val _openDirectChatEvents =
        MutableSharedFlow<DirectChatDestination>(extraBufferCapacity = 1)
    val openDirectChatEvents = _openDirectChatEvents.asSharedFlow()
    private val knownDirectStreamUuids = MutableStateFlow(
        repo.streams.value
            .asSequence()
            .filter(Stream::isDirectProviderChat)
            .mapNotNull { canonicalUuid(it.uuid) }
            .toSet(),
    )

    val profile: StateFlow<UserResponseData?> = repo.users
        .map { users -> resolveTargetUser(userId, users) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val channels: StateFlow<List<Stream>> = combine(
        repo.streams,
        bindings,
        knownDirectStreamUuids,
    ) { streams, loadedBindings, directStreamUuids ->
        resolveSharedChannels(
            userUuid = userId,
            streams = streams,
            bindings = loadedBindings,
            knownDirectStreamUuids = directStreamUuids,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refreshProfile()
    }

    fun refreshProfile(): Boolean {
        if (_profileLoading.value) return false
        val ownerKey = client.userViewModel.activeAccountId.value
        if (ownerKey.isNullOrBlank()) {
            _profileError.value = "Текущий аккаунт недоступен"
            return false
        }
        if (canonicalUuid(userId) == null) {
            _profileError.value = "Профиль содержит некорректный идентификатор"
            return false
        }
        val requestId = refreshRequestId.incrementAndGet()
        _profileLoading.value = true
        _profileError.value = null
        viewModelScope.launch {
            try {
                val results = coroutineScope {
                    val users = async {
                        client.performRequest(UsersRequest())
                    }
                    val streams = async {
                        client.performRequest(StreamsRequest())
                    }
                    val streamBindings = async {
                        client.performRequest(StreamBindingsRequest())
                    }
                    ProfileRefreshResults(
                        users = users.await(),
                        streams = streams.await(),
                        bindings = streamBindings.await(),
                    )
                }
                if (!isCurrentRequest(requestId, ownerKey)) return@launch

                val errors = mutableListOf<String>()
                when (val users = results.users) {
                    is ApiResult.Success -> {
                        if (resolveTargetUser(userId, users.value) == null) {
                            _profileLoaded.value = false
                            errors += "Пользователь больше недоступен"
                        } else {
                            repo.setInitialUsers(users.value)
                            _profileLoaded.value = true
                        }
                    }
                    is ApiResult.Error -> {
                        errors += userProfileErrorMessage(
                            "Не удалось обновить профиль",
                            users.error,
                        )
                    }
                }
                var streamsLoaded = false
                when (val streams = results.streams) {
                    is ApiResult.Success -> {
                        knownDirectStreamUuids.value = resolveKnownDirectStreamUuids(
                            previousStreams = repo.streams.value,
                            refreshedStreams = streams.value,
                            previouslyKnownDirectStreamUuids =
                                knownDirectStreamUuids.value,
                        )
                        repo.setInitialStreams(streams.value)
                        streamsLoaded = true
                    }
                    is ApiResult.Error -> {
                        errors += userProfileErrorMessage(
                            "Не удалось загрузить каналы",
                            streams.error,
                        )
                    }
                }
                when (val loadedBindings = results.bindings) {
                    is ApiResult.Success -> {
                        repo.setInitialStreamBindings(loadedBindings.value)
                        bindings.value = loadedBindings.value
                        if (streamsLoaded) {
                            _sharedChannelsLoaded.value = true
                        }
                    }
                    is ApiResult.Error -> {
                        errors += userProfileErrorMessage(
                            "Не удалось загрузить общие каналы",
                            loadedBindings.error,
                        )
                    }
                }
                _profileError.value = errors.firstOrNull()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (isCurrentRequest(requestId, ownerKey)) {
                    _profileError.value = "Не удалось обновить профиль"
                }
            } finally {
                if (refreshRequestId.get() == requestId) {
                    _profileLoading.value = false
                }
            }
        }
        return true
    }

    fun clearProfileError() {
        _profileError.value = null
    }

    fun openDirectChat(): Boolean {
        if (_directChatOpening.value) return false
        val ownerKey = client.userViewModel.activeAccountId.value
        val targetUuid = canonicalUuid(userId)
        val currentUserUuid = canonicalUuid(
            client.userViewModel.userId.value
                ?: client.userViewModel.userData?.uuid.orEmpty(),
        )
        if (ownerKey.isNullOrBlank() || targetUuid == null) {
            _directChatError.value = "Не удалось определить пользователя"
            return false
        }
        val loadedProfile = profile.value
        if (loadedProfile?.identityKind.equals("external", ignoreCase = true)) {
            _directChatError.value =
                "Личный чат для внешнего пользователя недоступен"
            return false
        }
        if (targetUuid == currentUserUuid) {
            _directChatError.value = "Личный чат с собой недоступен"
            return false
        }
        if (!canOpenDirectChatWith(loadedProfile, currentUserUuid)) {
            _directChatError.value = "Сначала обновите профиль пользователя"
            return false
        }
        _directChatOpening.value = true
        _directChatError.value = null
        viewModelScope.launch {
            try {
                when (
                    val destination = resolveOrCreateDirectChat(
                        ownerKey = ownerKey,
                        targetUuid = targetUuid,
                    )
                ) {
                    is DirectChatResolution.Success -> {
                        if (ownerStillActive(ownerKey)) {
                            _openDirectChatEvents.emit(destination.destination)
                        }
                    }
                    is DirectChatResolution.Error -> {
                        if (ownerStillActive(ownerKey)) {
                            _directChatError.value = destination.message
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (ownerStillActive(ownerKey)) {
                    _directChatError.value = "Не удалось открыть личный чат"
                }
            } finally {
                _directChatOpening.value = false
            }
        }
        return true
    }

    fun clearDirectChatError() {
        _directChatError.value = null
    }

    fun profileFields(): List<ProfileField> {
        return buildProfileFields(
            user = profile.value,
            targetUserId = userId,
            fallbackEmail = email,
        )
    }

    private suspend fun resolveOrCreateDirectChat(
        ownerKey: String,
        targetUuid: String,
    ): DirectChatResolution {
        when (
            val existing = resolveDirectChatCandidate(
                targetUserUuid = targetUuid,
                streams = repo.streams.value,
            )
        ) {
            is DirectChatCandidate.Found ->
                return directChatDestination(ownerKey, existing.stream)
            DirectChatCandidate.Ambiguous ->
                return DirectChatResolution.Error(
                    "Найдено несколько личных чатов; обновите каталог",
                )
            DirectChatCandidate.None -> Unit
        }

        val preflight = refreshAndResolveDirectChat(
            ownerKey = ownerKey,
            targetUuid = targetUuid,
        )
        if (preflight is DirectChatCatalogLookup.Error) {
            return DirectChatResolution.Error(preflight.message)
        }
        when (
            val refreshed =
                (preflight as DirectChatCatalogLookup.Resolved).candidate
        ) {
            is DirectChatCandidate.Found ->
                return directChatDestination(ownerKey, refreshed.stream)
            DirectChatCandidate.Ambiguous ->
                return DirectChatResolution.Error(
                    "Найдено несколько личных чатов; обновите каталог",
                )
            DirectChatCandidate.None -> Unit
        }

        val createResult = client.performRequest(
            AddStreamRequest(
                name = "Direct",
                description = "Private workspace",
                directUserUuid = targetUuid,
            ),
        )
        if (!ownerStillActive(ownerKey)) {
            return DirectChatResolution.Error("Аккаунт изменился")
        }
        if (createResult is ApiResult.Success) {
            val stream = createResult.value
            val validResponse =
                canonicalUuid(stream.uuid) != null &&
                    stream.isPrivate &&
                    canonicalUuid(stream.directUserUuid.orEmpty()) == targetUuid
            if (validResponse) {
                repo.addStream(stream)
                return directChatDestination(ownerKey, stream)
            }
        }

        val recovered = refreshAndResolveDirectChat(ownerKey, targetUuid)
        if (recovered is DirectChatCatalogLookup.Resolved) {
            when (val candidate = recovered.candidate) {
                is DirectChatCandidate.Found ->
                    return directChatDestination(ownerKey, candidate.stream)
                DirectChatCandidate.Ambiguous ->
                    return DirectChatResolution.Error(
                        "Личный чат создан неоднозначно; обновите каталог",
                    )
                DirectChatCandidate.None -> Unit
            }
        }
        if (
            recovered is DirectChatCatalogLookup.Error &&
            createResult is ApiResult.Success
        ) {
            return DirectChatResolution.Error(recovered.message)
        }
        return when (createResult) {
            is ApiResult.Error -> DirectChatResolution.Error(
                userProfileErrorMessage(
                    "Не удалось открыть личный чат",
                    createResult.error,
                ),
            )
            is ApiResult.Success -> DirectChatResolution.Error(
                "Сервер вернул некорректный личный чат",
            )
        }
    }

    private suspend fun refreshAndResolveDirectChat(
        ownerKey: String,
        targetUuid: String,
    ): DirectChatCatalogLookup {
        val response = client.performRequest(StreamsRequest())
        if (!ownerStillActive(ownerKey)) {
            return DirectChatCatalogLookup.Error("Аккаунт изменился")
        }
        return when (response) {
            is ApiResult.Success -> {
                knownDirectStreamUuids.value = resolveKnownDirectStreamUuids(
                    previousStreams = repo.streams.value,
                    refreshedStreams = response.value,
                    previouslyKnownDirectStreamUuids =
                        knownDirectStreamUuids.value,
                )
                repo.setInitialStreams(response.value)
                DirectChatCatalogLookup.Resolved(
                    resolveDirectChatCandidate(targetUuid, response.value),
                )
            }
            is ApiResult.Error -> DirectChatCatalogLookup.Error(
                userProfileErrorMessage(
                    "Не удалось проверить личный чат",
                    response.error,
                ),
            )
        }
    }

    private suspend fun directChatDestination(
        ownerKey: String,
        stream: Stream,
    ): DirectChatResolution {
        if (!ownerStillActive(ownerKey)) {
            return DirectChatResolution.Error("Аккаунт изменился")
        }
        var topicUuid = canonicalUuid(stream.defaultTopicUuid.orEmpty())
        if (topicUuid == null) {
            when (val response = client.performRequest(TopicsRequest(stream.uuid))) {
                is ApiResult.Success -> {
                    if (!ownerStillActive(ownerKey)) {
                        return DirectChatResolution.Error("Аккаунт изменился")
                    }
                    val topics = response.value.filter {
                        canonicalUuid(it.streamUuid) == canonicalUuid(stream.uuid)
                    }
                    repo.addStreamTopics(stream.uuid, topics)
                    topicUuid = resolveDirectChatTopicUuid(stream, topics)
                    if (topicUuid != null) {
                        repo.updateStream(stream.copy(defaultTopicUuid = topicUuid))
                    }
                }
                is ApiResult.Error -> {
                    return DirectChatResolution.Error(
                        userProfileErrorMessage(
                            "Не удалось загрузить личный чат",
                            response.error,
                        ),
                    )
                }
            }
        }
        val resolvedTopicUuid = topicUuid
            ?: return DirectChatResolution.Error(
                "У личного чата нет доступной темы",
            )
        if (!ownerStillActive(ownerKey)) {
            return DirectChatResolution.Error("Аккаунт изменился")
        }
        return DirectChatResolution.Success(
            DirectChatDestination(
                title = profile.value?.displayableName()
                    ?.takeIf(String::isNotBlank)
                    ?: userName,
                streamUuid = stream.uuid,
                topicUuid = resolvedTopicUuid,
            ),
        )
    }

    private fun isCurrentRequest(
        requestId: Long,
        ownerKey: String,
    ): Boolean =
        refreshRequestId.get() == requestId && ownerStillActive(ownerKey)

    private fun ownerStillActive(ownerKey: String): Boolean =
        client.userViewModel.activeAccountId.value == ownerKey
}

data class ProfileField(
    val title: String,
    val value: String,
    @param:DrawableRes val icon: Int,
    val copyable: Boolean = false,
)

internal fun buildProfileFields(
    user: UserResponseData?,
    targetUserId: String,
    fallbackEmail: String,
): List<ProfileField> = buildList {
    user?.statusText?.takeIf(String::isNotBlank)?.let { status ->
        add(
            ProfileField(
                title = "Статус",
                value = status,
                icon = R.drawable.ic_figma_profile_status,
            ),
        )
    }
    (user?.email?.takeIf(String::isNotBlank)
        ?: fallbackEmail.takeIf(String::isNotBlank))?.let { resolvedEmail ->
        add(
            ProfileField(
                title = "Email",
                value = resolvedEmail,
                icon = R.drawable.ic_figma_profile_email,
                copyable = true,
            ),
        )
    }
    val authoritativeUserId = user?.uuid
        ?.takeIf { canonicalUuid(it) == canonicalUuid(targetUserId) }
    add(
        ProfileField(
            title = "ID пользователя",
            value = authoritativeUserId ?: targetUserId,
            icon = R.drawable.ic_userid,
            copyable = authoritativeUserId != null,
        ),
    )
}

data class DirectChatDestination(
    val title: String,
    val streamUuid: String,
    val topicUuid: String,
)

private data class ProfileRefreshResults(
    val users: ApiResult<List<UserResponseData>, ApiError>,
    val streams: ApiResult<List<Stream>, ApiError>,
    val bindings: ApiResult<List<StreamBindingResponseData>, ApiError>,
)

private sealed interface DirectChatResolution {
    data class Success(
        val destination: DirectChatDestination,
    ) : DirectChatResolution

    data class Error(
        val message: String,
    ) : DirectChatResolution
}

private sealed interface DirectChatCatalogLookup {
    data class Resolved(
        val candidate: DirectChatCandidate,
    ) : DirectChatCatalogLookup

    data class Error(
        val message: String,
    ) : DirectChatCatalogLookup
}

internal sealed interface DirectChatCandidate {
    data class Found(
        val stream: Stream,
    ) : DirectChatCandidate

    data object None : DirectChatCandidate
    data object Ambiguous : DirectChatCandidate
}

internal fun resolveTargetUser(
    targetUserUuid: String,
    users: List<UserResponseData>,
): UserResponseData? {
    val target = canonicalUuid(targetUserUuid) ?: return null
    return users.singleOrNull { canonicalUuid(it.uuid) == target }
}

internal fun canOpenDirectChatWith(
    profile: UserResponseData?,
    currentUserUuid: String?,
): Boolean {
    val targetUuid = canonicalUuid(profile?.uuid.orEmpty()) ?: return false
    val currentUuid = canonicalUuid(currentUserUuid.orEmpty()) ?: return false
    return targetUuid != currentUuid &&
        !profile?.identityKind.equals("external", ignoreCase = true)
}

internal fun resolveSharedChannels(
    userUuid: String,
    streams: List<Stream>,
    bindings: List<StreamBindingResponseData>?,
    knownDirectStreamUuids: Set<String> = emptySet(),
): List<Stream> {
    if (bindings == null) return emptyList()

    val targetUserUuid = canonicalUuid(userUuid) ?: return emptyList()
    val knownDirect = knownDirectStreamUuids
        .mapNotNull(::canonicalUuid)
        .toSet()
    val streamUuids = bindings
        .asSequence()
        .filter { canonicalUuid(it.userUuid) == targetUserUuid }
        .mapNotNull { canonicalUuid(it.streamUuid) }
        .toSet()
    return streams.filter {
        val streamUuid = canonicalUuid(it.uuid)
        streamUuid in streamUuids &&
            streamUuid !in knownDirect &&
            it.isConfirmedSharedChannel()
    }
}

internal fun Stream.isConfirmedSharedChannel(): Boolean {
    if (isDirectProviderChat()) return false
    if (!isPrivate) return true
    if (sourceName == "native") return true
    return provider?.externalId
        ?.substringBefore(':')
        ?.equals("channel", ignoreCase = true) == true
}

internal fun resolveKnownDirectStreamUuids(
    previousStreams: List<Stream>,
    refreshedStreams: List<Stream>,
    previouslyKnownDirectStreamUuids: Set<String>,
): Set<String> {
    val previousDirect = previousStreams
        .asSequence()
        .filter(Stream::isDirectProviderChat)
        .mapNotNull { canonicalUuid(it.uuid) }
        .toSet()
    val knownDirect = previouslyKnownDirectStreamUuids
        .mapNotNull(::canonicalUuid)
        .toSet()
    return refreshedStreams
        .asSequence()
        .mapNotNull { stream ->
            canonicalUuid(stream.uuid)?.takeIf { streamUuid ->
                stream.isDirectProviderChat() ||
                    streamUuid in previousDirect ||
                    streamUuid in knownDirect
            }
        }
        .toSet()
}

internal fun resolveDirectChatCandidate(
    targetUserUuid: String,
    streams: List<Stream>,
): DirectChatCandidate {
    val target = canonicalUuid(targetUserUuid)
        ?: return DirectChatCandidate.None
    val matches = streams.filter { stream ->
        stream.isPrivate &&
            stream.sourceName == "native" &&
            canonicalUuid(stream.uuid) != null &&
            canonicalUuid(stream.directUserUuid.orEmpty()) == target
    }
    return when (matches.size) {
        0 -> DirectChatCandidate.None
        1 -> DirectChatCandidate.Found(matches.single())
        else -> DirectChatCandidate.Ambiguous
    }
}

internal fun resolveDirectChatTopicUuid(
    stream: Stream,
    topics: List<TopicsResponseData>,
): String? {
    val streamUuid = canonicalUuid(stream.uuid) ?: return null
    val matching = topics.filter {
        canonicalUuid(it.streamUuid) == streamUuid &&
            canonicalUuid(it.uuid) != null
    }
    return canonicalUuid(stream.defaultTopicUuid.orEmpty())
        ?: matching.singleOrNull { it.isDefault }?.uuid?.let(::canonicalUuid)
        ?: matching.singleOrNull()?.uuid?.let(::canonicalUuid)
}

internal fun userProfileErrorMessage(
    prefix: String,
    error: ApiError,
): String = when (error.kind) {
    ApiErrorKind.FORBIDDEN -> "$prefix: действие запрещено"
    ApiErrorKind.NOT_FOUND -> "$prefix: данные больше недоступны"
    ApiErrorKind.RATE_LIMITED -> "$prefix: слишком много запросов"
    ApiErrorKind.TIMEOUT -> "$prefix: сервер не ответил вовремя"
    ApiErrorKind.NETWORK -> "$prefix: нет подключения к сети"
    ApiErrorKind.SERVER -> "$prefix: сервис временно недоступен"
    ApiErrorKind.CONFLICT -> "$prefix: аккаунт или данные изменились"
    else -> prefix
}

private fun canonicalUuid(value: String): String? =
    runCatching { UUID.fromString(value).toString() }.getOrNull()
