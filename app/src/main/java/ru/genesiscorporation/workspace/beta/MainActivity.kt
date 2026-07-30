package ru.genesiscorporation.workspace.beta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.WorkspaceThemeMode
import ru.genesiscorporation.workspace.beta.data.navigation.WorkspaceDeepLink
import ru.genesiscorporation.workspace.beta.data.navigation.parseWorkspaceDeepLink
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatScreen
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ResolvedDeepLinkDestination
import ru.genesiscorporation.workspace.beta.modules.channelinfo.ChannelInfoScreen
import ru.genesiscorporation.workspace.beta.modules.channelinfo.ChannelInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoScreen
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerScreen
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.login.LoginScreen
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.modules.inbox.InboxScreen
import ru.genesiscorporation.workspace.beta.modules.feed.FeedScreen
import ru.genesiscorporation.workspace.beta.modules.feed.FeedViewModel
import ru.genesiscorporation.workspace.beta.modules.feed.MessageTimelineKind
import ru.genesiscorporation.workspace.beta.modules.drafts.DraftsScreen
import ru.genesiscorporation.workspace.beta.modules.drafts.DraftsViewModel
import ru.genesiscorporation.workspace.beta.modules.about.AboutScreen
import ru.genesiscorporation.workspace.beta.modules.externalintegrations.ExternalIntegrationsScreen
import ru.genesiscorporation.workspace.beta.modules.externalintegrations.ExternalIntegrationsViewModel
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileScreen
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.share.IncomingShareDialog
import ru.genesiscorporation.workspace.beta.modules.share.IncomingShareRequest
import ru.genesiscorporation.workspace.beta.modules.share.toIncomingShareRequestOrNull
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsScreen
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import ru.genesiscorporation.workspace.beta.ui.Avatar
import io.ktor.client.plugins.api.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager
import ru.genesiscorporation.workspace.beta.data.push.PushNavigationRequest
import ru.genesiscorporation.workspace.beta.ui.IncomingCall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private var pendingPushNavigation by mutableStateOf<PushNavigationRequest?>(null)
    private var pendingDeepLink by mutableStateOf<WorkspaceDeepLink?>(null)
    private var pendingIncomingShare by
        mutableStateOf<IncomingShareRequest?>(null)
    private val userState by viewModels<UserViewModel>()  {
        UserViewModelFactory(applicationContext)
    }
    private val networkState by viewModels<WorkspaceNetworkViewModel> {
        WorkspaceNetworkViewModelFactory(userState, applicationContext)
    }
    private val workspaceApiClient: WorkspaceAPIClient
        get() = networkState.apiClient
    private val eventsRepository: EventsRepository
        get() = networkState.eventsRepository
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager
        get() = networkState.pushDeviceRegistrationManager
    private val conversationStateStore: ConversationStateStore
        get() = networkState.conversationStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingShare = intent.toIncomingShareRequestOrNull(
            savedRequestId = savedInstanceState
                ?.getString(SAVED_INCOMING_SHARE_REQUEST_ID),
        )
        if (incomingShare != null) {
            pendingIncomingShare = incomingShare
            pendingPushNavigation = null
            pendingDeepLink = null
        } else if (savedInstanceState == null) {
            pendingPushNavigation = intent.pushNavigationRequest()
            pendingDeepLink = intent.workspaceDeepLink()
        }
        enableEdgeToEdge()
        setContent {
            val uiPreferences by userState.uiPreferences.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (uiPreferences.themeMode) {
                WorkspaceThemeMode.SYSTEM -> systemDarkTheme
                WorkspaceThemeMode.LIGHT -> false
                WorkspaceThemeMode.DARK -> true
            }
            WokspaceTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalUserState provides userState) {
                    ApplicationSwitcher(
                        workspaceApiClient = workspaceApiClient,
                        eventsRepository = eventsRepository,
                        pushDeviceRegistrationManager = pushDeviceRegistrationManager,
                        conversationStateStore = conversationStateStore,
                        pendingPushNavigation = pendingPushNavigation,
                        onPushNavigationHandled = { pendingPushNavigation = null },
                        pendingDeepLink = pendingDeepLink,
                        onDeepLinkHandled = { pendingDeepLink = null },
                        pendingIncomingShare = pendingIncomingShare,
                        onIncomingShareHandled = {
                            pendingIncomingShare = null
                            setIntent(
                                Intent(this, MainActivity::class.java)
                                    .setAction(Intent.ACTION_MAIN),
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingShare = intent.toIncomingShareRequestOrNull()
        if (incomingShare != null) {
            pendingPushNavigation = null
            pendingDeepLink = null
            pendingIncomingShare = incomingShare
        } else if (intent.action == Intent.ACTION_VIEW) {
            pendingPushNavigation = null
            pendingDeepLink = intent.workspaceDeepLink()
            pendingIncomingShare = null
        } else {
            pendingDeepLink = null
            pendingPushNavigation = intent.pushNavigationRequest()
            pendingIncomingShare = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingIncomingShare?.requestId?.let { requestId ->
            outState.putString(SAVED_INCOMING_SHARE_REQUEST_ID, requestId)
        }
        super.onSaveInstanceState(outState)
    }
}

private const val SAVED_INCOMING_SHARE_REQUEST_ID =
    "workspace.saved_incoming_share_request_id"

private fun Intent.pushNavigationRequest(): PushNavigationRequest? =
    PushNavigationRequest.fromIntentFields(
        providerChatKey = getStringExtra(
            PushNavigationRequest.EXTRA_PROVIDER_CHAT_KEY,
        ),
        topicName = getStringExtra(PushNavigationRequest.EXTRA_TOPIC_NAME),
        workspaceMessageId = getIntExtra(
            PushNavigationRequest.EXTRA_WORKSPACE_MESSAGE_ID,
            -1,
        ),
    )

private fun Intent.workspaceDeepLink(): WorkspaceDeepLink? =
    takeIf { action == Intent.ACTION_VIEW }
        ?.dataString
        ?.let(::parseWorkspaceDeepLink)

@Composable
fun RequestNotificationPermissionIfNeeded() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
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
    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("Уведомления отключены") },
            text = {
                Text(
                    "Без разрешения Android не покажет новые сообщения. " +
                        "Разрешение можно включить в настройках приложения.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                        activity.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
                        )
                    },
                ) {
                    Text("Открыть настройки")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDenied = false }) {
                    Text("Не сейчас")
                }
            },
        )
    }
}

