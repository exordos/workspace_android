package ru.genesiscorporation.workspace.beta

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
import io.ktor.http.HttpHeaders
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.ui.IncomingCall
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.SecureTokenStore
import ru.genesiscorporation.workspace.beta.data.ServerRepository
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
import ru.genesiscorporation.workspace.beta.modules.visualsettings.VisualSettingsScreen
import ru.genesiscorporation.workspace.beta.modules.visualsettings.VisualSettingsViewModel

class WorkspaceApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var serverRepository: ServerRepository
        private set
    lateinit var workspaceApiClient: WorkspaceAPIClient
        private set
    lateinit var eventsRepositoryStore: EventsRepositoryStore
        private set
    override fun onCreate() {
        super.onCreate()
        val httpClient = HttpClient {
            install(WebSockets)
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
            }
            install(ContentNegotiation) {
                json()
            }
        }
        workspaceApiClient = WorkspaceAPIClient(
            client = httpClient
        )
        val tokenStore = SecureTokenStore(this)
        eventsRepositoryStore = EventsRepositoryStore(tokenStore, workspaceApiClient)
        serverRepository = ServerRepository(
            context = this,
            tokenStore = SecureTokenStore(this),
            eventsRepositoryStore = eventsRepositoryStore,
            appScope = appScope
        )
    }
}
class MainActivity : ComponentActivity() {
    private val app get() = application as WorkspaceApplication
    private val userState by viewModels<UserViewModel> {
        UserViewModelFactory(app.serverRepository)
    }
    private var pendingDeepLink by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        app.workspaceApiClient.attachUserViewModel(userState)
        pendingDeepLink = intent.deeplinkOrNull()
        setContent {
            WorkspaceApp(
                apiClient = app.workspaceApiClient,
                eventsRepositoryStore = app.eventsRepositoryStore,
                userState = userState,
                pendingDeepLink = pendingDeepLink,
                onDeepLinkHandled = { pendingDeepLink = null },
            )
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.deeplinkOrNull()
    }
}
private fun Intent.deeplinkOrNull(): String? = getStringExtra("deeplink")
val LocalBottomBarVisible = staticCompositionLocalOf<(Boolean) -> Unit> {
    error("LocalBottomBarVisible not provided")
}

