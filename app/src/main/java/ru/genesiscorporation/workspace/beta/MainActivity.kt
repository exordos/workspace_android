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
import android.widget.CalendarView
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.*
import ru.genesiscorporation.workspace.beta.modules.addfolder.AddFolderView
import ru.genesiscorporation.workspace.beta.modules.addfolder.AddFolderViewModel
import ru.genesiscorporation.workspace.beta.modules.adduserstostream.AddUsersToStreamView
import ru.genesiscorporation.workspace.beta.modules.adduserstostream.AddUsersToStreamViewModel
import ru.genesiscorporation.workspace.beta.modules.calendar.CalendarScreen
import ru.genesiscorporation.workspace.beta.modules.calendar.CalendarViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.CreateDirectStreamView
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.CreateDirectStreamViewModel
import ru.genesiscorporation.workspace.beta.modules.createstream.CreateStreamView
import ru.genesiscorporation.workspace.beta.modules.createstream.CreateStreamViewModel
import ru.genesiscorporation.workspace.beta.modules.creationbase.CreationBaseView
import ru.genesiscorporation.workspace.beta.modules.creationbase.CreationBaseViewModel
import ru.genesiscorporation.workspace.beta.modules.foldersettings.FolderSettingsView
import ru.genesiscorporation.workspace.beta.modules.foldersettings.FolderSettingsViewModel
import ru.genesiscorporation.workspace.beta.modules.home.HomeScreen
import ru.genesiscorporation.workspace.beta.modules.home.HomeViewModel
import ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsScreen
import ru.genesiscorporation.workspace.beta.modules.homedrafts.HomeDraftsViewModel
import ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsScreen
import ru.genesiscorporation.workspace.beta.modules.homeinbounds.HomeInboundsViewModel
import ru.genesiscorporation.workspace.beta.modules.homementions.HomeMentionsScreen
import ru.genesiscorporation.workspace.beta.modules.homementions.HomeMentionsViewModel
import ru.genesiscorporation.workspace.beta.modules.mail.MailScreen
import ru.genesiscorporation.workspace.beta.modules.mail.MailViewModel
import ru.genesiscorporation.workspace.beta.modules.otp.OtpScreen
import ru.genesiscorporation.workspace.beta.modules.otp.OtpViewModel
import ru.genesiscorporation.workspace.beta.modules.ownusersettings.OwnUserSettingsView
import ru.genesiscorporation.workspace.beta.modules.ownusersettings.OwnUserSettingsViewModel
import ru.genesiscorporation.workspace.beta.modules.streaminfo.StreamInfoView
import ru.genesiscorporation.workspace.beta.modules.streaminfo.StreamInfoViewModel


