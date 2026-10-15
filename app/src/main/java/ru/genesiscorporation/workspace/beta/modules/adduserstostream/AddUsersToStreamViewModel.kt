package ru.genesiscorporation.workspace.beta.modules.adduserstostream

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
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddUsersToStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.creationbase.streamActionAccount

class AddUsersToStreamViewModel(
    val streamUuid: String,
    val client: WorkspaceAPIClient,
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    val eventsRepository = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    val users: StateFlow<List<UserResponseData>> = combine(eventsRepository.users, eventsRepository.streamBindings, eventsRepository.currentUser) { list, bindings, current ->
            val members = bindings[streamUuid].orEmpty().map { it.userUuid }.toSet()
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

    fun didTapOnUser(user: UserResponseData) {
        if (_createQueryState.value is QueryState.Loading || _createQueryState.value is QueryState.Success ||
            user.uuid == eventsRepository.currentUser.value?.uuid ||
            eventsRepository.streamBindings.value[streamUuid].orEmpty().any { it.userUuid == user.uuid }) return
        if (_selectedUserUuids.value.contains(user.uuid)) {
            _selectedUserUuids.update { it - user.uuid }
        } else {
            _selectedUserUuids.update { it + user.uuid }
        }
    }

    fun submitInvitation() { viewModelScope.launch { addUsersToStream() } }

    suspend fun addUsersToStream() {
        if (_createQueryState.value is QueryState.Loading || _createQueryState.value is QueryState.Success) return
        val account = streamActionAccount(client, eventsRepository) ?: run {
            _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
            return
        }
        val existing = eventsRepository.streamBindings.value[streamUuid].orEmpty().map { it.userUuid }.toSet()
        val selected = _selectedUserUuids.value.filter { it !in existing && it != eventsRepository.currentUser.value?.uuid }
        if (selected.isEmpty()) {
            // Realtime may have confirmed an invitation whose HTTP response was lost.
            if (_selectedUserUuids.value.isNotEmpty()) _createQueryState.value = QueryState.Success
            return
        }
        val invitation = try { AddUsersToStreamRequest(streamUuid, selected) } catch (_: IllegalArgumentException) {
            _createQueryState.value = QueryState.Error("Некорректный идентификатор стрима")
            return
        }
        _createQueryState.value = QueryState.Loading

        eventsRepository.beginStreamBindingsRequest(streamUuid).use { request ->
            val addUsersResponse = client.performRequest(
                invitation, account.server.id, account.server
            )
            if (!account.isCurrent()) {
                _createQueryState.value = QueryState.Error("Организация изменилась. Откройте экран заново.")
                return
            }
            when(addUsersResponse) {
                is ApiResult.Success -> {
                    if (addUsersResponse.value.any { it.streamUuid != streamUuid }) {
                        _createQueryState.value = QueryState.Error("Сервер вернул участников другого стрима")
                        return
                    }
                    _createQueryState.value = if (eventsRepository.applyStreamBindingsResponse(request, addUsersResponse.value)) {
                        QueryState.Success
                    } else QueryState.Error("Стрим больше недоступен")
                }
                is ApiResult.Error -> {
                    _createQueryState.value = QueryState.Error(addUsersResponse.error.errorMessage)
                }
            }
        }
    }
}
