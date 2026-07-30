package ru.genesiscorporation.workspace.beta.modules.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DEFAULT_MESSAGE_PAGE_SIZE
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageSortDirection
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesRequest

class FeedViewModel(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val kind: MessageTimelineKind = MessageTimelineKind.FEED,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state
    private val requestMutex = Mutex()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                refreshInternal()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    fun loadOlder() {
        viewModelScope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                loadOlderInternal()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    private suspend fun refreshInternal() {
        val ownerKey = activeOwnerKey() ?: run {
            _state.value = FeedUiState(
                hasLoaded = true,
                error = "Не удалось определить активную учётную запись",
            )
            return
        }
        val existing = _state.value.takeIf { it.ownerKey == ownerKey }
        _state.value = (existing ?: FeedUiState(ownerKey = ownerKey)).copy(
            initialLoading = existing?.messages.isNullOrEmpty(),
            refreshing = !existing?.messages.isNullOrEmpty(),
            loadingOlder = false,
            error = null,
            olderError = null,
        )

        try {
            when (
                val response = client.performRequest(
                    MessagesRequest(
                        pageLimit = FEED_PAGE_SIZE,
                        sortDirection = MessageSortDirection.DESCENDING,
                        starred = true.takeIf { kind.starredOnly },
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (!ownerIsCurrent(ownerKey)) {
                        failForAccountChange()
                        return
                    }
                    val page = validateFeedPage(
                        messages = response.value,
                        nextMarkerHeader = response.metadata.nextPageMarker,
                        requireStarred = kind.starredOnly,
                    )
                    val prior = _state.value
                    _state.value = if (page.error == null) {
                        FeedUiState(
                            ownerKey = ownerKey,
                            messages = page.messages,
                            hasLoaded = true,
                            nextPageMarker = page.nextPageMarker,
                        )
                    } else if (prior.messages.isNotEmpty()) {
                        prior.copy(
                            initialLoading = false,
                            refreshing = false,
                            loadingOlder = false,
                            hasLoaded = true,
                            error = page.error,
                        )
                    } else {
                        FeedUiState(
                            ownerKey = ownerKey,
                            hasLoaded = true,
                            error = page.error,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (!ownerIsCurrent(ownerKey)) {
                        failForAccountChange()
                        return
                    }
                    val prior = _state.value
                    _state.value = prior.copy(
                        initialLoading = false,
                        refreshing = false,
                        hasLoaded = true,
                        error = feedError(
                            kind.refreshError,
                            response.error.message,
                        ),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (!ownerIsCurrent(ownerKey)) {
                failForAccountChange()
            } else {
                _state.value = _state.value.copy(
                    initialLoading = false,
                    refreshing = false,
                    hasLoaded = true,
                    error = kind.refreshError,
                )
            }
        }
    }

    private suspend fun loadOlderInternal() {
        val current = _state.value
        val ownerKey = current.ownerKey ?: return
        val marker = current.nextPageMarker ?: return
        if (!ownerIsCurrent(ownerKey)) {
            failForAccountChange()
            return
        }
        _state.value = current.copy(
            loadingOlder = true,
            olderError = null,
        )
        try {
            when (
                val response = client.performRequest(
                    MessagesRequest(
                        pageLimit = FEED_PAGE_SIZE,
                        pageMarker = marker,
                        sortDirection = MessageSortDirection.DESCENDING,
                        starred = true.takeIf { kind.starredOnly },
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    if (
                        !ownerIsCurrent(ownerKey) ||
                        _state.value.ownerKey != ownerKey ||
                        _state.value.nextPageMarker != marker
                    ) {
                        failForAccountChange()
                        return
                    }
                    val page = validateFeedPage(
                        messages = response.value,
                        nextMarkerHeader = response.metadata.nextPageMarker,
                        previousMarker = marker,
                        requireStarred = kind.starredOnly,
                    )
                    val prior = _state.value
                    _state.value = if (page.error == null) {
                        prior.copy(
                            messages = mergeOlderFeedMessages(
                                current = prior.messages,
                                older = page.messages,
                            ),
                            loadingOlder = false,
                            nextPageMarker = page.nextPageMarker,
                            olderError = null,
                        )
                    } else {
                        prior.copy(
                            loadingOlder = false,
                            nextPageMarker = marker,
                            olderError = page.error,
                        )
                    }
                }

                is ApiResult.Error -> {
                    if (!ownerIsCurrent(ownerKey)) {
                        failForAccountChange()
                        return
                    }
                    _state.value = _state.value.copy(
                        loadingOlder = false,
                        olderError = feedError(
                            kind.olderError,
                            response.error.message,
                        ),
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (!ownerIsCurrent(ownerKey)) {
                failForAccountChange()
            } else {
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    olderError = kind.olderError,
                )
            }
        }
    }

    private suspend fun activeOwnerKey(): String? =
        userViewModel.repo.activeCredentialSnapshot()
            .ownerKey
            ?.takeIf(String::isNotBlank)

    private suspend fun ownerIsCurrent(ownerKey: String): Boolean =
        userViewModel.repo.isActiveCredentialOwner(ownerKey)

    private fun failForAccountChange() {
        _state.value = FeedUiState(
            hasLoaded = true,
            error = "Учётная запись изменилась во время загрузки",
        )
    }
}

private fun feedError(summary: String, detail: String?): String {
    val safeDetail = detail
        ?.replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(160)
    return if (safeDetail == null) summary else "$summary: $safeDetail"
}

private const val FEED_PAGE_SIZE = DEFAULT_MESSAGE_PAGE_SIZE