class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<String?>(null)
    val sessionCookieStore = SessionCookieStore()

    private val workspaceApiClient: WorkspaceAPIClient by lazy {
        val client = HttpClient() {
            install(WebSockets)
            install(createSessionCapturePlugin(sessionCookieStore))
            install(ContentNegotiation) {
                json()
            }
        }

        WorkspaceAPIClient(client, userState, sessionCookieStore)
    }
    private val userState by viewModels<UserViewModel>()  {
        UserViewModelFactory(applicationContext, lifecycleScope)
    }
    val eventsRepository = EventsRepository()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventsRepository.client = workspaceApiClient
        pendingDeepLink = intent.getStringExtra("deeplink")
        enableEdgeToEdge()
        setContent {
            WokspaceTheme {
                CompositionLocalProvider(UserState provides userState) {
                    ApplicationSwitcher(workspaceApiClient, eventsRepository, pendingDeepLink, onDeepLinkHandled = { pendingDeepLink = null })
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
    eventsRepository: EventsRepository,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val user = UserState.current
    val accessToken by user.accessToken.collectAsState()
    val isAccessTokenLoaded by user.isAccessTokenLoaded.collectAsState()

    Log.d("RepoCheck", "initnav repo instance = ${System.identityHashCode(eventsRepository)}")
    val workspaceViewModelFactory = remember { WorkspaceViewModelFactory(workspaceApiClient, eventsRepository) }
    var workspaceViewModel: WorkspaceViewModel = viewModel(factory = workspaceViewModelFactory)
    if (!isAccessTokenLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (accessToken == null) {
        LoginNavigation(workspaceApiClient)
    } else {
        WokspaceApp( workspaceViewModel, workspaceApiClient, eventsRepository, pendingDeepLink, onDeepLinkHandled)
    }
}

@Composable
fun WokspaceApp(
    viewModel: WorkspaceViewModel,
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var navController = rememberNavController()
    val destinationList = listOf<Destinations>(
        Home,
        Chat,
        Calendar,
        Mail,
        Profile
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = UserState.current
    val currentCallMessage by viewModel.currentCallMessage.collectAsState()
    var currentDestination = rememberSaveable { mutableStateOf(0 ) }


    LaunchedEffect(lifecycleOwner) {

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                eventsRepository.pushId = token
                Log.d("FCM", "fetched token $token")
//                scope.launch {
//                    viewModel.sendToken("$token")
//                }
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Token fetch failed", e)
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
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                RequestNotificationPermissionIfNeeded()
                NavHost(navController = navController, startDestination = Home.route) {
                    composable(Home.route) {
                        HomeNavigation(workspaceApiClient, eventsRepository)
                    }
                    composable(Chat.route) {
                        ChatNavigation(workspaceApiClient, eventsRepository, pendingDeepLink, onDeepLinkHandled)
                    }
                    composable(Calendar.route) {
                        val calendarViewModelFactory = remember { CalendarViewModelFactory(eventsRepository) }
                        var calendarViewModel: CalendarViewModel = viewModel(factory = calendarViewModelFactory)
                        CalendarScreen(calendarViewModel, navController)
                    }
                    composable(Mail.route) {
                        val mailViewModelFactory = remember { MailViewModelFactory(eventsRepository) }
                        var mailViewModel: MailViewModel = viewModel(factory = mailViewModelFactory)
                        MailScreen(mailViewModel, navController)
                    }
                    composable(Profile.route) {
                        ProfileNavigation(workspaceApiClient, eventsRepository)
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
fun HomeNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository
) {
    val navController = rememberNavController()
    val user = UserState.current
    val homeViewModelFactory = remember { HomeViewModelFactory(eventsRepository, workspaceApiClient) }
    val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory)

    NavHost(navController = navController, startDestination = HomeFlow.HomeBase) {
        composable<HomeFlow.HomeBase> {
            HomeScreen(homeViewModel, navController)
        }
        composable<HomeFlow.HomeInbounds> {
            val homeInboundsViewModelFactory = remember { HomeInboundsViewModelFactory(eventsRepository, workspaceApiClient) }
            val homeInboundsViewModel: HomeInboundsViewModel = viewModel(factory = homeInboundsViewModelFactory)
            HomeInboundsScreen(homeInboundsViewModel, navController)
        }
        composable<HomeFlow.HomeDrafts> {
            val homeDraftsViewModelFactory = remember { HomeDraftsViewModelFactory(eventsRepository, workspaceApiClient) }
            val homeDraftsViewModel: HomeDraftsViewModel = viewModel(factory = homeDraftsViewModelFactory)
            HomeDraftsScreen(homeDraftsViewModel, navController)
        }
        composable<HomeFlow.HomeMentions> {
            val homeMentionsViewModelFactory = remember { HomeMentionsViewModelFactory(eventsRepository, workspaceApiClient) }
            val homeMentionsViewModel: HomeMentionsViewModel = viewModel(factory = homeMentionsViewModelFactory)
            HomeMentionsScreen(homeMentionsViewModel, navController)
        }
        composable<HomeFlow.ChatDialog> {
            val args = it.toRoute<HomeFlow.ChatDialog>()
            val storage = AttachmentStorage(
                context = LocalContext.current,
                client = workspaceApiClient,
            )
            Log.d("RepoCheck", "chatnav repo instance = ${System.identityHashCode(eventsRepository)}")
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicName, args.topicUuid, args.isDirectMessages, eventsRepository, args.userId, storage) }
            val chatDialogViewModel: ChatDialogViewModel = viewModel(factory = chatDialogViewModelFactory)
            ChatDialogScreen(chatDialogViewModel, navController)
        }
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
            val storage = AttachmentStorage(
                context = LocalContext.current,
                client = workspaceApiClient,
            )
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicName, args.topicUuid, args.isDirectMessages, eventsRepository, args.userId, storage) }
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

        composable<ChatFlow.StreamInfo> {
            val args = it.toRoute<ChatFlow.StreamInfo>()
            val streamInfoViewModelFactory = remember { StreamInfoViewModelFactory(workspaceApiClient, args.streamUuid, args.topicUuid, eventsRepository) }
            val streamInfoViewModel: StreamInfoViewModel = viewModel(factory = streamInfoViewModelFactory)
            StreamInfoView(streamInfoViewModel, navController)
        }

        composable<ChatFlow.CreateBase> {
            val creationBaseViewModelFactory = remember { CreationBaseViewModelFactory(eventsRepository) }
            var creationBaseViewModel: CreationBaseViewModel = viewModel(factory = creationBaseViewModelFactory)
            CreationBaseView(creationBaseViewModel, navController)
        }
        composable<ChatFlow.CreateStream> {
            val createStreamViewModelFactory = remember { CreateStreamViewModelFactory(workspaceApiClient, eventsRepository) }
            val createStreamViewModel: CreateStreamViewModel = viewModel(factory = createStreamViewModelFactory)
            CreateStreamView(createStreamViewModel, navController)
        }
        composable<ChatFlow.CreateDirectStream> {
            val createDirectStreamViewModelFactory = remember { CreateDirectStreamViewModelFactory(workspaceApiClient, eventsRepository) }
            val createDirectStreamViewModel: CreateDirectStreamViewModel = viewModel(factory = createDirectStreamViewModelFactory)
            CreateDirectStreamView(createDirectStreamViewModel, navController)
        }
        composable<ChatFlow.AddUsersToStream> {
            val args = it.toRoute<ChatFlow.AddUsersToStream>()
            val addUsersToStreamViewModelFactory = remember { AddUsersToStreamViewModelFactory(workspaceApiClient, args.streamUuid, eventsRepository) }
            val addUsersToStreamViewModel: AddUsersToStreamViewModel = viewModel(factory = addUsersToStreamViewModelFactory)
            AddUsersToStreamView(addUsersToStreamViewModel, navController)
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

        composable<LoginFlow.Otp> {
            val args = it.toRoute<LoginFlow.Otp>()
            val otpViewModelFactory = remember { OtpViewModelFactory(workspaceApiClient, user, args.login, args.password) }
            val otpViewModel: OtpViewModel = viewModel(factory = otpViewModelFactory)
            OtpScreen(otpViewModel, navController)
        }
    }
}

@Composable
fun ProfileNavigation(workspaceApiClient: WorkspaceAPIClient, eventsRepository: EventsRepository,) {
    val navController = rememberNavController()
    val user = UserState.current
    NavHost(navController = navController, startDestination = ProfileFlow.Main) {
        composable<ProfileFlow.Main> {
            val profileViewModelFactory = remember { ProfileViewModelFactory(workspaceApiClient, user, eventsRepository) }
            var profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)
            ProfileScreen(profileViewModel, navController)
        }
        composable<ProfileFlow.OwnUserSettings> {
            val ownUserSettingsViewModelFactory = remember { OwnUserSettingsViewModelFactory(workspaceApiClient,eventsRepository) }
            val ownUserSettingsViewModel: OwnUserSettingsViewModel = viewModel(factory = ownUserSettingsViewModelFactory)
            OwnUserSettingsView(ownUserSettingsViewModel, navController)
        }
        composable<ProfileFlow.FolderSettings> {
            val folderSettingsViewModelFactory = remember { FolderSettingsViewModelFactory(eventsRepository) }
            val folderSettingsViewModel: FolderSettingsViewModel = viewModel(factory = folderSettingsViewModelFactory)
            FolderSettingsView(folderSettingsViewModel, navController)
        }
        composable<ProfileFlow.AddFolder> {
            val addFolderViewModelFactory = remember { AddFolderViewModelFactory(workspaceApiClient,eventsRepository) }
            val addFolderViewModel: AddFolderViewModel = viewModel(factory = addFolderViewModelFactory)
            AddFolderView(addFolderViewModel, navController)
        }
    }
}

@Composable
fun StreamCreationNavigation(workspaceApiClient: WorkspaceAPIClient, eventsRepository: EventsRepository,) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = StreamCreationFlow.CreateBase) {
        composable<StreamCreationFlow.CreateBase> {
            val creationBaseViewModelFactory = remember { CreationBaseViewModelFactory(eventsRepository) }
            var creationBaseViewModel: CreationBaseViewModel = viewModel(factory = creationBaseViewModelFactory)
            CreationBaseView(creationBaseViewModel, navController)
        }
        composable<StreamCreationFlow.CreateStream> {
            val createStreamViewModelFactory = remember { CreateStreamViewModelFactory(workspaceApiClient, eventsRepository) }
            val createStreamViewModel: CreateStreamViewModel = viewModel(factory = createStreamViewModelFactory)
            CreateStreamView(createStreamViewModel, navController)
        }
        composable<StreamCreationFlow.CreateDirectStream> {
            val createDirectStreamViewModelFactory = remember { CreateDirectStreamViewModelFactory(workspaceApiClient, eventsRepository) }
            val createDirectStreamViewModel: CreateDirectStreamViewModel = viewModel(factory = createDirectStreamViewModelFactory)
            CreateDirectStreamView(createDirectStreamViewModel, navController)
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