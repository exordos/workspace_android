package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatdialog.JitsiStyleRoomNameGenerator
import ru.genesiscorporation.workspace.beta.modules.chatdialog.pastEpochSecondsToRelativeRu
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatUserInfoScreen(
    viewModel: ChatUserInfoViewModel,
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel.createQueryState.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is QueryState.Success) {
            val createdStream = viewModel.createdStream
            if (createdStream != null) {
                val shouldSendMessage = viewModel.shouldSendMessage
                if (shouldSendMessage) {
//                    viewModel.shouldSendMessage = false
                    val roomName = JitsiStyleRoomNameGenerator.generate()
                    val messageText = "${viewModel.repo.jitsiServerUrl}/${roomName}"
                    scope.launch {
                        viewModel.sendTextMessage(messageText, createdStream)
                    }
                    val options = JitsiMeetConferenceOptions.Builder()
                        .setServerURL(URL(viewModel.repo.jitsiServerUrl))
                        .setRoom(roomName)
                        .build()
                    JitsiMeetActivity.launch(context, options)
                    viewModel.shouldSendMessage = false
                } else {
                    viewModel.createdStream = null
                    navController.navigate(
                        ChatFlow.ChatDialog(
                            createdStream.name,
                            createdStream.uuid,
                            null,
                            createdStream.defaultTopicUuid ?: "",
                            true,
                            null
                        )
                    ) {
                        popUpTo<ChatFlow.ChatList> {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.surface,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = { }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.surface)
                .padding(16.dp, innerPadding.calculateTopPadding(), 16.dp, innerPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    user.avatar,
                    viewModel.client.userViewModel.baseUrl.value ?: "",
                    viewModel.client.authHeaders(),
                    null,
                    user.displayableName(),
                    Modifier.size(64.dp)
                )
                Text(
                    text = viewModel.userName,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 20.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.shouldSendMessage = true
                        scope.launch {
                            viewModel.createPrivateStream(user)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalWorkspaceColorsPalette.current.cardBackgroundBase,
                        contentColor = LocalWorkspaceColorsPalette.current.iconBase
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.call),
                        contentDescription = "Call"
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.createPrivateStream(user)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalWorkspaceColorsPalette.current.cardBackgroundBase,
                        contentColor = LocalWorkspaceColorsPalette.current.iconBase
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.chat_bubble),
                        contentDescription = "Chat"
                    )
                }
            }
            ProfileRow(
                "Email",
                viewModel.email,
                R.drawable.ic_mail
            )
            ProfileRow(
                "ID пользователя",
                user.uuid,
                R.drawable.ic_userid
            )
        }
    }
}

@Composable
fun ProfileRow(
    title: String,
    text: String,
    imageId: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 8.dp)
    ) {
        Image(
            painter = painterResource(id = imageId),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 16.dp)
        )
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 12.sp,
                fontFamily = InterFontFamily,
            )
            Text(
                text = text,
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
            )
        }
    }
}