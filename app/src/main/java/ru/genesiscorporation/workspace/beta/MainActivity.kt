package ru.genesiscorporation.workspace.beta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.ktor.client.HttpClient
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatScreen
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogScreen
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerScreen
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerViewModel
import ru.genesiscorporation.workspace.beta.modules.login.LoginScreen
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileScreen
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsScreen
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

class MainActivity : ComponentActivity() {

    private val workspaceApiClient: WorkspaceAPIClient by lazy {
        WorkspaceAPIClient(HttpClient(), userState)
    }
    private val userState by viewModels<UserViewModel>()  {
        UserViewModelFactory(applicationContext)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WokspaceTheme {
                CompositionLocalProvider(UserState provides userState) {
                    ApplicationSwitcher(workspaceApiClient)
                }
            }
        }
    }
}

@Composable
fun ApplicationSwitcher(workspaceApiClient: WorkspaceAPIClient) {
    val user = UserState.current
    val apiKey by user.apiKey.collectAsState()
    val workspaceViewModelFactory = remember { WorkspaceViewModelFactory(workspaceApiClient) }
    var workspaceViewModel: WorkspaceViewModel = viewModel(factory = workspaceViewModelFactory)
    if (apiKey == null) {
        LoginNavigation(workspaceApiClient)
    } else {
        WokspaceApp( workspaceViewModel, workspaceApiClient)
    }
}

@Composable
fun WokspaceApp(
    viewModel: WorkspaceViewModel,
    workspaceApiClient: WorkspaceAPIClient
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var navController = rememberNavController()
    val destinationList = listOf<Destinations>(
        Chat,
        Profile
    )
    val user = UserState.current
    var currentDestination = rememberSaveable { mutableStateOf(0 ) }
    val profileViewModelFactory = remember { ProfileViewModelFactory(workspaceApiClient, user) }
    var profileViewModel: ProfileViewModel = viewModel(factory = profileViewModelFactory)

    LaunchedEffect(lifecycleOwner) {
//        viewModel.registerForEvents()
//        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//            viewModel.startLongPolling()
//            awaitCancellation()
//        }
    }

    NavigationSuiteScaffold(
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
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                NavHost(navController = navController, startDestination = Chat.route) {
                    composable(Chat.route) {
                        ChatNavigation(workspaceApiClient)
                    }
                    composable(Profile.route) {
                        ProfileScreen(profileViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatNavigation(workspaceApiClient: WorkspaceAPIClient) {
    val navController = rememberNavController()
    val user = UserState.current
    val chatViewModelFactory = remember { ChatViewModelFactory(workspaceApiClient, user) }
    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
    NavHost(navController = navController, startDestination = ChatFlow.ChatList) {
        composable<ChatFlow.ChatList> {
            ChatScreen(chatViewModel, navController)
        }
        composable<ChatFlow.ChatDialog> {
            val args = it.toRoute<ChatFlow.ChatDialog>()

            val chatDialogViewModelFactory = remember { ChatDialogViewModelFactory(workspaceApiClient, user, args.title, args.chatId, args.topicId, args.isDirectMessages) }
            val chatDialogViewModel: ChatDialogViewModel = viewModel(factory = chatDialogViewModelFactory)
            ChatDialogScreen(chatDialogViewModel, navController)
        }
        composable<ChatFlow.ChatTopic> {
            val args = it.toRoute<ChatFlow.ChatTopic>()

            val chatTopicsViewModelFactory = remember { ChatTopicsViewModelFactory(workspaceApiClient, user, args.channelName, args.channelId) }
            val chatTopicsViewModel: TopicsViewModel = viewModel(factory = chatTopicsViewModelFactory)
            TopicsScreen(chatTopicsViewModel, navController)
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
            LoginScreen(loginViewModel)
        }
    }
}


enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WokspaceTheme() {
        Greeting("Android")
    }
}