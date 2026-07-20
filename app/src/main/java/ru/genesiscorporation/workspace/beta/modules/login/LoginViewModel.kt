package ru.genesiscorporation.workspace.beta.modules.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OidcCompleteRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OidcLoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState


class LoginViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel
): ViewModel() {

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState
    private val _webUrl = MutableStateFlow<String?>(null)
    val webUrl: StateFlow<String?> = _webUrl.asStateFlow()

    fun setWebUrl(url: String?) {
        _webUrl.value = url
    }

    private val _loginText = MutableStateFlow("")
    val loginText: StateFlow<String> = _loginText

    fun onLoginChange(newText: String) {
        _loginText.value = newText
        _needsOtp.value = false
        _otpText.value = ""
    }

    private val _passwordText = MutableStateFlow("")
    val passwordText: StateFlow<String> = _passwordText

    fun onPasswordChange(newText: String) {
        _passwordText.value = newText
        _needsOtp.value = false
        _otpText.value = ""
    }

    private val _needsOtp = MutableStateFlow(false)
    val needsOtp: StateFlow<Boolean> = _needsOtp


    private val _otpText = MutableStateFlow("")
    val otpText: StateFlow<String> = _otpText

    fun onOtpTextChange(newText: String) {
        _otpText.value = newText
    }

    suspend fun onLoginClick() {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(LoginRequest(loginText.value, passwordText.value, otpText.value))
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
                userViewModel.setAccessToken(userResponse.accessToken)
                userViewModel.setRefreshToken(userResponse.refreshToken)
                client.baseAccessToken = userResponse.accessToken
            }
            is ApiResult.Error -> {
                if (response.error.code == "401") {
                    _needsOtp.value = true
                    _queryState.value = QueryState.Idle
                } else {
                    _queryState.value = QueryState.Error(response.error.message ?: "Error")
                }
            }
        }
    }
}