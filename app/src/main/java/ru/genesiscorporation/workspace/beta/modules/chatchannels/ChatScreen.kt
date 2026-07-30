package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import ru.genesiscorporation.workspace.beta.modules.inbox.buildInboxGroups
import ru.genesiscorporation.workspace.beta.modules.inbox.inboxUnreadCount
import ru.genesiscorporation.workspace.beta.modules.users.UsersScreen
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel
import ru.genesiscorporation.workspace.beta.ui.AddChatToFolder
import ru.genesiscorporation.workspace.beta.ui.CreateFolder
import ru.genesiscorporation.workspace.beta.ui.CreateChannelDialog
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
) {
    var showDetail by rememberSaveable { mutableStateOf(false) }
    var showUserList by rememberSaveable { mutableStateOf(false) }
    var showNewChatChooser by rememberSaveable { mutableStateOf(false) }
    var showCreateChannel by rememberSaveable { mutableStateOf(false) }
    var showAddFolderView by rememberSaveable { mutableStateOf(false) }
    var folderMenuUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var folderToRenameUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var folderToDeleteUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFolderActionRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    val scope = rememberCoroutineScope()
    val folders by chatViewModel.folders.collectAsStateWithLifecycle()
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val catalogUsers by chatViewModel.users.collectAsStateWithLifecycle()
    val inboxCount = remember(streams, topicsByStream, catalogUsers) {
        inboxUnreadCount(
            buildInboxGroups(streams, topicsByStream, catalogUsers),
        )
    }
    val folderMenu = folderMenuUuid?.let { uuid ->
        folders.firstOrNull { it.uuid == uuid }
    }
    val folderToRename = folderToRenameUuid?.let { uuid ->
        folders.firstOrNull { it.uuid == uuid }
    }
    val folderToDelete = folderToDeleteUuid?.let { uuid ->
        folders.firstOrNull { it.uuid == uuid }
    }
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val createState by chatViewModel.createQueryState.collectAsStateWithLifecycle()
    val folderMutationInProgress by
        chatViewModel.folderActionInProgress.collectAsStateWithLifecycle()
    val lastCatalogActionResult by
        chatViewModel.lastCatalogActionResult.collectAsStateWithLifecycle()
    val chatToAdd by chatViewModel.chatToAdd.collectAsState()
    val actionError by chatViewModel.actionError.collectAsStateWithLifecycle()
    val usersViewModelFactory = remember { UsersViewModelFactory(chatViewModel.client) }
    val usersViewModel: UsersViewModel = viewModel(factory = usersViewModelFactory)
    val availableUsers by usersViewModel.users.collectAsStateWithLifecycle()
    val currentUserUuid by
        chatViewModel.userViewModel.userId.collectAsStateWithLifecycle()
    val colors = LocalWorkspaceColorsPalette.current

    BackHandler(enabled = showDetail) {
        showDetail = false
        scope.launch { chatViewModel.updateSelectedChat(null) }
    }

    LaunchedEffect(createState) {
        if (createState is QueryState.Success) {
            val createdStream = chatViewModel.createdStream
            val defaultTopicUuid = createdStream?.defaultTopicUuid
            if (createdStream != null && !defaultTopicUuid.isNullOrBlank()) {
                chatViewModel.currentStreamId = createdStream.uuid
                val isDirect = createdStream.isDirectProviderChat()
                val defaultTopic = chatViewModel.streamTopics.value[createdStream.uuid]
                    .orEmpty()
                    .singleOrNull { it.uuid == defaultTopicUuid }
                navController.navigate(
                    ChatFlow.ChatDialog(
                        createdStream.name,
                        createdStream.uuid,
                        defaultTopic?.name?.takeUnless { isDirect },
                        defaultTopicUuid,
                        isDirect,
                        null,
                    ),
                )
            }
            showCreateChannel = false
            showUserList = false
            chatViewModel.consumeCreatedStream()
        }
    }
    LaunchedEffect(lastCatalogActionResult, pendingFolderActionRequestId) {
        val result = lastCatalogActionResult ?: return@LaunchedEffect
        if (result.requestId != pendingFolderActionRequestId) {
            return@LaunchedEffect
        }
        pendingFolderActionRequestId = null
        if (!result.success) return@LaunchedEffect
        when (result.kind) {
            CatalogActionKind.RENAME_FOLDER -> folderToRenameUuid = null
            CatalogActionKind.DELETE_FOLDER -> folderToDeleteUuid = null
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        MessengerTopBar(
            detailOpen = showDetail,
            onNavigationClick = {
                if (showDetail) {
                    showDetail = false
                    scope.launch { chatViewModel.updateSelectedChat(null) }
                }
            },
            inboxCount = inboxCount,
            onOpenInbox = {
                navController.navigate(ChatFlow.Inbox) {
                    launchSingleTop = true
                }
            },
            onNewChat = { showNewChatChooser = true },
        )
        actionError?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    color = colors.indicatorRed,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Закрыть",
                    color = colors.primary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = chatViewModel::clearActionError)
                        .padding(8.dp),
                )
            }
        }
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
            onManageFolder = { folderMenuUuid = it.uuid },
            onOpenFeed = {
                navController.navigate(ChatFlow.Feed) {
                    launchSingleTop = true
                }
            },
            onOpenStarred = {
                navController.navigate(ChatFlow.Starred) {
                    launchSingleTop = true
                }
            },
            onOpenDrafts = {
                navController.navigate(ChatFlow.Drafts) {
                    launchSingleTop = true
                }
            },
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
                chatViewModel.createPrivateStream(userResponse)
            },
            onDismiss = { showUserList = false },
        )
    }
    if (showNewChatChooser) {
        AlertDialog(
            onDismissRequest = { showNewChatChooser = false },
            title = { Text("Новый чат") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showNewChatChooser = false
                            showUserList = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Личный чат")
                    }
                    TextButton(
                        onClick = {
                            showNewChatChooser = false
                            showCreateChannel = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Канал")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewChatChooser = false }) {
                    Text("Отмена")
                }
            },
        )
    }
    if (showCreateChannel) {
        CreateChannelDialog(
            users = availableUsers,
            currentUserUuid = currentUserUuid,
            busy = createState is QueryState.Loading,
            error = (createState as? QueryState.Error)?.message,
            onDismiss = {
                if (createState !is QueryState.Loading) showCreateChannel = false
            },
            onSubmit = { input ->
                chatViewModel.createChannel(
                    name = input.name,
                    description = input.description,
                    inviteOnly = input.inviteOnly,
                    announce = input.announce,
                    memberUserUuids = input.memberUserUuids,
                )
            },
        )
    }
    if (showAddFolderView) {
        ModalScrim { showAddFolderView = false }
        CreateFolder(
            onCreateButtonTap = { folderName ->
                chatViewModel.addFolder(folderName)
                showAddFolderView = false
            },
            onDismiss = { showAddFolderView = false },
        )
    }
    folderMenu?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderMenuUuid = null },
            title = { Text(folder.title) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            folderMenuUuid = null
                            folderToRenameUuid = folder.uuid
                        },
                    ) {
                        Text("Переименовать")
                    }
                    TextButton(
                        onClick = {
                            folderMenuUuid = null
                            folderToDeleteUuid = folder.uuid
                        },
                    ) {
                        Text(
                            text = "Удалить",
                            color = colors.indicatorRed,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { folderMenuUuid = null }) {
                    Text("Отмена")
                }
            },
        )
    }
    folderToRename?.let { folder ->
        ModalScrim {
            if (!folderMutationInProgress) folderToRenameUuid = null
        }
        CreateFolder(
            initialName = folder.title,
            title = "Переименовать папку",
            submitLabel = if (folderMutationInProgress) "Сохранение…" else "Сохранить",
            onCreateButtonTap = { name ->
                if (
                    !folderMutationInProgress &&
                    pendingFolderActionRequestId == null
                ) {
                    pendingFolderActionRequestId =
                        chatViewModel.renameFolder(folder, name)
                }
            },
            onDismiss = {
                if (!folderMutationInProgress) folderToRenameUuid = null
            },
        )
    }
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = {
                if (!folderMutationInProgress) folderToDeleteUuid = null
            },
            title = { Text("Удалить папку?") },
            text = {
                Text("Папка «${folder.title}» будет удалена. Сами чаты останутся доступны.")
            },
            confirmButton = {
                TextButton(
                    enabled = !folderMutationInProgress,
                    onClick = {
                        if (pendingFolderActionRequestId == null) {
                            pendingFolderActionRequestId =
                                chatViewModel.deleteFolder(folder)
                        }
                    },
                ) {
                    Text(
                        text = if (folderMutationInProgress) "Удаление…" else "Удалить",
                        color = colors.indicatorRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !folderMutationInProgress,
                    onClick = { folderToDeleteUuid = null },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
    chatToAdd?.let { stream ->
        ModalScrim { chatViewModel.onChatToAddChange(null) }
        AddChatToFolder(
            folders,
            stream,
            onAddButtonTap = { folder, selectedChat ->
                chatViewModel.addChatFolder(
                    streamUuid = selectedChat.uuid,
                    chatType = selectedChat.folderItemChatType(),
                    folderUuid = folder.uuid,
                )
                chatViewModel.onChatToAddChange(null)
            },
        )
    }
}

@Composable
private fun MessengerTopBar(
    detailOpen: Boolean,
    onNavigationClick: () -> Unit,
    inboxCount: Int,
    onOpenInbox: () -> Unit,
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
        if (detailOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clickable(onClick = onNavigationClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Назад к чатам",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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
                .align(Alignment.CenterEnd)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onOpenInbox),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = if (inboxCount > 0) {
                        "Входящие, непрочитанных: $inboxCount"
                    } else {
                        "Входящие"
                    },
                    tint = colors.textAdditional50,
                    modifier = Modifier.size(24.dp),
                )
                UnreadBadge(
                    count = inboxCount,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onNewChat),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_figma_new_chat),
                    contentDescription = "Новый чат",
                    tint = colors.textAdditional50,
                    modifier = Modifier.size(28.dp),
                )
            }
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
    onManageFolder: (FolderResponseData) -> Unit,
    onOpenFeed: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenDrafts: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "workspace-feed") {
            MessageCollectionTab(
                drawable = R.drawable.ic_feed,
                label = "Лента",
                description = "Открыть ленту сообщений",
                onClick = onOpenFeed,
            )
        }
        item(key = "workspace-starred") {
            MessageCollectionTab(
                drawable = R.drawable.ic_star,
                label = "Избранное",
                description = "Открыть избранные сообщения",
                onClick = onOpenStarred,
            )
        }
        item(key = "workspace-drafts") {
            MessageCollectionTab(
                drawable = R.drawable.ic_draft,
                label = "Черновики",
                description = "Открыть черновики",
                onClick = onOpenDrafts,
            )
        }
        items(folders, key = { it.uuid }) { folder ->
            val isSelected = folder.uuid == selected?.uuid
            Column(
                modifier = Modifier
                    .height(48.dp)
                    .combinedClickable(
                        onClick = { onSelected(folder) },
                        onLongClick = if (folder.isUserManaged()) {
                            { onManageFolder(folder) }
                        } else {
                            null
                        },
                    )
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAddFolder)
                    .semantics {
                        contentDescription = "Создать папку"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = colors.textAdditional30,
                    fontSize = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageCollectionTab(
    drawable: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .height(48.dp)
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(drawable),
                contentDescription = null,
                tint = colors.textAdditional30,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = colors.textAdditional30,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = NavigationFontFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
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
