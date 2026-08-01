package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsResponseData
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
                if (!isUsableWorkspaceServerSettings(response.value)) {
                    _queryState.value = QueryState.Error(
                        "Сервер вернул небезопасные настройки Workspace",
                    )
                    return
                }
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
                _queryState.value = QueryState.Error(
                    serverDiscoveryErrorMessage(response.error),
                )
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

internal fun isUsableWorkspaceServerSettings(
    settings: ServerSettingsResponseData,
): Boolean {
    val realmName = settings.realmName.trim()
    if (
        realmName.isEmpty() ||
        realmName.length > MAX_REALM_NAME_CHARS ||
        realmName.any(Char::isISOControl)
    ) {
        return false
    }

    val meetUrl = settings.meetUrl.trim()
    if (meetUrl.isNotEmpty() && !isSafeHttpsUrl(meetUrl)) {
        return false
    }

    val realmUrl = settings.realmUrl?.trim().orEmpty()
    if (realmUrl.isNotEmpty() && normalizeWorkspaceServerUrl(realmUrl) == null) {
        return false
    }

    val realmIcon = settings.realmIcon?.trim().orEmpty()
    return realmIcon.isEmpty() || isSafeRealmIcon(realmIcon)
}

internal fun serverDiscoveryErrorMessage(error: ApiError): String =
    when (error.kind) {
        ApiErrorKind.TIMEOUT ->
            "Сервер не ответил вовремя. Повторите попытку"
        ApiErrorKind.NETWORK ->
            "Не удалось найти сервер или установить защищённое соединение"
        ApiErrorKind.NOT_FOUND ->
            "По этому адресу не найден Workspace"
        ApiErrorKind.UNAUTHORIZED,
        ApiErrorKind.FORBIDDEN,
        -> "Публичные настройки Workspace недоступны"
        ApiErrorKind.RATE_LIMITED,
        ApiErrorKind.SERVER,
        -> "Сервер временно недоступен. Повторите попытку позже"
        ApiErrorKind.MALFORMED_RESPONSE ->
            "Сервер вернул некорректный ответ Workspace"
        else -> "Не удалось подключиться. Проверьте адрес сервера"
    }

private fun isSafeHttpsUrl(
    value: String,
): Boolean {
    if (value.length > MAX_SERVER_ASSET_URL_CHARS) return false
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        uri.path.none(Char::isISOControl)
}

private fun isSafeRealmIcon(value: String): Boolean {
    if (
        value.length > MAX_SERVER_ASSET_URL_CHARS ||
        value.any(Char::isISOControl)
    ) {
        return false
    }
    val target = value.removePrefix(REALM_ICON_URL_URN_PREFIX)
    if (target.startsWith('/')) {
        if (target.startsWith("//") || '\\' in target) return false
        val relativeUri = runCatching { URI(target) }.getOrNull() ?: return false
        return relativeUri.isAbsolute.not() &&
            relativeUri.rawQuery == null &&
            relativeUri.rawFragment == null
    }
    return isSafeHttpsUrl(target)
}

private const val MAX_REALM_NAME_CHARS = 256
private const val MAX_SERVER_ASSET_URL_CHARS = 2_048
private const val REALM_ICON_URL_URN_PREFIX = "urn:url:"
