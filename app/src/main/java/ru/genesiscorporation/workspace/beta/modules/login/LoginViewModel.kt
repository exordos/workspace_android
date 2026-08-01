package ru.genesiscorporation.workspace.beta.modules.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.LoginRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.IamProjectsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TokenRefreshRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.WorkspaceProject
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseWorkspaceProjects
import ru.genesiscorporation.workspace.beta.data.remote.dto.workspaceProjectScope
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState

class LoginViewModel(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val loginProcessState: LoginProcessState,
) : ViewModel() {

    private val restoredProcessState = loginProcessState.restore()

    private val _queryState = MutableStateFlow<QueryState>(
        if (restoredProcessState.interrupted) {
            QueryState.Error("Вход был прерван. Введите пароль ещё раз")
        } else {
            QueryState.Idle
        },
    )
    val queryState: StateFlow<QueryState> = _queryState

    private val _loginText = MutableStateFlow(restoredProcessState.login)
    val loginText: StateFlow<String> = _loginText

    private val _passwordText = MutableStateFlow("")
    val passwordText: StateFlow<String> = _passwordText

    private val _otpText = MutableStateFlow("")
    val otpText: StateFlow<String> = _otpText

    private val _needsOtp = MutableStateFlow(false)
    val needsOtp: StateFlow<Boolean> = _needsOtp

    private val _needsProject = MutableStateFlow(false)
    val needsProject: StateFlow<Boolean> = _needsProject

    private val _projects = MutableStateFlow<List<WorkspaceProject>>(emptyList())
    val projects: StateFlow<List<WorkspaceProject>> = _projects

    private val _selectedProjectId = MutableStateFlow("")
    val selectedProjectId: StateFlow<String> = _selectedProjectId

    private var pendingRefreshToken: String? = null
    private var pendingUserUuid: String? = null

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
        loginProcessState.updateLogin(newText)
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
        loginProcessState.markPhase(LoginPhase.CREDENTIALS)
    }

    fun onProjectSelected(projectId: String) {
        if (_projects.value.any { it.uuid == projectId }) {
            _selectedProjectId.value = projectId
            clearQueryError()
        }
    }

    fun onBackFromProject() {
        pendingRefreshToken = null
        pendingUserUuid = null
        _projects.value = emptyList()
        _selectedProjectId.value = ""
        _needsProject.value = false
        _passwordText.value = ""
        _queryState.value = QueryState.Idle
        loginProcessState.markPhase(LoginPhase.CREDENTIALS)
    }

    fun onFlowCancelled() {
        pendingRefreshToken = null
        pendingUserUuid = null
        _passwordText.value = ""
        _otpText.value = ""
        loginProcessState.clear()
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

        loginProcessState.markPhase(
            if (_needsOtp.value) LoginPhase.OTP else LoginPhase.AUTHENTICATING,
        )
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
                val refreshToken = userResponse.refreshToken
                if (refreshToken.isNullOrBlank()) {
                    _queryState.value = QueryState.Error(
                        "Сервер не вернул refresh token",
                    )
                    loginProcessState.markPhase(LoginPhase.CREDENTIALS)
                    return
                }
                val userUuid = userUuidFromAccessToken(userResponse.accessToken)
                if (userUuid == null) {
                    _queryState.value = QueryState.Error(
                        "Токен не содержит идентификатор пользователя",
                    )
                    loginProcessState.markPhase(LoginPhase.CREDENTIALS)
                    return
                }
                when (
                    val projectsResponse = client.performRequest(
                        IamProjectsRequest(userResponse.accessToken),
                    )
                ) {
                    is ApiResult.Success -> {
                        val availableProjects = runCatching {
                            parseWorkspaceProjects(projectsResponse.value)
                        }.getOrElse {
                            _queryState.value = QueryState.Error(
                                "Сервер вернул некорректный список проектов",
                            )
                            loginProcessState.markPhase(LoginPhase.CREDENTIALS)
                            return
                        }
                        if (availableProjects.isEmpty()) {
                            _queryState.value = QueryState.Error(
                                "Для этой учётной записи нет доступных проектов",
                            )
                            loginProcessState.markPhase(LoginPhase.CREDENTIALS)
                            return
                        }
                        pendingRefreshToken = refreshToken
                        pendingUserUuid = userUuid
                        _projects.value = availableProjects
                        _selectedProjectId.value = availableProjects.first().uuid
                        _needsOtp.value = false
                        _needsProject.value = true
                        _passwordText.value = ""
                        _otpText.value = ""
                        _queryState.value = QueryState.Idle
                        loginProcessState.markPhase(LoginPhase.PROJECT)
                    }
                    is ApiResult.Error -> {
                        _queryState.value = QueryState.Error(
                            "Не удалось загрузить доступные проекты",
                        )
                        loginProcessState.markPhase(LoginPhase.CREDENTIALS)
                    }
                }
            }

            is ApiResult.Error -> {
                val error = response.error
                if (
                    LoginErrorClassifier.isOtpChallenge(
                        httpStatus = error.httpStatus,
                        errorCode = error.code,
                        safeMessage = error.errorMessage,
                        otpProvided = otp.isNotBlank(),
                    )
                ) {
                    _needsOtp.value = true
                    _otpText.value = ""
                    _queryState.value = QueryState.Idle
                    loginProcessState.markPhase(LoginPhase.OTP)
                } else {
                    _queryState.value = QueryState.Error(
                        LoginErrorClassifier.publicMessage(
                            httpStatus = error.httpStatus,
                            errorCode = error.code,
                            safeMessage = error.errorMessage,
                            otpProvided = otp.isNotBlank(),
                        ),
                    )
                    loginProcessState.markPhase(
                        if (_needsOtp.value) LoginPhase.OTP else LoginPhase.CREDENTIALS,
                    )
                }
            }
        }
    }

    suspend fun onProjectConfirm() {
        val projectId = _selectedProjectId.value
        val refreshToken = pendingRefreshToken
        val expectedUserUuid = pendingUserUuid
        val selectedProject = _projects.value.firstOrNull { it.uuid == projectId }
        if (
            projectId.isBlank() ||
            refreshToken.isNullOrBlank() ||
            expectedUserUuid.isNullOrBlank() ||
            selectedProject == null
        ) {
            _queryState.value = QueryState.Error("Выберите проект")
            return
        }

        _queryState.value = QueryState.Loading
        when (
            val response = client.performRequest(
                TokenRefreshRequest(
                    refreshToken = refreshToken,
                    scope = workspaceProjectScope(projectId),
                ),
            )
        ) {
            is ApiResult.Success -> {
                val finalUserUuid = userUuidFromAccessToken(response.value.accessToken)
                if (finalUserUuid == null || finalUserUuid != expectedUserUuid) {
                    _queryState.value = QueryState.Error(
                        "Сервер вернул токен другого пользователя",
                    )
                    return
                }
                val tokenProjectId = projectUuidFromAccessToken(response.value.accessToken)
                if (tokenProjectId != null && tokenProjectId != projectId) {
                    _queryState.value = QueryState.Error(
                        "Сервер вернул токен другого проекта",
                    )
                    return
                }
                val finalRefreshToken = response.value.refreshToken ?: refreshToken
                try {
                    userViewModel.repo.saveSessionCredentials(
                        projectId = projectId,
                        projectName = selectedProject.name,
                        organizationName = selectedProject.organizationName,
                        userId = finalUserUuid,
                        login = _loginText.value.trim(),
                        accessToken = response.value.accessToken,
                        refreshToken = finalRefreshToken,
                    )
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    _queryState.value = QueryState.Error(
                        "Не удалось безопасно сохранить сессию",
                    )
                    return
                }
                pendingRefreshToken = null
                pendingUserUuid = null
                loginProcessState.clear()
                _queryState.value = QueryState.Success
            }
            is ApiResult.Error -> {
                _queryState.value = QueryState.Error(
                    "Не удалось открыть выбранный проект",
                )
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
