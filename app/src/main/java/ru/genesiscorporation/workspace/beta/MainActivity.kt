package ru.genesiscorporation.workspace.beta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.FCMTokenHolder
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatScreen
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoScreen
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerScreen
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerViewModel
import ru.genesiscorporation.workspace.beta.modules.login.LoginScreen
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileScreen
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsScreen
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import io.ktor.client.plugins.api.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.ui.IncomingCall
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*


class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<String?>(null)
    val sessionCookieStore = SessionCookieStore()

    private val workspaceApiClient: WorkspaceAPIClient by lazy {
        val client = HttpClient() {
//            install(plugin)
            install(createSessionCapturePlugin(sessionCookieStore))
            install(ContentNegotiation) {
                json()
            }
        }

        WorkspaceAPIClient(client, userState, sessionCookieStore)
    }
    private val userState by viewModels<UserViewModel>()  {
        UserViewModelFactory(applicationContext)
    }
    val messagesRepository = EventsRepository()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink = intent.getStringExtra("deeplink")
        enableEdgeToEdge()
        setContent {
            WokspaceTheme {
                CompositionLocalProvider(UserState provides userState) {
                    ApplicationSwitcher(workspaceApiClient, messagesRepository, pendingDeepLink, onDeepLinkHandled = { pendingDeepLink = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.getStringExtra("deeplink")
    }
}

@Composable
fun RequestNotificationPermissionIfNeeded() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Notifications allowed (FCM notifications can be shown)
        } else {
            // User denied; handle gracefully
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun ApplicationSwitcher(
    workspaceApiClient: WorkspaceAPIClient,
    messagesRepository: EventsRepository,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val user = UserState.current
    val userId by user.userId.collectAsState()
    val isApiKeyLoaded by user.isApiKeyLoaded.collectAsState()

    Log.d("RepoCheck", "initnav repo instance = ${System.identityHashCode(messagesRepository)}")
    val workspaceViewModelFactory = remember { WorkspaceViewModelFactory(workspaceApiClient, messagesRepository) }
    var workspaceViewModel: WorkspaceViewModel = viewModel(factory = workspaceViewModelFactory)
    if (!isApiKeyLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (userId == null) {
        LoginNavigation(workspaceApiClient)
    } else {
        WokspaceApp( workspaceViewModel, workspaceApiClient, messagesRepository, pendingDeepLink, onDeepLinkHandled)
    }
}

@Composable
fun WokspaceApp(
    viewModel: WorkspaceViewModel,
    workspaceApiClient: WorkspaceAPIClient,
    messagesRepository: EventsRepository,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var navController = rememberNavController()
    val destinationList = listOf<Destinations>(
        Chat,
        Profile
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = UserState.current
    val currentCallMessage by viewModel.currentCallMessage.collectAsState()
    var currentDestination = rememberSaveable { mutableStateOf(0 ) }
    val profileViewModelFactory = remember { ProfileViewModelFactory(workspaceApiClient, user, messagesRepository) }
    var profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)


    LaunchedEffect(lifecycleOwner) {

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                messagesRepository.pushId = token
                Log.d("FCM", "fetched token $token")
                scope.launch {
                    viewModel.sendToken("workspace:android:$token")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Token fetch failed", e)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLongPolling()
        }
    }

    NavigationSuiteScaffold(
        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        navigationSuiteItems = {
            destinationList.forEachIndexed { index, destination ->
                item(
                    icon = {
                        Icon(
                            painter = painterResource( id = destination.icon),
                            contentDescription = destination.title
                        )
                    },
                    selected = index == currentDestination.value,
                    onClick = {
                        currentDestination.value = index
                        navController.navigate(destinationList[index].route) {
                            popUpTo(Chat.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) {
        Log.d("RepoCheck", "mainnav repo instance = ${System.identityHashCode(messagesRepository)}")
//        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
//                .padding(
//                    top = innerPadding.calculateTopPadding(),
//                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
//                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
//                )
//                .recalculateWindowInsets()
//                .consumeWindowInsets(insets = WindowInsets(0.dp, 0.dp, 0.dp, 70.dp))
            ) {
                RequestNotificationPermissionIfNeeded()
                NavHost(navController = navController, startDestination = Chat.route) {
                    composable(Chat.route) {
                        ChatNavigation(workspaceApiClient, messagesRepository, pendingDeepLink, onDeepLinkHandled)
                    }
                    composable(Profile.route) {
                        ProfileScreen(profileViewModel)
                    }
                }
                val callMessage = currentCallMessage
                if (callMessage != null) {
                    IncomingCall(callMessage, viewModel, context)
                }
            }
//        }
    }
}

@Composable
fun ChatNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val navController = rememberNavController()
    val user = UserState.current
    val chatViewModelFactory = remember { ChatViewModelFactory(workspaceApiClient, user, eventsRepository, pendingDeepLink, onDeepLinkHandled) }
    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
    NavHost(navController = navController, startDestination = ChatFlow.ChatList) {
        composable<ChatFlow.ChatList> {
            ChatScreen(chatViewModel, navController)
        }
        composable<ChatFlow.ChatDialog> {
            val args = it.toRoute<ChatFlow.ChatDialog>()
            Log.d("RepoCheck", "chatnav repo instance = ${System.identityHashCode(eventsRepository)}")
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicId, args.isDirectMessages, eventsRepository, args.userId) }
            val chatDialogViewModel: ChatDialogViewModel = viewModel(factory = chatDialogViewModelFactory)
            ChatDialogScreen(chatDialogViewModel, navController)
        }
        composable<ChatFlow.ChatTopic> {
            val args = it.toRoute<ChatFlow.ChatTopic>()

            val chatTopicsViewModelFactory = remember { ChatTopicsViewModelFactory(workspaceApiClient, user, args.channelName, args.channelId, eventsRepository) }
            val chatTopicsViewModel: TopicsViewModel = viewModel(factory = chatTopicsViewModelFactory)
            TopicsScreen(chatTopicsViewModel, navController)
        }
        composable<ChatFlow.ChatUserInfo> {
            val args = it.toRoute<ChatFlow.ChatUserInfo>()
            Log.d("RepoCheck", "chatnav repo instance = ${System.identityHashCode(eventsRepository)}")
            val chatUserInfoViewModelFactory = remember { ChatUserInfoViewModelFactory(workspaceApiClient, args.userName, args.userId, args.avatarUrl, args.email, eventsRepository) }
            val chatUserInfoViewModel: ChatUserInfoViewModel = viewModel(factory = chatUserInfoViewModelFactory)
            ChatUserInfoScreen(chatUserInfoViewModel, navController)
        }
    }
}

@Composable
fun LoginNavigation(workspaceApiClient: WorkspaceAPIClient) {
    val navController = rememberNavController()
    val user = UserState.current
    NavHost(navController = navController, startDestination = LoginFlow.ChooseServer) {
        composable<LoginFlow.ChooseServer> {
            val chooseServerViewModelFactory = remember { ChooseServerViewModelFactory(workspaceApiClient, user) }
            val chooseServerViewModel: ChooseServerViewModel = viewModel(factory = chooseServerViewModelFactory)
            ChooseServerScreen(chooseServerViewModel, navController)
        }
        composable<LoginFlow.Login> {

            val loginViewModelFactory = remember { LoginViewModelFactory(workspaceApiClient, user) }
            val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
            LoginScreen(loginViewModel, navController)
        }
    }
}

fun createSessionCapturePlugin(sessionCookieStore: SessionCookieStore) =
    createClientPlugin("session-capture-plugin") {
        on(Send) { request ->
            val call = proceed(request)

            val setCookies = call.response.headers.getAll(HttpHeaders.SetCookie).orEmpty()

            val sessionId = setCookies
                .asSequence()
                .mapNotNull { header ->
                    header.substringBefore(';')
                        .takeIf { it.startsWith("__Host-sessionid=") }
                        ?.substringAfter('=')
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
            val csfrToken = setCookies
                .asSequence()
                .mapNotNull { header ->
                    header.substringBefore(';')
                        .takeIf { it.startsWith("__Host-csrftoken=") }
                        ?.substringAfter('=')
                        ?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
            if (sessionId != null) {
                if (csfrToken != null) {
                    sessionCookieStore.setFullSessionCookie("__Host-sessionid=${sessionId}; __Host-csrftoken=${csfrToken}")
                } else {
                    sessionCookieStore.setSessionId(sessionId)
                }
            }
            call
        }
    }