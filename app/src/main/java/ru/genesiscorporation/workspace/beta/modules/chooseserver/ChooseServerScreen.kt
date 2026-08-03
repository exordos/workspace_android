package ru.genesiscorporation.workspace.beta.modules.chooseserver

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChooseServerScreen(
    viewModel: ChooseServerViewModel,
    navController: NavHostController
) {
    val serverText by viewModel.serverText.collectAsState()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user = UserState.current
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is QueryState.Idle) {
            user.clearAll()
        }
        if (state is QueryState.Success) {
            navController.navigate(LoginFlow.Login)
            viewModel.returnToIdleState()
        }
        if (state is QueryState.Error) {
            Toast
                .makeText(context, (state as QueryState.Error).message, Toast.LENGTH_SHORT)
                .show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()
        .background(LocalWorkspaceColorsPalette.current.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Добро пожаловать",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 16.sp,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Введите адрес вашей организации, \nчтобы продолжить",
                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 64.dp )
            )
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "Адрес организации",
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.searchBackground,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    BasicTextField(
                        value = serverText,
                        onValueChange = viewModel::onServerChange,
                        textStyle = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                        ),
                        cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.getServerSettings()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Войти")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f)
                        .padding(start = 20.dp),
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider,
                )
                Text(
                    text = "или",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f)
                        .padding(end = 20.dp),
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider,
                )
            }
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "Вы можете подключиться к нашему публичному серверу:",
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                Button(
                    onClick = {
                        viewModel.onServerChange("https://workspace.exordos.com")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalWorkspaceColorsPalette.current.searchBackground,
                        contentColor = LocalWorkspaceColorsPalette.current.textHeaders
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.icon),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Exordos public")
                    }
                }
            }
        }
    }
    if (state is QueryState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {  },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

