package ru.genesiscorporation.workspace.beta.modules.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    }

    private val _passwordText = MutableStateFlow("")
    val passwordText: StateFlow<String> = _passwordText

    fun onPasswordChange(newText: String) {
        _passwordText.value = newText
    }

    suspend fun onOidcLoginClick(urlSuffix: String) {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(OidcLoginRequest(urlSuffix))
        when(response) {
            is ApiResult.Success -> {
                setWebUrl(response.value)
                _queryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(response.error.message ?: "Error")
            }
        }
    }

    suspend fun onOidcPathCapture(path: String) {
        if (!path.isEmpty()) {
            _queryState.value = QueryState.Loading
            val response = client.performRequest(OidcCompleteRequest(path))
            when(response) {
                is ApiResult.Success -> {
                    userViewModel.setApiKey(response.value)
                    client.baseApiKey = response.value
                    loadUserInfo()
                }
                is ApiResult.Error -> {
                    _queryState.value = QueryState.Error(response.error.message ?: "Error")
                }
            }
        }
    }

    suspend fun onLoginClick() {
        _queryState.value = QueryState.Loading
        val response = client.performRequest(LoginRequest(loginText.value, passwordText.value))
        when(response) {
            is ApiResult.Success -> {
                val userResponse = response.value
                userViewModel.setApiKey(userResponse.api_key)
                client.baseApiKey = userResponse.api_key
                userViewModel.setEmail(userResponse.email)
                client.baseEmail = userResponse.email
                loadUserInfo()
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(response.error.message ?: "Error")
            }
        }
    }

    suspend fun loadUserInfo() {
        val response = client.performRequest(OwnUserRequest())
        when(response) {
            is ApiResult.Success -> {
                userViewModel.userData = response.value
                userViewModel.setEmail(response.value.delivery_email)
                userViewModel.setUserId("${response.value.user_id}")
                _queryState.value = QueryState.Success
            }

            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(response.error.message ?: "Error")
            }
        }
    }
}