package ru.genesiscorporation.workspace.beta.modules.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class OtpViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    val login: String,
    val password: String
): ViewModel() {

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState
    private val _otpText = MutableStateFlow("")
    val otpText: StateFlow<String> = _otpText

    fun onOtpTextChange(newText: String) {
        if (newText.length <= 6) {
            _otpText.value = newText
        }
        if (_otpText.value.length == 6) {
            viewModelScope.launch {
                login()
            }
        }
    }

    fun symbolForIndex(index: Int): String {
        return _otpText.value.getOrNull(index)?.toString() ?: " "
    }

    fun isIndexActive(index: Int): Boolean {
        return _otpText.value.length == index
    }

    suspend fun login() {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(LoginRequest(login, password, otpText.value))
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
                userViewModel.setAccessToken(userResponse.accessToken)
                userViewModel.setRefreshToken(userResponse.refreshToken)
                client.baseAccessToken = userResponse.accessToken
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(response.error.message ?: "Введён неверный код")
            }
        }
    }
}