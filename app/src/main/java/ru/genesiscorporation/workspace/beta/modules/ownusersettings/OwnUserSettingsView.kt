package ru.genesiscorporation.workspace.beta.modules.ownusersettings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnUserSettingsView(
    viewModel: OwnUserSettingsViewModel,
    navController: NavHostController
) {

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUser = viewModel.user.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract =
            ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        scope.launch {
            viewModel.onImageUriChange(uri, context)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text("Личная информация")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LocalWorkspaceColorsPalette.current.background)
        ) {
            Column() {
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
                            Box(
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Avatar(
                                    userData.avatar,
                                    viewModel.client.userViewModel.baseUrl.value ?: "",
                                    viewModel.client.authHeaders(),
                                    null,
                                    userData.displayableName(),
                                    Modifier.padding(end = 8.dp).size(64.dp)
                                )
                                IconButton(
                                    onClick = {
                                        showSheet = true
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_avatar_edit),
                                        contentDescription = "Edit avatar",
                                        tint = Color.Unspecified
                                    )
                                }
                            }
                            Text(
                                text = "${userData.firstName} ${userData.lastName}",
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = 20.sp,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_mail),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                            )
                            val email = userData.email
                            if (email != null) {
                                Column(
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "Email",
                                        color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                        fontSize = 12.sp,
                                        fontFamily = InterFontFamily,
                                    )
                                    Text(
                                        text = email,
                                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily,
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_userid),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Имя пользователя",
                                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                    fontSize = 12.sp,
                                    fontFamily = InterFontFamily,
                                )
                                Text(
                                    text = userData.username,
                                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                                    fontSize = 14.sp,
                                    fontFamily = InterFontFamily,
                                )
                            }
                        }
                    }
                }
            }
            if (showSheet) {
                ActionSheetExample(
                    onDismiss = { showSheet = false },
                    onGallery = {
                        launcher.launch("image/*")
                    },
                    onCamera = { /* ... */ },
                    onReset = {
                        scope.launch {
                            viewModel.resetAvatar()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheetExample(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    onGallery()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выбрать из галереи")
            }
            TextButton(
                onClick = {
                    onCamera()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сделать фото")
            }
            TextButton(
                onClick = {
                    onReset()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalWorkspaceColorsPalette.current.indicatorRed,
                ),
            ) {
                Text("Сбросить аватар")
            }
        }
    }
}