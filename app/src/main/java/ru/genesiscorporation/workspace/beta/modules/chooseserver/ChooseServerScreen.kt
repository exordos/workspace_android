package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChooseServerScreen(
    viewModel: ChooseServerViewModel,
    navController: NavHostController
) {
    val serverText by viewModel.serverText.collectAsState()
    val scope = rememberCoroutineScope()
    val user = UserState.current

    Box(modifier = Modifier.fillMaxSize()
        .background(LocalWorkspaceColorsPalette.current.background),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(horizontal = 16.dp)
                .background(LocalWorkspaceColorsPalette.current.surface)

        ) {
            Text(
                "Добавить организацию",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(20.dp)
            )
            Text(
                "Укажите ссылку на организацию, чтобы добавить её в список",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 12.dp )
            )
            Text(
                "Ссылка на организацию",
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 14.sp,
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
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(LocalWorkspaceColorsPalette.current.searchBackground,
                        RoundedCornerShape(8.dp))
            ) {
                BasicTextField(
                    value = serverText,
                    onValueChange = viewModel::onServerChange,
                    textStyle = TextStyle(
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Или можете подключиться к нашему публичному серверу:",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(20.dp)
            )
            Button(
                onClick = {
                    viewModel.onServerChange("https://workspace.example.com")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.textHeaders     // green text
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Text("Genesis core public")
                }
            }
            Button(
                onClick = {
//                    scope.launch {
//                        viewModel.getServerSettings()
                        user.setBaseUrl(viewModel.serverText.value)
                        navController.navigate(LoginFlow.Login)
//                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalWorkspaceColorsPalette.current.primary,
                    contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Добавить")
            }
        }
    }
}