package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.ui.AuthPrimaryButton
import ru.genesiscorporation.workspace.beta.ui.AuthScreen
import ru.genesiscorporation.workspace.beta.ui.AuthTextField
import ru.genesiscorporation.workspace.beta.ui.authColors

private const val PUBLIC_SERVER_URL = "https://workspace.exordos.com"

@Composable
fun ChooseServerScreen(
    viewModel: ChooseServerViewModel,
    navController: NavHostController,
) {
    val serverText by viewModel.serverText.collectAsState()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = UserState.current
    val colors = authColors()

    LaunchedEffect(Unit) {
        user.clearAll()
    }
    LaunchedEffect(state) {
        if (state is QueryState.Success) {
            navController.navigate(LoginFlow.Login)
            viewModel.returnToIdleState()
        }
    }

    val errorMessage = (state as? QueryState.Error)?.message
    AuthScreen(
        colors = colors,
        errorMessage = errorMessage,
    ) {
        Spacer(Modifier.height(if (errorMessage == null) 76.dp else 32.dp))
        Text(
            text = "Добро пожаловать",
            color = colors.text,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Введите адрес вашей организации,\nчтобы продолжить",
            color = colors.mutedText,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(78.dp))
        AuthTextField(
            value = serverText,
            onValueChange = viewModel::onServerChange,
            label = "Адрес организации",
            placeholder = "https://example.com",
            colors = colors,
            imeAction = ImeAction.Done,
            onImeAction = {
                if (viewModel.canSubmit) {
                    scope.launch { viewModel.getServerSettings() }
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        AuthPrimaryButton(
            text = "Войти",
            enabled = viewModel.canSubmit && state !is QueryState.Loading,
            colors = colors,
            onClick = {
                scope.launch { viewModel.getServerSettings() }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.divider),
            )
            Text(
                text = "ИЛИ",
                color = colors.mutedText,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.divider),
            )
        }

        Text(
            text = "Вы можете подключиться к нашему\nпубличному серверу:",
            color = colors.text,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(colors.field, RoundedCornerShape(10.dp))
                .clickable(enabled = state !is QueryState.Loading) {
                    viewModel.onServerChange(PUBLIC_SERVER_URL)
                    scope.launch { viewModel.getServerSettings() }
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.logoBackground, RoundedCornerShape(9.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = "Exordos public",
                color = colors.text,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }

    if (state is QueryState.Loading) {
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
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
