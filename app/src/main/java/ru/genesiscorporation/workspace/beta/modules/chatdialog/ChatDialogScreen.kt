package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxEntry
import ru.genesiscorporation.workspace.beta.data.PersistedOutboxStatus
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.LocalWorkspaceMentionCatalog
import ru.genesiscorporation.workspace.beta.ui.WorkspaceMentionCandidate
import ru.genesiscorporation.workspace.beta.ui.WorkspaceMentionCatalog
import ru.genesiscorporation.workspace.beta.ui.WorkspaceEmojiShortcodeCatalog
import ru.genesiscorporation.workspace.beta.ui.workspaceReactionDisplayText
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ChatDialogScreen(
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
) {
    val applicationContext = LocalContext.current.applicationContext
    val reactionEmojiResolver = remember(applicationContext) {
        WorkspaceEmojiShortcodeCatalog.resolver(applicationContext)
    }
    val reactionAliasesByGlyph = remember(applicationContext) {
        WorkspaceEmojiShortcodeCatalog
            .pickerEntries(applicationContext)
            .associate { entry -> entry.glyph to entry.aliases.toSet() }
    }
    val streamTopicMessages by viewModel.streamTopicMessages.collectAsStateWithLifecycle()
    val reactionCountOverrides by
        viewModel.repo.messageReactionOverrides
            .collectAsStateWithLifecycle()
    val userReactions by
        viewModel.repo.userReactions.collectAsStateWithLifecycle()
    val myReactionNamesByMessage = remember(userReactions) {
        userReactions
            .groupBy(
                keySelector = { it.messageUuid },
                valueTransform = { it.emojiName },
            )
            .mapValues { (_, names) -> names.toSet() }
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val actionNotice by viewModel.actionNotice.collectAsState()
    val accessibilityManager = LocalAccessibilityManager.current
    val actionNoticeMillis =
        accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = ACTION_NOTICE_MILLIS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: ACTION_NOTICE_MILLIS
    val readError by viewModel.readError.collectAsStateWithLifecycle()
    val focusedMessageUuid by viewModel.focusedMessageUuid.collectAsState()
    val outboxEntries by viewModel.outboxEntries.collectAsStateWithLifecycle()
    val draftSyncState by viewModel.draftSyncState.collectAsStateWithLifecycle()
    val verifyingOutbox by viewModel.verifyingOutbox.collectAsStateWithLifecycle()
    val loadingOlderMessages by
        viewModel.loadingOlderMessages.collectAsStateWithLifecycle()
    val hasOlderMessages by viewModel.hasOlderMessages.collectAsStateWithLifecycle()
    val olderMessagesError by viewModel.olderMessagesError.collectAsStateWithLifecycle()
    val loadingNewerMessages by
        viewModel.loadingNewerMessages.collectAsStateWithLifecycle()
    val hasNewerMessages by viewModel.hasNewerMessages.collectAsStateWithLifecycle()
    val newerMessagesError by viewModel.newerMessagesError.collectAsStateWithLifecycle()
    val topicUnreadCount by viewModel.topicUnreadCount.collectAsStateWithLifecycle()
    val forwardDialogState by
        viewModel.forwardDialogState.collectAsStateWithLifecycle()
    val reactionPickerMessageUuid by
        viewModel.reactionPickerMessageUuid.collectAsStateWithLifecycle()
    val users by viewModel.repo.users.collectAsStateWithLifecycle()
    val mentionCandidates = remember(users) {
        users.map { user ->
            WorkspaceMentionCandidate(
                userUuid = user.uuid,
                displayText = user.displayableName(),
                username = user.username,
            )
        }
    }
    val mentionCatalog = remember(mentionCandidates) {
        WorkspaceMentionCatalog.from(mentionCandidates)
    }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenResumed by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(
                Lifecycle.State.RESUMED,
            ),
        )
    }
    LaunchedEffect(actionNotice?.eventId, actionNoticeMillis) {
        val eventId = actionNotice?.eventId ?: return@LaunchedEffect
        delay(actionNoticeMillis)
        viewModel.clearActionNoticeIfCurrent(eventId)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isScreenResumed =
                lifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.RESUMED,
                )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val scope = rememberCoroutineScope()
    val uiMode = LocalConfiguration.current.uiMode
    val key = "${viewModel.chatId}.${viewModel.topicUuid}"
    var hasPositionedConversation by rememberSaveable(key) {
        mutableStateOf(false)
    }
    var pendingHistoryViewportAnchor by remember(key) {
        mutableStateOf<HistoryViewportAnchor?>(null)
    }
    var unreadAnchorUuid by rememberSaveable(key, "unread-anchor") {
        mutableStateOf<String?>(null)
    }
    var userScrollSeen by rememberSaveable(key, "read-scroll") {
        mutableStateOf(false)
    }
    val messages = remember(streamTopicMessages[key]) {
        streamTopicMessages[key].orEmpty()
            .sortedBy { messageSortInstant(it.createdAt) }
    }
    val loadedUnreadMessages = remember(messages) {
        messages.filter { !it.read && !it.isOwn }
    }
    val effectiveUnreadAnchorUuid =
        unreadAnchorUuid ?: loadedUnreadMessages.firstOrNull()?.uuid
    val effectiveUnreadCount =
        topicUnreadCount.takeIf { it > 0 } ?: loadedUnreadMessages.size
    val currentOldestMessageUuid by rememberUpdatedState(
        messages.firstOrNull()?.uuid,
    )
    val showOlderHistoryStatus =
        loadingOlderMessages || olderMessagesError != null || hasOlderMessages
    val showNewerHistoryStatus =
        loadingNewerMessages || newerMessagesError != null || hasNewerMessages
    val historyTopItemOffset = if (showOlderHistoryStatus) 1 else 0
    val lastMessageListIndex = messages.lastIndex + historyTopItemOffset

    LaunchedEffect(reactionCountOverrides, lastMessageListIndex) {
        if (
            reactionCountOverrides.isEmpty() ||
            lastMessageListIndex < 0
        ) {
            return@LaunchedEffect
        }
        val lastVisibleIndex =
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val wasAtConversationEnd =
            lastVisibleIndex == null ||
                lastVisibleIndex >= lastMessageListIndex
        if (wasAtConversationEnd) {
            delay(32)
            listState.scrollToItem(lastMessageListIndex)
        }
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.openSourceMessageEvents.collect { event ->
            navController.navigate(
                ChatFlow.ChatDialog(
                    title = event.title,
                    chatId = event.streamUuid,
                    topicName = event.topicName,
                    topicUuid = event.topicUuid,
                    isDirectMessages = event.isDirectMessages,
                    userId = null,
                    focusMessageUuid = event.messageUuid,
                ),
            )
        }
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.openWorkspaceConversationEvents.collect { event ->
            when (event) {
                is OpenWorkspaceConversationEvent.Dialog ->
                    navController.navigate(event.route)

                is OpenWorkspaceConversationEvent.TopicList ->
                    navController.navigate(event.route)
            }
        }
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.openWorkspaceUserEvents.collect { route ->
            navController.navigate(route)
        }
    }

    fun captureAndStoreHistoryViewportAnchor(unstableBoundaryUuid: String?) {
        pendingHistoryViewportAnchor = listState.captureHistoryViewportAnchor(
            unstableBoundaryUuid = unstableBoundaryUuid,
        )
    }

    LaunchedEffect(messages, loadedUnreadMessages) {
        unreadAnchorUuid = when {
            loadedUnreadMessages.isEmpty() -> null
            unreadAnchorUuid != null &&
                loadedUnreadMessages.any { it.uuid == unreadAnchorUuid } ->
                unreadAnchorUuid

            else -> loadedUnreadMessages.first().uuid
        }
    }

    LaunchedEffect(
        messages.size,
        uiMode,
        historyTopItemOffset,
        effectiveUnreadAnchorUuid,
    ) {
        if (messages.isNotEmpty() && focusedMessageUuid == null) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
            val unreadIndex = messages.indexOfFirst {
                it.uuid == effectiveUnreadAnchorUuid
            }
            if (!hasPositionedConversation && unreadIndex >= 0) {
                listState.scrollToItem(unreadIndex + historyTopItemOffset)
            } else if (shouldPositionConversationAtLatest(
                    hasPositionedConversation = hasPositionedConversation,
                    lastVisibleIndex = lastVisibleIndex,
                    lastListIndex = lastMessageListIndex,
                )
            ) {
                listState.scrollToItem(lastMessageListIndex)
            }
            hasPositionedConversation = true
        }
    }

    LaunchedEffect(listState, key) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userScrollSeen = true
            }
        }
    }

    LaunchedEffect(
        listState,
        messages,
        userScrollSeen,
        historyTopItemOffset,
    ) {
        if (!userScrollSeen) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.mapNotNull { itemInfo ->
                val messageIndex = itemInfo.index - historyTopItemOffset
                val message = messages.getOrNull(messageIndex)
                    ?: return@mapNotNull null
                val visibleStart = max(
                    itemInfo.offset,
                    layout.viewportStartOffset,
                )
                val visibleEnd = min(
                    itemInfo.offset + itemInfo.size,
                    layout.viewportEndOffset,
                )
                val visiblePixels =
                    (visibleEnd - visibleStart).coerceAtLeast(0)
                message.uuid.takeIf {
                    !message.read &&
                        !message.isOwn &&
                        visiblePixels * 2 >= itemInfo.size
                }
            }
        }
            .distinctUntilChanged()
            .collect(viewModel::markVisibleMessagesRead)
    }

    LaunchedEffect(
        listState,
        messages,
        loadedUnreadMessages,
        topicUnreadCount,
        userScrollSeen,
        isScreenResumed,
        hasNewerMessages,
        loadingNewerMessages,
        historyTopItemOffset,
    ) {
        if (
            userScrollSeen ||
            !isScreenResumed ||
            hasNewerMessages ||
            loadingNewerMessages ||
            loadedUnreadMessages.isEmpty()
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = listState.layoutInfo
            val visibleUnread = mutableListOf<String>()
            var lastMessageFullyVisible = false
            layout.visibleItemsInfo.forEach { itemInfo ->
                val messageIndex = itemInfo.index - historyTopItemOffset
                val message = messages.getOrNull(messageIndex)
                    ?: return@forEach
                val visibleStart = max(
                    itemInfo.offset,
                    layout.viewportStartOffset,
                )
                val visibleEnd = min(
                    itemInfo.offset + itemInfo.size,
                    layout.viewportEndOffset,
                )
                val visiblePixels =
                    (visibleEnd - visibleStart).coerceAtLeast(0)
                if (
                    !message.read &&
                    !message.isOwn &&
                    visiblePixels * 2 >= itemInfo.size
                ) {
                    visibleUnread += message.uuid
                }
                if (
                    messageIndex == messages.lastIndex &&
                    visiblePixels >= itemInfo.size
                ) {
                    lastMessageFullyVisible = true
                }
            }
            CompleteUnreadTailSnapshot(
                visibleUnreadUuids = visibleUnread,
                lastMessageFullyVisible = lastMessageFullyVisible,
            )
        }
            .distinctUntilChanged()
            .collect { snapshot ->
                if (
                    shouldAutoReadCompleteUnreadTail(
                        userScrollSeen = userScrollSeen,
                        isScreenResumed = isScreenResumed,
                        hasExplicitMessageRoute =
                            viewModel.hasExplicitMessageRoute,
                        hasNewerMessages = hasNewerMessages,
                        loadingNewerMessages = loadingNewerMessages,
                        loadedUnreadCount = loadedUnreadMessages.size,
                        topicUnreadCount = topicUnreadCount,
                        visibleUnreadCount =
                            snapshot.visibleUnreadUuids.size,
                        lastMessageFullyVisible =
                            snapshot.lastMessageFullyVisible,
                    )
                ) {
                    viewModel.markVisibleMessagesRead(
                        snapshot.visibleUnreadUuids,
                    )
                }
            }
    }

    LaunchedEffect(focusedMessageUuid, messages) {
        val messageUuid = focusedMessageUuid ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.uuid == messageUuid }
        if (index >= 0) {
            // External routes must land deterministically even when a cached
            // conversation and the first network page change layout together.
            // An animation can be cancelled by that relayout and leave the
            // user at an unrelated older row.
            withFrameNanos { }
            listState.scrollToItem(index + historyTopItemOffset)
            hasPositionedConversation = true
            viewModel.clearMessageFocus()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisibleItemIndex ->
                if (firstVisibleItemIndex <= HISTORY_LOAD_TRIGGER_INDEX) {
                    if (viewModel.loadOlderMessages()) {
                        captureAndStoreHistoryViewportAnchor(
                            unstableBoundaryUuid = currentOldestMessageUuid,
                        )
                    }
                }
            }
    }

    LaunchedEffect(
        listState,
        showNewerHistoryStatus,
        lastMessageListIndex,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .distinctUntilChanged()
            .collect { lastVisibleItemIndex ->
                if (
                    lastVisibleItemIndex != null &&
                    lastVisibleItemIndex >=
                        lastMessageListIndex - HISTORY_LOAD_TRIGGER_INDEX
                ) {
                    viewModel.loadNewerMessages()
                }
            }
    }

    LaunchedEffect(
        messages.size,
        loadingOlderMessages,
        historyTopItemOffset,
    ) {
        val anchor = pendingHistoryViewportAnchor ?: return@LaunchedEffect
        if (loadingOlderMessages) return@LaunchedEffect
        val messageIndex = messages.indexOfFirst {
            it.uuid == anchor.messageUuid
        }
        if (messageIndex >= 0) {
            // Wait until both the new page and the history status row have
            // reached LazyColumn's layout. Otherwise removing a retry/error
            // row can shift the viewport again immediately after restoration.
            withFrameNanos { }
            listState.scrollToItem(
                index = messageIndex + historyTopItemOffset,
                scrollOffset = -anchor.offsetFromViewportStart,
            )
            repeat(HISTORY_ANCHOR_CORRECTION_FRAMES) {
                withFrameNanos { }
                val layout = listState.layoutInfo
                val anchoredItem = layout.visibleItemsInfo.firstOrNull {
                    it.key == anchor.messageUuid
                } ?: return@repeat
                val currentOffset =
                    anchoredItem.offset - layout.viewportStartOffset
                val correction = historyViewportCorrection(
                    currentOffset = currentOffset,
                    targetOffset = anchor.offsetFromViewportStart,
                )
                if (correction != 0) {
                    listState.scrollBy(correction.toFloat())
                }
            }
        }
        pendingHistoryViewportAnchor = null
    }

    Scaffold(
        containerColor = LocalWorkspaceColorsPalette.current.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ConversationHeader(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onInfo = {
                    if (!viewModel.isDirectMessages) {
                        navController.navigate(ChatFlow.ChannelInfo(viewModel.chatId))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(LocalWorkspaceColorsPalette.current.background)
                .imePadding(),
        ) {
            when {
                isLoading && messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedGif(Modifier.size(72.dp))
                    }
                }

                loadError != null && messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = loadError.orEmpty(),
                            color = LocalWorkspaceColorsPalette.current.indicatorRed,
                            fontSize = 15.sp,
                        )
                        TextButton(onClick = viewModel::retryLoad) {
                            Text("Повторить")
                        }
                    }
                }

                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Сообщений пока нет",
                            color = LocalWorkspaceColorsPalette.current.textAdditional50,
                            fontSize = 15.sp,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            space = 5.dp,
                            alignment = Alignment.Bottom,
                        ),
                    ) {
                        if (showOlderHistoryStatus) {
                            item(key = OLDER_HISTORY_STATUS_KEY) {
                                MessageHistoryStatus(
                                    loading = loadingOlderMessages,
                                    error = olderMessagesError,
                                    hasMore = hasOlderMessages,
                                    loadingLabel = "Загрузка предыдущих сообщений…",
                                    loadLabel = "Загрузить предыдущие",
                                    onLoad = {
                                        if (viewModel.loadOlderMessages()) {
                                            captureAndStoreHistoryViewportAnchor(
                                                unstableBoundaryUuid =
                                                    messages.firstOrNull()?.uuid,
                                            )
                                        }
                                    },
                                    onRetry = {
                                        if (viewModel.retryOlderMessages()) {
                                            captureAndStoreHistoryViewportAnchor(
                                                unstableBoundaryUuid =
                                                    messages.firstOrNull()?.uuid,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        items(
                            items = messages,
                            key = { it.uuid },
                        ) { message ->
                            Column {
                                if (message.uuid == effectiveUnreadAnchorUuid) {
                                    UnreadMessagesMarker(effectiveUnreadCount)
                                }
                                CompositionLocalProvider(
                                    LocalWorkspaceMentionCatalog provides
                                        mentionCatalog,
                                ) {
                                    ChatMessage(
                                        item = message,
                                        viewModel = viewModel,
                                        navController = navController,
                                        myReactionEmojiNames =
                                            myReactionNamesByMessage[
                                                message.uuid
                                            ].orEmpty(),
                                        reactionEmojiResolver =
                                            reactionEmojiResolver,
                                        reactionAliasesByGlyph =
                                            reactionAliasesByGlyph,
                                        reactionCounts =
                                            reactionCountOverrides[
                                                message.uuid
                                            ] ?: message.reactions,
                                        outboxEntry = outboxEntries.firstOrNull {
                                            it.localMessageUuid == message.uuid
                                        },
                                        isVerifyingOutbox =
                                            message.uuid in verifyingOutbox,
                                        onImageLoad = {
                                            val lastVisibleIndex =
                                                listState.layoutInfo
                                                    .visibleItemsInfo
                                                    .lastOrNull()
                                                    ?.index
                                            if (
                                                lastVisibleIndex == null ||
                                                lastVisibleIndex >=
                                                    lastMessageListIndex - 1
                                            ) {
                                                scope.launch {
                                                    listState.scrollToItem(
                                                        lastMessageListIndex,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        if (showNewerHistoryStatus) {
                            item(key = NEWER_HISTORY_STATUS_KEY) {
                                MessageHistoryStatus(
                                    loading = loadingNewerMessages,
                                    error = newerMessagesError,
                                    hasMore = hasNewerMessages,
                                    loadingLabel = "Загрузка следующих сообщений…",
                                    loadLabel = "Загрузить следующие",
                                    onLoad = { viewModel.loadNewerMessages() },
                                    onRetry = { viewModel.retryNewerMessages() },
                                )
                            }
                        }
                    }
                }
            }
            if (loadError != null && messages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.infoCardBackground,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = loadError.orEmpty(),
                        color = LocalWorkspaceColorsPalette.current.indicatorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retryLoad) {
                        Text("Повторить")
                    }
                }
            }
            actionError?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.infoCardBackground,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error,
                        color = LocalWorkspaceColorsPalette.current.indicatorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::clearActionError) {
                        Text("Закрыть")
                    }
                }
            }
            actionNotice?.let { notice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.infoCardBackground,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notice.message,
                        color = LocalWorkspaceColorsPalette.current.messageAccentGreen,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                    TextButton(onClick = viewModel::clearActionNotice) {
                        Text("Закрыть")
                    }
                }
            }
            readError?.let { error ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(
                            LocalWorkspaceColorsPalette.current.infoCardBackground,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(start = 12.dp, top = 8.dp),
                ) {
                    Text(
                        text = error,
                        color = LocalWorkspaceColorsPalette.current.indicatorRed,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp),
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        TextButton(onClick = viewModel::clearReadError) {
                            Text("Закрыть")
                        }
                        TextButton(onClick = viewModel::retryReadMessages) {
                            Text("Повторить")
                        }
                    }
                }
            }
            val visibleDraftSync = draftSyncState?.takeIf {
                it.status == PersistedDraftSyncStatus.FAILED ||
                    it.status == PersistedDraftSyncStatus.CONFLICT
            }
            if (visibleDraftSync != null) {
                DraftSyncBanner(
                    status = visibleDraftSync.status,
                    error = visibleDraftSync.errorMessage,
                    onRetry = viewModel::retryDraftSync,
                    onAcceptServer = viewModel::acceptServerDraft,
                    onKeepLocal = viewModel::keepLocalDraft,
                    onDelete = viewModel::deleteConflictedDraft,
                )
            }
            SendMessageView(viewModel)
        }
    }
    forwardDialogState?.let { state ->
        CompositionLocalProvider(
            LocalWorkspaceMentionCatalog provides mentionCatalog,
        ) {
            ForwardMessageDialog(
                viewModel = viewModel,
                state = state,
            )
        }
    }
    MessageReactionPicker(
        open = reactionPickerMessageUuid != null,
        onDismiss = viewModel::closeMessageReactionPicker,
        onReaction = { reaction ->
            reactionPickerMessageUuid?.let { messageUuid ->
                viewModel.onMessageReactionTap(
                    messageUuid = messageUuid,
                    emojiName = reaction.emojiName,
                    equivalentEmojiNames =
                        reaction.equivalentEmojiNames,
                )
            }
            viewModel.closeMessageReactionPicker()
        },
    )
}

@Composable
private fun DraftSyncBanner(
    status: PersistedDraftSyncStatus,
    error: String?,
    onRetry: () -> Unit,
    onAcceptServer: () -> Unit,
    onKeepLocal: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (status == PersistedDraftSyncStatus.CONFLICT) {
                "Черновик изменён на другом устройстве"
            } else {
                error ?: "Не удалось синхронизировать черновик"
            },
            color = colors.indicatorRed,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        if (status == PersistedDraftSyncStatus.CONFLICT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onAcceptServer) {
                    Text("Версия сервера")
                }
                TextButton(onClick = onKeepLocal) {
                    Text("Оставить мою")
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = "Удалить",
                        color = colors.indicatorRed,
                    )
                }
            }
        } else {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Повторить")
            }
        }
    }
}

