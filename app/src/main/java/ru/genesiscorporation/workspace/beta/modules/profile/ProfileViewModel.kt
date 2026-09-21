package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteFcmTokenRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class ProfileViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {
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

    val repo = eventsRepo.value ?: error("Cannot get current event repository")


    val user: StateFlow<UserResponseData?> = repo.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
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

    fun currentPushToken(): String? {
        return repo.pushId
    }
    fun logout() {
        val token = currentPushToken()
        if (token != null) {
            viewModelScope.launch {
                deleteToken("workspace:android:$token")
            }
        }
        userViewModel.removeCurrentServer()
    }

    suspend fun deleteToken(token: String) {
        client.performRequest(DeleteFcmTokenRequest(token))
    }
}
