package ru.genesiscorporation.workspace.beta.modules.activity

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.FolderTabs
import ru.genesiscorporation.workspace.beta.modules.inbox.buildInboxGroups
import ru.genesiscorporation.workspace.beta.modules.inbox.inboxUnreadCount
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
fun MyActivityScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    onOpenChats: () -> Unit,
) {
    val context = LocalContext.current
    val folders by chatViewModel.folders.collectAsStateWithLifecycle()
    val selectedFolder by
        chatViewModel.currentlySelectedFolder.collectAsStateWithLifecycle()
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by
        chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val users by chatViewModel.users.collectAsStateWithLifecycle()
    val unreadCount = remember(streams, topicsByStream, users) {
        inboxUnreadCount(buildInboxGroups(streams, topicsByStream, users))
    }

    MyActivityContent(
        folders = folders,
        selectedFolder = selectedFolder,
        inboxUnreadCount = unreadCount,
        onOpenChats = onOpenChats,
        onFolderSelected = { folder ->
            chatViewModel.updateCurrentlySelectedFolder(folder)
            onOpenChats()
        },
        onManageFolders = {
            navController.navigate(ChatFlow.FolderDisplay) {
                launchSingleTop = true
            }
        },
        onDestinationSelected = { destination ->
            navController.navigate(
                when (destination) {
                    MyActivityDestination.INBOX -> ChatFlow.Inbox
                    MyActivityDestination.STARRED -> ChatFlow.Starred
                    MyActivityDestination.PINNED -> ChatFlow.Pinned
                    MyActivityDestination.MENTIONS -> ChatFlow.Mentions
                    MyActivityDestination.REACTIONS -> {
                        Toast.makeText(
                            context,
                            "Реакции станут доступны после " +
                                "добавления backend API",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@MyActivityContent
                    }
                    MyActivityDestination.DRAFTS -> ChatFlow.Drafts
                    MyActivityDestination.FEED -> ChatFlow.Feed
                },
            )
        },
    )
}

@Composable
internal fun MyActivityContent(
    folders: List<FolderResponseData>,
    selectedFolder: FolderResponseData?,
    inboxUnreadCount: Int,
    onOpenChats: () -> Unit,
    onFolderSelected: (FolderResponseData) -> Unit,
    onManageFolders: () -> Unit,
    onDestinationSelected: (MyActivityDestination) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val destinations = remember(searchQuery) {
        supportedMyActivityDestinations(searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        MyActivityTopBar(onOpenChats = onOpenChats)
        MyActivitySearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
        )
        FolderTabs(
            folders = folders,
            selected = selectedFolder,
            onSelected = onFolderSelected,
            onAddFolder = onManageFolders,
            onManageFolder = { onManageFolders() },
        )
        if (destinations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Ничего не найдено",
                    color = colors.textAdditional50,
                    fontSize = 14.sp,
                    fontFamily = NavigationFontFamily,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    top = 6.dp,
                    end = 12.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(destinations, key = MyActivityDestination::name) {
                    destination ->
                    MyActivityRow(
                        destination = destination,
                        unreadCount = if (
                            destination == MyActivityDestination.INBOX
                        ) {
                            inboxUnreadCount
                        } else {
                            0
                        },
                        onClick = { onDestinationSelected(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MyActivityTopBar(onOpenChats: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = "Моя активность",
            color = colors.textHeaders,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = NavigationFontFamily,
            modifier = Modifier
                .align(Alignment.Center)
                .semantics { heading() },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clickable(onClick = onOpenChats)
                .semantics {
                    role = Role.Button
                    contentDescription = "Открыть мессенджер"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_figma_new_chat),
                contentDescription = null,
                tint = colors.iconActive,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun MyActivitySearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(36.dp)
            .background(colors.searchBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(24.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = NavigationFontFamily,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Поиск по моей активности"
                },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Найти",
                            color = colors.textAdditional30,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontFamily = NavigationFontFamily,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun MyActivityRow(
    destination: MyActivityDestination,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val (icon, indicator) = when (destination) {
        MyActivityDestination.INBOX ->
            R.drawable.ic_mail to colors.indicatorPurple
        MyActivityDestination.STARRED ->
            R.drawable.ic_star to colors.indicatorBlue
        MyActivityDestination.PINNED ->
            R.drawable.ic_activity_bookmark to colors.indicatorRed
        MyActivityDestination.MENTIONS ->
            R.drawable.ic_activity_mention to colors.indicatorYellow
        MyActivityDestination.REACTIONS ->
            R.drawable.ic_activity_reaction to colors.indicatorGreen
        MyActivityDestination.DRAFTS ->
            R.drawable.ic_draft to colors.indicatorPink
        MyActivityDestination.FEED ->
            R.drawable.ic_feed to colors.indicatorOrange
    }
    val safeUnreadCount = unreadCount.coerceAtLeast(0)
    val semanticLabel = if (safeUnreadCount > 0) {
        "${destination.title}, непрочитанных: $safeUnreadCount"
    } else {
        destination.title
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.cardBackgroundActive, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = semanticLabel
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(indicator, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = destination.title,
            color = colors.textHeaders,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = NavigationFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
        if (safeUnreadCount > 0) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .widthIn(min = 20.dp)
                    .background(colors.noticeCounterBadge, CircleShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = safeUnreadCount.coerceAtMost(999).toString(),
                    color = colors.noticeOnBadge,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NavigationFontFamily,
                )
            }
        }
    }
}