private data class CompleteUnreadTailSnapshot(
    val visibleUnreadUuids: List<String>,
    val lastMessageFullyVisible: Boolean,
)

internal fun shouldAutoReadCompleteUnreadTail(
    userScrollSeen: Boolean,
    isScreenResumed: Boolean,
    hasExplicitMessageRoute: Boolean,
    hasNewerMessages: Boolean,
    loadingNewerMessages: Boolean,
    loadedUnreadCount: Int,
    topicUnreadCount: Int,
    visibleUnreadCount: Int,
    lastMessageFullyVisible: Boolean,
): Boolean {
    if (
        userScrollSeen ||
        !isScreenResumed ||
        hasExplicitMessageRoute ||
        hasNewerMessages ||
        loadingNewerMessages ||
        loadedUnreadCount <= 0 ||
        !lastMessageFullyVisible
    ) {
        return false
    }
    val entireUnreadSetLoaded =
        topicUnreadCount <= 0 || topicUnreadCount == loadedUnreadCount
    return entireUnreadSetLoaded &&
        visibleUnreadCount == loadedUnreadCount
}

@Composable
private fun UnreadMessagesMarker(unreadCount: Int) {
    val colors = LocalWorkspaceColorsPalette.current
    val markerColor = colors.messageAccentBlue
    val label = if (unreadCount > 0) {
        "Непрочитанные сообщения • $unreadCount"
    } else {
        "Непрочитанные сообщения"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(markerColor.copy(alpha = 0.55f)),
        )
        Text(
            text = label,
            color = markerColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(markerColor.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun MessageHistoryStatus(
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    loadingLabel: String,
    loadLabel: String,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
                    text = loadingLabel,
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = onRetry) {
                    Text("Повторить")
                }
            }

            hasMore -> {
                TextButton(onClick = onLoad) {
                    Text(loadLabel)
                }
            }
        }
    }
}

