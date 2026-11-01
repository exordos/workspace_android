package com.exordos.workspace

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.work.Configuration
import com.google.firebase.messaging.FirebaseMessaging
import com.exordos.workspace.data.remote.workspaceHttpClient
import io.ktor.client.plugins.HttpTimeout
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.AuthHeader
import com.exordos.workspace.data.remote.dto.UserResponseData
import com.exordos.workspace.modules.chatchannels.ChatScreen
import com.exordos.workspace.modules.chatchannels.ChatViewModel
import com.exordos.workspace.modules.chatdialog.ChatDialogScreen
import com.exordos.workspace.modules.chatdialog.ChatDialogViewModel
import com.exordos.workspace.modules.chatuserinfo.ChatUserInfoScreen
import com.exordos.workspace.modules.chatuserinfo.ChatUserInfoViewModel
import com.exordos.workspace.modules.chooseserver.ChooseServerScreen
import com.exordos.workspace.modules.chooseserver.ChooseServerViewModel
import com.exordos.workspace.modules.login.LoginScreen
import com.exordos.workspace.modules.login.LoginViewModel
import com.exordos.workspace.modules.profile.ProfileScreen
import com.exordos.workspace.modules.profile.ProfileViewModel
import com.exordos.workspace.modules.topics.TopicsScreen
import com.exordos.workspace.modules.topics.TopicsViewModel
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette
import com.exordos.workspace.ui.theme.WokspaceTheme
import io.ktor.client.plugins.api.*
import io.ktor.http.HttpHeaders
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.confirmedAccountContext
import com.exordos.workspace.data.push.PushReconciliationWorker
import com.exordos.workspace.data.push.PushRegistrationWorker
import com.exordos.workspace.data.push.PushWorkerFactory
import com.exordos.workspace.data.push.WorkspacePushNotifications
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.remote.dto.PushStreamRequest
import com.exordos.workspace.data.remote.dto.TopicsByIdsRequest
import com.exordos.workspace.ui.IncomingCall
import com.exordos.workspace.ui.Avatar
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.SecureTokenStore
import com.exordos.workspace.data.ServerRepository
import com.exordos.workspace.modules.addfolder.AddFolderView
import com.exordos.workspace.modules.addfolder.AddFolderViewModel
import com.exordos.workspace.modules.adduserstostream.AddUsersToStreamView
import com.exordos.workspace.modules.adduserstostream.AddUsersToStreamViewModel
import com.exordos.workspace.modules.calendar.CalendarScreen
import com.exordos.workspace.modules.calendar.CalendarViewModel
import com.exordos.workspace.modules.chatdialog.AttachmentStorage
import com.exordos.workspace.modules.createdirectstream.CreateDirectStreamView
import com.exordos.workspace.modules.createdirectstream.CreateDirectStreamViewModel
import com.exordos.workspace.modules.createstream.CreateStreamView
import com.exordos.workspace.modules.createstream.CreateStreamViewModel
import com.exordos.workspace.modules.creationbase.CreationBaseView
import com.exordos.workspace.modules.creationbase.CreationBaseViewModel
import com.exordos.workspace.modules.foldersettings.FolderSettingsView
import com.exordos.workspace.modules.foldersettings.FolderSettingsViewModel
import com.exordos.workspace.modules.home.HomeScreen
import com.exordos.workspace.modules.home.HomeViewModel
import com.exordos.workspace.modules.homedrafts.HomeDraftsScreen
import com.exordos.workspace.modules.homedrafts.HomeDraftsViewModel
import com.exordos.workspace.modules.homeinbounds.HomeInboundsScreen
import com.exordos.workspace.modules.homeinbounds.HomeInboundsViewModel
import com.exordos.workspace.modules.homementions.HomeMentionsScreen
import com.exordos.workspace.modules.homementions.HomeMentionsViewModel
import com.exordos.workspace.modules.mail.MailScreen
import com.exordos.workspace.modules.mail.MailViewModel
import com.exordos.workspace.modules.otp.OtpScreen
import com.exordos.workspace.modules.otp.OtpViewModel
import com.exordos.workspace.modules.ownusersettings.OwnUserSettingsView
import com.exordos.workspace.modules.ownusersettings.OwnUserSettingsViewModel
import com.exordos.workspace.modules.projects.ProjectsScreen
import com.exordos.workspace.modules.projects.ProjectsViewModel
import com.exordos.workspace.modules.streaminfo.StreamInfoView
import com.exordos.workspace.modules.streaminfo.StreamInfoViewModel
import com.exordos.workspace.modules.visualsettings.VisualSettingsScreen
import com.exordos.workspace.modules.visualsettings.VisualSettingsViewModel

