package ru.genesiscorporation.workspace.beta

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager
import ru.genesiscorporation.workspace.beta.data.push.PushTokenUpdates
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse

@OptIn(FlowPreview::class)
@SuppressLint("LogNotTimber") // Timber is transitive; this app uses android.util.Log.
class WorkspaceViewModel(
    val client: WorkspaceAPIClient,
    val repo: EventsRepository,
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager,
): ViewModel() {
    private val _currentCallMessage = MutableStateFlow<MessageResponse?>(null)
    val currentCallMessage: StateFlow<MessageResponse?> = _currentCallMessage
    val realtimeConnectionState = repo.realtimeConnectionState

    init {
        viewModelScope.launch {
            combine(
                client.userViewModel.activeAccountId,
                client.userViewModel.baseUrl,
                client.userViewModel.accessToken,
            ) { accountId, baseUrl, accessToken ->
                AccountRuntime(
                    ownerKey = accountId ?: baseUrl,
                    authenticated = accessToken != null,
                )
            }
                .distinctUntilChanged()
                .collectLatest { runtime ->
                repo.resetAccountState()
                if (
                    runtime.authenticated &&
                    !runtime.ownerKey.isNullOrBlank()
                ) {
                    runAccountRuntime(runtime.ownerKey)
                } else {
                    repo.resetRealtimeCursor()
                    repo.pauseRealtimeForAuthentication()
                }
            }
        }
        viewModelScope.launch {
            combine(
                client.userViewModel.activeAccountId,
                client.userViewModel.baseUrl,
                client.userViewModel.accessToken,
            ) { accountId, baseUrl, accessToken ->
                AccountRuntime(
                    ownerKey = accountId ?: baseUrl,
                    authenticated = accessToken != null,
                )
            }
                .distinctUntilChanged()
                .collectLatest { runtime ->
                if (runtime.authenticated) {
                    pushDeviceRegistrationManager.registerCurrentTokenWithRetry()
                }
            }
        }
        viewModelScope.launch {
            PushTokenUpdates.tokens.collectLatest { token ->
                if (client.userViewModel.accessToken.value != null) {
                    pushDeviceRegistrationManager.registerTokenWithRetry(token)
                }
            }
        }
    }

    fun setCurrentCallMessage(callMessage: MessageResponse?) {
        _currentCallMessage.value = callMessage
    }

    fun retryRealtimeConnectionNow(): Boolean =
        repo.requestRealtimeReconnect()

    private suspend fun runAccountRuntime(ownerKey: String) {
        val snapshotStore =
            client.userViewModel.workspaceSnapshotStore
        val cachedSnapshot = try {
            snapshotStore.read(ownerKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(
                SNAPSHOT_TAG,
                "Offline snapshot is unavailable for this runtime",
            )
            null
        }
        val cacheRuntimeAvailable = if (cachedSnapshot == null) {
            false
        } else {
            client.userViewModel.repo
                .withActiveCredentialOwner(ownerKey) {
                    repo.hydrateCachedSnapshot(cachedSnapshot)
                    true
                } == true
        }
        if (!cacheRuntimeAvailable) {
            if (
                client.userViewModel.repo
                    .isActiveCredentialOwner(ownerKey)
            ) {
                repo.start(ownerKey)
            }
            return
        }

        coroutineScope {
            launch {
                var cacheContainsData =
                    cachedSnapshot?.hasPersistableData() == true
                combine(
                    repo.streams,
                    repo.streamTopics,
                    repo.streamTopicMessages,
                ) { streams, topics, messages ->
                    ru.genesiscorporation.workspace.beta.data.WorkspaceSnapshot(
                        streams = streams,
                        topicsByStream = topics,
                        messagesByConversation = messages,
                    )
                }
                    .debounce(SNAPSHOT_WRITE_DEBOUNCE_MILLIS)
                    .collectLatest { snapshot ->
                        val hasData = snapshot.hasPersistableData()
                        if (!hasData && !cacheContainsData) {
                            return@collectLatest
                        }
                        try {
                            val written = client.userViewModel.repo
                                .withActiveCredentialOwner(ownerKey) {
                                    snapshotStore.write(ownerKey, snapshot)
                                    true
                                } == true
                            if (written) cacheContainsData = hasData
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            Log.d(
                                SNAPSHOT_TAG,
                                "Failed to persist an offline snapshot",
                            )
                        }
                    }
            }
            repo.start(ownerKey)
        }
    }
}

private data class AccountRuntime(
    val ownerKey: String?,
    val authenticated: Boolean,
)

private fun ru.genesiscorporation.workspace.beta.data.WorkspaceSnapshot
    .hasPersistableData(): Boolean =
    streams.isNotEmpty() ||
        topicsByStream.values.any { it.isNotEmpty() } ||
        messagesByConversation.values.any { it.isNotEmpty() }

private const val SNAPSHOT_TAG = "WorkspaceSnapshot"
private const val SNAPSHOT_WRITE_DEBOUNCE_MILLIS = 750L