private const val HISTORY_LOAD_TRIGGER_INDEX = 2
private const val ACTION_NOTICE_MILLIS = 4_000L
private const val HISTORY_ANCHOR_CORRECTION_FRAMES = 2

internal fun shouldPositionConversationAtLatest(
    hasPositionedConversation: Boolean,
    lastVisibleIndex: Int?,
    lastListIndex: Int,
): Boolean =
    !hasPositionedConversation ||
        (
            lastVisibleIndex != null &&
                lastVisibleIndex >= lastListIndex - 1
            )

internal fun historyViewportCorrection(
    currentOffset: Int,
    targetOffset: Int,
): Int {
    val correction = currentOffset - targetOffset
    return correction.takeIf { abs(it) > 1 } ?: 0
}

private data class HistoryViewportAnchor(
    val messageUuid: String,
    val offsetFromViewportStart: Int,
)

private fun androidx.compose.foundation.lazy.LazyListState
    .captureHistoryViewportAnchor(
        unstableBoundaryUuid: String?,
    ): HistoryViewportAnchor? {
    val layout = layoutInfo
    val visibleMessages = layout.visibleItemsInfo.filter {
        it.key != OLDER_HISTORY_STATUS_KEY &&
            it.key != NEWER_HISTORY_STATUS_KEY
    }
    // Prepending a page can add/remove the date separator inside the previous
    // oldest message. Prefer the next visible message, whose internal layout
    // is stable because its predecessor already existed before pagination.
    val message = visibleMessages.firstOrNull {
        it.key != unstableBoundaryUuid
    } ?: visibleMessages.firstOrNull() ?: return null
    val messageUuid = message.key as? String ?: return null
    return HistoryViewportAnchor(
        messageUuid = messageUuid,
        offsetFromViewportStart = message.offset - layout.viewportStartOffset,
    )
}

