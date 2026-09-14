package ru.genesiscorporation.workspace.beta

import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.ServerRepository
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.TokenPair
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import java.util.UUID
import kotlin.String

class UserViewModel(
    val repo: ServerRepository
):  ViewModel() {

    var userData: UserResponseData? = null
    val selectedServerId: StateFlow<String?> = repo.selectedServerIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streamsOrderIsUnreadFirst: StateFlow<Boolean> = repo.isUnreadFirst
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val servers: StateFlow<List<ServerConfig>> = repo.serversFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedServer: StateFlow<ServerConfig?> = combine(
        servers,
        selectedServerId,
    ) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val organizationName: StateFlow<String?> = selectedServer
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val organizationUrl: StateFlow<String?> = selectedServer
        .map { it?.baseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val organizationImageUrl: StateFlow<String?> = selectedServer
        .map { it?.imageUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val baseUrl: StateFlow<String?> = organizationUrl

    val accessToken: StateFlow<String?> = repo.selectedAccessTokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val refreshToken: StateFlow<String?> = repo.selectedRefreshTokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isAccessTokenLoaded: StateFlow<Boolean> = accessToken
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun getServer(serverId: String): ServerConfig? {
        return repo.getServer(serverId)
    }

    fun selectServer(serverId: String) {
        viewModelScope.launch {
            repo.setSelectedServerId(serverId)
        }
    }
    fun addServer(
        baseUrl: String,
        imageUrl: String,
        name: String,
        tokens: TokenPair? = null,
    ) {
        viewModelScope.launch {
            val config = ServerConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                baseUrl = baseUrl,
                imageUrl = imageUrl,
            )
            repo.addServer(config, tokens)
            repo.setSelectedServerId(config.id)
        }
    }
    fun updateServer(config: ServerConfig, tokens: TokenPair? = null) {
        viewModelScope.launch {
            repo.updateServer(config, tokens)
        }
    }
    fun removeServer(serverId: String) {
        viewModelScope.launch {
            repo.removeServer(serverId)
        }
    }

    fun setStreamsOrderIsUnreadFirst(isUnreadFirst: Boolean) {
        viewModelScope.launch {
            repo.setStreamsOrderIsUnreadFirst(isUnreadFirst)
        }
    }

    fun getTokens(serverId: String): TokenPair? {
        return repo.tokensFor(serverId)
    }

    fun getCurrentTokens(): TokenPair? {
        val serverId = selectedServer.value?.id ?: return null
        return repo.tokensFor(serverId)
    }

    fun setAccessToken(newKey: String) {
        viewModelScope.launch {
            val id = selectedServer.value?.id ?: return@launch
            val refresh = repo.tokensFor(id)?.refreshToken.orEmpty()
            repo.saveTokens(id, TokenPair(newKey, refresh))
        }
    }

    fun setAccessToken(newKey: String, id: String?) {
        viewModelScope.launch {
            val serverId = id ?: selectedServer.value?.id ?: return@launch
            val refresh = repo.tokensFor(serverId)?.refreshToken.orEmpty()
            repo.saveTokens(serverId, TokenPair(newKey, refresh))
        }
    }
    fun setRefreshToken(newKey: String) {
        viewModelScope.launch {
            val id = selectedServer.value?.id ?: return@launch
            val access = repo.tokensFor(id)?.accessToken.orEmpty()
            repo.saveTokens(id, TokenPair(access, newKey))
        }
    }

    fun setRefreshToken(newKey: String, id: String?) {
        viewModelScope.launch {
            val serverId = id ?: selectedServer.value?.id ?: return@launch
            val access = repo.tokensFor(serverId)?.accessToken.orEmpty()
            repo.saveTokens(serverId, TokenPair(access, newKey))
        }
    }

    fun setTokens(access: String, refresh: String) {
        viewModelScope.launch {
            val id = selectedServer.value?.id ?: return@launch
            repo.saveTokens(id, TokenPair(access, refresh))
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll() // clears servers + selected id + email/userId + all secure tokens
            userData = null
        }
    }
}

val UserState = compositionLocalOf<UserViewModel> { error("User state not found") }