@Composable
fun ApplicationSwitcher(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository,
    pushDeviceRegistrationManager: PushDeviceRegistrationManager,
    conversationStateStore: ConversationStateStore,
    pendingPushNavigation: PushNavigationRequest?,
    onPushNavigationHandled: () -> Unit,
    pendingDeepLink: WorkspaceDeepLink?,
    onDeepLinkHandled: () -> Unit,
    pendingIncomingShare: IncomingShareRequest?,
    onIncomingShareHandled: () -> Unit,
) {
    val user = LocalUserState.current
    val accessToken by user.accessToken.collectAsState()
    val activeAccountId by user.activeAccountId.collectAsState()
    val uiPreferencesOwnerKey by user.uiPreferencesOwnerKey.collectAsState()
    val activeAccount by user.activeAccount.collectAsState()
    val accounts by user.accounts.collectAsState()
    val baseUrl by user.baseUrl.collectAsState()
    val isAccessTokenLoaded by user.isAccessTokenLoaded.collectAsState()
    val initializationError by user.initializationError.collectAsState()
    val deepLink = pendingDeepLink
    val uiPreferencesReady =
        accessToken == null ||
            (activeAccountId != null && uiPreferencesOwnerKey == activeAccountId)

    val workspaceViewModelFactory = remember {
        WorkspaceViewModelFactory(
            workspaceApiClient,
            eventsRepository,
            pushDeviceRegistrationManager,
        )
    }
    val workspaceViewModel: WorkspaceViewModel = viewModel(factory = workspaceViewModelFactory)
    val matchingAccounts = deepLink
        ?.let { link -> accounts.filter(link::matches) }
        .orEmpty()
    val automaticTarget = matchingAccounts.singleOrNull()
    var automaticSwitchFailed by remember(deepLink) {
        mutableStateOf(false)
    }
    val isSwitchingForDeepLink =
        deepLink != null &&
            automaticTarget != null &&
            !automaticSwitchFailed &&
            activeAccountId != automaticTarget.accountId

    LaunchedEffect(deepLink, automaticTarget?.accountId, activeAccountId) {
        if (isSwitchingForDeepLink) {
            automaticSwitchFailed =
                !user.switchAccountAndWait(automaticTarget.accountId)
        }
    }
    if (!isAccessTokenLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (initializationError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.background)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Безопасное хранилище недоступно. Перезапустите приложение.",
                color = LocalWorkspaceColorsPalette.current.indicatorRed,
            )
        }
    } else if (!uiPreferencesReady) {
        FullScreenProgress()
    } else if (accessToken == null) {
        LoginNavigation(workspaceApiClient)
    } else if (
        deepLink != null &&
        activeAccount?.let(deepLink::matches) != true
    ) {
        if (isSwitchingForDeepLink) {
            FullScreenProgress()
        } else {
            DeepLinkAccountScreen(
                deepLink = deepLink,
                matchingAccounts = matchingAccounts,
                onAccountSelected = user::switchAccountAndWait,
                onConnectAccount = {
                    user.beginAddAccountAndWait()
                    deepLink.baseUrl?.let {
                        user.addBaseUrlAndWait(it)
                    }
                },
                onOpenCurrentAccount = onDeepLinkHandled,
            )
        }
    } else {
        key(activeAccountId ?: baseUrl) {
            WokspaceApp(
                workspaceViewModel,
                workspaceApiClient,
                eventsRepository,
                pushDeviceRegistrationManager,
                conversationStateStore,
                pendingPushNavigation,
                onPushNavigationHandled,
                deepLink,
                onDeepLinkHandled,
                pendingIncomingShare,
                onIncomingShareHandled,
            )
        }
    }
}