private const val OLDER_HISTORY_STATUS_KEY = "message-history-status-older"
private const val NEWER_HISTORY_STATUS_KEY = "message-history-status-newer"

@Composable
private fun ConversationHeader(
    viewModel: ChatDialogViewModel,
    onBack: () -> Unit,
    onInfo: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val context = LocalContext.current
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val conversationStateReady by
        viewModel.conversationStateReady.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(
                colors.chatHeaderBackground,
                RoundedCornerShape(bottomStart = 13.dp, bottomEnd = 13.dp),
            )
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Назад",
                tint = colors.iconBase,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    enabled = !viewModel.isDirectMessages,
                    onClick = onInfo,
                ),
        ) {
            Text(
                text = viewModel.chatTitle,
                color = colors.textHeaders,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            viewModel.topicName?.takeIf { it.isNotBlank() }?.let { topic ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(width = 3.dp, height = 18.dp)
                            .background(colors.indicatorYellow, RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = "# $topic",
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val callServerUrl = viewModel.repo.jitsiServerUrl
            .takeIf(String::isNotBlank)
            ?.let { runCatching { URL(it) }.getOrNull() }
            ?.takeIf { it.protocol == "https" && it.host.isNotBlank() }
        if (callServerUrl != null) {
            LaunchedEffect(viewModel, callServerUrl) {
                viewModel.callLaunchEvents.collect { event ->
                    val options = JitsiMeetConferenceOptions.Builder()
                        .setServerURL(callServerUrl)
                        .setRoom(event.roomName)
                        .build()
                    JitsiMeetActivity.launch(context, options)
                }
            }
            Button(
                onClick = {
                    val roomName = JitsiStyleRoomNameGenerator.generate()
                    viewModel.startCall(
                        callUrl = "$callServerUrl/$roomName",
                        roomName = roomName,
                    )
                },
                enabled = conversationStateReady && !sending,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.indicatorGreen,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.call),
                    contentDescription = "Начать звонок",
                )
            }
        }
    }
}

