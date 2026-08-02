package ru.genesiscorporation.workspace.beta.modules.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FeedScreen(
    feedViewModel: FeedViewModel,
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    kind: MessageTimelineKind = MessageTimelineKind.FEED,
    title: String = kind.title,
    streamUuid: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val state by feedViewModel.state.collectAsStateWithLifecycle()
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val users by chatViewModel.users.collectAsStateWithLifecycle()
    val avatarBaseUrl by
        chatViewModel.userViewModel.baseUrl.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    // The resolver uses this screen's coroutine scope. A configuration change
    // cancels that read-only lookup, so its busy flag must be recreated too;
    // persisting the flag would leave the new screen permanently disabled.
    var openingMessageUuid by remember { mutableStateOf<String?>(null) }
    var hasPositioned by rememberSaveable(state.ownerKey) { mutableStateOf(false) }
    var paginationArmed by rememberSaveable(state.ownerKey) { mutableStateOf(true) }
    var pendingAnchorUuid by rememberSaveable(state.ownerKey) {
        mutableStateOf<String?>(null)
    }
    var pendingAnchorOffset by rememberSaveable(state.ownerKey) {
        mutableStateOf(0)
    }
    var stickToBottomAfterRefresh by rememberSaveable(state.ownerKey) {
        mutableStateOf(false)
    }
    val currentMessages by rememberUpdatedState(state.messages)
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisible == null || lastVisible >= state.messages.size
        }
    }

    fun openMessage(message: MessageResponse, beginForward: Boolean) {
        if (openingMessageUuid != null) return
        openingMessageUuid = message.uuid
        actionError = null
        scope.launch {
            try {
                val result = chatViewModel.resolveFeedMessageNavigation(
                    streamUuid = message.streamUuid,
                    topicUuid = message.topicUuid,
                    messageUuid = message.uuid,
                    beginForward = beginForward,
                )
                val route = result.route
                if (route == null) {
                    actionError = result.error
                        ?: "Не удалось открыть сообщение"
                } else {
                    navController.navigate(route)
                }
            } finally {
                openingMessageUuid = null
            }
        }
    }

    fun captureAnchorAndLoadOlder() {
        if (state.loadingOlder || !state.hasMore) return
        val firstMessageItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index > 0 }
        val message = firstMessageItem
            ?.index
            ?.minus(1)
            ?.let(state.messages::getOrNull)
        pendingAnchorUuid = message?.uuid
        pendingAnchorOffset = firstMessageItem?.let { item ->
            item.offset - listState.layoutInfo.viewportStartOffset
        } ?: 0
        paginationArmed = false
        feedViewModel.loadOlder()
    }

    fun refresh() {
        if (state.initialLoading || state.refreshing || state.loadingOlder) return
        stickToBottomAfterRefresh = isAtBottom
        actionError = null
        feedViewModel.refresh()
    }

    val navigateBack = onBack ?: {
        navController.popBackStack()
        Unit
    }
    BackHandler(onBack = navigateBack)
    LaunchedEffect(
        state.ownerKey,
        state.hasLoaded,
        state.initialLoading,
        state.messages.size,
    ) {
        if (
            !hasPositioned &&
            state.hasLoaded &&
            !state.initialLoading &&
            state.messages.isNotEmpty()
        ) {
            listState.scrollToItem(state.messages.size)
            hasPositioned = true
        }
    }
    LaunchedEffect(state.loadingOlder, state.messages, pendingAnchorUuid) {
        val anchorUuid = pendingAnchorUuid
        if (!state.loadingOlder && anchorUuid != null) {
            val anchorIndex = state.messages.indexOfFirst { it.uuid == anchorUuid }
            if (anchorIndex >= 0) {
                withFrameNanos { }
                listState.scrollToItem(
                    index = anchorIndex + 1,
                    scrollOffset = -pendingAnchorOffset,
                )
                repeat(FEED_ANCHOR_CORRECTION_FRAMES) {
                    withFrameNanos { }
                    val layout = listState.layoutInfo
                    val anchoredItem = layout.visibleItemsInfo.firstOrNull {
                        it.key == anchorUuid
                    } ?: return@repeat
                    val currentOffset =
                        anchoredItem.offset - layout.viewportStartOffset
                    val correction = currentOffset - pendingAnchorOffset
                    if (kotlin.math.abs(correction) > 1) {
                        listState.scrollBy(correction.toFloat())
                    }
                }
            }
            pendingAnchorUuid = null
        }
    }
    LaunchedEffect(state.refreshing, state.messages.size, stickToBottomAfterRefresh) {
        if (!state.refreshing && stickToBottomAfterRefresh && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size)
            stickToBottomAfterRefresh = false
        }
    }
    LaunchedEffect(
        listState,
        hasPositioned,
        state.hasMore,
        state.loadingOlder,
    ) {
        if (!hasPositioned) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                if (firstIndex > FEED_PAGINATION_REARM_INDEX) {
                    paginationArmed = true
                } else if (
                    paginationArmed &&
                    firstIndex <= 1 &&
                    state.hasMore &&
                    !state.loadingOlder
                ) {
                    captureAnchorAndLoadOlder()
                }
            }
    }

    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (kind == MessageTimelineKind.STREAM && streamUuid != null) {
            StreamTimelineTopBar(
                title = title,
                busyDescription = kind.busyDescription,
                busy = state.initialLoading || state.refreshing || state.loadingOlder,
                onBack = navigateBack,
                onRefresh = ::refresh,
                onInfo = {
                    navController.navigate(ChatFlow.ChannelInfo(streamUuid))
                },
            )
        } else {
            FeedTopBar(
                title = title,
                refreshDescription = kind.refreshDescription,
                busyDescription = kind.busyDescription,
                busy = state.initialLoading || state.refreshing || state.loadingOlder,
                onBack = navigateBack,
                onRefresh = ::refresh,
            )
        }
        if (state.refreshing && state.messages.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.searchBackground,
            )
        }
        val visibleError = actionError ?: state.error
        if (
            visibleError != null &&
            hasDisplayableFeedSnapshot(state)
        ) {
            FeedErrorBanner(
                message = visibleError,
                showRetry = actionError == null,
                onRetry = ::refresh,
                onDismiss = { actionError = null },
            )
        }
        when {
            state.messages.isNotEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = if (kind == MessageTimelineKind.STREAM) {
                                24.dp
                            } else {
                                76.dp
                            },
                        ),
                        verticalArrangement = if (kind == MessageTimelineKind.STREAM) {
                            Arrangement.Top
                        } else {
                            Arrangement.spacedBy(8.dp)
                        },
                    ) {
                        item(key = "feed-history-state") {
                            FeedHistoryState(
                                loading = state.loadingOlder,
                                error = state.olderError,
                                hasMore = state.hasMore,
                                onLoad = ::captureAnchorAndLoadOlder,
                            )
                        }
                        itemsIndexed(
                            items = state.messages,
                            key = { _, message -> message.uuid },
                        ) { index, message ->
                            val topic = topicsByStream[message.streamUuid]
                                .orEmpty()
                                .firstOrNull { it.uuid == message.topicUuid }
                            val author = users.firstOrNull {
                                it.uuid == message.authorUuid
                            }
                            if (kind == MessageTimelineKind.STREAM) {
                                StreamMessageRow(
                                    message = message,
                                    topic = topic,
                                    author = author,
                                    avatarBaseUrl = avatarBaseUrl.orEmpty(),
                                    showAvatar = streamMessageGroupEndsAt(
                                        state.messages,
                                        index,
                                    ),
                                    busy = openingMessageUuid == message.uuid,
                                    onOpen = { openMessage(message, false) },
                                    onForward = { openMessage(message, true) },
                                    modifier = Modifier.padding(
                                        bottom = streamMessageBottomSpacing(
                                            state.messages,
                                            index,
                                        ).dp,
                                    ),
                                )
                            } else {
                                FeedMessageCard(
                                    message = message,
                                    stream = streams.firstOrNull {
                                        it.uuid == message.streamUuid
                                    },
                                    topic = topic,
                                    author = author,
                                    busy = openingMessageUuid == message.uuid,
                                    onOpen = { openMessage(message, false) },
                                    onForward = { openMessage(message, true) },
                                )
                            }
                        }
                    }
                    if (!isAtBottom) {
                        FeedIconButton(
                            drawable = R.drawable.ic_arrow_down,
                            description = "К новым сообщениям",
                            enabled = true,
                            onClick = {
                                if (currentMessages.isNotEmpty()) {
                                    listState.requestScrollToItem(currentMessages.size)
                                    paginationArmed = true
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            elevated = true,
                        )
                    }
                }
            }

            state.initialLoading || (!state.hasLoaded && state.error == null) -> {
                FeedStateCard("Загрузка…", loading = true)
            }

            state.error != null &&
                !hasDisplayableFeedSnapshot(state) -> {
                FeedStateCard(
                    message = state.error.orEmpty(),
                    action = "Повторить",
                    onAction = ::refresh,
                )
            }

            else -> {
                FeedStateCard(kind.emptyMessage)
            }
        }
    }
}

