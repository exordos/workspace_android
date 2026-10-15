package ru.genesiscorporation.workspace.beta.modules.streaminfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.AuthHeader
import ru.genesiscorporation.workspace.beta.modules.share.shareWorkspaceLink
import ru.genesiscorporation.workspace.beta.modules.share.workspaceStreamShareLink
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import kotlin.collections.count
import kotlin.collections.mapNotNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamInfoView(
    viewModel: StreamInfoViewModel,
    navController: NavHostController
) {

    val streamSnapshot by viewModel.stream.collectAsStateWithLifecycle()
    if (streamSnapshot?.encryption == true || viewModel.hasMountedProtectedStream) {
        ProtectedStreamInfoView(viewModel, navController)
        return
    }
    val topic by viewModel.topic.collectAsStateWithLifecycle()
    val streamBindings by viewModel.streamBindings.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                            contentDescription = stringResource(R.string.stream_info_back)
                        )
                    }
                },
                actions = { }
            )
        },
    ) { innerPadding ->
        val stream = streamSnapshot
        if (stream == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding).background(LocalWorkspaceColorsPalette.current.background),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.stream_info_unavailable), color = LocalWorkspaceColorsPalette.current.textAdditional50,
                    fontFamily = InterFontFamily, modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
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
                    viewModel.client.userViewModel?.baseUrl?.value ?: "",
                    viewModel.client.authHeaders(),
                    stream.color,
                    stream.displayableName(),
                    Modifier
                        .padding(end = 12.dp)
                        .size(64.dp)
                )
                Column {
                    Text(
                        text = stream.displayableName(),
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
            val shareLink = workspaceStreamShareLink(
                viewModel.client.userViewModel?.baseUrl?.value ?: "",
                viewModel.client.userViewModel?.selectedServer?.value?.projectUuid ?: "",
                stream.uuid
            )
            ActionButtonsRow(
                shareEnabled = shareLink != null,
                onShare = { shareLink?.let { shareWorkspaceLink(context, stream.displayableName(), it) } }
            )
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
    shareEnabled: Boolean,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onShare,
            enabled = shareEnabled,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalWorkspaceColorsPalette.current.cardBackgroundBase,
                contentColor = LocalWorkspaceColorsPalette.current.iconBase,
                disabledContainerColor = LocalWorkspaceColorsPalette.current.cardBackgroundBase,
                disabledContentColor = LocalWorkspaceColorsPalette.current.iconDisable
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = "Поделиться стримом"
            )
        }
    }
}

@Composable
fun NotificationButtonsRow(
    viewModel: StreamInfoViewModel
) {
    val stream = viewModel.stream.collectAsStateWithLifecycle().value ?: return
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
    val selectedServer by viewModel.client.requireUserViewModel().selectedServer.collectAsStateWithLifecycle()
    val baseUrl = selectedServer?.takeIf { it.id == viewModel.repo.serverId }?.baseUrl.orEmpty()
    val authHeaders = viewModel.client.authHeaders(viewModel.repo.serverId)
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
                        user,
                        baseUrl,
                        authHeaders,
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

internal data class UserRowStyle(
    val name: TextStyle = TextStyle(fontSize = 14.sp, fontFamily = InterFontFamily),
    val subtitle: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = InterFontFamily),
    val dividerColor: Color = Color.Unspecified,
    val height: Dp? = null,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val textSpacing: Dp = 0.dp,
    val avatarInitialFontSize: TextUnit = 24.sp,
)

/** The caller supplies the server/auth snapshot; rows never resolve a different selected account. */
@Composable
internal fun UserRow(
    user: UserResponseData,
    baseUrl: String,
    authHeaders: List<AuthHeader>,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    style: UserRowStyle = UserRowStyle(),
    nameLeading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalWorkspaceColorsPalette.current
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(style.height?.let { Modifier.height(it) } ?: Modifier)
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
                .padding(style.contentPadding)
        ) {
            Avatar(
                user.avatar,
                baseUrl,
                authHeaders,
                null,
                user.displayableName(),
                Modifier
                    .padding(end = 12.dp)
                    .size(30.dp),
                initialFontSize = style.avatarInitialFontSize,
            )
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(style.textSpacing, Alignment.CenterVertically),
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    nameLeading?.invoke()
                    Text(
                        text = user.displayableName(),
                        color = if (style.name.color == Color.Unspecified) palette.textHeaders else style.name.color,
                        style = style.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = user.statusDescription(),
                    color = if (style.subtitle.color == Color.Unspecified) palette.textAdditional50 else style.subtitle.color,
                    style = style.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = if (style.dividerColor == Color.Unspecified) palette.divider else style.dividerColor
        )
    }
}
