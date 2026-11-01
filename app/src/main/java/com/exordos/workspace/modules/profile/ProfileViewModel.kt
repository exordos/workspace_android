package com.exordos.workspace.modules.profile

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineStart
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.ServerSettingsRequest
import com.exordos.workspace.data.remote.dto.ProjectsRequest
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.chooseserver.QueryState

class ProfileViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
    init {
        viewModelScope.launch { refreshOrganizationNames() }
    }

    private suspend fun refreshOrganizationNames() {
        val servers = userViewModel.repo.serversFlow.first()
        for (server in servers) {
            val projectUuid = server.projectUuid ?: continue
            if (server.needsToRelogin || userViewModel.getTokens(server.id) == null) continue
            val response = client.performRequest(ProjectsRequest(), server.id)
            if (response is ApiResult.Success) {
                val project = response.value.firstOrNull { it.uuid == projectUuid } ?: continue
                // Keep the cached label offline and ignore responses for a replaced project.
                userViewModel.repo.updateServerProjectName(server.id, projectUuid, project.name)
            }
        }
    }

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState
//    val repo = eventsRepositoryStore.get(client.getCurrentServerId()) ?: error("Cannot get current event repository")

    private val currentServerId: Flow<String?> = userViewModel.selectedServerId

    val eventsRepo: StateFlow<EventsRepository?> = combine(
        currentServerId,
        userViewModel.servers,
    ) { id, servers ->
        id?.let { eventsRepositoryStore.getOrCreateForId(it, servers) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)


    val user: StateFlow<UserResponseData?> = eventsRepo
        .flatMapLatest { repo ->
            repo?.currentUser ?: flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val selectedServerId: StateFlow<String?> = userViewModel.selectedServerId


    private val _shouldShowAddOrganizationView = MutableStateFlow<Boolean>(false)
    var shouldShowAddOrganizationView: StateFlow<Boolean> = _shouldShowAddOrganizationView


    fun onAddOrganizationButtonTap() {
        _shouldShowAddOrganizationView.value = true
    }

    fun onDismissAddOrganizationButtonTap() {
        _shouldShowAddOrganizationView.value = false
    }

    private val _serverText = MutableStateFlow("")
    val serverText: StateFlow<String> = _serverText

    fun onServerChange(newText: String) {
        _serverText.value = newText
    }

    suspend fun getServerSettings() {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(ServerSettingsRequest(baseUrl = serverText.value))
        when(response) {
            is ApiResult.Success -> {
                userViewModel.addServer(serverText.value, response.value.realmIcon, response.value.realmName)
                _queryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(response.error.message ?: "Error")
            }
        }
    }

    fun logout() {
        val captured = userViewModel.selectedServer.value?.copy() ?: return
        if (userViewModel.selectedServerId.value != captured.id) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                userViewModel.removeServer(captured.id)
            } catch (_: Exception) {
                _queryState.value = QueryState.Error("Не удалось удалить локальные ключи. Повторите выход.")
            }
        }
    }


}
