package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest

sealed interface QueryState {
    object Idle : QueryState
    object Loading : QueryState
    object Success : QueryState
    data class Error(val message: String) : QueryState
}
class ChooseServerViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel
): ViewModel() {

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState

    private val _serverText = MutableStateFlow("")
    val serverText: StateFlow<String> = _serverText
    val canSubmit: Boolean
        get() = _serverText.value.isNotBlank()

    fun onServerChange(newText: String) {
        _serverText.value = newText
        if (_queryState.value is QueryState.Error) {
            _queryState.value = QueryState.Idle
        }
    }

    fun returnToIdleState() {
        _queryState.value = QueryState.Idle
    }

    suspend fun getServerSettings() {
        val normalizedServer = ensureHttpsPrefix(_serverText.value)
        if (normalizedServer.isBlank()) {
            _queryState.value = QueryState.Error("Введите адрес организации")
            return
        }
        _serverText.value = normalizedServer
        _queryState.value = QueryState.Loading
        val response = client.performRequest(ServerSettingsRequest(baseUrl = normalizedServer))
        when(response) {
            is ApiResult.Success -> {
                userViewModel.addBaseUrl(normalizedServer)
                _queryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error("Не удалось подключиться. Проверьте адрес сервера")
            }
        }
    }

    private fun ensureHttpsPrefix(input: String): String {
        val trimmed = input.trim()

        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }
}