@Composable
private fun FeedTopBar(
    title: String,
    refreshDescription: String,
    busyDescription: String,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.background)
            .padding(horizontal = 4.dp),
    ) {
        FeedIconButton(
            drawable = R.drawable.arrow_back,
            description = "Назад к чатам",
            enabled = true,
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontFamily = NavigationFontFamily,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 52.dp),
        )
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(20.dp)
                    .semantics { contentDescription = busyDescription },
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        } else {
            FeedIconButton(
                drawable = R.drawable.ic_refresh,
                description = refreshDescription,
                enabled = true,
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun FeedMessageCard(
    message: MessageResponse,
    stream: Stream?,
    topic: TopicsResponseData?,
    author: UserResponseData?,
    busy: Boolean,
    onOpen: () -> Unit,
    onForward: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val streamName = stream?.name?.trim()?.takeIf(String::isNotEmpty)
    val topicName = topic?.name?.trim()?.takeIf(String::isNotEmpty)
    val context = when {
        streamName == null && topicName == null -> "Чат"
        streamName == null -> topicName.orEmpty()
        stream.isDirectProviderChat() && topicName != null ->
            "$streamName · $topicName"
        stream.isDirectProviderChat() -> streamName
        topicName != null -> "#$streamName · $topicName"
        else -> "#$streamName"
    }
    val authorName = author?.displayableName()?.trim()?.takeIf(String::isNotEmpty)
        ?: "Участник"
    val summary = remember(message.payload.content) {
        feedMessageSummary(message.payload.content)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackgroundBase)
            .border(
                width = 1.dp,
                color = colors.cardBackgroundActive,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = feedMessageTime(message.createdAt),
                color = colors.messageTimeColor,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text(
                text = context,
                color = colors.textAdditional50,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        Text(
            text = authorName,
            color = colors.messageSecondaryText,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            text = summary,
            color = colors.textHeaders,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.background)
                .clickable(
                    enabled = !busy,
                    role = Role.Button,
                    onClick = onOpen,
                )
                .semantics {
                    role = Role.Button
                    contentDescription =
                        "Открыть сообщение от $authorName в $context"
                }
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(14.dp)
                        .size(20.dp)
                        .semantics {
                            contentDescription = "Открытие сообщения"
                        },
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
            }
            FeedIconButton(
                drawable = R.drawable.chat_bubble,
                description = "Открыть в чате",
                enabled = !busy,
                onClick = onOpen,
            )
            FeedIconButton(
                drawable = R.drawable.send,
                description = "Переслать сообщение",
                enabled = !busy,
                onClick = onForward,
            )
        }
    }
}

@Composable
private fun FeedHistoryState(
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onLoad: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Загрузка предыдущих сообщений…",
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            error != null -> {
                Text(
                    text = error,
                    color = colors.indicatorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onLoad) { Text("Повторить") }
            }

            hasMore -> {
                TextButton(onClick = onLoad) {
                    Text("Загрузить предыдущие")
                }
            }

            else -> {
                Text(
                    text = "Начало ленты",
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun FeedErrorBanner(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = colors.indicatorRed,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = if (showRetry) onRetry else onDismiss) {
            Text(if (showRetry) "Повторить" else "Закрыть")
        }
    }
}

@Composable
private fun FeedStateCard(
    message: String,
    loading: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(28.dp),
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = message,
            color = if (action == null) {
                colors.textAdditional50
            } else {
                colors.indicatorRed
            },
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun FeedIconButton(
    drawable: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (elevated) {
                    Modifier
                        .background(colors.cardBackgroundBase)
                        .border(1.dp, colors.cardBackgroundActive, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = null,
            tint = if (enabled) colors.iconActive else colors.iconDisable,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun feedMessageTime(value: String): String {
    val instant = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: return ""
    return FEED_TIME_FORMATTER
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private const val FEED_PAGINATION_REARM_INDEX = 3
private const val FEED_ANCHOR_CORRECTION_FRAMES = 2
private val FEED_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM, HH:mm")
