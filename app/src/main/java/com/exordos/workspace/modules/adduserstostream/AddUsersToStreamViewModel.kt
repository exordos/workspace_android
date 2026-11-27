package com.exordos.workspace.modules.adduserstostream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.AddUsersToStreamRequest
import com.exordos.workspace.data.uio.UserUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.modules.creationbase.streamActionAccount

class AddUsersToStreamViewModel(
    val streamUuid: String,
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    private val storedBindings = eventsRepository.streamBindingDao.observeStreamBindings(streamUuid)
    private val streamBindings = storedBindings.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val users: StateFlow<List<UserUIO>> = combine(eventsRepository.users, streamBindings, eventsRepository.currentUser) { list, bindings, current ->
            val members = bindings.map { it.userUuid }.toSet()
            list.filter { it.uuid !in members && it.uuid != current?.uuid }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedUserUuids = MutableStateFlow<List<String>>(emptyList())

    val selectedUserUuids: StateFlow<List<String>> = _selectedUserUuids.asStateFlow()


    private val _createQueryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val createQueryState: StateFlow<QueryState> = _createQueryState

    fun didTapOnUser(user: UserUIO) {
        if (_createQueryState.value is QueryState.Loading || _createQueryState.value is QueryState.Success ||
            user.uuid == eventsRepository.currentUser.value?.uuid ||
            streamBindings.value.any { it.userUuid == user.uuid }) return
        if (_selectedUserUuids.value.contains(user.uuid)) {
            _selectedUserUuids.update { it - user.uuid }
        } else {
            _selectedUserUuids.update { it + user.uuid }
        }
    }

    fun submitInvitation() { viewModelScope.launch { addUsersToStream() } }

    suspend fun addUsersToStream() {
        val previousState = _createQueryState.value
        if (previousState is QueryState.Loading || previousState is QueryState.Success) return
        val account = streamActionAccount(client, eventsRepository) ?: run {
            _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
            return
        }
        if (!_createQueryState.compareAndSet(previousState, QueryState.Loading)) return
        val streamRevision = eventsRepository.streamRemovalRevision(streamUuid)
        val initialBindings = storedBindings.first()
        val existing = initialBindings.map { it.userUuid }.toSet()
        val selected = _selectedUserUuids.value.filter { it !in existing && it != eventsRepository.currentUser.value?.uuid }
        if (selected.isEmpty()) {
            // Realtime may have confirmed an invitation whose HTTP response was lost.
            _createQueryState.value = if (_selectedUserUuids.value.isEmpty()) previousState else QueryState.Success
            return
        }
        val invitation = try { AddUsersToStreamRequest(streamUuid, selected) } catch (_: IllegalArgumentException) {
            _createQueryState.value = QueryState.Error("Некорректный идентификатор стрима")
            return
        }
        if (!account.isCurrent()) {
            _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
            return
        }
        val revisions = selected.associateWith { eventsRepository.streamBindingRevision(streamUuid, it) }
        val response = client.performRequest(invitation, account.server.id, account.server)
        if (!account.isCurrent()) {
            _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
            return
        }
        _createQueryState.value = when (response) {
            is ApiResult.Error -> QueryState.Error(response.error.errorMessage)
            is ApiResult.Success -> eventsRepository.db.withWriteTransaction {
                if (response.value.any { it.streamUuid != streamUuid })
                    return@withWriteTransaction QueryState.Error("Сервер вернул участников другого стрима")
                if (!account.isCurrent() || eventsRepository.streamRemovalRevision(streamUuid) != streamRevision)
                    return@withWriteTransaction QueryState.Error("Стрим больше недоступен")
                // Realtime changes made after sending the invitation take precedence.
                eventsRepository.setInitialStreamBindings(response.value.filter {
                    revisions[it.userUuid] == eventsRepository.streamBindingRevision(streamUuid, it.userUuid)
                })
                QueryState.Success
            }
        }
    }
}
