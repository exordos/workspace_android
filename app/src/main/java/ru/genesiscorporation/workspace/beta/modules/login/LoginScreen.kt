package ru.genesiscorporation.workspace.beta.modules.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AuthLogo
import ru.genesiscorporation.workspace.beta.ui.AuthLogoutButton
import ru.genesiscorporation.workspace.beta.ui.AuthPrimaryButton
import ru.genesiscorporation.workspace.beta.ui.AuthScreen
import ru.genesiscorporation.workspace.beta.ui.AuthTextField
import ru.genesiscorporation.workspace.beta.ui.AuthColors
import ru.genesiscorporation.workspace.beta.ui.authColors

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    navController: NavHostController,
) {
    val loginText by viewModel.loginText.collectAsState()
    val passwordText by viewModel.passwordText.collectAsState()
    val otpText by viewModel.otpText.collectAsState()
    val needsOtp by viewModel.needsOtp.collectAsState()
    val loginFieldError by viewModel.loginFieldError.collectAsState()
    val passwordFieldError by viewModel.passwordFieldError.collectAsState()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val colors = authColors()
    val scope = rememberCoroutineScope()
    val user = UserState.current
    val baseUrl by user.baseUrl.collectAsStateWithLifecycle()
    val loading = state is QueryState.Loading
    val errorMessage = (state as? QueryState.Error)?.message

    BackHandler(enabled = needsOtp) {
        viewModel.onBackFromOtp()
    }

    AuthScreen(
        colors = colors,
        errorMessage = errorMessage,
    ) {
        if (needsOtp) {
            OtpContent(
                otp = otpText,
                enabled = !loading,
                colors = colors,
                onOtpChange = viewModel::onOtpTextChange,
                onConfirm = {
                    scope.launch { viewModel.onLoginClick() }
                },
                onBackToLogin = viewModel::onBackFromOtp,
            )
        } else {
            CredentialsContent(
                login = loginText,
                password = passwordText,
                baseUrl = baseUrl.orEmpty(),
                loginError = loginFieldError,
                passwordError = passwordFieldError,
                enabled = !loading,
                colors = colors,
                onLoginChange = viewModel::onLoginChange,
                onPasswordChange = viewModel::onPasswordChange,
                onLogin = {
                    scope.launch { viewModel.onLoginClick() }
                },
                canSubmit = viewModel.canSubmitCredentials,
                onLogoutOrganization = {
                    scope.launch {
                        user.clearAll()
                        navController.popBackStack()
                    }
                },
            )
        }
    }

    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
private fun CredentialsContent(
    login: String,
    password: String,
    baseUrl: String,
    loginError: String?,
    passwordError: String?,
    enabled: Boolean,
    colors: AuthColors,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    canSubmit: Boolean,
    onLogoutOrganization: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Spacer(Modifier.height(54.dp))
    AuthLogo(colors)
    Text(
        text = organizationTitle(baseUrl),
        color = colors.text,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        text = baseUrl,
        color = colors.mutedText,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 18.dp)
            .height(1.dp)
            .background(colors.divider),
    )

    AuthTextField(
        value = login,
        onValueChange = onLoginChange,
        label = "Имя пользователя или email",
        placeholder = "username или email@example.com",
        colors = colors,
        error = loginError,
        enabled = enabled,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(14.dp))
    AuthTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Пароль",
        placeholder = "Введите пароль",
        colors = colors,
        error = passwordError,
        enabled = enabled,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        onImeAction = {
            if (canSubmit) {
                onLogin()
            }
        },
        trailingContent = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (passwordVisible) R.drawable.ic_visibility_off
                        else R.drawable.ic_visibility,
                    ),
                    contentDescription =
                        if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                    tint = colors.mutedText,
                    modifier = Modifier.size(25.dp),
                )
            }
        },
    )
    Text(
        text = "Не помню пароль",
        color = colors.accent,
        fontSize = 15.sp,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )

    Spacer(Modifier.height(26.dp))
    AuthPrimaryButton(
        text = "Войти",
        enabled = enabled && canSubmit,
        colors = colors,
        onClick = onLogin,
    )
    Spacer(Modifier.height(14.dp))
    AuthLogoutButton(
        colors = colors,
        onClick = onLogoutOrganization,
    )
}

@Composable
private fun OtpContent(
    otp: String,
    enabled: Boolean,
    colors: AuthColors,
    onOtpChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(180)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Spacer(Modifier.height(76.dp))
    Text(
        text = "Введите код",
        color = colors.text,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Введите 6-значный код из\nприложения-аутентификатора",
        color = colors.mutedText,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp),
    )

    Spacer(Modifier.height(64.dp))
    OtpCodeField(
        value = otp,
        onValueChange = onOtpChange,
        enabled = enabled,
        colors = colors,
        focusRequester = focusRequester,
        onDone = {
            if (otp.length == 6) {
                onConfirm()
            }
        },
    )

    Spacer(Modifier.height(40.dp))
    AuthPrimaryButton(
        text = "Подтвердить",
        enabled = enabled && otp.length == 6,
        colors = colors,
        onClick = onConfirm,
    )
    Text(
        text = "Вернуться к логину",
        color = colors.accent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(top = 22.dp)
            .clickable(enabled = enabled, onClick = onBackToLogin)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun OtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    colors: AuthColors,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val activeIndex = value.length.coerceAtMost(5)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) { index ->
                        val digit = value.getOrNull(index)?.toString().orEmpty()
                        val active = focused && index == activeIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .background(colors.field, RoundedCornerShape(10.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (active) colors.accent else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = digit,
                                color = colors.text,
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0.01f),
                ) {
                    innerTextField()
                }
            }
        },
    )
}

private fun organizationTitle(baseUrl: String): String {
    val host = runCatching {
        java.net.URI(baseUrl).host
    }.getOrNull()
    return when (host) {
        "workspace.exordos.com" -> "Exordos Workspace"
        null -> "Workspace"
        else -> host
            .removePrefix("www.")
            .split('.')
            .firstOrNull()
            ?.replaceFirstChar(Char::uppercase)
            ?: "Workspace"
    }
}