@Composable
fun WorkspaceApp(
    apiClient: WorkspaceAPIClient,
    eventsRepositoryStore: EventsRepositoryStore,
    userState: UserViewModel,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit,
) {
    WokspaceTheme {
        CompositionLocalProvider(UserState provides userState) {
            ApplicationSwitcher(
                workspaceApiClient = apiClient,
                eventsRepositoryStore = eventsRepositoryStore,
                pendingDeepLink = pendingDeepLink,
                onDeepLinkHandled = onDeepLinkHandled,
            )
        }
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
    eventsRepositoryStore: EventsRepositoryStore,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val user = UserState.current
    val accessToken by user.accessToken.collectAsState()
    val servers by user.servers.collectAsState()
    val isAccessTokenLoaded by user.isAccessTokenLoaded.collectAsState()
    val isServersLoaded by user.isServersLoaded.collectAsState()
    var navController = rememberNavController()
    val chooseServerViewModelFactory = remember { ChooseServerViewModelFactory(workspaceApiClient, user) }
    val chooseServerViewModel: ChooseServerViewModel = viewModel(factory = chooseServerViewModelFactory)

    Log.d("RepoCheck", "initnav repo instance = ${System.identityHashCode(eventsRepositoryStore)}")
    val workspaceViewModelFactory = remember { WorkspaceViewModelFactory(workspaceApiClient, eventsRepositoryStore) }
    var workspaceViewModel: WorkspaceViewModel = viewModel(factory = workspaceViewModelFactory)
    if (!isAccessTokenLoaded || !isServersLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (servers.isEmpty()) {
        ChooseServerScreen(chooseServerViewModel, navController)
    } else if (accessToken == null) {
        LoginNavigation(workspaceApiClient)
    } else {
        WorkspaceApp( workspaceViewModel, workspaceApiClient, eventsRepositoryStore, pendingDeepLink, onDeepLinkHandled)
    }
}

@Composable
fun WorkspaceApp(
    viewModel: WorkspaceViewModel,
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepositoryStore: EventsRepositoryStore,
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
    var showNavigation by rememberSaveable { mutableStateOf(true) }


    LaunchedEffect(lifecycleOwner) {

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
//                eventsRepositoryStore.pushId = token
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
        layoutType = if (showNavigation) {
            NavigationSuiteType.ShortNavigationBarCompact
        } else {
            NavigationSuiteType.None
        },
        navigationSuiteItems = {
            if (!showNavigation) return@NavigationSuiteScaffold

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

        CompositionLocalProvider(
            LocalBottomBarVisible provides { visible -> showNavigation = visible }
        ) {
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                RequestNotificationPermissionIfNeeded()
                NavHost(navController = navController, startDestination = Home.route) {
                    composable(Home.route) {
                        HomeNavigation(workspaceApiClient, eventsRepositoryStore)
                    }
                    composable(Chat.route) {
                        ChatNavigation(
                            workspaceApiClient,
                            eventsRepositoryStore,
                            pendingDeepLink,
                            onDeepLinkHandled
                        )
                    }
                    composable(Calendar.route) {
                        val calendarViewModelFactory =
                            remember { CalendarViewModelFactory(eventsRepositoryStore) }
                        var calendarViewModel: CalendarViewModel =
                            viewModel(factory = calendarViewModelFactory)
                        CalendarScreen(calendarViewModel, navController)
                    }
                    composable(Mail.route) {
                        val mailViewModelFactory =
                            remember { MailViewModelFactory(eventsRepositoryStore) }
                        var mailViewModel: MailViewModel = viewModel(factory = mailViewModelFactory)
                        MailScreen(mailViewModel, navController)
                    }
                    composable(Profile.route) {
                        ProfileNavigation(workspaceApiClient, eventsRepositoryStore)
                    }
                }
                val callMessage = currentCallMessage
                if (callMessage != null) {
                    IncomingCall(callMessage, viewModel, context)
                }
            }
        }
    }
}

@Composable
fun HomeNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepositoryStore: EventsRepositoryStore
) {
    val navController = rememberNavController()
    val user = UserState.current
    val homeViewModelFactory = remember { HomeViewModelFactory(eventsRepositoryStore, workspaceApiClient) }
    val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory)

    NavHost(navController = navController, startDestination = HomeFlow.HomeBase) {
        composable<HomeFlow.HomeBase> {
            HomeScreen(homeViewModel, navController)
        }
        composable<HomeFlow.HomeInbounds> {
            val homeInboundsViewModelFactory = remember { HomeInboundsViewModelFactory(eventsRepositoryStore, workspaceApiClient) }
            val homeInboundsViewModel: HomeInboundsViewModel = viewModel(factory = homeInboundsViewModelFactory)
            HomeInboundsScreen(homeInboundsViewModel, navController)
        }
        composable<HomeFlow.HomeDrafts> {
            val homeDraftsViewModelFactory = remember { HomeDraftsViewModelFactory(eventsRepositoryStore, workspaceApiClient) }
            val homeDraftsViewModel: HomeDraftsViewModel = viewModel(factory = homeDraftsViewModelFactory)
            HomeDraftsScreen(homeDraftsViewModel, navController)
        }
        composable<HomeFlow.HomeMentions> {
            val homeMentionsViewModelFactory = remember { HomeMentionsViewModelFactory(eventsRepositoryStore, workspaceApiClient) }
            val homeMentionsViewModel: HomeMentionsViewModel = viewModel(factory = homeMentionsViewModelFactory)
            HomeMentionsScreen(homeMentionsViewModel, navController)
        }
        composable<HomeFlow.ChatDialog> {
            val args = it.toRoute<HomeFlow.ChatDialog>()
            val storage = AttachmentStorage(
                context = LocalContext.current,
                client = workspaceApiClient,
            )
            Log.d("RepoCheck", "chatnav repo instance = ${System.identityHashCode(eventsRepositoryStore)}")
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicName, args.topicUuid, args.isDirectMessages, eventsRepositoryStore, args.userId, storage) }
            val chatDialogViewModel: ChatDialogViewModel = viewModel(factory = chatDialogViewModelFactory)
            ChatDialogScreen(chatDialogViewModel, navController)
        }
    }
}

