package ru.genesiscorporation.workspace.beta.modules.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ResolvedDeepLinkDestination
import ru.genesiscorporation.workspace.beta.modules.chatchannels.formatMessageTime
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
fun InboxScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
) {
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val users by chatViewModel.users.collectAsStateWithLifecycle()
    val syncState by chatViewModel.inboxSyncState.collectAsStateWithLifecycle()
    val actionError by chatViewModel.actionError.collectAsStateWithLifecycle()
    val groups = remember(streams, topicsByStream, users) {
        buildInboxGroups(streams, topicsByStream, users)
    }
    val directGroups = remember(groups) {
        groups.filter { it.kind == InboxGroupKind.DIRECT }
    }
    val channelGroups = remember(groups) {
        groups.filter { it.kind == InboxGroupKind.CHANNEL }
    }
    val scope = rememberCoroutineScope()
    var openingStreamUuid by rememberSaveable { mutableStateOf<String?>(null) }

    fun navigateTo(destination: ResolvedDeepLinkDestination) {
        when (destination) {
            is ResolvedDeepLinkDestination.Dialog ->
                navController.navigate(destination.route)

            is ResolvedDeepLinkDestination.TopicList ->
                navController.navigate(destination.route)
        }
    }

    fun openRow(row: InboxRow) {
        when (val destination = row.destination) {
            is InboxDestination.Topic -> {
                val stream = streams
                    .filter { it.uuid == destination.streamUuid && !it.isArchived }
                    .singleOrNull()
                val topic = topicsByStream[destination.streamUuid]
                    .orEmpty()
                    .filter { it.uuid == destination.topicUuid }
                    .singleOrNull()
                if (stream == null || topic == null) {
                    chatViewModel.reportActionError(
                        "Разговор изменился. Обновите входящие и попробуйте снова",
                    )
                    return
                }
                navController.navigate(
                    ChatFlow.ChatDialog(
                        title = stream.name,
                        chatId = stream.uuid,
                        topicName = topic.name.takeUnless {
                            stream.isDirectProviderChat()
                        },
                        topicUuid = topic.uuid,
                        isDirectMessages = stream.isDirectProviderChat(),
                        userId = null,
                    ),
                )
            }

            is InboxDestination.Stream -> {
                if (openingStreamUuid != null) return
                openingStreamUuid = destination.streamUuid
                scope.launch {
                    try {
                        chatViewModel
                            .resolveInboxStreamNavigation(destination.streamUuid)
                            ?.let(::navigateTo)
                    } finally {
                        openingStreamUuid = null
                    }
                }
            }
        }
    }

    BackHandler { navController.popBackStack() }
    LaunchedEffect(Unit) {
        chatViewModel.clearActionError()
        chatViewModel.refreshInbox()
    }

    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        InboxTopBar(
            refreshing = syncState.refreshing,
            onBack = { navController.popBackStack() },
            onRefresh = chatViewModel::refreshInbox,
        )
        if (syncState.refreshing && groups.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.searchBackground,
            )
        }
        val visibleError = syncState.error ?: actionError
        if (
            visibleError != null &&
            hasDisplayableInboxSnapshot(groups, syncState)
        ) {
            InboxErrorBanner(
                message = visibleError,
                retry = syncState.error != null,
                onRetry = chatViewModel::refreshInbox,
                onDismiss = {
                    if (syncState.error == null) {
                        chatViewModel.clearActionError()
                    }
                },
            )
        }

        when {
            groups.isNotEmpty() -> {
                InboxGroups(
                    directGroups = directGroups,
                    channelGroups = channelGroups,
                    openingStreamUuid = openingStreamUuid,
                    onOpen = ::openRow,
                )
            }

            (
                syncState.refreshing &&
                    !hasDisplayableInboxSnapshot(groups, syncState)
            ) || (
                !syncState.hasLoaded &&
                    syncState.error == null
            ) -> {
                InboxStateCard(
                    message = "Загрузка входящих…",
                    loading = true,
                )
            }

            syncState.error != null &&
                !hasDisplayableInboxSnapshot(groups, syncState) -> {
                InboxStateCard(
                    message = syncState.error.orEmpty(),
                    action = "Повторить",
                    onAction = chatViewModel::refreshInbox,
                )
            }

            else -> {
                InboxStateCard(message = "Нет непрочитанных сообщений")
            }
        }
    }
}

