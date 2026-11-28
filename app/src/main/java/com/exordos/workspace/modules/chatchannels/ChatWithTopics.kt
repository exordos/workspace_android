package com.exordos.workspace.modules.chatchannels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import com.exordos.workspace.data.WorkspaceDateTime
import com.exordos.workspace.data.uio.FolderUIO
import com.exordos.workspace.data.uio.LastMessageUIO
import com.exordos.workspace.data.uio.StreamUIO
import com.exordos.workspace.data.uio.TopicUIO
import com.exordos.workspace.modules.chooseserver.QueryState
import com.exordos.workspace.ui.AnimatedGif
import com.exordos.workspace.ui.FullScreenError
import com.exordos.workspace.ui.theme.InterFontFamily
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import kotlin.collections.sortedByDescending
import kotlin.math.roundToInt

@Composable
internal fun ChatWithTopics(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    showDetail: Boolean,
    onShowDetailChange: (Boolean) -> Unit,
    folders: List<FolderUIO>,
    folderIds: List<String>,
    pages: FolderPageState,
) {
    val streamsQueryState by chatViewModel.streamsQueryState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    when (streamsQueryState) {
        QueryState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnimatedGif(Modifier.size(80.dp))
            }
        }
        is QueryState.Error -> FullScreenError {
            scope.launch { chatViewModel.loadServerSettings() }
        }
        QueryState.Success -> {
            val streams by chatViewModel.streams.collectAsStateWithLifecycle()
            val selectedStream by chatViewModel.currentlySelectedStream.collectAsStateWithLifecycle()
            val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()
            val lastMessages by chatViewModel.allLastMessages.collectAsStateWithLifecycle()
            val unreadFirst by chatViewModel.userViewModel.streamsOrderIsUnreadFirst.collectAsStateWithLifecycle()
            val byUuid = remember(streams) { streams.associateBy { it.uuid } }
            val keys = remember(folderIds) { folderIds.ifEmpty { listOf(null) } }
            Box(Modifier.fillMaxSize()) {
                FolderPages(pages, keys) { index ->
                    val folder = folders.getOrNull(index)
                    val folderStreams = if (folder == null || folder.systemType == "all") {
                        streams
                    } else {
                        val items by remember(chatViewModel, folder.uuid) {
                            chatViewModel.observeFolderItems(folder.uuid)
                        }.collectAsStateWithLifecycle(initialValue = null)
                        remember(items, byUuid) { items?.mapNotNull { byUuid[it.streamUuid] } }
                            ?: return@FolderPages
                    }
                    val visible = remember(folderStreams, searchQuery, lastMessages, unreadFirst) {
                        val matching = if (searchQuery.isBlank()) folderStreams else {
                            folderStreams.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        if (unreadFirst) matching.sortedForInbox(lastMessages) else matching.sortedByActivity(lastMessages)
                    }
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Список каналов пуст")
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(visible) { stream ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ChatChannel(
                                        stream, chatViewModel, showDetail, folder, selectedStream,
                                        onChatNumberToAddChange = chatViewModel::onChatToAddChange,
                                        onClick = {
                                            scope.launch {
                                                onShowDetailChange(true)
                                                chatViewModel.updateSelectedChat(stream)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                ChatTopicsPanel(chatViewModel, navController, showDetail, onShowDetailChange, selectedStream, lastMessages)
            }
        }
        else -> Unit
    }
}

@Composable
private fun ChatTopicsPanel(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    showDetail: Boolean,
    onShowDetailChange: (Boolean) -> Unit,
    selectedStream: StreamUIO?,
    lastMessages: List<LastMessageUIO>,
) {
    val topicsQueryState by chatViewModel.topicsQueryState.collectAsStateWithLifecycle()
    val topics by chatViewModel.selectedStreamTopics.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val openOffsetPx = with(density) { 60.dp.toPx() }
        val closedOffsetPx = with(density) { maxWidth.toPx() } + 20
        val offsetX = remember { Animatable(closedOffsetPx) }
        LaunchedEffect(showDetail, openOffsetPx, closedOffsetPx) {
            offsetX.animateTo(
                if (showDetail) openOffsetPx else closedOffsetPx,
                spring(dampingRatio = 0.9f, stiffness = 400f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(maxWidth - 60.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(LocalWorkspaceColorsPalette.current.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val shouldOpen =
                                    offsetX.value < (openOffsetPx + closedOffsetPx) / 2f
                                onShowDetailChange(shouldOpen)
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
            Row {
                VerticalDivider(
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider
                )
                when (topicsQueryState) {
                    QueryState.Loading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            AnimatedGif(Modifier.size(80.dp))
                        }
                    }

                    is QueryState.Error -> {
                        FullScreenError {

                        }
                    }

                    QueryState.Success -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (selectedStream != null) {
                                if (topics.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Список тем пуст")
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        items(
                                            items = if (chatViewModel.client.userViewModel?.streamsOrderIsUnreadFirst?.value ?: true) topics.topicsSortedForInbox(lastMessages) else topics.topicsSortedByActivity(lastMessages)
                                        ) { item ->
                                            ChatTopic(
                                                chatViewModel,
                                                item,
                                                selectedStream,
                                                navController
                                            )
                                        }
                                    }
                                }
                            }
                            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                                TextButton(
                                    onClick = {
                                        chatViewModel.onCreateTopicButtonTap()
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = LocalWorkspaceColorsPalette.current.primary,
                                    )
                                ) {
                                    Text(
                                        "+ Новая тема",
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily,
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

fun StreamUIO.sortKey(lastMessage: LastMessageUIO?): Instant {
    return WorkspaceDateTime.parse(lastMessage?.createdAt ?: updatedAt)
}

fun List<StreamUIO>.sortedForInbox(lastMessages: List<LastMessageUIO>): List<StreamUIO> =
    sortedWith(
        compareByDescending<StreamUIO> { it.activeUnreadCount > 0 }
            .thenByDescending {  stream ->
                val lastMessage = lastMessages.firstOrNull { it.streamUuid == stream.uuid }
                stream.sortKey(lastMessage)
            }
    )

fun List<StreamUIO>.sortedByActivity(lastMessages: List<LastMessageUIO>): List<StreamUIO> =
    sortedByDescending { stream ->
        val lastMessage = lastMessages.firstOrNull { it.streamUuid == stream.uuid }
        stream.sortKey(lastMessage)
    }

fun TopicUIO.sortKey(lastMessage: LastMessageUIO?): Instant {
    return WorkspaceDateTime.parse(lastMessage?.createdAt ?: updatedAt)
}
private val TopicUIO.isNotMuted: Boolean
    get() = notificationMode != "mute"

fun List<TopicUIO>.topicsSortedForInbox(lastMessages: List<LastMessageUIO>): List<TopicUIO> =
    sortedWith(
        compareByDescending<TopicUIO> { it.isNotMuted }
            .thenByDescending { it.unreadCount }
            .thenByDescending { topic ->
    val lastMessage = lastMessages.firstOrNull { it.topicUuid == topic.uuid }
    topic.sortKey(lastMessage)
}
    )

fun List<TopicUIO>.topicsSortedByActivity(lastMessages: List<LastMessageUIO>): List<TopicUIO> =
    sortedWith(
        compareByDescending<TopicUIO> { it.isNotMuted }
            .thenByDescending { topic ->
                val lastMessage = lastMessages.firstOrNull { it.topicUuid == topic.uuid }
                topic.sortKey(lastMessage)
            }
    )