@Composable
fun ChatNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepositoryStore: EventsRepositoryStore,
    pendingDeepLink: String?,
    onDeepLinkHandled: () -> Unit
) {
    val navController = rememberNavController()
    val user = UserState.current
    val chatViewModelFactory = remember { ChatViewModelFactory(workspaceApiClient, user, eventsRepositoryStore, pendingDeepLink, onDeepLinkHandled) }
    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
    NavHost(navController = navController, startDestination = ChatFlow.ChatList) {
        composable<ChatFlow.ChatList> {
            ChatScreen(chatViewModel, navController)
        }
        composable<ChatFlow.ChatDialog> {
            val args = it.toRoute<ChatFlow.ChatDialog>()
            Log.d("RepoCheck", "chatnav repo instance = ${System.identityHashCode(eventsRepositoryStore)}")
            val storage = AttachmentStorage(
                context = LocalContext.current,
                client = workspaceApiClient,
            )
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicName, args.topicUuid, args.isDirectMessages, eventsRepositoryStore, args.userId, storage) }
            val chatDialogViewModel: ChatDialogViewModel = viewModel(factory = chatDialogViewModelFactory)
            ChatDialogScreen(chatDialogViewModel, navController)
        }
        composable<ChatFlow.ChatTopic> {
            val args = it.toRoute<ChatFlow.ChatTopic>()

            val chatTopicsViewModelFactory = remember { ChatTopicsViewModelFactory(workspaceApiClient, user, args.channelName, args.channelId, eventsRepositoryStore) }
            val chatTopicsViewModel: TopicsViewModel = viewModel(factory = chatTopicsViewModelFactory)
            TopicsScreen(chatTopicsViewModel, navController)
        }
        composable<ChatFlow.ChatUserInfo> {
            val args = it.toRoute<ChatFlow.ChatUserInfo>()
            Log.d("RepoCheck", "chatnav repo instance = ${System.identityHashCode(eventsRepositoryStore)}")
            val chatUserInfoViewModelFactory = remember { ChatUserInfoViewModelFactory(workspaceApiClient, args.userName, args.userId, args.avatarUrl, args.email, eventsRepositoryStore) }
            val chatUserInfoViewModel: ChatUserInfoViewModel = viewModel(factory = chatUserInfoViewModelFactory)
            ChatUserInfoScreen(chatUserInfoViewModel, navController)
        }

        composable<ChatFlow.StreamInfo> {
            val args = it.toRoute<ChatFlow.StreamInfo>()
            val streamInfoViewModelFactory = remember { StreamInfoViewModelFactory(workspaceApiClient, args.streamUuid, args.topicUuid, eventsRepositoryStore) }
            val streamInfoViewModel: StreamInfoViewModel = viewModel(factory = streamInfoViewModelFactory)
            StreamInfoView(streamInfoViewModel, navController)
        }

        composable<ChatFlow.CreateBase> {
            val creationBaseViewModelFactory = remember { CreationBaseViewModelFactory(eventsRepositoryStore) }
            var creationBaseViewModel: CreationBaseViewModel = viewModel(factory = creationBaseViewModelFactory)
            CreationBaseView(creationBaseViewModel, navController)
        }
        composable<ChatFlow.CreateStream> {
            val createStreamViewModelFactory = remember { CreateStreamViewModelFactory(workspaceApiClient, eventsRepositoryStore) }
            val createStreamViewModel: CreateStreamViewModel = viewModel(factory = createStreamViewModelFactory)
            CreateStreamView(createStreamViewModel, navController)
        }
        composable<ChatFlow.CreateDirectStream> {
            val createDirectStreamViewModelFactory = remember { CreateDirectStreamViewModelFactory(workspaceApiClient, eventsRepositoryStore) }
            val createDirectStreamViewModel: CreateDirectStreamViewModel = viewModel(factory = createDirectStreamViewModelFactory)
            CreateDirectStreamView(createDirectStreamViewModel, navController)
        }
        composable<ChatFlow.AddUsersToStream> {
            val args = it.toRoute<ChatFlow.AddUsersToStream>()
            val addUsersToStreamViewModelFactory = remember { AddUsersToStreamViewModelFactory(workspaceApiClient, args.streamUuid, eventsRepositoryStore) }
            val addUsersToStreamViewModel: AddUsersToStreamViewModel = viewModel(factory = addUsersToStreamViewModelFactory)
            AddUsersToStreamView(addUsersToStreamViewModel, navController)
        }
    }
}

@Composable
fun LoginNavigation(workspaceApiClient: WorkspaceAPIClient) {
    val navController = rememberNavController()
    val user = UserState.current
    NavHost(navController = navController, startDestination = LoginFlow.Login(false)) {
        composable<LoginFlow.Login> {
            val args = it.toRoute<LoginFlow.Login>()
            val loginViewModelFactory = remember { LoginViewModelFactory(workspaceApiClient, user, args.isFirstOrganization) }
            val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
            LoginScreen(loginViewModel, navController)
        }

        composable<LoginFlow.Otp> {
            val args = it.toRoute<LoginFlow.Otp>()
            val otpViewModelFactory = remember { OtpViewModelFactory(workspaceApiClient, user, args.login, args.password, args.isFirstOrganization) }
            val otpViewModel: OtpViewModel = viewModel(factory = otpViewModelFactory)
            OtpScreen(otpViewModel, navController)
        }
    }
}