class WorkspaceApplication : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var serverRepository: ServerRepository
        private set
    lateinit var workspaceApiClient: WorkspaceAPIClient
        private set
    lateinit var eventsRepositoryStore: EventsRepositoryStore
        private set
    private lateinit var pushApiClient: WorkspaceAPIClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(PushWorkerFactory(pushApiClient, serverRepository))
            .build()

    override fun onCreate() {
        super.onCreate()
        val httpClient = workspaceHttpClient {
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
        // The background adapter lives with the application and never needs an Activity.
        pushApiClient = WorkspaceAPIClient(httpClient).apply {
            attachUserViewModel(UserViewModel(serverRepository))
        }
        eventsRepositoryStore.onPushMessagesKnown = { serverId, owner, messages ->
            if (messages.any { it.read }) appScope.launch(Dispatchers.IO) {
                WorkspacePushNotifications(this@WorkspaceApplication)
                    .knownMessages(serverId, owner, messages)
            }
        }
        eventsRepositoryStore.onPushMessageRemoved = { serverId, owner, messageUuid ->
            appScope.launch(Dispatchers.IO) {
                WorkspacePushNotifications(this@WorkspaceApplication)
                    .removedMessage(serverId, owner, messageUuid)
            }
        }
        eventsRepositoryStore.onPushRead = { serverId, owner, messageUuids ->
            appScope.launch(Dispatchers.IO) {
                WorkspacePushNotifications(this@WorkspaceApplication)
                    .readMessages(serverId, owner, messageUuids)
            }
            PushReconciliationWorker.schedule(this)
        }
        eventsRepositoryStore.onPushRefreshNeeded = {
            PushReconciliationWorker.schedule(this)
        }
        appScope.launch(Dispatchers.IO) { WorkspacePushNotifications(this@WorkspaceApplication).restore() }
        PushReconciliationWorker.installPeriodic(this)
        PushReconciliationWorker.schedule(this)
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    PushReconciliationWorker.schedule(this@WorkspaceApplication)
                }
            }
        )
        serverRepository.onPushRegistrationNeeded = { PushRegistrationWorker.schedule(this) }
        serverRepository.unregisterPush = { serverId ->
            PushRegistrationWorker.unregister(this, serverId, pushApiClient, serverRepository)
        }
        PushRegistrationWorker.schedule(this)
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
                onDeepLinkHandled = {
                    pendingDeepLink = null
                    intent.removeExtra("deeplink")
                    intent.data = null
                },
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
/** null releases the protected override; false hides it, true shows the protected bar. */
val LocalProtectedBottomBarStyle = staticCompositionLocalOf<(Boolean?) -> Unit> { {} }

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
    val selectedServer by user.selectedServer.collectAsState()
    val isAccessTokenLoaded by user.isAccessTokenLoaded.collectAsState()
    val isServersLoaded by user.isServersLoaded.collectAsState()
    val pushContext = LocalContext.current
    LaunchedEffect(pendingDeepLink, isServersLoaded, servers) {
        if (!isServersLoaded || pendingDeepLink?.startsWith("workspace-push/") != true) return@LaunchedEffect
        val route = WorkspacePushNotifications(pushContext)
            .resolve(pendingDeepLink)
        val target = servers.firstOrNull { it.id == route?.serverId && it.projectUuid == route.projectUuid }
        if (target == null) { onDeepLinkHandled(); return@LaunchedEffect }
        if (user.selectedServerId.value != target.id) user.selectServer(target.id)
        // A cold target may be created after the initial startAllRepos pass.
        // Bootstrap it explicitly, including when another connection shows login.
        if (!target.needsToRelogin) eventsRepositoryStore.getOrCreate(target).loadServerSettings()
    }
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
    } else if (selectedServer?.projectUuid == null) {
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
    val selectedServerSnapshot by user.selectedServer.collectAsState()
    val selectedServerId by user.selectedServerId.collectAsState()
    val selectedServer = selectedServerSnapshot?.takeIf { it.id == selectedServerId }
    val currentUserRepository = remember(selectedServer?.id) {
        selectedServer?.let { eventsRepositoryStore.getOrCreate(it) }
    }
    val currentUser = key(selectedServer?.id) {
        currentUserRepository?.currentUser?.collectAsState()?.value
    }
    val accessToken by user.accessToken.collectAsState()
    val avatarAccessToken = selectedServer?.id?.let(user::getTokens)?.accessToken
        ?.takeIf { it == accessToken }
    val avatarUser = currentUser?.takeIf { avatarAccessToken != null }
    val avatarAuthHeaders = workspaceApiClient.authHeaders(selectedServer?.id)
    val pushOwnerProof = currentUserRepository?.verifiedOwnerAuthentication?.collectAsState()?.value
    val currentCallMessage by viewModel.currentCallMessage.collectAsState()
    var currentDestination = rememberSaveable { mutableStateOf(0 ) }
    var showNavigation by rememberSaveable { mutableStateOf(true) }
    var protectedBottomBarVisibility by remember { mutableStateOf<Boolean?>(null) }
    val setProtectedBottomBarStyle: (Boolean?) -> Unit = remember { { protectedBottomBarVisibility = it } }
    val protectedBottomBar = protectedBottomBarVisibility != null
    val navigationVisible = protectedBottomBarVisibility ?: showNavigation
    val selectDestination: (Int) -> Unit = { index ->
        currentDestination.value = index
        navController.navigate(destinationList[index].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(Unit) {
        eventsRepositoryStore.startAllRepos()
    }

    LaunchedEffect(pendingDeepLink, selectedServerId, selectedServer, pushOwnerProof, currentUser) {
        if (pendingDeepLink?.startsWith("workspace-push/") != true) return@LaunchedEffect
        val route = WorkspacePushNotifications(context).resolve(pendingDeepLink)
        if (route == null) { onDeepLinkHandled(); return@LaunchedEffect }
        val target = user.repo.getServer(route.serverId)
        if (target == null || target.projectUuid != route.projectUuid) { onDeepLinkHandled(); return@LaunchedEffect }
        if (selectedServerId != target.id) return@LaunchedEffect
        val repository = currentUserRepository ?: return@LaunchedEffect
        val account = confirmedAccountContext(workspaceApiClient, repository) ?: return@LaunchedEffect
        if (account.userUuid != route.userUuid) { onDeepLinkHandled(); return@LaunchedEffect }
        selectDestination(1)
    }

    LaunchedEffect(lifecycleOwner) {
        PushRegistrationWorker.schedule(context)
    }



    NavigationSuiteScaffold(
        layoutType = if (navigationVisible && !protectedBottomBar) {
            NavigationSuiteType.ShortNavigationBarCompact
        } else {
            NavigationSuiteType.None
        },
        navigationSuiteItems = {
            if (!navigationVisible || protectedBottomBar) return@NavigationSuiteScaffold

            destinationList.forEachIndexed { index, destination ->
                item(
                    icon = {
                        DestinationIcon(destination, avatarUser, selectedServer?.baseUrl.orEmpty(),
                            avatarAuthHeaders, avatarSize = 28.dp, contentDescription = destination.title)
                    },
                    selected = index == currentDestination.value,
                    onClick = { selectDestination(index) }
                )
            }
        }
    ) {

        CompositionLocalProvider(
            LocalBottomBarVisible provides { visible -> showNavigation = visible },
            LocalProtectedBottomBarStyle provides setProtectedBottomBarStyle,
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
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
                if (navigationVisible && protectedBottomBar) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color(0xFF333333), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .height(68.dp)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        destinationList.forEachIndexed { index, destination ->
                            val selected = index == currentDestination.value
                            Box(
                                Modifier.size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFF484848) else Color.Transparent)
                                    .selectable(selected, role = Role.Tab, onClick = { selectDestination(index) })
                                    .semantics { contentDescription = destination.title },
                                contentAlignment = Alignment.Center,
                            ) {
                                DestinationIcon(destination, avatarUser, selectedServer?.baseUrl.orEmpty(),
                                    avatarAuthHeaders, avatarSize = 32.dp, iconSize = 24.dp,
                                    tint = if (selected) Color.White else Color(0xFF757575), contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shared icon content; each navigation container retains its own layout and tab semantics. */
@Composable
private fun DestinationIcon(
    destination: Destinations,
    profile: UserResponseData?,
    baseUrl: String,
    authHeaders: List<AuthHeader>,
    avatarSize: Dp,
    iconSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String?,
) {
    if (destination == Profile && profile != null) {
        val avatarDescription = contentDescription
        Avatar(profile.avatar, baseUrl, authHeaders, null, profile.displayableName(),
            Modifier.size(avatarSize).then(if (avatarDescription != null) Modifier.semantics {
                this.contentDescription = avatarDescription
            } else Modifier), initialFontSize = 16.sp)
    } else {
        Icon(painterResource(destination.icon), contentDescription, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun HomeNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepositoryStore: EventsRepositoryStore
) {
    val navController = rememberNavController()
    val user = UserState.current
    val homeViewModelFactory = remember { HomeViewModelFactory(eventsRepositoryStore, workspaceApiClient, user) }
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
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicName, args.topicUuid, args.isDirectMessages, eventsRepositoryStore, args.userId, storage, args.firstUnreadMessageUuid) }
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
    val pushContext = LocalContext.current
    val pushServer by user.selectedServer.collectAsState()
    val pushRepository = pushServer?.let { eventsRepositoryStore.get(it.id) }
    val pushProof = pushRepository?.verifiedOwnerAuthentication?.collectAsState()?.value
    LaunchedEffect(pendingDeepLink, pushServer, pushProof) {
        if (pendingDeepLink?.startsWith("workspace-push/") != true) return@LaunchedEffect
        val route = WorkspacePushNotifications(pushContext).resolve(pendingDeepLink) ?: return@LaunchedEffect
        val server = pushServer?.takeIf { it.id == route.serverId && it.projectUuid == route.projectUuid } ?: return@LaunchedEffect
        val account = pushRepository?.let { confirmedAccountContext(workspaceApiClient, it) } ?: return@LaunchedEffect
        if (account.userUuid != route.userUuid) return@LaunchedEffect
        val response = workspaceApiClient.performRequest(PushStreamRequest(route.streamUuid), server.id, server)
        if (!account.isCurrent(workspaceApiClient, requireNotNull(pushRepository))) return@LaunchedEffect
        val stream = (response as? ApiResult.Success)?.value
            ?: run { onDeepLinkHandled(); return@LaunchedEffect }
        val topics = workspaceApiClient.performRequest(TopicsByIdsRequest(listOf(route.topicUuid)), server.id, server)
        if (!account.isCurrent(workspaceApiClient, requireNotNull(pushRepository))) return@LaunchedEffect
        val topic = (topics as? ApiResult.Success)?.value?.firstOrNull { it.uuid == route.topicUuid && it.streamUuid == route.streamUuid }
            ?: run { onDeepLinkHandled(); return@LaunchedEffect }
        val messages = workspaceApiClient.performRequest(MessagesByIdsRequest(listOf(route.messageUuid)), server.id, server)
        if (!account.isCurrent(workspaceApiClient, requireNotNull(pushRepository))) return@LaunchedEffect
        val message = (messages as? ApiResult.Success)?.value
            ?.firstOrNull { it.uuid == route.messageUuid && it.streamUuid == route.streamUuid && it.topicUuid == route.topicUuid }
            ?: run { onDeepLinkHandled(); return@LaunchedEffect }
        navController.navigate(ChatFlow.ChatDialog(stream?.displayableName() ?: "Workspace", route.streamUuid, topic?.name,
            route.topicUuid, stream?.directUserUuid != null, null, route.messageUuid)) {
            popUpTo<ChatFlow.ChatList>()
            launchSingleTop = true
        }
        onDeepLinkHandled()
    }
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
            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicName, args.topicUuid, args.isDirectMessages, eventsRepositoryStore, args.userId, storage, args.firstUnreadMessageUuid) }
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
            val infoContext = LocalContext.current.applicationContext
            val streamInfoViewModelFactory = remember { StreamInfoViewModelFactory(workspaceApiClient, args.streamUuid, args.topicUuid, eventsRepositoryStore, infoContext) }
            val streamInfoViewModel: StreamInfoViewModel = viewModel(factory = streamInfoViewModelFactory)
            StreamInfoView(streamInfoViewModel, navController)
        }

        composable<ChatFlow.CreateBase> {
            val creationBaseViewModelFactory = remember { CreationBaseViewModelFactory(eventsRepositoryStore) }
            var creationBaseViewModel: CreationBaseViewModel = viewModel(factory = creationBaseViewModelFactory)
            CreationBaseView(creationBaseViewModel, navController)
        }
        composable<ChatFlow.CreateStream> {
            val creationContext = LocalContext.current.applicationContext
            val createStreamViewModelFactory = remember { CreateStreamViewModelFactory(workspaceApiClient, eventsRepositoryStore, creationContext) }
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

        composable<LoginFlow.Projects> {
            val args = it.toRoute<LoginFlow.Projects>()
            val projectsViewModelFactory = remember { ProjectsViewModelFactory(workspaceApiClient, user, args.isFirstOrganization) }
            val projectsViewModel: ProjectsViewModel = viewModel(factory = projectsViewModelFactory)
            ProjectsScreen(projectsViewModel, navController)
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
            val creationContext = LocalContext.current.applicationContext
            val createStreamViewModelFactory = remember { CreateStreamViewModelFactory(workspaceApiClient, eventsRepositoryStore, creationContext) }
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
