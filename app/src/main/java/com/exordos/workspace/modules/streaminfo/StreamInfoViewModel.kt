package com.exordos.workspace.modules.streaminfo

import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.exordos.workspace.modules.encryptedchat.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.exordos.workspace.R
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.ConfirmedAccountContext
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.ProtectedRequestAuthentication
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.StreamBindingResponseData
import com.exordos.workspace.data.remote.dto.TopicsResponseData
import com.exordos.workspace.data.remote.dto.UpdateStreamNotificationModeRequest
import com.exordos.workspace.data.remote.dto.UpdateStreamNotificationModeRequestData
import com.exordos.workspace.data.remote.dto.UpdateStreamNotificationModeResponseData
import com.exordos.workspace.data.remote.dto.UpdateTopicNotificationModeRequest
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.data.remote.dto.ProtectedStreamDetailsRequest
import com.exordos.workspace.data.remote.dto.UpdateProtectedStreamRequest
import com.exordos.workspace.data.remote.dto.UpdateProtectedStreamRequestData
import com.exordos.workspace.data.remote.dto.DeleteProtectedStreamRequest
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.modules.chatdialog.JitsiStyleRoomNameGenerator
import java.net.URI
import java.util.UUID

class StreamInfoViewModel(
    val streamUuid: String,
    val topicUuid: String,
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore,
    context: Context? = null,
): ViewModel() {
    val repo = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")
    private var mountedProtectedStream = repo.streams.value.firstOrNull { it.uuid == streamUuid }?.encryption == true
    val hasMountedProtectedStream: Boolean get() = mountedProtectedStream
    private val notificationMountServer = client.requireUserViewModel().selectedServer.value?.takeIf { it.id == repo.serverId }?.copy()
    private val notificationMountOwner = notificationMountServer?.let { server -> repo.currentUser.value?.uuid?.let {
        ProtectedStreamEditOwner(server.id, server.baseUrl, server.projectUuid, it, streamUuid)
    } }
    private val protectedNotifications = ProtectedStreamNotificationUpdater(
        currentContext = ::currentProtectedContext,
        currentAuthentication = { client.requireUserViewModel().getTokens(repo.serverId)?.accessToken },
        write = { captured, mode, authentication ->
            when (val result = client.performRequest(protectedNotificationRequest(streamUuid, mode, authentication),
                captured.server.id, captured.server)) {
                is ApiResult.Success -> ApiResult.Success(Unit)
                is ApiResult.Error -> result
            }
        },
        commit = { _, mode ->
            repo.streams.value.firstOrNull { it.uuid == streamUuid }?.let {
                repo.updateStream(it.copy(notificationMode = mode))
            }
        },
        initialOwner = notificationMountOwner,
        initialConfiguration = notificationMountServer,
    )

    private val protectedStreamEditor = ProtectedStreamEditor(
        currentContext = ::currentProtectedContext,
        readDetails = { captured -> client.performRequest(captured.pin(ProtectedStreamDetailsRequest(streamUuid)),
            captured.server.id, captured.server) },
        writeDetails = { captured, body -> client.performRequest(captured.pin(UpdateProtectedStreamRequest(streamUuid,
            body.name, body.description)), captured.server.id, captured.server) },
        commit = { _, response ->
            // Metadata edits must not roll a concurrently rotated key back to an older response.
            repo.streams.value.firstOrNull { it.uuid == streamUuid }?.let { current ->
                repo.updateStream(current.copy(name = response.name, description = response.description,
                    updatedAt = response.updatedAt))
            }
        },
        deleteDetails = { captured -> client.performRequest(captured.pin(DeleteProtectedStreamRequest(streamUuid)),
            captured.server.id, captured.server) },
        commitDelete = {
            encryptionRuntime?.close()
            repo.removeStream(streamUuid)
        },
        currentDeletionAccount = { protectedStreamAccountCurrent(client, repo, it) },
    )
    val protectedStreamDetails: StateFlow<Stream?> = protectedStreamEditor.details
    val protectedStreamUpdateState: StateFlow<ProtectedStreamUpdateState> = protectedStreamEditor.state
    fun loadProtectedStreamDetails() { viewModelScope.launch { protectedStreamEditor.load() } }
    fun updateProtectedStream(name: String, description: String) {
        viewModelScope.launch { protectedStreamEditor.update(name, description) }
    }
    fun clearProtectedStreamUpdateState() = protectedStreamEditor.clearStatus()
    val protectedStreamDeleteState: StateFlow<ProtectedStreamDeleteState> = protectedStreamEditor.deleteState
    fun deleteProtectedStream() { viewModelScope.launch { protectedStreamEditor.delete() } }
    fun clearProtectedStreamDeleteState() = protectedStreamEditor.clearDeleteStatus()

    private val protectedCallStarter = ProtectedCallStarter(
        currentContext = ::currentProtectedContext,
        serverUrl = { repo.jitsiServerUrl },
        topicUuid = topicUuid,
        createRoom = { JitsiStyleRoomNameGenerator.generate() + "-" + UUID.randomUUID().toString() },
        sendEncrypted = { _, link, canonical ->
            (encryptionRuntime ?: throw IllegalStateException("Encryption runtime unavailable"))
                .sendText(link, topicUuid, canonical)
        },
    )
    val protectedStreamCallState: StateFlow<ProtectedStreamCallState> = protectedCallStarter.state
    suspend fun createProtectedCall(): String? = protectedCallStarter.createCall()
    fun clearProtectedStreamCallState() = protectedCallStarter.clearStatus()

    private fun currentProtectedContext() = currentProtectedStreamContext(client, repo, streamUuid)

    init {
        viewModelScope.launch {
            merge(combine(client.requireUserViewModel().selectedServer, client.requireUserViewModel().accessToken,
                repo.currentUser, repo.streams, repo.verifiedOwnerAuthentication) { _, _, _, _, _ -> Unit },
                ConfirmedAccountContext.invalidations.map { Unit }).collect {
                    if (repo.streams.value.any { it.uuid == streamUuid && it.encryption }) mountedProtectedStream = true
                    protectedStreamEditor.onContextChanged()
                    protectedCallStarter.onContextChanged()
                    protectedNotifications.onContextChanged()
                }
        }
    }

    private val encryptionRuntime = context?.let { EncryptedChatRuntime(it, client, repo, viewModelScope, streamUuid, topicUuid) }
    val encryptedChatState: StateFlow<EncryptedChatSessionState> = encryptionRuntime?.state ?: MutableStateFlow(EncryptedChatSessionState())
    private fun encryptionAction(block: suspend (EncryptedChatRuntime) -> Unit) { viewModelScope.launch {
        try { encryptionRuntime?.let { block(it) } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    } }
    fun requestEncryptionKey(uuid: String) = encryptionAction { it.requestKey(uuid) }
    fun grantEncryptionRequest(uuid: String) = encryptionAction { it.grantKey(uuid) }
    fun rejectEncryptionRequest(uuid: String) = encryptionAction { it.rejectKey(uuid) }
    fun grantAllEncryptionRequests() = encryptionAction { it.grantAllKeys() }
    fun rotateEncryptionKey() = encryptionAction { it.rotate() }
    fun deleteEncryptionKeys(keys: Set<String>) = encryptionAction { runtime -> keys.forEach { runtime.removeLocalKey(it) } }
    fun clearEncryptedError() = encryptionRuntime?.clearError()
    override fun onCleared() { encryptionRuntime?.close(); super.onCleared() }

    val stream: StateFlow<Stream?> = repo.streams
        .map { list -> list.firstOrNull { it.uuid == streamUuid }?.also { if (it.encryption) mountedProtectedStream = true } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.streams.value.firstOrNull { it.uuid == streamUuid }
        )

    val topic: StateFlow<TopicsResponseData?> = repo.streamTopics
        .map { map -> map[streamUuid] }
        .map { list -> list?.firstOrNull { it.uuid == topicUuid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.streamTopics.value[streamUuid]?.firstOrNull { it.uuid == topicUuid }
        )

    val streamBindings: StateFlow<Map<String, List<StreamBindingResponseData>>> = repo.streamBindings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val users: StateFlow<List<UserResponseData>> = repo.users
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    suspend fun setStreamNotificationMode(notificationMode: String) {
        // A mounted protected VM must never fall back to the current account's ordinary transport,
        // including after its original stream disappears during account switching.
        if (mountedProtectedStream || repo.streams.value.any { it.uuid == streamUuid && it.encryption }) {
            mountedProtectedStream = true
            protectedNotifications.update(notificationMode)
            return
        }
        val response = client.performRequest(
            UpdateStreamNotificationModeRequest(
                streamUuid,
                notificationMode
            )
        )
        when(response) {
            is ApiResult.Success -> {

            }
            is ApiResult.Error -> {

            }
        }
    }
}

enum class ProtectedStreamUpdateFailure { INVALID_NAME, FORBIDDEN, STALE, REQUEST_FAILED, INVALID_RESPONSE, UNAVAILABLE }

data class ProtectedStreamUpdateState(
    val busy: Boolean = false,
    val error: ProtectedStreamUpdateFailure? = null,
    val saved: Boolean = false,
)

enum class ProtectedStreamDeleteOutcome { IDLE, VERIFYING, STILL_PRESENT, DELETED }

data class ProtectedStreamDeleteState(
    val busy: Boolean = false,
    val error: ProtectedStreamUpdateFailure? = null,
    val outcome: ProtectedStreamDeleteOutcome = ProtectedStreamDeleteOutcome.IDLE,
) {
    val deleted: Boolean get() = outcome == ProtectedStreamDeleteOutcome.DELETED
    /** Unresolved writes are reads only; STILL_PRESENT requires a new user confirmation. */
    val verificationRequired: Boolean get() = outcome == ProtectedStreamDeleteOutcome.VERIFYING
}

data class ProtectedStreamCallState(val busy: Boolean = false, val error: ProtectedStreamUpdateFailure? = null)

internal data class ProtectedStreamEditContext(val server: ServerConfig, val userUuid: String, val stream: Stream,
                                               val authentication: ProtectedRequestAuthentication? = null,
                                               val confirmedAccount: ConfirmedAccountContext? = null) {
    fun owner() = ProtectedStreamEditOwner(server.id, server.baseUrl, server.projectUuid, userUuid, stream.uuid)
    fun sameAuthorization(other: ProtectedStreamEditContext?): Boolean = if (confirmedAccount != null)
        confirmedAccount.matches(other?.confirmedAccount)
    else authentication == null || authentication.matches(other?.authentication)
    fun <D, R, E> pin(request: ApiRequest<D, R, E>): ApiRequest<D, R, E> =
        confirmedAccount?.pin(request) ?: requireNotNull(authentication).pin(request)
}

internal data class ProtectedStreamEditOwner(val serverId: String, val baseUrl: String,
                                            val projectUuid: String?, val userUuid: String, val streamUuid: String)

internal fun currentProtectedStreamContext(client: WorkspaceAPIClient, repo: EventsRepository,
                                            streamUuid: String): ProtectedStreamEditContext? {
    val account = confirmedAccountContext(client, repo) ?: return null
    val target = repo.streams.value.firstOrNull { it.uuid == streamUuid }
    if (target == null || !target.encryption || (target.projectId != null && target.projectId != account.projectUuid) ||
        (target.userUuid != null && target.userUuid != account.userUuid)) return null
    return ProtectedStreamEditContext(account.serverConfig(), account.userUuid, target.copy(), confirmedAccount = account)
}

/** A DELETE may remove its own projection before its receipt or verification read arrives. */
internal fun protectedStreamAccountCurrent(client: WorkspaceAPIClient, repo: EventsRepository,
                                            captured: ProtectedStreamEditContext): Boolean {
    return captured.confirmedAccount?.isCurrent(client, repo) == true
}

/** Notification preferences belong to every member, but only to this mounted account/configuration. */
internal class ProtectedStreamNotificationUpdater(
    private val currentContext: () -> ProtectedStreamEditContext?,
    private val currentAuthentication: () -> String?,
    private val write: suspend (ProtectedStreamEditContext, String, String) -> ApiResult<Unit, ApiError>,
    private val commit: (ProtectedStreamEditContext, String) -> Unit,
    initialOwner: ProtectedStreamEditOwner? = currentContext()?.owner(),
    initialConfiguration: ServerConfig? = currentContext()?.server,
) {
    private var bound = currentContext()
    private val mountedOwner = initialOwner
    private val mountedConfiguration = initialConfiguration?.copy()
    private val authentication = currentAuthentication()?.takeIf { it.isNotBlank() }
    private val mutex = Mutex()
    private var revision = 0L
    private var stale = false

    fun onContextChanged() {
        val live = currentContext()
        if (authentication == null || currentAuthentication() != authentication ||
            (live != null && (live.owner() != mountedOwner || live.server != mountedConfiguration)) ||
            (bound != null && live == null)) {
            revision++
            stale = true
        }
        if (!stale && bound == null && live != null) bound = live
    }

    suspend fun update(mode: String): Boolean {
        if (mode !in setOf("mentions_only", "muted", "all_messages") || !mutex.tryLock()) return false
        try {
            onContextChanged()
            val captured = bound ?: return false
            val capturedAuthentication = authentication ?: return false
            val epoch = revision
            fun current(): Boolean {
                val live = currentContext()
                return !stale && revision == epoch && live != null && live.owner() == captured.owner() &&
                    live.server == captured.server && captured.sameAuthorization(live) && currentAuthentication() == capturedAuthentication
            }
            if (!current()) return false
            val result = write(captured, mode, capturedAuthentication)
            if (!current() || result !is ApiResult.Success) return false
            commit(captured, mode)
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } finally { mutex.unlock() }
    }
}

/** This one request cannot refresh/retry using a replacement account's current credentials. */
internal fun protectedNotificationRequest(streamUuid: String, mode: String, authentication: String):
    ApiRequest<UpdateStreamNotificationModeRequestData, UpdateStreamNotificationModeResponseData, ApiError> =
    ProtectedRequestAuthentication(authentication).pin(UpdateStreamNotificationModeRequest(streamUuid, mode))

/** Testable owner/role/response boundary for the mounted protected-chat editor. */
internal class ProtectedStreamEditor(
    private val currentContext: () -> ProtectedStreamEditContext?,
    private val readDetails: suspend (ProtectedStreamEditContext) -> ApiResult<Stream, ApiError>,
    private val writeDetails: suspend (ProtectedStreamEditContext, UpdateProtectedStreamRequestData) -> ApiResult<Stream, ApiError>,
    private val commit: (ProtectedStreamEditContext, Stream) -> Unit,
    private val deleteDetails: suspend (ProtectedStreamEditContext) -> ApiResult<String, ApiError> = {
        ApiResult.Error(ApiError("Delete transport unavailable", "UNAVAILABLE"))
    },
    private val commitDelete: (ProtectedStreamEditContext) -> Unit = {},
    private val currentDeletionAccount: (ProtectedStreamEditContext) -> Boolean = { captured ->
        currentContext()?.let { live -> live.owner() == captured.owner() && live.server == captured.server &&
            captured.sameAuthorization(live) } == true
    },
) {
    private val mutex = Mutex()
    private var boundOwner: ProtectedStreamEditOwner? = null
    private var revision = 0L
    private val mutableState = MutableStateFlow(ProtectedStreamUpdateState())
    val state = mutableState.asStateFlow()
    private val mutableDetails = MutableStateFlow<Stream?>(null)
    val details = mutableDetails.asStateFlow()
    private val mutableDeleteState = MutableStateFlow(ProtectedStreamDeleteState())
    val deleteState = mutableDeleteState.asStateFlow()
    private var pendingDeletion: ProtectedStreamEditContext? = null

    fun onContextChanged() {
        val deleting = pendingDeletion
        val ownerChanged = if (deleting != null) !currentDeletionAccount(deleting)
            else boundOwner != null && currentContext()?.owner() != boundOwner
        if (ownerChanged) {
            revision++
            mutableDetails.value = null
            mutableState.value = ProtectedStreamUpdateState(error = ProtectedStreamUpdateFailure.STALE)
            if (!mutableDeleteState.value.deleted) mutableDeleteState.value = ProtectedStreamDeleteState(
                error = ProtectedStreamUpdateFailure.STALE, outcome = deletionOutcome())
        }
    }

    fun clearStatus() {
        if (!mutableState.value.busy) mutableState.value = ProtectedStreamUpdateState()
    }

    fun clearDeleteStatus() {
        if (!mutableDeleteState.value.busy) mutableDeleteState.value = mutableDeleteState.value.copy(error = null)
    }

    private fun deletionOutcome() = if (pendingDeletion != null) ProtectedStreamDeleteOutcome.VERIFYING
        else if (mutableDeleteState.value.outcome == ProtectedStreamDeleteOutcome.STILL_PRESENT) ProtectedStreamDeleteOutcome.STILL_PRESENT
        else ProtectedStreamDeleteOutcome.IDLE

    suspend fun delete() {
        if (mutableDeleteState.value.deleted || !mutex.tryLock()) return
        try {
            mutableDeleteState.value = ProtectedStreamDeleteState(busy = true,
                outcome = deletionOutcome())
            val captured = pendingDeletion ?: currentContext() ?: fail(ProtectedStreamUpdateFailure.STALE)
            if (boundOwner == null) boundOwner = captured.owner()
            val capturedRevision = revision
            if (pendingDeletion == null) requireCurrent(captured, capturedRevision)
            else requireDeletionCurrent(captured, capturedRevision)
            if (pendingDeletion == null) {
                val original = readDetails(captured).unwrap()
                requireCurrent(captured, capturedRevision)
                validateResponse(original, captured)
                val liveRole = currentContext()?.stream?.role
                if (original.role !in EDIT_ROLES || (liveRole != null && liveRole !in EDIT_ROLES))
                    fail(ProtectedStreamUpdateFailure.FORBIDDEN)
                pendingDeletion = captured
                mutableDeleteState.value = mutableDeleteState.value.copy(outcome = ProtectedStreamDeleteOutcome.VERIFYING)
                val result = try { deleteDetails(captured) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ApiResult.Error(ApiError("Delete outcome unknown", "REQUEST_FAILED")) }
                requireDeletionCurrent(captured, capturedRevision)
                if (result is ApiResult.Error) {
                    val status = result.error.httpStatusCode
                    if (status != null && status in 400..499 && status !in setOf(404, 408, 409, 425)) {
                        pendingDeletion = null
                        fail(if (status == 403) ProtectedStreamUpdateFailure.FORBIDDEN else ProtectedStreamUpdateFailure.REQUEST_FAILED)
                    }
                }
            }
            requireDeletionCurrent(captured, capturedRevision)
            val verification = readDetails(captured)
            requireDeletionCurrent(captured, capturedRevision)
            if (verification is ApiResult.Error && verification.error.httpStatusCode == 404) {
                commitDelete(captured)
                mutableDetails.value = null
                pendingDeletion = null
                mutableDeleteState.value = ProtectedStreamDeleteState(outcome = ProtectedStreamDeleteOutcome.DELETED)
            } else {
                if (verification is ApiResult.Success) {
                    validateResponse(verification.value, captured)
                    // An authoritative existing stream settles this write as non-deletion.
                    // A subsequent delete() now belongs to a newly confirmed user intent.
                    pendingDeletion = null
                    mutableDetails.value = verification.value
                    mutableDeleteState.value = ProtectedStreamDeleteState(error = ProtectedStreamUpdateFailure.REQUEST_FAILED,
                        outcome = ProtectedStreamDeleteOutcome.STILL_PRESENT)
                    return
                }
                fail(ProtectedStreamUpdateFailure.REQUEST_FAILED)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProtectedStreamEditException) {
            mutableDeleteState.value = ProtectedStreamDeleteState(error = failure.reason,
                outcome = deletionOutcome())
        } catch (_: Exception) {
            mutableDeleteState.value = ProtectedStreamDeleteState(error = ProtectedStreamUpdateFailure.UNAVAILABLE,
                outcome = deletionOutcome())
        } finally {
            mutableDeleteState.value = mutableDeleteState.value.copy(busy = false)
            mutex.unlock()
        }
    }

    suspend fun load() = execute(null)
    suspend fun update(name: String, description: String) =
        execute(UpdateProtectedStreamRequestData(name.trim(), description.trim()))

    private suspend fun execute(body: UpdateProtectedStreamRequestData?) {
        if (!mutex.tryLock()) return
        try {
            mutableState.value = ProtectedStreamUpdateState(busy = true)
            if (body != null && body.name.isBlank()) fail(ProtectedStreamUpdateFailure.INVALID_NAME)
            val captured = currentContext() ?: fail(ProtectedStreamUpdateFailure.STALE)
            if (boundOwner == null) boundOwner = captured.owner()
            val capturedRevision = revision
            requireCurrent(captured, capturedRevision)
            val original = readDetails(captured).unwrap()
            requireCurrent(captured, capturedRevision)
            validateResponse(original, captured)
            mutableDetails.value = original
            if (body == null) {
                mutableState.value = ProtectedStreamUpdateState()
                return
            }
            val liveRole = currentContext()?.stream?.role
            if (original.role !in EDIT_ROLES || (liveRole != null && liveRole !in EDIT_ROLES) || original.isArchived)
                fail(ProtectedStreamUpdateFailure.FORBIDDEN)
            requireCurrent(captured, capturedRevision)
            val updated = writeDetails(captured, body).unwrap()
            requireCurrent(captured, capturedRevision)
            validateResponse(updated, captured)
            if (updated.name != body.name || updated.description != body.description)
                fail(ProtectedStreamUpdateFailure.INVALID_RESPONSE)
            commit(captured, updated)
            mutableDetails.value = updated
            mutableState.value = ProtectedStreamUpdateState(saved = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProtectedStreamEditException) {
            if (failure.reason == ProtectedStreamUpdateFailure.STALE) mutableDetails.value = null
            mutableState.value = ProtectedStreamUpdateState(error = failure.reason)
        } catch (_: Exception) {
            mutableState.value = ProtectedStreamUpdateState(error = ProtectedStreamUpdateFailure.UNAVAILABLE)
        } finally {
            mutableState.value = mutableState.value.copy(busy = false)
            mutex.unlock()
        }
    }

    private fun requireCurrent(captured: ProtectedStreamEditContext, capturedRevision: Long) {
        val live = currentContext()
        if (revision != capturedRevision || live?.owner() != captured.owner() ||
            !captured.sameAuthorization(live) ||
            boundOwner != captured.owner()) fail(ProtectedStreamUpdateFailure.STALE)
    }

    private fun requireDeletionCurrent(captured: ProtectedStreamEditContext, capturedRevision: Long) {
        if (revision != capturedRevision || boundOwner != captured.owner() || !currentDeletionAccount(captured))
            fail(ProtectedStreamUpdateFailure.STALE)
    }

    private fun validateResponse(response: Stream, captured: ProtectedStreamEditContext) {
        if (response.uuid != captured.stream.uuid || response.projectId != captured.server.projectUuid ||
            response.userUuid != captured.userUuid || !response.encryption ||
            response.isPrivate != captured.stream.isPrivate || response.directUserUuid != captured.stream.directUserUuid)
            fail(ProtectedStreamUpdateFailure.INVALID_RESPONSE)
    }

    private fun ApiResult<Stream, ApiError>.unwrap(): Stream = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Error -> fail(if (error.httpStatusCode == 403) ProtectedStreamUpdateFailure.FORBIDDEN
            else ProtectedStreamUpdateFailure.REQUEST_FAILED)
    }

    private fun fail(reason: ProtectedStreamUpdateFailure): Nothing = throw ProtectedStreamEditException(reason)
    private class ProtectedStreamEditException(val reason: ProtectedStreamUpdateFailure) : Exception(reason.name)
    private companion object { val EDIT_ROLES = setOf("owner", "administrator") }
}

/** Returns a room only after its link has been acknowledged as an encrypted stream message. */
internal class ProtectedCallStarter(
    private val currentContext: () -> ProtectedStreamEditContext?,
    private val serverUrl: () -> String,
    private val topicUuid: String,
    private val createRoom: () -> String,
    private val sendEncrypted: suspend (ProtectedStreamEditContext, String, String) -> MessageResponse,
) {
    private val mutex = Mutex()
    private var boundOwner: ProtectedStreamEditOwner? = null
    private var revision = 0L
    private val mutableState = MutableStateFlow(ProtectedStreamCallState())
    val state = mutableState.asStateFlow()

    fun onContextChanged() {
        if (boundOwner != null && currentContext()?.owner() != boundOwner) {
            revision++
            mutableState.value = ProtectedStreamCallState(error = ProtectedStreamUpdateFailure.STALE)
        }
    }

    fun clearStatus() {
        if (!mutableState.value.busy) mutableState.value = ProtectedStreamCallState()
    }

    suspend fun createCall(): String? {
        if (!mutex.tryLock()) return null
        try {
            mutableState.value = ProtectedStreamCallState(busy = true)
            val captured = currentContext() ?: fail(ProtectedStreamUpdateFailure.STALE)
            if (boundOwner == null) boundOwner = captured.owner()
            val capturedRevision = revision
            if (captured.owner() != boundOwner || captured.stream.isArchived || !captured.stream.encryption)
                fail(ProtectedStreamUpdateFailure.FORBIDDEN)
            val baseUrl = serverUrl().trimEnd('/')
            val uri = try { URI(baseUrl) } catch (_: Exception) { fail(ProtectedStreamUpdateFailure.UNAVAILABLE) }
            if (uri.scheme !in setOf("https", "http") || uri.host == null || uri.userInfo != null ||
                uri.query != null || uri.fragment != null) fail(ProtectedStreamUpdateFailure.UNAVAILABLE)
            val room = createRoom()
            if (!Regex("[A-Za-z0-9_-]{1,200}").matches(room)) fail(ProtectedStreamUpdateFailure.UNAVAILABLE)
            val canonical = UUID.randomUUID().toString()
            val receipt = sendEncrypted(captured, "$baseUrl/$room", canonical)
            val live = currentContext()
            if (revision != capturedRevision || live == null || live.owner() != captured.owner() || live.server != captured.server ||
                !captured.sameAuthorization(live))
                fail(ProtectedStreamUpdateFailure.STALE)
            if (!receipt.isOwn || receipt.authorUuid != captured.userUuid || receipt.streamUuid != captured.stream.uuid ||
                receipt.topicUuid != topicUuid || (receipt.projectId != null && receipt.projectId != captured.server.projectUuid) ||
                receipt.payload.kind != "e2ee" || receipt.payload.content.isBlank() || receipt.payload.keyUuid.isNullOrBlank())
                fail(ProtectedStreamUpdateFailure.INVALID_RESPONSE)
            mutableState.value = ProtectedStreamCallState()
            return room
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProtectedCallException) {
            mutableState.value = ProtectedStreamCallState(error = failure.reason)
        } catch (failure: EncryptedChatException) {
            mutableState.value = ProtectedStreamCallState(error = when (failure.failure) {
                EncryptedChatFailure.STALE -> ProtectedStreamUpdateFailure.STALE
                EncryptedChatFailure.FORBIDDEN -> ProtectedStreamUpdateFailure.FORBIDDEN
                else -> ProtectedStreamUpdateFailure.REQUEST_FAILED
            })
        } catch (_: Exception) {
            mutableState.value = ProtectedStreamCallState(error = ProtectedStreamUpdateFailure.UNAVAILABLE)
        } finally {
            mutableState.value = mutableState.value.copy(busy = false)
            mutex.unlock()
        }
        return null
    }

    private fun fail(reason: ProtectedStreamUpdateFailure): Nothing = throw ProtectedCallException(reason)
    private class ProtectedCallException(val reason: ProtectedStreamUpdateFailure) : Exception(reason.name)
}
