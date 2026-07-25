package ru.genesiscorporation.workspace.beta.modules.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class LoginViewModel(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
) : ViewModel() {

    private val _queryState = MutableStateFlow<QueryState>(QueryState.Idle)
    val queryState: StateFlow<QueryState> = _queryState

    private val _loginText = MutableStateFlow("")
    val loginText: StateFlow<String> = _loginText

    private val _passwordText = MutableStateFlow("")
    val passwordText: StateFlow<String> = _passwordText

    private val _otpText = MutableStateFlow("")
    val otpText: StateFlow<String> = _otpText

    private val _needsOtp = MutableStateFlow(false)
    val needsOtp: StateFlow<Boolean> = _needsOtp

    private val _loginFieldError = MutableStateFlow<String?>(null)
    val loginFieldError: StateFlow<String?> = _loginFieldError

    private val _passwordFieldError = MutableStateFlow<String?>(null)
    val passwordFieldError: StateFlow<String?> = _passwordFieldError

    val canSubmitCredentials: Boolean
        get() = _loginText.value.isNotBlank() && _passwordText.value.isNotBlank()

    val canSubmitOtp: Boolean
        get() = _otpText.value.length == OTP_LENGTH

    fun onLoginChange(newText: String) {
        _loginText.value = newText
        _loginFieldError.value = null
        clearQueryError()
    }

    fun onPasswordChange(newText: String) {
        _passwordText.value = newText
        _passwordFieldError.value = null
        clearQueryError()
    }

    fun onOtpTextChange(newText: String) {
        _otpText.value = newText.filter(Char::isDigit).take(OTP_LENGTH)
        clearQueryError()
    }

    fun onBackFromOtp() {
        _needsOtp.value = false
        _otpText.value = ""
        _queryState.value = QueryState.Idle
    }

    suspend fun onLoginClick() {
        if (_needsOtp.value) {
            if (!canSubmitOtp) {
                _queryState.value = QueryState.Error("Введите шестизначный код OTP")
                return
            }
        } else if (!validateCredentials()) {
            return
        }

        _queryState.value = QueryState.Loading
        val otp = _otpText.value
        when (
            val response = client.performRequest(
                LoginRequest(
                    username = _loginText.value.trim(),
                    password = _passwordText.value,
                    otp = otp,
                ),
            )
        ) {
            is ApiResult.Success -> {
                val userResponse = response.value
                userViewModel.setAccessToken(userResponse.accessToken)
                userViewModel.setRefreshToken(userResponse.refreshToken)
                client.baseAccessToken = userResponse.accessToken
                _queryState.value = QueryState.Success
            }

            is ApiResult.Error -> {
                val error = response.error
                if (
                    LoginErrorClassifier.isOtpChallenge(
                        statusCode = error.code,
                        responseBody = error.errorMessage,
                        otpProvided = otp.isNotBlank(),
                    )
                ) {
                    _needsOtp.value = true
                    _otpText.value = ""
                    _queryState.value = QueryState.Idle
                } else {
                    _queryState.value = QueryState.Error(
                        LoginErrorClassifier.publicMessage(
                            statusCode = error.code,
                            responseBody = error.errorMessage,
                            otpProvided = otp.isNotBlank(),
                        ),
                    )
                }
            }
        }
    }

    private fun validateCredentials(): Boolean {
        _loginFieldError.value =
            if (_loginText.value.isBlank()) "Введите имя пользователя или email" else null
        _passwordFieldError.value =
            if (_passwordText.value.isBlank()) "Введите пароль" else null
        return _loginFieldError.value == null && _passwordFieldError.value == null
    }

    private fun clearQueryError() {
        if (_queryState.value is QueryState.Error) {
            _queryState.value = QueryState.Idle
        }
    }

    private companion object {
        const val OTP_LENGTH = 6
    }
}
