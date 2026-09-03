package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.ProfileFlow
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    navController: NavHostController
) {
    val currentUser = viewModel.user.collectAsState()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text("Мой профиль")
                },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LocalWorkspaceColorsPalette.current.background)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp).weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                val userData = currentUser.value
                if (userData != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                    ) {
                        Avatar(
                            userData.avatar,
                            viewModel.client.userViewModel.baseUrl.value ?: "",
                            viewModel.client.authHeaders(),
                            null,
                            userData.displayableName(),
                            Modifier.padding(end = 8.dp).size(64.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${userData.firstName} ${userData.lastName}",
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = 20.sp,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "В сети",
                                color = LocalWorkspaceColorsPalette.current.indicatorGreen,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider
                )
                Text(
                    text = "ОПИСАНИЕ",
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(
                            onClick = {
                                navController.navigate(ProfileFlow.OwnUserSettings)
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_personal_settings),
                            contentDescription = null,
                            modifier = Modifier.padding(0.dp, 6.dp, 8.dp, 6.dp)
                        )
                        Text(
                            text = "Личная информация"
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = LocalWorkspaceColorsPalette.current.divider
                    )
                }
                Text(
                    text = "НАСТРОЙКИ",
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(
                            onClick = {
                                navController.navigate(ProfileFlow.FolderSettings)
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_folder_settings),
                            contentDescription = null,
                            modifier = Modifier.padding(0.dp, 6.dp, 8.dp, 6.dp)
                        )
                        Text(
                            text = "Отображение папок"
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = LocalWorkspaceColorsPalette.current.divider
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logout),
                        contentDescription = null
                    )
                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = LocalWorkspaceColorsPalette.current.indicatorRed
                        )
                    ) {
                        Text(
                            text = "Выйти"
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    BuildConfig.VERSION_NAME,
                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                    fontSize = 10.sp,
                    fontFamily = InterFontFamily,
                )
            }
        }
    }
}