@Composable
fun ChatMessage(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    myReactionEmojiNames: Set<String>,
    reactionEmojiResolver: (String) -> String?,
    reactionAliasesByGlyph: Map<String, Set<String>>,
    reactionCounts: Map<String, Int>,
    outboxEntry: PersistedOutboxEntry? = null,
    isVerifyingOutbox: Boolean = false,
    onImageLoad: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val previous = viewModel.previousMessageByUuid(item.uuid)
    val currentDate = messageLocalDate(item.createdAt)
    val previousDate = previous?.let {
        messageLocalDate(it.createdAt)
    }
    val locale = LocalLocale.current.platformLocale

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (item.isOwn) Alignment.End else Alignment.Start,
    ) {
        if (currentDate != null && currentDate != previousDate) {
            Text(
                text = currentDate.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                color = colors.textAdditional50,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .background(colors.surface, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val upload = item.payload.content.parseWorkspaceAttachmentsOrNull()
        val jitsiBaseUrl = viewModel.repo.jitsiServerUrl
        when {
            outboxEntry != null ->
                OutboxMessageView(
                    item = item,
                    outboxEntry = outboxEntry,
                    isVerifying = isVerifyingOutbox,
                    viewModel = viewModel,
                    navController = navController,
                )

            jitsiBaseUrl.isNotBlank() &&
                Patterns.WEB_URL.matcher(item.payload.content).matches() &&
                item.payload.content.startsWith(jitsiBaseUrl) ->
                CallMessageView(item, viewModel, navController)

            upload != null &&
                upload.attachments.all { it.kind == WorkspaceAttachmentKind.IMAGE } ->
                ImageMessageView(
                    text = upload.caption,
                    imageUrls = upload.attachments.map { it.urn },
                    viewModel = viewModel,
                    item = item,
                    navController = navController,
                    onImageLoad = onImageLoad,
                )

            upload != null ->
                AttachmentMessageView(
                    text = upload.caption,
                    attachments = upload.attachments,
                    viewModel = viewModel,
                    item = item,
                    navController = navController,
                )

            else ->
                TextMessageView(item, viewModel, navController)
        }

        if (outboxEntry == null && reactionCounts.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        start =
                            if (
                                !item.isOwn &&
                                !viewModel.isDirectMessages
                            ) {
                                44.dp
                            } else {
                                0.dp
                            },
                        top = 3.dp,
                    ),
            ) {
                reactionCounts
                    .toSortedMap()
                    .forEach { (emojiName, count) ->
                    val equivalentEmojiNames =
                        reactionEmojiResolver(emojiName)
                            ?.let(reactionAliasesByGlyph::get)
                            .orEmpty() + emojiName
                    val selected =
                        equivalentEmojiNames.any(
                            myReactionEmojiNames::contains,
                        )
                    val displayEmoji = workspaceReactionDisplayText(
                        emojiName,
                        reactionEmojiResolver,
                    )
                    val reactionContentDescription = stringResource(
                        R.string.message_reaction_count_description,
                        displayEmoji,
                        count,
                    )
                    Row(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.primary else colors.iconDisable,
                                shape = CircleShape,
                            )
                            .background(
                                if (selected) colors.primary.copy(alpha = 0.16f) else colors.surface,
                                CircleShape,
                            )
                            .clickable(role = Role.Button) {
                                viewModel.onMessageReactionTap(
                                    item.uuid,
                                    emojiName,
                                    equivalentEmojiNames,
                                )
                            }
                            .heightIn(min = 48.dp)
                            .semantics {
                                this.selected = selected
                                contentDescription =
                                    reactionContentDescription
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = displayEmoji, fontSize = 16.sp)
                        Text(
                            text = count.toString(),
                            color = colors.textAdditional50,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboxMessageView(
    item: MessageResponse,
    outboxEntry: PersistedOutboxEntry,
    isVerifying: Boolean,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    var confirmRetry by remember(outboxEntry.localMessageUuid) {
        mutableStateOf(false)
    }
    var confirmRemove by remember(outboxEntry.localMessageUuid) {
        mutableStateOf(false)
    }
    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .background(
                    colors.messageOwnBackground,
                    messageBubbleShape(isOwn = true),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            EnhancedMarkdown(
                markdown = item.payload.content,
                style = TextStyle(
                    color = colors.textHeaders,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                ),
                navController = navController,
                viewModel = viewModel,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                    Spacer(Modifier.size(5.dp))
                }
                Text(
                    text = when (outboxEntry.status) {
                        PersistedOutboxStatus.SENDING -> "Отправляется…"
                        PersistedOutboxStatus.FAILED -> "Не отправлено"
                        PersistedOutboxStatus.UNCERTAIN ->
                            "Результат отправки не подтверждён"
                    },
                    color = when (outboxEntry.status) {
                        PersistedOutboxStatus.SENDING ->
                            colors.messageTimeColor
                        PersistedOutboxStatus.FAILED ->
                            colors.indicatorRed
                        PersistedOutboxStatus.UNCERTAIN ->
                            colors.messageSecondaryText
                    },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            outboxEntry.errorMessage
                ?.takeIf(String::isNotBlank)
                ?.let { error ->
                    Text(
                        text = error,
                        color = colors.messageSecondaryText,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            when (outboxEntry.status) {
                PersistedOutboxStatus.SENDING -> Unit

                PersistedOutboxStatus.FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.removeOutbox(
                                    outboxEntry.localMessageUuid,
                                )
                            },
                            enabled = !sending,
                        ) {
                            Text("Удалить")
                        }
                        TextButton(
                            onClick = {
                                viewModel.retryOutbox(
                                    outboxEntry.localMessageUuid,
                                )
                            },
                            enabled = !sending,
                        ) {
                            Text("Повторить")
                        }
                    }
                }

                PersistedOutboxStatus.UNCERTAIN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { confirmRemove = true },
                        ) {
                            Text("Скрыть")
                        }
                        TextButton(
                            onClick = {
                                viewModel.verifyOutbox(
                                    outboxEntry.localMessageUuid,
                                )
                            },
                            enabled = !isVerifying,
                        ) {
                            Text("Проверить")
                        }
                        TextButton(
                            onClick = { confirmRetry = true },
                            enabled = !sending && !isVerifying,
                        ) {
                            Text("Отправить снова")
                        }
                    }
                }
            }
        }
    }

    if (confirmRetry) {
        AlertDialog(
            onDismissRequest = { confirmRetry = false },
            title = { Text("Отправить ещё раз?") },
            text = {
                Text(
                    "Сервер мог принять исходное сообщение без ответа. " +
                        "Повторная отправка может создать дубль.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmRetry = false }) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRetry = false
                        viewModel.retryOutbox(outboxEntry.localMessageUuid)
                    },
                ) {
                    Text("Всё равно отправить")
                }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Скрыть локальную запись?") },
            text = {
                Text(
                    "Это уберёт запись только с телефона. " +
                        "Если сервер уже принял сообщение, оно останется в чате.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.removeOutbox(outboxEntry.localMessageUuid)
                    },
                ) {
                    Text("Скрыть")
                }
            },
        )
    }
}

