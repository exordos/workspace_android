package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UsersViewModelFactory
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.users.UsersScreen
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel
import ru.genesiscorporation.workspace.beta.ui.AddChatToFolder
import ru.genesiscorporation.workspace.beta.ui.CreateFolder
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
) {
    var showDetail by remember { mutableStateOf(false) }
    var showUserList by remember { mutableStateOf(false) }
    var showAddFolderView by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val folders by chatViewModel.folders.collectAsStateWithLifecycle()
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val createState by chatViewModel.createQueryState.collectAsStateWithLifecycle()
    val chatToAdd by chatViewModel.chatToAdd.collectAsState()
    val usersViewModelFactory = remember { UsersViewModelFactory(chatViewModel.client) }
    val usersViewModel: UsersViewModel = viewModel(factory = usersViewModelFactory)
    val colors = LocalWorkspaceColorsPalette.current

    BackHandler(enabled = showDetail) {
        showDetail = false
        scope.launch { chatViewModel.updateSelectedChat(null) }
    }

    LaunchedEffect(createState) {
        if (createState is QueryState.Success) {
            chatViewModel.createdStream?.let { createdStream ->
                chatViewModel.currentStreamId = createdStream.uuid
                navController.navigate(
                    ChatFlow.ChatDialog(
                        createdStream.name,
                        createdStream.uuid,
                        null,
                        createdStream.defaultTopicUuid.orEmpty(),
                        true,
                        null,
                    ),
                )
                chatViewModel.createdStream = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        MessengerTopBar(
            hasNotifications = folders.sumOf { it.unreadCount } > 0,
            detailOpen = showDetail,
            onNavigationClick = {
                if (showDetail) {
                    showDetail = false
                    scope.launch { chatViewModel.updateSelectedChat(null) }
                }
            },
            onNewChat = { showUserList = true },
        )
        SearchField(
            value = searchQuery,
            onValueChange = {
                showDetail = false
                chatViewModel.onSearchQueryChange(it)
            },
        )
        FolderTabs(
            folders = folders,
            selected = currentlySelectedFolder,
            onSelected = { folder ->
                showDetail = false
                scope.launch { chatViewModel.updateSelectedChat(null) }
                chatViewModel.updateCurrentlySelectedFolder(folder)
            },
            onAddFolder = { showAddFolderView = true },
        )
        ChatWithTopics(
            chatViewModel = chatViewModel,
            navController = navController,
            showDetail = showDetail,
            onShowDetailChange = { showDetail = it },
        )
    }

    if (showUserList) {
        UsersScreen(
            usersViewModel,
            onUserSelected = { userResponse ->
                showUserList = false
                scope.launch { chatViewModel.createPrivateStream(userResponse) }
            },
            onDismiss = { showUserList = false },
        )
    }
    if (showAddFolderView) {
        ModalScrim { showAddFolderView = false }
        CreateFolder(
            onCreateButtonTap = { folderName ->
                scope.launch { chatViewModel.addFolder(folderName) }
                showAddFolderView = false
            },
            onDismiss = { showAddFolderView = false },
        )
    }
    chatToAdd?.let { stream ->
        ModalScrim { chatViewModel.onChatToAddChange(null) }
        AddChatToFolder(
            folders,
            stream,
            onAddButtonTap = { _, _ ->
                chatViewModel.onChatToAddChange(null)
            },
        )
    }
}

@Composable
private fun MessengerTopBar(
    hasNotifications: Boolean,
    detailOpen: Boolean,
    onNavigationClick: () -> Unit,
    onNewChat: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(colors.background)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_figma_menu),
            contentDescription = if (detailOpen) "Назад к чатам" else "Меню",
            tint = colors.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(32.dp)
                .clickable(onClick = onNavigationClick),
        )
        Text(
            text = "Мессенджер",
            color = colors.textHeaders,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontFamily = NavigationFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_figma_notifications),
                    contentDescription = "Уведомления",
                    tint = colors.iconBase,
                    modifier = Modifier.size(32.dp),
                )
                if (hasNotifications) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .background(colors.noticeCounterBadge, CircleShape),
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_figma_new_chat),
                contentDescription = "Новый чат",
                tint = colors.textAdditional30,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onNewChat),
            )
        }
    }
}

@Composable
private fun SearchField(
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
            modifier = Modifier.weight(1f),
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
private fun FolderTabs(
    folders: List<FolderResponseData>,
    selected: FolderResponseData?,
    onSelected: (FolderResponseData) -> Unit,
    onAddFolder: () -> Unit,
) {
    if (folders.isEmpty()) return
    val colors = LocalWorkspaceColorsPalette.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(folders, key = { it.uuid }) { folder ->
            val isSelected = folder.uuid == selected?.uuid
            Column(
                modifier = Modifier
                    .height(44.dp)
                    .clickable { onSelected(folder) }
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.title,
                        color = if (isSelected) colors.textHeaders else colors.textAdditional30,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = NavigationFontFamily,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (folder.unreadCount > 0) {
                        UnreadBadge(
                            count = folder.unreadCount,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(88.dp)
                        .background(if (isSelected) colors.textHeaders else Color.Transparent),
                )
            }
        }
        item {
            Text(
                text = "+",
                color = colors.textAdditional30,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAddFolder)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ModalScrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
    )
}