@Composable
fun ProfileNavigation(workspaceApiClient: WorkspaceAPIClient, eventsRepositoryStore: EventsRepositoryStore) {
    val navController = rememberNavController()
    val user = UserState.current
    NavHost(navController = navController, startDestination = ProfileFlow.Main) {
        composable<ProfileFlow.Main> {
            val profileViewModelFactory = remember { ProfileViewModelFactory(workspaceApiClient, user, eventsRepositoryStore) }
            var profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)
            ProfileScreen(profileViewModel, navController)
        }
        composable<ProfileFlow.OwnUserSettings> {
            val ownUserSettingsViewModelFactory = remember { OwnUserSettingsViewModelFactory(workspaceApiClient,eventsRepositoryStore) }
            val ownUserSettingsViewModel: OwnUserSettingsViewModel = viewModel(factory = ownUserSettingsViewModelFactory)
            OwnUserSettingsView(ownUserSettingsViewModel, navController)
        }
        composable<ProfileFlow.FolderSettings> {
            val folderSettingsViewModelFactory = remember { FolderSettingsViewModelFactory(workspaceApiClient,eventsRepositoryStore) }
            val folderSettingsViewModel: FolderSettingsViewModel = viewModel(factory = folderSettingsViewModelFactory)
            FolderSettingsView(folderSettingsViewModel, navController)
        }
        composable<ProfileFlow.AddFolder> {
            val addFolderViewModelFactory = remember { AddFolderViewModelFactory(workspaceApiClient,eventsRepositoryStore) }
            val addFolderViewModel: AddFolderViewModel = viewModel(factory = addFolderViewModelFactory)
            AddFolderView(addFolderViewModel, navController)
        }
        composable<ProfileFlow.VisualSettings> {
            val visualSettingsViewModelFactory = remember { VisualSettingsViewModelFactory(user) }
            val visualSettingsViewModel: VisualSettingsViewModel = viewModel(factory = visualSettingsViewModelFactory)
            VisualSettingsScreen(visualSettingsViewModel, navController)
        }
        composable<ProfileFlow.Login> {
            val args = it.toRoute<ProfileFlow.Login>()
            val loginViewModelFactory = remember { LoginViewModelFactory(workspaceApiClient, user, args.isFirstOrganization) }
            val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
            LoginScreen(loginViewModel, navController)
        }
        composable<ProfileFlow.Otp> {
            val args = it.toRoute<ProfileFlow.Otp>()
            val otpViewModelFactory = remember { OtpViewModelFactory(workspaceApiClient, user, args.login, args.password, args.isFirstOrganization) }
            val otpViewModel: OtpViewModel = viewModel(factory = otpViewModelFactory)
            OtpScreen(otpViewModel, navController)
        }
    }
}

@Composable
fun StreamCreationNavigation(workspaceApiClient: WorkspaceAPIClient, eventsRepositoryStore: EventsRepositoryStore) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = StreamCreationFlow.CreateBase) {
        composable<StreamCreationFlow.CreateBase> {
            val creationBaseViewModelFactory = remember { CreationBaseViewModelFactory(eventsRepositoryStore) }
            var creationBaseViewModel: CreationBaseViewModel = viewModel(factory = creationBaseViewModelFactory)
            CreationBaseView(creationBaseViewModel, navController)
        }
        composable<StreamCreationFlow.CreateStream> {
            val createStreamViewModelFactory = remember { CreateStreamViewModelFactory(workspaceApiClient, eventsRepositoryStore) }
            val createStreamViewModel: CreateStreamViewModel = viewModel(factory = createStreamViewModelFactory)
            CreateStreamView(createStreamViewModel, navController)
        }
        composable<StreamCreationFlow.CreateDirectStream> {
            val createDirectStreamViewModelFactory = remember { CreateDirectStreamViewModelFactory(workspaceApiClient, eventsRepositoryStore) }
            val createDirectStreamViewModel: CreateDirectStreamViewModel = viewModel(factory = createDirectStreamViewModelFactory)
            CreateDirectStreamView(createDirectStreamViewModel, navController)
        }
    }
}