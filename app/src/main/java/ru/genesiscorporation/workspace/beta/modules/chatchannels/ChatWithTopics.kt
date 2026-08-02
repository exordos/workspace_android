package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.ChatListDensity
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferences
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.chatdialog.forwardTopicLabel
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.TopicActionsDialog
import ru.genesiscorporation.workspace.beta.ui.TopicNameDialog
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

@Composable
fun ChatWithTopics(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    showDetail: Boolean,
    onShowDetailChange: (Boolean) -> Unit,
) {
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    val currentlySelectedStream by chatViewModel.currentlySelectedStream.collectAsState()
    val streamTopics by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val state by chatViewModel.queryState.collectAsStateWithLifecycle()
    val baseUrl by chatViewModel.userViewModel.baseUrl.collectAsStateWithLifecycle()
    val uiPreferences by
        chatViewModel.userViewModel.uiPreferences.collectAsStateWithLifecycle()
    val topicActionBusy by
        chatViewModel.topicActionInProgress.collectAsStateWithLifecycle()
    val lastCatalogActionResult by
        chatViewModel.lastCatalogActionResult.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val streamListState = rememberLazyListState()
    var createTopicForStreamUuid by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var topicToManageUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var topicToRenameUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTopicActionRequestId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    val createTopicForStream = createTopicForStreamUuid?.let { targetUuid ->
        streams.firstOrNull { it.uuid == targetUuid }
    }
    val topicToManage = topicToManageUuid?.let { targetUuid ->
        streamTopics.values
            .asSequence()
            .flatten()
            .firstOrNull { it.uuid == targetUuid }
    }
    val topicToRename = topicToRenameUuid?.let { targetUuid ->
        streamTopics.values
            .asSequence()
            .flatten()
            .firstOrNull { it.uuid == targetUuid }
    }

    LaunchedEffect(lastCatalogActionResult, pendingTopicActionRequestId) {
        val result = lastCatalogActionResult ?: return@LaunchedEffect
        if (result.requestId != pendingTopicActionRequestId) {
            return@LaunchedEffect
        }
        pendingTopicActionRequestId = null
        if (!result.success) return@LaunchedEffect
        when (result.kind) {
            CatalogActionKind.CREATE_TOPIC -> createTopicForStreamUuid = null
            CatalogActionKind.RENAME_TOPIC -> topicToRenameUuid = null
            CatalogActionKind.MARK_TOPIC_READ,
            CatalogActionKind.TOGGLE_TOPIC_DONE,
            CatalogActionKind.TOPIC_NOTIFICATIONS -> topicToManageUuid = null
            else -> Unit
        }
    }

    val visibleStreams = remember(
        searchQuery,
        streams,
        currentlySelectedFolder,
        uiPreferences.prioritizePersonalUnread,
        uiPreferences.prioritizeUnmutedUnreadChannels,
    ) {
        val folderItems = currentlySelectedFolder?.items
        val folderStreams = if (
            folderItems == null || currentlySelectedFolder?.isAllChatsFolder() == true
        ) {
            streams
        } else {
            folderItems.mapNotNull { item ->
                streams.firstOrNull { it.uuid == item.streamUuid }
            }
        }
        val folderItemsByStream = folderItems
            .orEmpty()
            .associateBy { it.streamUuid }
        folderStreams
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .let { filtered ->
                orderChatStreams(
                    streams = filtered,
                    folderItemsByStream = folderItemsByStream,
                    preferences = uiPreferences,
                )
            }
    }

    if (visibleStreams.isEmpty()) {
        when {
            state is QueryState.Loading -> EmptyMessengerState(
                loading = true,
                text = "",
            )
            state is QueryState.Error && searchQuery.isBlank() -> MessengerErrorState(
                message = (state as QueryState.Error).message,
                onRetry = { scope.launch { chatViewModel.loadServerSettings() } },
            )
            else -> EmptyMessengerState(
                loading = false,
                text = if (searchQuery.isBlank()) "Список чатов пуст" else "Ничего не найдено",
            )
        }
        return
    }

    val selectedStream = currentlySelectedStream
    LaunchedEffect(searchQuery, currentlySelectedFolder?.uuid) {
        streamListState.scrollToItem(0)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colors = LocalWorkspaceColorsPalette.current
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val openOffsetPx = with(density) { TOPIC_PANEL_RAIL_WIDTH.toPx() }
        val closedOffsetPx = screenWidthPx
        var draggingPanel by remember { mutableStateOf(false) }
        var dragOffsetPx by remember(screenWidthPx) {
            mutableFloatStateOf(closedOffsetPx)
        }
        val targetOffsetPx = if (showDetail && selectedStream != null) {
            openOffsetPx
        } else {
            closedOffsetPx
        }
        val animatedOffsetPx by animateFloatAsState(
            targetValue = if (draggingPanel) dragOffsetPx else targetOffsetPx,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f),
            label = "topic-panel-offset",
        )
        val currentAnimatedOffsetPx by rememberUpdatedState(animatedOffsetPx)

        LazyColumn(
            state = streamListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            itemsIndexed(
                items = visibleStreams,
                key = { _, stream -> stream.uuid },
            ) { index, stream ->
                ChatChannel(
                    item = stream,
                    viewModel = chatViewModel,
                    baseUrl = baseUrl.orEmpty(),
                    showDetail = showDetail && selectedStream?.uuid == stream.uuid,
                    topicRailOpen = showDetail,
                    currentlySelectedFolder = currentlySelectedFolder,
                    latestTopicName = stream.lastMessage?.topicUuid?.let { topicUuid ->
                        streamTopics[stream.uuid]
                            .orEmpty()
                            .firstOrNull { it.uuid == topicUuid }
                            ?.name
                    },
                    density = uiPreferences.chatListDensity,
                    onChatNumberToAddChange = chatViewModel::onChatToAddChange,
                    onClick = {
                        val defaultTopic = stream.defaultTopicUuid
                        if (stream.isDirectProviderChat() && defaultTopic != null) {
                            onShowDetailChange(false)
                            navController.navigate(
                                ChatFlow.ChatDialog(
                                    stream.name,
                                    stream.uuid,
                                    null,
                                    defaultTopic,
                                    true,
                                    null,
                                ),
                            )
                        } else {
                            scope.launch {
                                onShowDetailChange(true)
                                chatViewModel.updateSelectedChat(stream)
                            }
                        }
                    },
                )
                if (!showDetail && index < visibleStreams.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (
                                    uiPreferences.chatListDensity == ChatListDensity.COMPACT
                                ) {
                                    52.dp
                                } else {
                                    60.dp
                                },
                            )
                            .height(1.dp)
                            .background(colors.cardBackgroundActive),
                    )
                }
            }
        }

        selectedStream?.let { stream ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth - TOPIC_PANEL_RAIL_WIDTH)
                    .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                    .background(colors.surface)
                    .pointerInput(
                        stream.uuid,
                        openOffsetPx,
                        closedOffsetPx,
                        targetOffsetPx,
                    ) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                draggingPanel = true
                                dragOffsetPx = currentAnimatedOffsetPx
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount)
                                    .coerceIn(openOffsetPx, closedOffsetPx)
                            },
                            onDragCancel = {
                                draggingPanel = false
                                dragOffsetPx = targetOffsetPx
                            },
                            onDragEnd = {
                                val shouldStayOpen = topicPanelShouldStayOpen(
                                    offsetPx = dragOffsetPx,
                                    openOffsetPx = openOffsetPx,
                                    closedOffsetPx = closedOffsetPx,
                                )
                                dragOffsetPx = if (shouldStayOpen) {
                                    openOffsetPx
                                } else {
                                    closedOffsetPx
                                }
                                draggingPanel = false
                                onShowDetailChange(shouldStayOpen)
                            },
                        )
                    },
            ) {
                val topics = streamTopics[stream.uuid].orEmpty()
                val orderedTopics = orderTopicsForDisplay(topics)
                val selectedTopicIndex = orderedTopics
                    .indexOfFirst { it.uuid == stream.lastMessage?.topicUuid }
                    .takeIf { it >= 0 }
                Column(modifier = Modifier.fillMaxSize()) {
                    AllTopicsRow(
                        stream = stream,
                        onClick = {
                            onShowDetailChange(false)
                            navController.navigate(
                                ChatFlow.StreamFeed(
                                    streamName = stream.name,
                                    streamUuid = stream.uuid,
                                ),
                            )
                        },
                    )
                    if (shouldShowTopicDividerAfter(-1, selectedTopicIndex)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp)
                                .height(1.dp)
                                .background(colors.cardBackgroundActive),
                        )
                    }
                    if (topics.isEmpty()) {
                        if (state is QueryState.Error) {
                            MessengerErrorState(
                                message = (state as QueryState.Error).message,
                                onRetry = {
                                    scope.launch { chatViewModel.loadTopics(stream) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            EmptyMessengerState(
                                loading = state is QueryState.Loading,
                                text = "Список тем пуст",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            itemsIndexed(
                                items = orderedTopics,
                                key = { _, topic -> topic.uuid },
                            ) { index, topic ->
                                ChatTopic(
                                    viewModel = chatViewModel,
                                    item = topic,
                                    displayName = forwardTopicLabel(topic, topics),
                                    stream = stream,
                                    navController = navController,
                                    selected = topic.uuid == stream.lastMessage?.topicUuid,
                                    onLongClick = { topicToManageUuid = topic.uuid },
                                )
                                if (
                                    shouldShowTopicDividerAfter(
                                        itemIndex = index,
                                        selectedTopicIndex = selectedTopicIndex,
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp)
                                            .height(1.dp)
                                            .background(colors.cardBackgroundActive),
                                    )
                                }
                            }
                            item(key = "create-topic") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clickable {
                                            createTopicForStreamUuid = stream.uuid
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "+ Новая тема",
                                        color = colors.primary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    createTopicForStream?.let { stream ->
        TopicNameDialog(
            title = "Новая тема в «${stream.name}»",
            initialName = "",
            busy = topicActionBusy,
            submitLabel = "Создать",
            onSubmit = { name ->
                if (!topicActionBusy && pendingTopicActionRequestId == null) {
                    pendingTopicActionRequestId =
                        chatViewModel.createTopic(stream, name)
                }
            },
            onDismiss = {
                if (!topicActionBusy) createTopicForStreamUuid = null
            },
        )
    }
    topicToManage?.let { selected ->
        val currentTopic = streamTopics[selected.streamUuid]
            .orEmpty()
            .firstOrNull { it.uuid == selected.uuid }
            ?: selected
        TopicActionsDialog(
            topic = currentTopic,
            busy = topicActionBusy,
            onDismiss = { topicToManageUuid = null },
            onRename = {
                topicToManageUuid = null
                topicToRenameUuid = currentTopic.uuid
            },
            onMarkRead = {
                if (!topicActionBusy && pendingTopicActionRequestId == null) {
                    pendingTopicActionRequestId =
                        chatViewModel.markTopicRead(currentTopic)
                }
            },
            onToggleDone = {
                if (!topicActionBusy && pendingTopicActionRequestId == null) {
                    pendingTopicActionRequestId =
                        chatViewModel.toggleTopicDone(currentTopic)
                }
            },
            onSetNotificationMode = { mode ->
                if (!topicActionBusy && pendingTopicActionRequestId == null) {
                    pendingTopicActionRequestId =
                        chatViewModel.setTopicNotificationMode(currentTopic, mode)
                }
            },
        )
    }
    topicToRename?.let { selected ->
        val currentTopic = streamTopics[selected.streamUuid]
            .orEmpty()
            .firstOrNull { it.uuid == selected.uuid }
            ?: selected
        TopicNameDialog(
            title = "Переименовать тему",
            initialName = currentTopic.name,
            busy = topicActionBusy,
            onSubmit = { name ->
                if (!topicActionBusy && pendingTopicActionRequestId == null) {
                    pendingTopicActionRequestId =
                        chatViewModel.renameTopic(currentTopic, name)
                }
            },
            onDismiss = {
                if (!topicActionBusy) topicToRenameUuid = null
            },
        )
    }
}

@Composable
private fun EmptyMessengerState(
    loading: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            AnimatedGif(Modifier.size(72.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    color = colors.textAdditional50,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun MessengerErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = colors.textAdditional50,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Повторить",
            color = colors.primary,
            fontSize = 15.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

internal fun topicPanelShouldStayOpen(
    offsetPx: Float,
    openOffsetPx: Float,
    closedOffsetPx: Float,
): Boolean = offsetPx < (openOffsetPx + closedOffsetPx) / 2f

private val TOPIC_PANEL_RAIL_WIDTH = 74.dp

private fun streamSortTime(stream: Stream): Instant =
    parseTime(stream.lastMessage?.createdAt ?: stream.updatedAt)

internal fun orderChatStreams(
    streams: List<Stream>,
    folderItemsByStream: Map<String, FolderItem>,
    preferences: WorkspaceUiPreferences,
): List<Stream> = streams.sortedWith { first, second ->
    val firstFolderItem = folderItemsByStream[first.uuid]
    val secondFolderItem = folderItemsByStream[second.uuid]

    comparePriority(
        firstFolderItem?.pinnedAt != null,
        secondFolderItem?.pinnedAt != null,
    ).takeIf { it != 0 }
        ?: comparePersonalUnreadPriority(
            first,
            second,
            enabled = preferences.prioritizePersonalUnread,
        ).takeIf { it != 0 }
        ?: compareUnmutedUnreadPriority(
            first,
            second,
            enabled = preferences.prioritizeUnmutedUnreadChannels,
        ).takeIf { it != 0 }
        ?: compareValues(
            firstFolderItem?.orderIndex ?: Int.MAX_VALUE,
            secondFolderItem?.orderIndex ?: Int.MAX_VALUE,
        ).takeIf { it != 0 }
        ?: streamSortTime(second)
            .compareTo(streamSortTime(first))
            .takeIf { it != 0 }
        ?: first.uuid.compareTo(second.uuid)
}

internal fun shouldShowTopicDividerAfter(
    itemIndex: Int,
    selectedTopicIndex: Int?,
): Boolean = selectedTopicIndex == null || (
    itemIndex != selectedTopicIndex && itemIndex + 1 != selectedTopicIndex
)

private fun comparePersonalUnreadPriority(
    first: Stream,
    second: Stream,
    enabled: Boolean,
): Int {
    if (!enabled || first.unreadCount <= 0 || second.unreadCount <= 0) return 0
    return comparePriority(
        first.isPersonalDirectChat(),
        second.isPersonalDirectChat(),
    )
}

private fun compareUnmutedUnreadPriority(
    first: Stream,
    second: Stream,
    enabled: Boolean,
): Int {
    if (
        !enabled ||
        first.unreadCount <= 0 ||
        second.unreadCount <= 0 ||
        first.isDirectProviderChat() ||
        second.isDirectProviderChat()
    ) {
        return 0
    }
    return comparePriority(
        !first.notificationMode.equals("muted", ignoreCase = true),
        !second.notificationMode.equals("muted", ignoreCase = true),
    )
}

private fun comparePriority(first: Boolean, second: Boolean): Int = when {
    first == second -> 0
    first -> -1
    else -> 1
}

private fun Stream.isPersonalDirectChat(): Boolean {
    if (!isDirectProviderChat()) return false
    if (!directUserUuid.isNullOrBlank()) return true
    return (
        provider?.externalId
            ?.substringBefore(':')
            ?.let { it == "direct" }
        ) == true
}

internal fun parseTime(value: String?): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrDefault(Instant.EPOCH)

internal fun orderTopicsForDisplay(
    topics: List<TopicsResponseData>,
): List<TopicsResponseData> =
    topics.sortedWith(
        compareBy<TopicsResponseData> { it.isDone }.thenByDescending {
            parseTime(it.lastMessage?.createdAt ?: it.updatedAt)
        },
    )