@Composable
private fun InboxTopBar(
    refreshing: Boolean,
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
        InboxIconButton(
            drawable = R.drawable.arrow_back,
            description = "Назад к чатам",
            enabled = true,
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = "Входящие",
            color = colors.textHeaders,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontFamily = NavigationFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(20.dp)
                    .semantics {
                        contentDescription = "Обновление входящих"
                    },
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        } else {
            InboxIconButton(
                drawable = R.drawable.ic_refresh,
                description = "Обновить входящие",
                enabled = true,
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun InboxIconButton(
    drawable: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
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

@Composable
private fun InboxGroups(
    directGroups: List<InboxGroup>,
    channelGroups: List<InboxGroup>,
    openingStreamUuid: String?,
    onOpen: (InboxRow) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (directGroups.isNotEmpty()) {
            item(key = "direct-heading") {
                InboxSectionHeading("Личные сообщения")
            }
            items(
                items = directGroups,
                key = { "direct:${it.streamUuid}" },
            ) { group ->
                InboxStreamCard(
                    group = group,
                    busy = openingStreamUuid == group.streamUuid,
                    onOpen = onOpen,
                )
            }
        }
        if (channelGroups.isNotEmpty()) {
            item(key = "channel-heading") {
                InboxSectionHeading("Каналы")
            }
            items(
                items = channelGroups,
                key = { "channel:${it.streamUuid}" },
            ) { group ->
                InboxStreamCard(
                    group = group,
                    busy = openingStreamUuid == group.streamUuid,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun InboxSectionHeading(title: String) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            color = colors.textAdditional50,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colors.cardBackgroundActive,
        )
    }
}

@Composable
private fun InboxStreamCard(
    group: InboxGroup,
    busy: Boolean,
    onOpen: (InboxRow) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.cardBackgroundActive, shape)
            .background(colors.cardBackgroundBase, shape)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InboxKindIcon(group.kind, size = 28)
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.streamTitle,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                UnreadBadge(group.unreadCount)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.rows.forEach { row ->
                InboxMessageRow(
                    row = row,
                    kind = group.kind,
                    enabled = !busy,
                    onOpen = { onOpen(row) },
                )
            }
        }
    }
}

@Composable
private fun InboxMessageRow(
    row: InboxRow,
    kind: InboxGroupKind,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val time = remember(row.updatedAt) { formatMessageTime(row.updatedAt) }
    val semanticsLabel = buildString {
        append(row.title)
        append(", непрочитанных: ")
        append(row.unreadCount)
        if (time.isNotEmpty()) {
            append(", ")
            append(time)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics {
                contentDescription = semanticsLabel
                role = Role.Button
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InboxKindIcon(kind, size = 32)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (time.isNotEmpty()) {
                Text(
                    text = time,
                    color = colors.textAdditional50,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        UnreadBadge(row.unreadCount)
    }
}

@Composable
private fun InboxKindIcon(
    kind: InboxGroupKind,
    size: Int,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colors.searchBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (kind == InboxGroupKind.DIRECT) {
                    R.drawable.ic_profile
                } else {
                    R.drawable.chat_bubble
                },
            ),
            contentDescription = null,
            tint = colors.textAdditional50,
            modifier = Modifier.size((size - 10).coerceAtLeast(16).dp),
        )
    }
}

@Composable
private fun InboxErrorBanner(
    message: String,
    retry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = colors.indicatorRed,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (retry) "Повторить" else "Закрыть",
            color = colors.primary,
            fontSize = 12.sp,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(
                    role = Role.Button,
                    onClick = if (retry) onRetry else onDismiss,
                )
                .padding(horizontal = 10.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun InboxStateCard(
    message: String,
    loading: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.primary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = message,
            color = colors.textAdditional50,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = colors.primary,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
