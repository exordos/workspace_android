package ru.genesiscorporation.workspace.beta.modules.login

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    navController: NavHostController
) {
    val loginText by viewModel.loginText.collectAsState()
    val passwordText by viewModel.passwordText.collectAsState()
    val scope = rememberCoroutineScope()
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is QueryState.Error) {
            val message = (state as QueryState.Error).message
            if (message == "needs_otp") {
                viewModel.idleQueryState()
                navController.navigate(LoginFlow.Otp(loginText, passwordText))
            } else {
                Toast
                    .makeText(context, message, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()
        .background(LocalWorkspaceColorsPalette.current.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(LocalWorkspaceColorsPalette.current.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val organizationImageUrl = UrnParser.parseUrl(viewModel.userViewModel.organizationImageUrl, "")
                    if (organizationImageUrl != null) {
                        AsyncImage(
                            model = organizationImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(116.dp)
                                .padding(top = 48.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.icon),
                            contentDescription = null,
                            modifier = Modifier.size(116.dp)
                                .padding(top = 48.dp)
                        )
                    }
                    Text(
                        viewModel.userViewModel.organizationName ?: "Название организации",
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(20.dp, 12.dp, 4.dp, 20.dp)
                    )
                    Text(
                        viewModel.userViewModel.organizationUrl ?: "",
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .padding(20.dp),
                thickness = 1.dp,
                color = LocalWorkspaceColorsPalette.current.divider,
            )
            Column(
                horizontalAlignment = Alignment.Start
            ) {

                Text(
                    "Логин",
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
                        .padding(vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.searchBackground,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    BasicTextField(
                        value = loginText,
                        onValueChange = viewModel::onLoginChange,
                        textStyle = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
                Text(
                    "Пароль",
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
                        .padding(vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.searchBackground,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    BasicTextField(
                        value = passwordText,
                        onValueChange = viewModel::onPasswordChange,
                        textStyle = TextStyle(
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                        ),
                        cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textHeaders),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 12.dp, end = 44.dp)
                    )
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                    ) {
                        Image(
                            painter = if (passwordVisible) painterResource(id = R.drawable.ic_visibility_off) else painterResource(
                                id = R.drawable.ic_visibility
                            ),
                            contentDescription = null
                        )
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.onLoginClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalWorkspaceColorsPalette.current.primary,
                        contentColor = LocalWorkspaceColorsPalette.current.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(20.dp, 6.dp, 20.dp, 6.dp)
                ) {
                    Text(
                        "Войти",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.userViewModel.clearAll()
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = LocalWorkspaceColorsPalette.current.indicatorRed
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = LocalWorkspaceColorsPalette.current.indicatorRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(20.dp, 6.dp, 20.dp, 6.dp)
                ) {
                    Text(
                        "Выйти из организации",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
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
                ) { },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}