@Composable
private fun FullScreenProgress() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalWorkspaceColorsPalette.current.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeepLinkAccountScreen(
    deepLink: WorkspaceDeepLink,
    matchingAccounts: List<WorkspaceAccount>,
    onAccountSelected: suspend (String) -> Boolean,
    onConnectAccount: suspend () -> Unit,
    onOpenCurrentAccount: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val scope = rememberCoroutineScope()
    var operationInProgress by remember(deepLink) { mutableStateOf(false) }
    var actionError by remember(deepLink) { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeContentPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (matchingAccounts.isEmpty()) {
                    "Нужна другая организация"
                } else {
                    "Выберите аккаунт"
                },
                color = colors.textHeaders,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (matchingAccounts.isEmpty()) {
                    "Ссылка ведёт в ${deepLink.organizationId}. Подключите эту организацию, чтобы открыть нужный чат."
                } else {
                    "Ссылка доступна в нескольких сохранённых аккаунтах."
                },
                color = colors.textAdditional50,
                fontSize = 15.sp,
            )
            actionError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = colors.indicatorRed,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            matchingAccounts.forEach { account ->
                Button(
                    onClick = {
                        scope.launch {
                            operationInProgress = true
                            actionError = null
                            val switched = runCatching {
                                onAccountSelected(account.accountId)
                            }.getOrElse { false }
                            if (!switched) {
                                actionError =
                                    "Сохранённый аккаунт больше недоступен"
                            }
                            operationInProgress = false
                        }
                    },
                    enabled = !operationInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        account.displayName
                            ?: account.login.ifBlank { account.projectName },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (matchingAccounts.isEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            operationInProgress = true
                            actionError = null
                            runCatching { onConnectAccount() }
                                .onFailure {
                                    actionError =
                                        "Не удалось подготовить вход. Повторите попытку."
                                }
                            operationInProgress = false
                        }
                    },
                    enabled = !operationInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Подключить организацию")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onOpenCurrentAccount,
                enabled = !operationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть текущий аккаунт")
            }
        }
        if (operationInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun WokspaceApp(
    viewModel: WorkspaceViewModel,
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository,
    pushDeviceRegistrationManager: PushDeviceRegistrationManager,
    conversationStateStore: ConversationStateStore,
    pendingPushNavigation: PushNavigationRequest?,
    onPushNavigationHandled: () -> Unit,
    pendingDeepLink: WorkspaceDeepLink?,
    onDeepLinkHandled: () -> Unit,
    pendingIncomingShare: IncomingShareRequest?,
    onIncomingShareHandled: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val context = LocalContext.current
    val currentCallMessage by viewModel.currentCallMessage.collectAsState()
    var currentDestination by rememberSaveable { mutableIntStateOf(0) }
    var showBottomNavigation by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(
        pendingPushNavigation,
        pendingDeepLink,
        pendingIncomingShare?.requestId,
    ) {
        if (
            pendingPushNavigation != null ||
            pendingDeepLink != null ||
            pendingIncomingShare != null
        ) {
            navController.navigate(Chat.route) {
                popUpTo(Chat.route)
                launchSingleTop = true
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        RequestNotificationPermissionIfNeeded()
        NavHost(
            navController = navController,
            startDestination = Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomNavigation) 102.dp else 0.dp),
        ) {
            composable(Chat.route) {
                currentDestination = 0
                ChatNavigation(
                    workspaceApiClient = workspaceApiClient,
                    eventsRepository = eventsRepository,
                    conversationStateStore = conversationStateStore,
                    pendingPushNavigation = pendingPushNavigation,
                    onPushNavigationHandled = onPushNavigationHandled,
                    pendingDeepLink = pendingDeepLink,
                    onDeepLinkHandled = onDeepLinkHandled,
                    pendingIncomingShare = pendingIncomingShare,
                    onIncomingShareHandled = onIncomingShareHandled,
                    onBottomNavigationVisibilityChange = { showBottomNavigation = it },
                )
            }
            composable(Profile.route) {
                LaunchedEffect(Unit) { showBottomNavigation = true }
                currentDestination = 1
                ProfileNavigation(
                    workspaceApiClient,
                    eventsRepository,
                    pushDeviceRegistrationManager,
                    onBottomNavigationVisibilityChange = {
                        showBottomNavigation = it
                    },
                )
            }
        }
        if (showBottomNavigation) {
            WorkspaceBottomNavigation(
                selectedDestination = currentDestination,
                onChatClick = {
                    currentDestination = 0
                    navController.navigate(Chat.route) {
                        popUpTo(Chat.route)
                        launchSingleTop = true
                    }
                },
                onProfileClick = {
                    currentDestination = 1
                    navController.navigate(Profile.route) {
                        popUpTo(Chat.route)
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        val callMessage = currentCallMessage
        if (callMessage != null) {
            IncomingCall(callMessage, viewModel, context)
        }
    }
}

@Composable
private fun WorkspaceBottomNavigation(
    selectedDestination: Int,
    onChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val user = LocalUserState.current
    val baseUrl by user.baseUrl.collectAsState()
    val profile = user.userData

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(102.dp)
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(102.dp)
                .background(
                    colors.chatHeaderBackground,
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceAround,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selectedDestination == 0) colors.cardBackgroundActive
                        else Color.Transparent,
                    )
                    .selectable(
                        selected = selectedDestination == 0,
                        role = Role.Tab,
                        onClick = onChatClick,
                    )
                    .clearAndSetSemantics {
                        contentDescription = "Чаты"
                        selected = selectedDestination == 0
                        role = Role.Tab
                        onClick(label = "Открыть чаты") {
                            onChatClick()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.chat_bubble),
                    contentDescription = null,
                    tint = if (selectedDestination == 0) colors.iconActive else colors.iconBase,
                    modifier = Modifier.size(36.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selectedDestination == 1) colors.cardBackgroundActive
                        else Color.Transparent,
                    )
                    .selectable(
                        selected = selectedDestination == 1,
                        role = Role.Tab,
                        onClick = onProfileClick,
                    )
                    .clearAndSetSemantics {
                        contentDescription = "Профиль"
                        selected = selectedDestination == 1
                        role = Role.Tab
                        onClick(label = "Открыть профиль") {
                            onProfileClick()
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (profile != null) {
                    Avatar(
                        avatarUrn = profile.avatar,
                        baseUrl = baseUrl.orEmpty(),
                        color = null,
                        name = profile.displayableName(),
                        size = 36,
                        hasPadding = false,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_profile),
                        contentDescription = null,
                        tint = if (selectedDestination == 1) colors.iconActive else colors.iconBase,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ChatNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository,
    conversationStateStore: ConversationStateStore,
    pendingPushNavigation: PushNavigationRequest?,
    onPushNavigationHandled: () -> Unit,
    pendingDeepLink: WorkspaceDeepLink?,
    onDeepLinkHandled: () -> Unit,
    pendingIncomingShare: IncomingShareRequest?,
    onIncomingShareHandled: () -> Unit,
    onBottomNavigationVisibilityChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val user = LocalUserState.current
    val chatViewModelFactory = remember {
        ChatViewModelFactory(
            workspaceApiClient,
            user,
            eventsRepository,
            conversationStateStore,
        )
    }
    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
    LaunchedEffect(pendingIncomingShare?.requestId) {
        if (pendingIncomingShare != null) {
            navController.popBackStack(
                navController.graph.startDestinationId,
                false,
            )
        }
    }
    LaunchedEffect(pendingPushNavigation, pendingDeepLink) {
        val pushRequest = pendingPushNavigation
        val deepLink = pendingDeepLink
        if (pushRequest == null && deepLink == null) return@LaunchedEffect
        val catalogState = chatViewModel.queryState.first {
            it !is QueryState.Idle && it !is QueryState.Loading
        }
        val destination = when {
            catalogState is QueryState.Success && deepLink != null ->
                chatViewModel.resolveDeepLinkNavigation(deepLink)

            catalogState is QueryState.Success && pushRequest != null ->
                chatViewModel.resolvePushNavigation(pushRequest)
                    ?.let(ResolvedDeepLinkDestination::Dialog)

            deepLink != null ->
                chatViewModel.resolvePersistedDeepLinkNavigation(deepLink)

            else -> null
        }
        when (destination) {
            is ResolvedDeepLinkDestination.Dialog -> {
                navController.popBackStack(
                    navController.graph.startDestinationId,
                    false,
                )
                navController.navigate(destination.route) {
                    launchSingleTop = false
                }
            }

            is ResolvedDeepLinkDestination.TopicList -> {
                navController.popBackStack(
                    navController.graph.startDestinationId,
                    false,
                )
                navController.navigate(destination.route) {
                    launchSingleTop = false
                }
            }

            null -> Unit
        }
        if (
            catalogState !is QueryState.Success &&
            destination == null &&
            chatViewModel.actionError.value == null
        ) {
            chatViewModel.reportActionError(
                if (deepLink != null) {
                    "Не удалось открыть ссылку: список чатов недоступен"
                } else {
                    "Не удалось открыть уведомление: список чатов недоступен"
                }
            )
        }
        if (deepLink != null) {
            onDeepLinkHandled()
        } else {
            onPushNavigationHandled()
        }
    }
    NavHost(navController = navController, startDestination = ChatFlow.ChatList) {
        composable<ChatFlow.ChatList> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            ChatScreen(chatViewModel, navController)
        }
        composable<ChatFlow.Inbox> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            InboxScreen(chatViewModel, navController)
        }
        composable<ChatFlow.Feed> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            val feedViewModelFactory = remember {
                FeedViewModelFactory(
                    workspaceApiClient,
                    user,
                    MessageTimelineKind.FEED,
                )
            }
            val feedViewModel: FeedViewModel = viewModel(
                factory = feedViewModelFactory,
            )
            FeedScreen(
                feedViewModel,
                chatViewModel,
                navController,
                MessageTimelineKind.FEED,
            )
        }
        composable<ChatFlow.Starred> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            val starredViewModelFactory = remember {
                FeedViewModelFactory(
                    workspaceApiClient,
                    user,
                    MessageTimelineKind.STARRED,
                )
            }
            val starredViewModel: FeedViewModel = viewModel(
                factory = starredViewModelFactory,
            )
            FeedScreen(
                starredViewModel,
                chatViewModel,
                navController,
                MessageTimelineKind.STARRED,
            )
        }
        composable<ChatFlow.Drafts> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            val appContext = LocalContext.current.applicationContext
            val draftsViewModelFactory = remember(appContext) {
                DraftsViewModelFactory(
                    workspaceApiClient,
                    user,
                    conversationStateStore,
                    appContext,
                )
            }
            val draftsViewModel: DraftsViewModel = viewModel(
                factory = draftsViewModelFactory,
            )
            DraftsScreen(
                draftsViewModel,
                chatViewModel,
                navController,
            )
        }
        composable<ChatFlow.ChatDialog> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(false) }
            val args = it.toRoute<ChatFlow.ChatDialog>()
            val chatDialogViewModelFactory = remember {
                ChatDialogViewModelFactory(
                    workspaceApiClient,
                    user,
                    args.title,
                    args.chatId,
                    args.topicName,
                    args.topicUuid,
                    args.isDirectMessages,
                    eventsRepository,
                    args.userId,
                    args.focusProviderMessageId,
                    args.focusMessageUuid,
                    args.beginForwardMessageUuid,
                    args.draftStorageSlot,
                    conversationStateStore,
                )
            }
            val chatDialogViewModel: ChatDialogViewModel = viewModel(factory = chatDialogViewModelFactory)
            ChatDialogScreen(chatDialogViewModel, navController)
        }
        composable<ChatFlow.ChatTopic> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            val args = it.toRoute<ChatFlow.ChatTopic>()

            val chatTopicsViewModelFactory = remember { ChatTopicsViewModelFactory(workspaceApiClient, user, args.channelName, args.channelId, eventsRepository) }
            val chatTopicsViewModel: TopicsViewModel = viewModel(factory = chatTopicsViewModelFactory)
            TopicsScreen(chatTopicsViewModel, navController)
        }
        composable<ChatFlow.ChatUserInfo> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(true) }
            val args = it.toRoute<ChatFlow.ChatUserInfo>()
            val chatUserInfoViewModelFactory = remember { ChatUserInfoViewModelFactory(workspaceApiClient, args.userName, args.userId, args.avatarUrl, args.email, eventsRepository) }
            val chatUserInfoViewModel: ChatUserInfoViewModel = viewModel(factory = chatUserInfoViewModelFactory)
            ChatUserInfoScreen(chatUserInfoViewModel, navController)
        }
        composable<ChatFlow.ChannelInfo> {
            LaunchedEffect(Unit) { onBottomNavigationVisibilityChange(false) }
            val args = it.toRoute<ChatFlow.ChannelInfo>()
            val factory = remember {
                ChannelInfoViewModelFactory(
                    workspaceApiClient,
                    args.channelId,
                    eventsRepository,
                )
            }
            val channelInfoViewModel: ChannelInfoViewModel = viewModel(factory = factory)
            ChannelInfoScreen(channelInfoViewModel, navController)
        }
    }
    pendingIncomingShare?.let { request ->
        IncomingShareDialog(
            request = request,
            viewModel = chatViewModel,
            conversationStateStore = conversationStateStore,
            onDismiss = onIncomingShareHandled,
            onCommitted = { target ->
                onIncomingShareHandled()
                navController.popBackStack(
                    navController.graph.startDestinationId,
                    false,
                )
                navController.navigate(
                    ChatFlow.ChatDialog(
                        title = target.chatTitle,
                        chatId = target.streamUuid,
                        topicName = target.topicName,
                        topicUuid = target.topicUuid,
                        isDirectMessages = target.isDirectMessages,
                        userId = null,
                    ),
                )
            },
        )
    }
}

