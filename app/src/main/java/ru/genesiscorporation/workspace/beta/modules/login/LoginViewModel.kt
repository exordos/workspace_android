package ru.genesiscorporation.workspace.beta.modules.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest

class LoginViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel
): ViewModel() {


    private val _loginText = MutableStateFlow("")
    val loginText: StateFlow<String> = _loginText

    fun onLoginChange(newText: String) {
        _loginText.value = newText
    }

    private val _passwordText = MutableStateFlow("")
    val passwordText: StateFlow<String> = _passwordText

    fun onPasswordChange(newText: String) {
        _passwordText.value = newText
    }

    suspend fun onLoginClick() {
        val response = client.performRequest(LoginRequest(loginText.value, passwordText.value))
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
                userViewModel.setApiKey(userResponse.api_key)
                userViewModel.setEmail(userResponse.email)
                userViewModel.setUserId("${userResponse.user_id}")
            }
            is ApiResult.Error -> {

            }
        }
    }
}