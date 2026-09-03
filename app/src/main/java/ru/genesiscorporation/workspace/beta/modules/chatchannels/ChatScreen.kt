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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import ru.genesiscorporation.workspace.beta.LoginFlow
import ru.genesiscorporation.workspace.beta.UsersViewModelFactory
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.users.UsersScreen
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel
import ru.genesiscorporation.workspace.beta.ui.AddChatToFolder
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.CreateFolder
import ru.genesiscorporation.workspace.beta.ui.CreateTopic
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.mapNotNull
import kotlin.collections.sortedByDescending
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController
) {
    var showDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val folders by chatViewModel.folders.collectAsStateWithLifecycle()
    val shouldShowCreateTopicView by chatViewModel.shouldShowCreateTopicView.collectAsStateWithLifecycle()
    val queryState by chatViewModel.queryState.collectAsState()
    val chatToAdd by chatViewModel.chatToAdd.collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val cameBack = backStackEntry?.savedStateHandle
        ?.getStateFlow("from_detail_back", false)
        ?.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()

    val createState by chatViewModel.createQueryState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(cameBack?.value) {
        if (cameBack?.value == true) {
            chatViewModel.currentStreamId = ""
            backStackEntry?.savedStateHandle?.set(
                "from_detail_back",
                false
            )
        }
    }

//    LaunchedEffect(queryState) {
//        if (queryState is QueryState.Error) {
//            Toast
//                .makeText(context, "Чат добавлен в папку", Toast.LENGTH_SHORT)
//                .show()
//        }
//    }

    LaunchedEffect(createState) {
        if (createState is QueryState.Success) {
            val createdStream = chatViewModel.createdStream
            if (createdStream != null) {
                chatViewModel.currentStreamId = createdStream.uuid
                navController.navigate(
                    ChatFlow.ChatDialog(
                        createdStream.name,
                        createdStream.uuid,
                        null,
                        createdStream.defaultTopicUuid ?: "",
                        true,
                        null
                    )
                )
                chatViewModel.createdStream = null
            }
        }
    }

    LaunchedEffect(Unit) {
        chatViewModel.navEvents.collect { event ->
            when (event) {
                is ChatNavEvent.OpenDialog -> {
//                    navController.navigate(
//                        ChatFlow.ChatDialog(
//                            event.title,
//                            event.chatId,
//                            event.topicId,
//                            event.isDirectMessages,
//                            event.userId
//                        )
//                    ) {
//                        popUpTo<ChatFlow.ChatList> { inclusive = false }
//                        launchSingleTop = true
//                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text(
                        "Мессенджер",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    IconButton(onClick = {
                        navController.navigate(ChatFlow.CreateBase)
                    }) {
                        Image(
                            painter = painterResource(id = ru.genesiscorporation.workspace.beta.R.drawable.ic_new_stream),
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
                    .background(LocalWorkspaceColorsPalette.current.background)
            ) {
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
                                onValueChange = {
                                    showDetail = false
                                    chatViewModel.onSearchQueryChange(it)
                                },
                                textStyle = TextStyle(
                                    color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                    fontSize = 14.sp,
                                    fontFamily = InterFontFamily,
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
                                            color = LocalWorkspaceColorsPalette.current.textAdditional30,
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!folders.isEmpty()) {
                            FolderList(chatViewModel, {
                                showDetail = it
                                if (!showDetail) {
                                    scope.launch {
                                        chatViewModel.updateSelectedChat(null)
                                    }
                                }
                            })
                        }
                        ChatWithTopics(chatViewModel, navController, showDetail, {
                            showDetail = it
                            if (!showDetail) {
                                scope.launch {
                                    chatViewModel.updateSelectedChat(null)
                                }
                            }
                        })
                    }
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
                            onClick = { chatViewModel.onChatToAddChange(null) },
                        ),
                )
                AddChatToFolder(
                    folders,
                    chat,
                    onAddButtonTap = { folder, chat ->
                        scope.launch {
                            chatViewModel.addChatFolder(
                                chat.uuid,
                                if (chat.isPrivate) "private" else "stream",
                                folder.uuid
                            )
                        }
                        chatViewModel.onChatToAddChange(null)
                    }
                )
            }
            if (shouldShowCreateTopicView) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { chatViewModel.onDismissTopicCreationButtonTap() },
                        ),
                )
                CreateTopic(
                    onCreateButtonTap = { topicName ->
                        scope.launch {
                            chatViewModel.addTopic(topicName)
                        }
                        chatViewModel.onDismissTopicCreationButtonTap()
                    },
                    onDismiss = {
                        chatViewModel.onDismissTopicCreationButtonTap()
                    }
                )
            }
        }
    }
}

@Composable
fun FolderList(
    chatViewModel: ChatViewModel,
    setShowDetail: (Boolean) -> Unit
) {
    val folders by chatViewModel.folders.collectAsStateWithLifecycle()
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.BottomStart
    ) {

        HorizontalDivider(
            thickness = 1.dp,
            color = LocalWorkspaceColorsPalette.current.divider
        )
        LazyRow {
            items(
                items = folders
            ) { folder ->
                val dividerColor =
                    if (folder.uuid == currentlySelectedFolder?.uuid) {
                        LocalWorkspaceColorsPalette.current.textHeaders
                    } else {
                        Color.Transparent
                    }
                val unreadCount = folder.unreadCount
                val endPadding = if (unreadCount > 0) 0f else 4f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            drawLine(
                                color = dividerColor,
                                start = Offset(16f, size.height - strokeWidth / 2f),
                                end = Offset(size.width - endPadding, size.height - strokeWidth / 2f),
                                strokeWidth = strokeWidth
                            )
                        }
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        folder.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (folder.uuid == currentlySelectedFolder?.uuid) LocalWorkspaceColorsPalette.current.textHeaders else LocalWorkspaceColorsPalette.current.textAdditional30,
                        modifier = Modifier
                            .padding(start = 16.dp, 0.dp, endPadding.dp, 0.dp)
                            .clickable(
                                onClick = {
                                    scope.launch {
                                        setShowDetail(false)
                                        chatViewModel.updateSelectedChat(null)
                                    }
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
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(4.dp, 0.dp, 8.dp, 0.dp)
                                .background(
                                    color = LocalWorkspaceColorsPalette.current.noticeBase,
                                    shape = RoundedCornerShape(100.dp)
                                )
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}