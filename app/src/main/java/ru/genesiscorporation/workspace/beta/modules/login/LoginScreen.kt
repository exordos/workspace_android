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
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserState
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    navController: NavHostController
) {
    val loginText by viewModel.loginText.collectAsState()
    val passwordText by viewModel.passwordText.collectAsState()
    val scope = rememberCoroutineScope()
    val user = UserState.current
    val webUrl by viewModel.webUrl.collectAsStateWithLifecycle()
    val state by viewModel.queryState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is QueryState.Error) {
            Toast
                .makeText(context, (state as QueryState.Error).message, Toast.LENGTH_SHORT)
                .show()
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
                    Image(
                        painter = painterResource(id = R.drawable.icon),
                        contentDescription = null,
                        modifier = Modifier.size(116.dp)
                            .padding(top = 48.dp)
                    )
                    Text(
                        "Название организации",
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(20.dp)
                    )
                    Text(
                        user.baseUrl.value ?: "",
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.Start
            ) {

                Text(
                    "Email",
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
                            fontSize = 14.sp
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
                            fontSize = 14.sp
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
                    modifier = Modifier.fillMaxWidth()
                        .padding(6.dp)
                ) {
                    Text("Войти")
                }
                Button(
                    onClick = {
                        scope.launch {
                            user.clearAll()
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
                    modifier = Modifier.fillMaxWidth()
                        .padding(6.dp)
                ) {
                    Text("Выйти из организации")
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FullscreenWebViewDialog(
    url: String,
    onDismiss: (cookie: String) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var capturedCookie by remember { mutableStateOf<String?>(null) }
    Dialog(
        onDismissRequest = { onDismiss(capturedCookie ?: "") },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // key for fullscreen
            decorFitsSystemWindows = false   // optional edge-to-edge
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Web page") },
                        navigationIcon = {
                            IconButton(onClick = { onDismiss("") }) {
                                Image(
                                    painter = painterResource(R.drawable.ic_close_small),
                                    contentDescription = "Close"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                AndroidView(
                    modifier = Modifier.fillMaxSize()
                        .padding(padding),
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            webViewClient = LoggingWebViewClient { cookie ->

                                if (capturedCookie == null) {
                                    capturedCookie = cookie
                                    post { destroy() }
                                    onDismiss(cookie)
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(url)
                        }
                    },
                    update = { wv ->
                        if (wv.url != url) wv.loadUrl(url)
                    }
                )
            }
        }
    }
}

class LoggingWebViewClient(
    private val onSessionPathCaptured: (cookie: String) -> Unit
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
//        val url = request?.url?.toString().orEmpty()
//        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
//        if (cookie.contains("__Host-sessionid=") && cookie.contains("__Host-csrftoken=")) {
//            // Pass full cookie string to caller (or parse if you want specific values)
////            onSessionCookieCaptured(cookie)
//            Log.d(TAG, "cookie: $cookie")
//        }

        val fullUrl = request?.url?.toString().orEmpty()
        if (fullUrl.contains("/complete/oidc/")) {
            onSessionPathCaptured(fullUrl)
        }
        return super.shouldInterceptRequest(view, request)
    }
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        Log.d(TAG, "onPageStarted: $url")
    }
    override fun onPageFinished(view: WebView?, url: String?) {
        Log.d(TAG, "onPageFinished: $url")
    }
    // Primary path (API 23+): request + error object
    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        // Ignore subresource failures; focus on the main document
        if (request?.isForMainFrame != true) return
        val code = error?.errorCode ?: -1
        val desc = error?.description?.toString() ?: "unknown"
        val failingUrl = request.url?.toString() ?: view?.url
        val msg = "WebView error (main frame): code=$code desc=$desc url=$failingUrl"
        Log.e(TAG, msg)
    }
    // Legacy overload — still called on some paths / older behavior
    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // On API 23+, prefer the non-deprecated overload above
            return
        }
        val msg = "WebView error (legacy): code=$errorCode desc=$description url=$failingUrl"
        Log.e(TAG, msg)
    }
    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        val status = errorResponse?.statusCode ?: -1
        val reason = errorResponse?.reasonPhrase ?: ""
        val mime = errorResponse?.mimeType ?: ""
        val url = request?.url?.toString()
        val msg =
            "HTTP error: status=$status reason=$reason mime=$mime isMain=${request?.isForMainFrame} url=$url"
        Log.w(TAG, msg)
    }
    companion object {
        private const val TAG = "WebViewClient"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web page") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
//                        Icon(
//                            imageVector = R.drawable.ic_close_small,
//                            contentDescription = "Close"
//                        )
                        Image(
                            painter = painterResource(R.drawable.ic_close_small),
                            contentDescription = "Close"
                        )
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true // if your page needs JS
                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) webView.loadUrl(url)
            }
        )
    }
}