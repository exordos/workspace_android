package com.exordos.workspace.modules.createstream

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.exordos.workspace.data.crypto.*
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.ConfirmedAccountContext
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.AddStreamRequest
import com.exordos.workspace.data.remote.dto.AddUsersToStreamRequest
import com.exordos.workspace.data.remote.dto.MessageReaction
import com.exordos.workspace.data.remote.dto.Stream
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.chooseserver.QueryState

class CreateStreamViewModel(
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore,
    private val applicationContext: Context? = null,
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val users: StateFlow<List<UserResponseData>> = combine(eventsRepository.users, eventsRepository.currentUser) { users, current ->
        users.filter { it.uuid != current?.uuid }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    var createdStream: Stream? = null
    private val _encrypted = MutableStateFlow(false)
    val encrypted: StateFlow<Boolean> = _encrypted.asStateFlow()
    fun setEncrypted(value: Boolean) { if (canEditCreation()) _encrypted.value = value }
    private enum class CreationPhase { UNCONFIRMED, CREATED, READY }
    private class CreationAttempt(
        val server: ServerConfig,
        val userUuid: String,
        val account: ConfirmedAccountContext?,
        val request: AddStreamRequest,
        val keyScope: ChatKeyScope?,
        val staged: StagedChatCreation?,
        val members: List<String>,
        var phase: CreationPhase = CreationPhase.UNCONFIRMED,
        var stream: Stream? = null,
    )
    private var creationAttempt: CreationAttempt? = null
    fun canEditCreation() = _createQueryState.value !is QueryState.Loading && creationAttempt == null && createdStream == null


    private val _streamName = MutableStateFlow("")
    val streamName: StateFlow<String> = _streamName

    private val _selectedUserUuids = MutableStateFlow<List<String>>(emptyList())

    val selectedUserUuids: StateFlow<List<String>> = _selectedUserUuids.asStateFlow()


    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    fun didTapOnUser(user: UserResponseData) {
        if (!canEditCreation() || user.uuid == eventsRepository.currentUser.value?.uuid) return
        if (_selectedUserUuids.value.contains(user.uuid)) {
            _selectedUserUuids.update { it - user.uuid }
        } else {
            _selectedUserUuids.update { it + user.uuid }
        }
    }
    fun onStreamNameChange(newText: String) {
        if (canEditCreation()) _streamName.value = newText
    }

    override fun onCleared() { creationAttempt?.staged?.groupKey?.fill(0); super.onCleared() }

    fun submitCreation() { viewModelScope.launch { createStream() } }

    suspend fun createStream() {
        if (_createQueryState.value is QueryState.Loading || _createQueryState.value is QueryState.Success || _streamName.value.isBlank()) return
        _createQueryState.value = QueryState.Loading
        try {
            val selected = client.requireUserViewModel().selectedServer.value ?: error("Нет организации")
            check(selected.id == eventsRepository.serverId) { "Организация изменилась" }
            val useEncryption = encrypted.value
            if (useEncryption) check(!selected.projectUuid.isNullOrBlank()) { "Нет проекта" }
            val user = eventsRepository.currentUser.value ?: error("Нет учётной записи")
            val account = if (useEncryption) requireNotNull(confirmedAccountContext(client, eventsRepository)) {
                "Учётная запись не подтверждена"
            } else null
            val config = account?.serverConfig() ?: selected.copy()
            check(eventsRepository.matchesServerConfiguration(config)) { "Организация изменилась. Откройте экран заново." }
            val project = config.projectUuid
            val userUuid = account?.userUuid ?: user.uuid
            val isCurrent = { if (account != null) account.isCurrent(client, eventsRepository) else client.requireUserViewModel().selectedServer.value?.let {
                it.id == config.id && it.id == eventsRepository.serverId && it.baseUrl == config.baseUrl && it.projectUuid == project && !it.needsToRelogin
            } == true && eventsRepository.currentUser.value?.uuid == userUuid &&
                client.requireUserViewModel().getTokens(config.id)?.accessToken?.isNotBlank() == true }
            check(isCurrent()) { "Учётная запись изменилась" }
            val store = if (useEncryption) withContext(Dispatchers.IO) {
                EncryptedChatKeyStore(requireNotNull(applicationContext) { "Нет хранилища ключей" })
            } else null
            val attempt = creationAttempt ?: run {
                val canonicalUuid = UUID.randomUUID().toString()
                val keyScope = if (store == null) null else withContext(Dispatchers.IO) {
                    check(isCurrent()) { "Учётная запись изменилась" }
                    val owner = requireNotNull(account).owner
                    ChatKeyScope(owner, store.ensureDeviceIdentity(owner, "${Build.MANUFACTURER} ${Build.MODEL}".take(255)).deviceId)
                }
                val stage = if (store == null) null else withContext(Dispatchers.IO) {
                    val staged = StagedChatCreation(canonicalUuid, UUID.randomUUID().toString(), WorkspaceChatCrypto().createGroupKey())
                    try {
                        check(isCurrent() && store.writeStagedCreation(requireNotNull(keyScope), staged)) { "Не удалось сохранить ключ" }
                        staged
                    } catch (failure: Throwable) { staged.groupKey.fill(0); throw failure }
                }
                CreationAttempt(config, userUuid, account,
                    AddStreamRequest(_streamName.value.trim(), "", null, useEncryption, stage?.keyUuid, canonicalUuid), keyScope, stage,
                    _selectedUserUuids.value.toList())
                    .also { creationAttempt = it }
            }
            check(attempt.server.id == config.id && attempt.server.baseUrl == config.baseUrl &&
                attempt.server.projectUuid == project && attempt.userUuid == userUuid &&
                (attempt.account == null || account?.sameOwner(attempt.account) == true)) { "Учётная запись изменилась" }
            check(isCurrent()) { "Учётная запись изменилась" }
            if (attempt.phase == CreationPhase.UNCONFIRMED) {
                when (val response = client.performRequest(account?.pin(attempt.request) ?: attempt.request, config.id, config)) {
                    is ApiResult.Success -> {
                        check(isCurrent()) { "Учётная запись изменилась" }
                        val stream = response.value
                        if (useEncryption) check(stream.encryption && stream.currentEncryptionKeyUuid == attempt.staged?.keyUuid &&
                            stream.uuid == attempt.request.canonicalUuid) { "Сервер вернул другой шифрованный чат" }
                        check((stream.ownerUuid == null || stream.ownerUuid == userUuid) &&
                            (project.isNullOrBlank() || stream.projectId == null || stream.projectId == project) &&
                            (stream.userUuid == null || stream.userUuid == userUuid)) { "Сервер вернул чат другой учётной записи" }
                        attempt.stream = stream
                        attempt.phase = CreationPhase.CREATED
                        createdStream = stream
                    }
                    is ApiResult.Error -> {
                        check(isCurrent()) { "Учётная запись изменилась" }
                        if (isDefinitiveStreamCreationRejection(response.error)) {
                            if (attempt.staged != null) withContext(Dispatchers.IO) {
                                check(isCurrent() && requireNotNull(store).discardStagedCreation(requireNotNull(attempt.keyScope), attempt.staged.creationId)) {
                                    "Не удалось удалить ключ отклонённого чата"
                                }
                            }
                            attempt.staged?.groupKey?.fill(0)
                            creationAttempt = null
                            createdStream = null
                        }
                        error(response.error.errorMessage)
                    }
                }
            }
            val created = requireNotNull(attempt.stream)
            if (attempt.phase == CreationPhase.CREATED) {
                if (store != null) withContext(Dispatchers.IO) {
                    val stage = requireNotNull(attempt.staged)
                    check(isCurrent() && store.finalizeCreation(requireNotNull(attempt.keyScope), stage.creationId, created.uuid)) {
                        "Не удалось сохранить ключ созданного чата"
                    }
                    stage.groupKey.fill(0)
                }
                attempt.phase = CreationPhase.READY
            }
            check(isCurrent()) { "Учётная запись изменилась" }
            if (attempt.members.isNotEmpty()) {
                val request = AddUsersToStreamRequest(created.uuid, attempt.members)
                when (val response = client.performRequest(account?.pin(request) ?: request, config.id, config)) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Error -> error(response.error.errorMessage)
                }
            }
            check(isCurrent()) { "Учётная запись изменилась" }
            if (eventsRepository.streams.value.none { it.uuid == created.uuid }) eventsRepository.addStream(created)
            _createQueryState.value = QueryState.Success
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            _createQueryState.value = QueryState.Error(error.message ?: "Не удалось создать чат")
        }
    }
}

internal fun isDefinitiveStreamCreationRejection(error: ApiError): Boolean =
    error.httpStatusCode?.let { it in 400..499 && it !in setOf(408, 409, 425) } == true
