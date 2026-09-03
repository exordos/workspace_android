package ru.genesiscorporation.workspace.beta.modules.streaminfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.JitsiStyleRoomNameGenerator
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.UserCell
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL
import kotlin.collections.count
import kotlin.collections.mapNotNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamInfoView(
    viewModel: StreamInfoViewModel,
    navController: NavHostController
) {

    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val topic by viewModel.topic.collectAsStateWithLifecycle()
    val streamBindings by viewModel.streamBindings.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUserId by viewModel.client.userViewModel.repo.userIdFlow.collectAsStateWithLifecycle(
        initialValue = 0
    )
//    val profile = viewModel.repo.users.collectAsState().value.firstOrNull { it.userId.toString() == viewModel.userId }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
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
            modifier = Modifier
                .fillMaxSize()
                .background(LocalWorkspaceColorsPalette.current.background)
                .padding(
                    16.dp,
                    innerPadding.calculateTopPadding(),
                    16.dp,
                    innerPadding.calculateBottomPadding()
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    null,
                    viewModel.client.userViewModel.baseUrl.value ?: "",
                    viewModel.client.authHeaders(),
                    stream.color,
                    stream.name,
                    Modifier
                        .padding(end = 12.dp)
                        .size(64.dp)
                )
                Column {
                    Text(
                        text = stream.name,
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 20.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    val currentStreamBindings = streamBindings[viewModel.streamUuid]
                    if (currentStreamBindings != null && currentStreamBindings.count() > 0) {
                        val currentBindedOnlineUsers = currentStreamBindings.mapNotNull { binding -> users.firstOrNull { binding.userUuid == it.uuid && it.status == "active" } }
                        var baseText = context.resources.getQuantityString(
                            R.plurals.participants_count, currentStreamBindings.count(), currentStreamBindings.count()
                        )
                        if (currentBindedOnlineUsers.count() > 0) {
                            baseText += ", ${currentBindedOnlineUsers.count()} онлайн"
                        }
                        Text(
                            baseText,
                            color = LocalWorkspaceColorsPalette.current.textAdditional30,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                        )
                    }
                }
            }
//            ActionButtonsRow(viewModel, navController)
            NotificationButtonsRow(viewModel)
            val summary = topic?.summary
            if (summary != null) {
                TopicSummary(summary)
            }
            BoundUsers(viewModel, navController)
        }
    }
}

@Composable
fun ActionButtonsRow(
    viewModel: StreamInfoViewModel,
    navController: NavHostController
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {

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
                painter = painterResource(id = R.drawable.ic_call),
                contentDescription = "Call"
            )
        }
        Button(
            onClick = {

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
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = "Chat"
            )
        }
        Button(
            onClick = {

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
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = "Chat"
            )
        }
    }
}

@Composable
fun NotificationButtonsRow(
    viewModel: StreamInfoViewModel
) {
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Уведомления",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "(Для всего чата)",
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 12.sp,
                fontFamily = InterFontFamily,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(
                    width = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.setStreamNotificationMode("mentions_only")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (stream.notificationMode == "mentions_only")  LocalWorkspaceColorsPalette.current.cardBackgroundBase else Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.iconBase
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mentions),
                    contentDescription = "Call"
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.setStreamNotificationMode("muted")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (stream.notificationMode == "muted")  LocalWorkspaceColorsPalette.current.cardBackgroundBase else Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.iconBase
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_notifications_off_large),
                    contentDescription = "Chat"
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.setStreamNotificationMode("all_messages")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (stream.notificationMode == "all_messages")  LocalWorkspaceColorsPalette.current.cardBackgroundBase else Color.Transparent,
                    contentColor = LocalWorkspaceColorsPalette.current.iconBase
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_notifications_large),
                    contentDescription = "Chat"
                )
            }
        }
    }
}

@Composable
fun BoundUsers(
    viewModel: StreamInfoViewModel,
    navController: NavHostController
) {
    val streamBindings by viewModel.streamBindings.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    Column {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "Участники",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                navController.navigate(ChatFlow.AddUsersToStream(viewModel.streamUuid))
            }) {
                Image(
                    painter = painterResource(id = R.drawable.ic_person_add),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        val currentStreamBindings = streamBindings[viewModel.streamUuid]
        if (currentStreamBindings != null && currentStreamBindings.count() > 0) {
            val currentBindedOnlineUsers =
                currentStreamBindings.mapNotNull { binding -> users.firstOrNull { binding.userUuid == it.uuid } }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                currentBindedOnlineUsers.forEach { user ->
                    UserRow(
                        viewModel,
                        user,
                        navController
                    )
                }
            }
        }
    }
}

@Composable
fun TopicSummary(
    summary: String
) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Контекст топика",
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "(AI✨)",
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 12.sp,
                fontFamily = InterFontFamily,
            )
        }
        Text(
            summary,
            color = LocalWorkspaceColorsPalette.current.textHeaders,
            fontSize = 12.sp,
            fontFamily = InterFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(
                    width = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun UserRow(
    viewModel: StreamInfoViewModel,
    user: UserResponseData,
    navController: NavHostController
) {
    Column {
        val baseUrl = viewModel.client.userViewModel.baseUrl.collectAsState().value
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = {
                    navController.navigate(
                        ChatFlow.ChatUserInfo(
                            user.displayableName(),
                            user.uuid,
                            user.avatar,
                            user.email ?: ""
                        )
                    )
                })
        ) {
            Avatar(
                user.avatar,
                baseUrl ?: "",
                viewModel.client.authHeaders(),
                null,
                user.displayableName(),
                Modifier
                    .padding(end = 12.dp)
                    .size(30.dp)
            )
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = user.displayableName(),
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.statusDescription(),
                    color = LocalWorkspaceColorsPalette.current.textAdditional50,
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = LocalWorkspaceColorsPalette.current.divider
        )
    }
}
