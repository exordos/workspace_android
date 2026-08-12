package ru.genesiscorporation.workspace.beta.modules.topics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatchannels.TopicHeader
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.TopicActionsDialog
import ru.genesiscorporation.workspace.beta.ui.CreateTopicDialog
import ru.genesiscorporation.workspace.beta.ui.TopicNameDialog
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    topicsViewModel: TopicsViewModel,
    navController: NavHostController
) {
    val subscriptions by topicsViewModel.subscriptions.collectAsState()
    val state by topicsViewModel.state.collectAsState()
    val actionError by topicsViewModel.actionError.collectAsState()
    val actionInProgress by topicsViewModel.actionInProgress.collectAsState()
    val lastActionResult by topicsViewModel.lastActionResult.collectAsState()
    var createDialogOpen by rememberSaveable { mutableStateOf(false) }
    var managedTopicUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var renamedTopicUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingActionRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val cameBack = backStackEntry?.savedStateHandle
        ?.getStateFlow("from_detail_back", false)
        ?.collectAsState()

    LaunchedEffect(cameBack?.value) {
        if (cameBack?.value == true) {
            topicsViewModel.currentTopicName = ""
            backStackEntry?.savedStateHandle?.set("from_detail_back", false) // consume one-shot event
        }
    }
    LaunchedEffect(lastActionResult, pendingActionRequestId) {
        val result = lastActionResult ?: return@LaunchedEffect
        if (result.requestId != pendingActionRequestId) return@LaunchedEffect
        pendingActionRequestId = null
        if (!result.success) return@LaunchedEffect
        when (result.kind) {
            TopicActionKind.CREATE -> createDialogOpen = false
            TopicActionKind.RENAME -> renamedTopicUuid = null
            TopicActionKind.MARK_READ,
            TopicActionKind.TOGGLE_DONE,
            TopicActionKind.NOTIFICATIONS -> managedTopicUuid = null
            else -> Unit
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.surface,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text(topicsViewModel.channelName)
                },
                actions = {
                    TextButton(onClick = { createDialogOpen = true }) {
                        Text("Новый топик")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
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
                actionError?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(
                                LocalWorkspaceColorsPalette.current.infoCardBackground,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            color = LocalWorkspaceColorsPalette.current.indicatorRed,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = topicsViewModel::clearActionError) {
                            Text("Закрыть")
                        }
                    }
                }
                when (val currentState = state) {
                    QueryState.Idle, QueryState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    is QueryState.Error -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = currentState.message,
                            color = LocalWorkspaceColorsPalette.current.indicatorRed,
                        )
                        TextButton(onClick = topicsViewModel::retry) {
                            Text("Повторить")
                        }
                    }

                    QueryState.Success -> if (subscriptions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "В этом канале пока нет топиков",
                                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                            )
                        }
                    } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(items = subscriptions) { item ->
                            ChatOldTopic(
                                topicsViewModel = topicsViewModel,
                                item = item,
                                navController = navController,
                                onLongClick = { managedTopicUuid = item.uuid },
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    if (createDialogOpen) {
        CreateTopicDialog(
            busy = actionInProgress,
            onSubmit = { name ->
                if (!actionInProgress && pendingActionRequestId == null) {
                    pendingActionRequestId = topicsViewModel.createTopic(name)
                }
            },
            onDismiss = {
                if (!actionInProgress) createDialogOpen = false
            },
        )
    }
    managedTopicUuid
        ?.let(topicsViewModel::topic)
        ?.let { topic ->
            TopicActionsDialog(
                expanded = true,
                topic = topic,
                busy = actionInProgress,
                onDismiss = { managedTopicUuid = null },
                onRename = {
                    managedTopicUuid = null
                    renamedTopicUuid = topic.uuid
                },
                onMarkRead = {
                    if (!actionInProgress && pendingActionRequestId == null) {
                        pendingActionRequestId = topicsViewModel.markTopicRead(topic)
                    }
                },
                onToggleDone = {
                    if (!actionInProgress && pendingActionRequestId == null) {
                        pendingActionRequestId = topicsViewModel.toggleTopicDone(topic)
                    }
                },
                onSetNotificationMode = { mode ->
                    if (!actionInProgress && pendingActionRequestId == null) {
                        pendingActionRequestId =
                            topicsViewModel.setTopicNotificationMode(topic, mode)
                    }
                },
            )
        }
    renamedTopicUuid
        ?.let(topicsViewModel::topic)
        ?.let { topic ->
            TopicNameDialog(
                title = "Переименовать топик",
                initialName = topic.name,
                busy = actionInProgress,
                onSubmit = { name ->
                    if (!actionInProgress && pendingActionRequestId == null) {
                        pendingActionRequestId =
                            topicsViewModel.renameTopic(topic, name)
                    }
                },
                onDismiss = {
                    if (!actionInProgress) renamedTopicUuid = null
                },
            )
        }
}

@Composable
fun ChatOldTopic(
    topicsViewModel: TopicsViewModel,
    item: TopicHeader,
    navController: NavHostController,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .alpha(if (item.effectivelyMuted) 0.7f else 1f)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(LocalWorkspaceColorsPalette.current.chatHeaderBackground)
            .padding(start = 16.dp)
            .combinedClickable(
                onClick = {
                    topicsViewModel.currentTopicName = item.title
                    navController.navigate(
                        ChatFlow.ChatDialog(
                            item.channelName,
                            item.channelId,
                            item.title,
                            item.uuid,
                            false,
                            null,
                        ),
                    )
                },
                onLongClick = onLongClick,
            )
            .semantics {
                stateDescription = if (item.isDone) {
                    "Тема завершена"
                } else {
                    "Тема активна"
                }
            }
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.displayTitle,
                    color = if (item.isDone) {
                        LocalWorkspaceColorsPalette.current.textAdditional50
                    } else {
                        LocalWorkspaceColorsPalette.current.textHeaders
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isDone) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val lastMessage = item.lastMessage
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastMessage = item.lastMessage
                Spacer(modifier = Modifier.weight(1f))
                UnreadBadge(
                    count = item.unreadCount,
                    muted = item.unreadPassive,
                )
            }
        }
    }
}