private fun messageLocalDate(createdAt: String) =
    runCatching {
        OffsetDateTime.parse(createdAt)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDate()
    }.getOrNull()

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Long.formatHHmm(zoneId: ZoneId = ZoneId.systemDefault()): String =
    HHMM.format(Instant.ofEpochSecond(this).atZone(zoneId))

object JitsiStyleRoomNameGenerator {
    private val adjectives = listOf(
        "amber", "brisk", "calm", "clever", "daring", "eager", "fancy", "gentle",
        "jolly", "kind", "lucky", "merry", "nimble", "proud", "quick", "sunny",
        "tidy", "vivid", "witty", "zesty",
    )
    private val qualifiers = listOf(
        "blue", "crimson", "golden", "green", "indigo", "ivory", "jade", "lavender",
        "orange", "pearl", "ruby", "silver", "teal", "violet",
    )
    private val nouns = listOf(
        "anchor", "badger", "beacon", "comet", "dolphin", "falcon", "forest", "harbor",
        "lantern", "meadow", "otter", "panda", "river", "rocket", "sparrow", "summit",
        "tiger", "valley", "willow", "zephyr",
    )

    fun generate(): String {
        val parts = listOf(adjectives.random(), qualifiers.random(), nouns.random())
        return parts.first().lowercase() +
            parts.drop(1).joinToString("") { it.lowercase().replaceFirstChar(Char::titlecase) }
    }
}

private fun ruPlural(n: Long, one: String, few: String, many: String): String {
    val absolute = kotlin.math.abs(n) % 100
    val last = absolute % 10
    return when {
        absolute in 11L..14L -> many
        last == 1L -> one
        last in 2L..4L -> few
        else -> many
    }
}

fun pastEpochSecondsToRelativeRu(
    pastEpochSeconds: Long,
    now: Instant = Instant.now(),
): String {
    val past = Instant.ofEpochSecond(pastEpochSeconds)
    val seconds = Duration.between(past, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "только что"
        seconds < 3600 -> {
            val minutes = seconds / 60
            "$minutes ${ruPlural(minutes, "минуту", "минуты", "минут")} назад"
        }
        seconds < 86400 -> {
            val hours = seconds / 3600
            "$hours ${ruPlural(hours, "час", "часа", "часов")} назад"
        }
        seconds < 604800 -> {
            val days = seconds / 86400
            "$days ${ruPlural(days, "день", "дня", "дней")} назад"
        }
        else -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru-RU"))
            .withZone(ZoneId.systemDefault())
            .format(past)
    }
}
