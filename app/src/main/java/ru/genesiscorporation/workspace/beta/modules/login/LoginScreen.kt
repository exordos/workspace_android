package ru.genesiscorporation.workspace.beta.modules.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AuthColors
import ru.genesiscorporation.workspace.beta.ui.AuthLoadingOverlay
import ru.genesiscorporation.workspace.beta.ui.AuthLogo
import ru.genesiscorporation.workspace.beta.ui.AuthLogoutButton
import ru.genesiscorporation.workspace.beta.ui.AuthPrimaryButton
import ru.genesiscorporation.workspace.beta.ui.AuthScreen
import ru.genesiscorporation.workspace.beta.ui.AuthTextField
import ru.genesiscorporation.workspace.beta.ui.authColors

@Composable
fun LoginScreen(viewModel: LoginViewModel, navController: NavHostController) {
    val login by viewModel.loginText.collectAsState()
    val password by viewModel.passwordText.collectAsState()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val organizationName by viewModel.userViewModel.organizationName.collectAsState()
    val organizationUrl by viewModel.userViewModel.organizationUrl.collectAsState()
    val organizationImage by viewModel.userViewModel.organizationImageUrl.collectAsState()
    val servers by viewModel.userViewModel.servers.collectAsState()
    val selectedServer by viewModel.userViewModel.selectedServer.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = authColors()
    val loading = state is QueryState.Loading

    LaunchedEffect(state) {
        when (val current = state) {
            is QueryState.Error -> {
                if (current.message == "needs_otp") {
                    viewModel.idleQueryState()
                    navController.navigate(LoginFlow.Otp(login, password, viewModel.isFirstOrganization))
                } else {
                    Toast.makeText(context, current.message, Toast.LENGTH_SHORT).show()
                }
            }
            QueryState.Success -> navController.navigate(LoginFlow.Projects(viewModel.isFirstOrganization))
            else -> Unit
        }
    }

    AuthScreen(colors = colors) {
        CredentialsContent(
            login = login,
            password = password,
            organizationName = organizationName ?: "Название организации",
            organizationUrl = organizationUrl.orEmpty(),
            organizationImageUrl = UrnParser.parseUrl(organizationImage, ""),
            loading = loading,
            colors = colors,
            onLoginChange = viewModel::onLoginChange,
            onPasswordChange = viewModel::onPasswordChange,
            onLogin = { scope.launch { viewModel.onLoginClick() } },
            onLogout = {
                scope.launch {
                    viewModel.userViewModel.removeCurrentServer()
                    navController.popBackStack()
                }
            },
        )
        if (selectedServer?.needsToRelogin == true && servers.size > 1) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            Text(
                text = "Ваши организации",
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
            )
            Organizations(viewModel)
        }
    }
    if (loading) AuthLoadingOverlay(colors)
}

@Composable
internal fun CredentialsContent(
    login: String,
    password: String,
    organizationName: String,
    organizationUrl: String,
    organizationImageUrl: String?,
    loading: Boolean,
    colors: AuthColors,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val canSubmit = !loading && login.isNotBlank() && password.isNotBlank()
    Spacer(Modifier.height(54.dp))
    AuthLogo(colors, imageUrl = organizationImageUrl, contentDescription = organizationName)
    Text(
        text = organizationName,
        color = colors.text,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        text = organizationUrl,
        color = colors.mutedText,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
    Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 18.dp).height(1.dp).background(colors.divider))
    AuthTextField(
        value = login,
        onValueChange = onLoginChange,
        label = "Имя пользователя или email",
        placeholder = "username или email@example.com",
        colors = colors,
        enabled = !loading,
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
        enabled = !loading,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        onImeAction = { if (canSubmit) onLogin() },
        trailingContent = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = !loading,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    painter = painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                    tint = colors.mutedText,
                    modifier = Modifier.size(25.dp),
                )
            }
        },
    )
    Spacer(Modifier.height(26.dp))
    AuthPrimaryButton(text = "Войти", enabled = canSubmit, colors = colors, onClick = onLogin)
    Spacer(Modifier.height(14.dp))
    AuthLogoutButton(colors = colors, onClick = onLogout, enabled = !loading)
}

@Composable
fun Organizations(viewModel: LoginViewModel) {
    val servers by viewModel.userViewModel.servers.collectAsState()
    val selectedServerId by viewModel.userViewModel.selectedServerId.collectAsState()
    val colors = authColors()
    Column(modifier = Modifier.fillMaxWidth()) {
        servers.filterNot { it.id == selectedServerId }.forEach { server ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .background(colors.field, RoundedCornerShape(10.dp))
                    .clickable { viewModel.userViewModel.selectServer(server.id) }
                    .padding(12.dp),
            ) {
                AsyncImage(
                    model = UrnParser.parseUrl(server.imageUrl, ""),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(server.name, color = colors.text, fontSize = 17.sp)
                    if (server.needsToRelogin) {
                        Text("Сессия истекла", color = colors.error, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
