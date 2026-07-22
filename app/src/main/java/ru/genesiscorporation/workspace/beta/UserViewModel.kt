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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import kotlin.String

class UserViewModel(
    val repo: ApiKeyRepository
):  ViewModel() {

    var userData: UserResponseData? = null

    val isAccessTokenLoaded: StateFlow<Boolean> = repo.accessTokenFlow
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val accessToken: StateFlow<String?> = repo.accessTokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val refreshToken: StateFlow<String?> = repo.refreshTokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val email: StateFlow<String?> = repo.emailFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val baseUrl: StateFlow<String?> = repo.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val userId: StateFlow<String?> = repo.userIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun setAccessToken(newKey: String) {
        viewModelScope.launch {
            repo.saveAccessToken(newKey)
        }
    }

    fun setRefreshToken(newKey: String) {
        viewModelScope.launch {
            repo.saveRefreshToken(newKey)
        }
    }

    fun setEmail(newEmail: String) {
        viewModelScope.launch {
            repo.saveEmail(newEmail)
        }
    }

    fun addBaseUrl(newBaseUrl: String) {
        viewModelScope.launch {
            repo.addBaseUrl(newBaseUrl)
        }
    }
    fun setUserId(newUserId: String) {
        viewModelScope.launch {
            repo.saveUserId(newUserId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
        }
    }
}

val UserState = compositionLocalOf<UserViewModel> { error("User state not found") }