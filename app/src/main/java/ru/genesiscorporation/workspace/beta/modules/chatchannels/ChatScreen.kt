package ru.genesiscorporation.workspace.beta.modules.chatchannels

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.UsersViewModelFactory
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.topics.TopicHeader
import ru.genesiscorporation.workspace.beta.modules.users.UsersScreen
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel
import ru.genesiscorporation.workspace.beta.ui.AddChatToFolder
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.CreateFolder
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController
) {
    var showDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val subscriptions by chatViewModel.subscriptions.collectAsState()
    val topics by chatViewModel.topics.collectAsState()
    val folders by chatViewModel.folders.collectAsState()
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    var showUserList by remember { mutableStateOf(false) }
    var showAddFolderView by remember { mutableStateOf(false) }
    var chatToAdd: ChatHeader? by remember { mutableStateOf(null) }
    val usersViewModelFactory = remember { UsersViewModelFactory(chatViewModel.client) }
    var usersViewModel: UsersViewModel = viewModel(factory = usersViewModelFactory)
    val userId by chatViewModel.userViewModel.repo.userIdFlow.collectAsStateWithLifecycle(
        initialValue = 0
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val cameBack = backStackEntry?.savedStateHandle
        ?.getStateFlow("from_detail_back", false)
        ?.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val state by chatViewModel.queryState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filteredSubscriptions = remember(searchQuery, subscriptions, currentlySelectedFolder) {
        val folderSubscriptions = if (currentlySelectedFolder?.systemType == "all") {
            subscriptions
        } else {
            val folderItems = currentlySelectedFolder?.items?.map { it.chatId }
            if (folderItems != null) {
                subscriptions.filter { folderItems.contains(it.chatId) }
            } else {
                subscriptions
            }
        }
        if (searchQuery.isBlank()) {
            folderSubscriptions
        } else {
            folderSubscriptions.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(cameBack?.value) {
        if (cameBack?.value == true) {
            chatViewModel.currentStreamId = ""
            backStackEntry?.savedStateHandle?.set(
                "from_detail_back",
                false
            ) // consume one-shot event
        }
    }

    LaunchedEffect(state) {
        if (state is QueryState.Error) {
            Toast
                .makeText(context, "Чат добавлен в папку", Toast.LENGTH_SHORT)
                .show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.surface,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text("Мессенджер")
                },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    IconButton(onClick = {
                        showUserList = true
                    }) {
                        Image(
                            painter = painterResource(id = ru.genesiscorporation.workspace.beta.R.drawable.new_chat),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .background(LocalWorkspaceColorsPalette.current.surface)
            ) {
                if (subscriptions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading"
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp)
                                .background(
                                    LocalWorkspaceColorsPalette.current.searchBackground,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(LocalWorkspaceColorsPalette.current.textAdditional30),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Поиск...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!folders.isEmpty()) {
                            LazyRow(modifier = Modifier.padding(vertical = 16.dp)) {
                                items(
                                    items = folders
                                ) { folder ->
                                    Row {
                                        val unreadCount = chatViewModel.unreadCountForFolder(folder)
                                        val endPadding = if (unreadCount > 0) 0.dp else 8.dp
                                        Text(
                                            folder.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (folder.uuid == currentlySelectedFolder?.uuid) LocalWorkspaceColorsPalette.current.textHeaders else LocalWorkspaceColorsPalette.current.textAdditional30,
                                            modifier = Modifier
                                                .padding(start = 16.dp, 0.dp, endPadding, 0.dp)
                                                .clickable(
                                                    onClick = {
                                                        chatViewModel.updateCurrentlySelectedFolder(
                                                            folder
                                                        )
                                                    }
                                                ),
                                        )
                                        if (unreadCount > 0) {
                                            Text(
                                                text = "${unreadCount}",
                                                color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .padding(4.dp, 0.dp, 8.dp, 0.dp)
                                                    .background(
                                                        color = LocalWorkspaceColorsPalette.current.noticeCounterBadge,
                                                        shape = RoundedCornerShape(100.dp)
                                                    )
                                                    .padding(horizontal = 8.dp)
                                            )
                                        }
                                    }
                                }
                                item {
                                    Text(
                                        text = "+",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clickable {
                                                showAddFolderView = true
                                            }
                                    )
                                }
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val density = LocalDensity.current
                            val screenWidthPx = with(density) { maxWidth.toPx() }
                            val openOffsetPx =
                                with(density) { 55.dp.toPx() }      // left edge when open
                            val closedOffsetPx =
                                screenWidthPx + 20                    // fully off-screen right
                            val offsetX = remember { Animatable(closedOffsetPx) }
                            // Animate open / close when showDetail changes
                            LaunchedEffect(showDetail) {
                                offsetX.animateTo(
                                    targetValue = if (showDetail) openOffsetPx else closedOffsetPx,
                                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)
                                )
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                items(
                                    items = filteredSubscriptions
                                ) { item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (item.streamId == chatViewModel.currentlySelectedSubscription.collectAsState().value?.streamId) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(56.dp)
                                                    .background(LocalWorkspaceColorsPalette.current.primary)
                                            )
                                        }
                                        ChatChannel(
                                            item,
                                            chatViewModel,
                                            showDetail,
                                            currentlySelectedFolder,
                                            onChatNumberToAddChange = { chatToAdd = it },
                                            onClick = {
                                                if (item.isDirectMessages) {
                                                    chatViewModel.currentStreamId = item.streamId
                                                    navController.navigate(
                                                        ChatFlow.ChatDialog(
                                                            item.title,
                                                            item.streamId,
                                                            null,
                                                            true,
                                                            item.user?.userId
                                                        )
                                                    )
                                                } else {
                                                    scope.launch {
                                                        chatViewModel.updateSelectedChat(item)
                                                        showDetail = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            val scope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(maxWidth - 55.dp) // panel width when open
                                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                    .background(LocalWorkspaceColorsPalette.current.surface)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                scope.launch {
                                                    val shouldOpen = offsetX.value < (openOffsetPx + closedOffsetPx) / 2f
                                                    showDetail = shouldOpen
                                                    offsetX.animateTo(
                                                        if (shouldOpen) openOffsetPx else closedOffsetPx
                                                    )
                                                }
                                            },
                                            onHorizontalDrag = { _, dragAmount ->
                                                scope.launch {
                                                    offsetX.snapTo(
                                                        (offsetX.value + dragAmount)
                                                            .coerceIn(openOffsetPx, closedOffsetPx)
                                                    )
                                                }
                                            }
                                        )
                                    }
                            ) {
                                if (topics.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Loading"
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                    ) {
                                        items(items = topics) { item ->
                                            ChatTopic(chatViewModel, item, navController)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showUserList) {
                UsersScreen(
                    usersViewModel,
                    onUserSelected = { userResponse ->
                        navController.navigate(
                            ChatFlow.ChatDialog(
                                userResponse.fullName,
                                "[${userResponse.userId}, ${userId}]",
                                null,
                                true,
                                userResponse.userId
                            )
                        )
                        showUserList = false
                    },
                    onDismiss = {
                        showUserList = false
                    }
                )
            }
            if (showAddFolderView) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { showAddFolderView = false },
                        ),
                )
                CreateFolder(
                    onCreateButtonTap = { folderName ->
                        scope.launch {
                            chatViewModel.addFolder(folderName)
                        }
                        showAddFolderView = false
                    },
                    onDismiss = {
                        showAddFolderView = false
                    }
                )
            }
            val chat = chatToAdd
            if (chat != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { chatToAdd = null },
                        ),
                )
                AddChatToFolder(
                    folders,
                    chat,
                    onAddButtonTap = { folder, chat ->
                        scope.launch {
                            chatViewModel.addChatFolder(
                                chat.chatId,
                                folder.uuid,
                                if (chat.isDirectMessages) "private" else "stream"
                            )
                        }
                        chatToAdd = null
                    }
                )
            }
        }
    }
}