@Composable
fun LoginNavigation(workspaceApiClient: WorkspaceAPIClient) {
    val user = LocalUserState.current
    val baseUrl by user.baseUrl.collectAsState()
    val navController = rememberNavController()
    val startDestination = remember(baseUrl) {
        if (baseUrl.isNullOrBlank()) LoginFlow.ChooseServer else LoginFlow.Login
    }
    NavHost(navController = navController, startDestination = startDestination) {
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

@Composable
fun ProfileNavigation(
    workspaceApiClient: WorkspaceAPIClient,
    eventsRepository: EventsRepository,
    pushDeviceRegistrationManager: PushDeviceRegistrationManager,
    onBottomNavigationVisibilityChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val user = LocalUserState.current
    val appContext = LocalContext.current.applicationContext
    NavHost(navController = navController, startDestination = ProfileFlow.Main) {
        composable<ProfileFlow.Main> {
            LaunchedEffect(Unit) {
                onBottomNavigationVisibilityChange(true)
            }
            val profileViewModelFactory = remember {
                ProfileViewModelFactory(
                    user,
                    workspaceApiClient,
                    pushDeviceRegistrationManager,
                    appContext,
                )
            }
            var profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)
            ProfileScreen(
                viewModel = profileViewModel,
                onOpenAbout = { navController.navigate(ProfileFlow.About) },
                onOpenExternalIntegrations = {
                    navController.navigate(ProfileFlow.ExternalIntegrations)
                },
            )
        }
        composable<ProfileFlow.About> {
            LaunchedEffect(Unit) {
                onBottomNavigationVisibilityChange(false)
            }
            AboutScreen(onBack = navController::popBackStack)
        }
        composable<ProfileFlow.ExternalIntegrations> {
            LaunchedEffect(Unit) {
                onBottomNavigationVisibilityChange(false)
            }
            val factory = remember {
                ExternalIntegrationsViewModelFactory(
                    userViewModel = user,
                    client = workspaceApiClient,
                    eventsRepository = eventsRepository,
                )
            }
            val externalIntegrationsViewModel:
                ExternalIntegrationsViewModel = viewModel(factory = factory)
            ExternalIntegrationsScreen(
                viewModel = externalIntegrationsViewModel,
                onBack = navController::popBackStack,
            )
        }
        composable<ProfileFlow.Login> {
            LaunchedEffect(Unit) {
                onBottomNavigationVisibilityChange(false)
            }
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
