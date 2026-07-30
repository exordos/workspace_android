package ru.genesiscorporation.workspace.beta

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferences
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferencesRepository
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModel(
    val repo: ApiKeyRepository,
    val conversationStateStore: ConversationStateStore,
    private val uiPreferencesRepository: WorkspaceUiPreferencesRepository,
):  ViewModel() {

    private val _userData = MutableStateFlow<UserResponseData?>(null)
    val userDataFlow: StateFlow<UserResponseData?> = _userData.asStateFlow()
    var userData: UserResponseData?
        get() = _userData.value
        set(value) {
            _userData.value = value
        }

    private val _isAccessTokenLoaded = MutableStateFlow(false)
    val isAccessTokenLoaded: StateFlow<Boolean> = _isAccessTokenLoaded.asStateFlow()
    private val _initializationError = MutableStateFlow(false)
    val initializationError: StateFlow<Boolean> = _initializationError.asStateFlow()

    private val initialized = _isAccessTokenLoaded.filter { it }

    val accessToken: StateFlow<String?> = initialized
        .flatMapLatest { repo.accessTokenFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val refreshToken: StateFlow<String?> = initialized
        .flatMapLatest { repo.refreshTokenFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val projectId: StateFlow<String?> = initialized
        .flatMapLatest { repo.projectIdFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val email: StateFlow<String?> = initialized
        .flatMapLatest { repo.emailFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val baseUrl: StateFlow<String?> = initialized
        .flatMapLatest { repo.baseUrlFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val userId: StateFlow<String?> = initialized
        .flatMapLatest { repo.userIdFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val accounts: StateFlow<List<WorkspaceAccount>> = initialized
        .flatMapLatest { repo.accountsFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeAccountId: StateFlow<String?> = initialized
        .flatMapLatest { repo.activeAccountIdFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val activeAccount: StateFlow<WorkspaceAccount?> = initialized
        .flatMapLatest { repo.activeAccountFlow }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val ownedUiPreferences: StateFlow<OwnedWorkspaceUiPreferences> =
        activeAccountId
            .flatMapLatest { accountId ->
                if (accountId == null) {
                    flowOf(
                        OwnedWorkspaceUiPreferences(
                            ownerKey = null,
                            preferences = WorkspaceUiPreferences(),
                        ),
                    )
                } else {
                    uiPreferencesRepository
                        .preferencesFlow(accountId)
                        .map { preferences ->
                            OwnedWorkspaceUiPreferences(
                                ownerKey = accountId,
                                preferences = preferences,
                            )
                        }
                        .onStart {
                            emit(
                                OwnedWorkspaceUiPreferences(
                                    ownerKey = null,
                                    preferences = WorkspaceUiPreferences(),
                                ),
                            )
                        }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                OwnedWorkspaceUiPreferences(
                    ownerKey = null,
                    preferences = WorkspaceUiPreferences(),
                ),
            )
    val uiPreferences: StateFlow<WorkspaceUiPreferences> =
        ownedUiPreferences
            .map { owned -> owned.preferences }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                WorkspaceUiPreferences(),
            )
    val uiPreferencesOwnerKey: StateFlow<String?> =
        ownedUiPreferences
            .map { owned -> owned.ownerKey }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            try {
                repo.migrateLegacyCredentials()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _initializationError.value = true
            } finally {
                _isAccessTokenLoaded.value = true
            }
        }
        viewModelScope.launch {
            initialized
                .flatMapLatest { repo.activeAccountIdFlow }
                .collectLatest {
                    _userData.value = null
                }
        }
    }

    suspend fun addBaseUrlAndWait(newBaseUrl: String) {
        repo.addBaseUrl(newBaseUrl)
    }

    suspend fun beginAddAccountAndWait() {
        _userData.value = null
        repo.beginAddAccount()
    }

    suspend fun cancelPendingLoginAndWait() {
        _userData.value = null
        repo.removePendingBaseUrl()
    }

    suspend fun switchAccountAndWait(accountId: String): Boolean {
        _userData.value = null
        return repo.activateAccount(accountId)
    }

    suspend fun updateUiPreferences(
        transform: (WorkspaceUiPreferences) -> WorkspaceUiPreferences,
    ): Boolean {
        val accountId = activeAccountId.value ?: return false
        uiPreferencesRepository.update(accountId, transform)
        return true
    }

    fun removeActiveAccount() {
        viewModelScope.launch {
            _userData.value = null
            repo.removeActiveAccount()
        }
    }

    suspend fun removeActiveAccountAndWait() {
        _userData.value = null
        repo.removeActiveAccount()
    }

    suspend fun removeActiveAccountIfOwnerAndWait(ownerKey: String): Boolean {
        val removed = repo.removeActiveAccountIfOwner(ownerKey)
        if (removed) {
            _userData.value = null
        }
        return removed
    }
}

val LocalUserState = compositionLocalOf<UserViewModel> {
    error("User state not found")
}

private data class OwnedWorkspaceUiPreferences(
    val ownerKey: String?,
    val preferences: WorkspaceUiPreferences,
)
