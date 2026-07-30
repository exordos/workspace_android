package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import java.net.URI
import kotlinx.coroutines.CancellationException

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
        val normalizedServer = normalizeWorkspaceServerUrl(_serverText.value)
        if (normalizedServer == null) {
            _queryState.value = QueryState.Error(
                "Введите корректный HTTPS-адрес организации",
            )
            return
        }
        _serverText.value = normalizedServer
        _queryState.value = QueryState.Loading
        val response = client.performRequest(ServerSettingsRequest(baseUrl = normalizedServer))
        when(response) {
            is ApiResult.Success -> {
                try {
                    userViewModel.addBaseUrlAndWait(normalizedServer)
                    _queryState.value = QueryState.Success
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    _queryState.value = QueryState.Error(
                        "Не удалось сохранить организацию на устройстве",
                    )
                }
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error("Не удалось подключиться. Проверьте адрес сервера")
            }
        }
    }

}

internal fun normalizeWorkspaceServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (
        !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null ||
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        !uri.path.isNullOrBlank()
    ) {
        return null
    }
    return URI(
        "https",
        null,
        uri.host.lowercase(),
        uri.port,
        null,
        null,
        null,
    ).toString()
}
