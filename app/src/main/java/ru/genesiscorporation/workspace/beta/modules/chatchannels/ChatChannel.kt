package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.ChatListDensity
import ru.genesiscorporation.workspace.beta.data.remote.dto.DisplayedUnreadCount
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderItem
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.displayedUnreadCount
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.NotificationModeSelector
import ru.genesiscorporation.workspace.beta.ui.STREAM_NOTIFICATION_MODE_OPTIONS
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.WorkspaceContextMenu
import ru.genesiscorporation.workspace.beta.ui.WorkspaceMenuActionRow
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatChannel(
    item: Stream,
    hasUnreadMention: Boolean,
    fullyMuted: Boolean,
    viewModel: ChatViewModel,
    baseUrl: String,
    showDetail: Boolean,
    topicRailOpen: Boolean,
    currentlySelectedFolder: FolderResponseData?,
    folders: List<FolderResponseData>,
    folderMutationInProgress: Boolean,
    latestTopicName: String?,
    density: ChatListDensity,
    onOpenMembers: (Stream) -> Unit,
    onNewTopic: (Stream) -> Unit,
    onClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val colors = LocalWorkspaceColorsPalette.current
    val compact = density == ChatListDensity.COMPACT
    val lastMessage = item.lastMessage
    val isDirect = item.isDirectProviderChat()
    val folderItem = currentlySelectedFolder?.items
        ?.firstOrNull { it.streamUuid == item.uuid }
    val availableFolders = remember(folders, item.uuid) {
        availableFoldersForStream(folders, item.uuid)
    }
    val pinned = folderItem?.pinnedAt != null
    val displayedUnread = displayedUnreadForStream(item, folderItem)
    val hasSplitCounters = if (folderItem != null) {
        folderItem.activeUnreadCount != null || folderItem.passiveUnreadCount != null
    } else {
        item.activeUnreadCount != null || item.passiveUnreadCount != null
    }
    val badgeState = streamUnreadBadgeState(
        notificationMode = item.notificationMode,
        hasUnreadMention = hasUnreadMention,
        displayedUnreadIsPassive = displayedUnread?.passive == true,
        hasSplitCounters = hasSplitCounters,
    )
    val avatarUrn = if (isDirect) {
        lastMessage?.user?.avatar ?: item.avatar
    } else {
        item.avatar
    }

    Box {
        if (topicRailOpen) {
            StreamRailCard(
                item = item,
                hasUnreadMention = hasUnreadMention,
                fullyMuted = fullyMuted,
                displayedUnread = displayedUnread,
                hasSplitCounters = hasSplitCounters,
                baseUrl = baseUrl,
                avatarUrn = avatarUrn,
                selected = showDetail,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 52.dp else 64.dp)
                    .alpha(if (fullyMuted) 0.7f else 1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (showDetail) colors.cardBackgroundActive else colors.background,
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(
                        horizontal = 8.dp,
                        vertical = if (compact) 5.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarUrn = avatarUrn,
                    baseUrl = baseUrl,
                    color = item.color,
                    name = item.name,
                    size = if (compact) 34 else 40,
                    hasPadding = false,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .padding(
                            start = if (compact) 10.dp else 12.dp,
                            end = if (compact) 10.dp else 12.dp,
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isDirect && item.isPrivate) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lock),
                                contentDescription = "Закрытый стрим",
                                tint = colors.iconBase,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                            )
                        }
                        Text(
                            text = streamTitle(item.name, latestTopicName, isDirect),
                            color = colors.textHeaders,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.notificationMode.equals("muted", ignoreCase = true)) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_figma_channel_mute),
                                contentDescription = "Уведомления канала отключены",
                                tint = colors.iconBase,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isDirect && lastMessage?.user != null) {
                            Text(
                                text = lastMessage.user?.displayableName().orEmpty(),
                                color = colors.primary,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 96.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = lastMessage?.payload?.content
                                ?.let(::messagePreview)
                                ?.takeIf(String::isNotBlank)
                                ?: "Сообщений пока нет",
                            color = colors.textAdditional50,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .heightIn(min = if (compact) 42.dp else 48.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pinned) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pin),
                                contentDescription = "Закреплено",
                                tint = colors.iconBase,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (pinned && displayedUnread != null) {
                            Spacer(Modifier.width(8.dp))
                        }
                        UnreadBadge(
                            count = displayedUnread?.count ?: 0,
                            muted = badgeState.muted,
                            mentioned = badgeState.mentioned,
                        )
                    }
                    Text(
                        text = lastMessage?.createdAt?.let(::formatMessageTime).orEmpty(),
                        color = colors.messageTimeColor,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        WorkspaceContextMenu(
            expanded = menuExpanded,
            onDismissRequest = {
                menuExpanded = false
                folderMenuExpanded = false
            },
            width = 264.dp,
        ) {
            if (folderMenuExpanded) {
                WorkspaceMenuActionRow(
                    text = "Назад",
                    iconRes = R.drawable.arrow_back,
                    iconSize = androidx.compose.ui.unit.DpSize(10.dp, 20.dp),
                    onClick = { folderMenuExpanded = false },
                )
                availableFolders.forEach { folder ->
                    WorkspaceMenuActionRow(
                        text = folder.localizedTitle(),
                        iconRes = null,
                        enabled = !folderMutationInProgress,
                        onClick = {
                            viewModel.addChatFolder(
                                streamUuid = item.uuid,
                                chatType = item.folderItemChatType(),
                                folderUuid = folder.uuid,
                            )
                            folderMenuExpanded = false
                            menuExpanded = false
                        },
                    )
                }
            } else {
                NotificationModeSelector(
                    options = STREAM_NOTIFICATION_MODE_OPTIONS,
                    selectedMode = item.notificationMode,
                    onModeSelected = { mode ->
                        menuExpanded = false
                        viewModel.setStreamNotificationMode(item, mode)
                    },
                    modifier = Modifier.padding(4.dp),
                )
                if (item.unreadCount > 0) {
                    WorkspaceMenuActionRow(
                        text = "Отметить всё как прочитанное",
                        iconRes = R.drawable.ic_menu_check,
                        onClick = {
                            menuExpanded = false
                            viewModel.markStreamRead(item)
                        },
                    )
                }
                if (currentlySelectedFolder != null && folderItem != null) {
                    WorkspaceMenuActionRow(
                        text = if (folderItem.pinnedAt == null) {
                            "Закрепить"
                        } else {
                            "Открепить"
                        },
                        iconRes = R.drawable.ic_menu_pin,
                        enabled = !folderMutationInProgress,
                        onClick = {
                            menuExpanded = false
                            viewModel.setFolderItemPinned(
                                folder = currentlySelectedFolder,
                                streamUuid = item.uuid,
                                pinned = folderItem.pinnedAt == null,
                            )
                        },
                    )
                }
                if (!isDirect) {
                    WorkspaceMenuActionRow(
                        text = "Участники",
                        iconRes = R.drawable.ic_menu_group,
                        onClick = {
                            menuExpanded = false
                            onOpenMembers(item)
                        },
                    )
                }
                WorkspaceMenuActionRow(
                    text = "Добавить в папку",
                    iconRes = R.drawable.ic_menu_folder,
                    trailingIconRes = R.drawable.ic_menu_chevron_right,
                    onClick = { folderMenuExpanded = true },
                )
                if (!isDirect) {
                    WorkspaceMenuActionRow(
                        text = "Новая тема",
                        iconRes = R.drawable.ic_menu_plus,
                        onClick = {
                            menuExpanded = false
                            onNewTopic(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun StreamRailCard(
    item: Stream,
    hasUnreadMention: Boolean,
    fullyMuted: Boolean,
    displayedUnread: DisplayedUnreadCount?,
    hasSplitCounters: Boolean,
    baseUrl: String,
    avatarUrn: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    avatarContent: (@Composable () -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val badgeState = streamUnreadBadgeState(
        notificationMode = item.notificationMode,
        hasUnreadMention = hasUnreadMention,
        displayedUnreadIsPassive = displayedUnread?.passive == true,
        hasSplitCounters = hasSplitCounters,
    )
    Box(
        modifier = Modifier
            .width(STREAM_RAIL_CARD_WIDTH)
            .height(STREAM_RAIL_CARD_HEIGHT)
            .alpha(if (fullyMuted) 0.7f else 1f)
            .testTag("stream-rail-${item.uuid}")
            .clip(RoundedCornerShape(STREAM_RAIL_CARD_RADIUS))
            .background(
                if (selected) colors.cardBackgroundActive else Color.Transparent,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = buildString {
                    append("Открыть канал ${item.name}")
                    if (displayedUnread != null) {
                        append(", непрочитанных: ${displayedUnread.count}")
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(STREAM_RAIL_AVATAR_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarContent != null) {
                avatarContent()
            } else {
                Avatar(
                    avatarUrn = avatarUrn,
                    baseUrl = baseUrl,
                    color = item.color,
                    name = item.name,
                    size = STREAM_RAIL_AVATAR_SIZE.value.toInt(),
                    hasPadding = false,
                )
            }
            UnreadBadge(
                count = displayedUnread?.count ?: 0,
                muted = badgeState.muted,
                mentioned = badgeState.mentioned,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .testTag("stream-rail-unread-${item.uuid}"),
            )
            if (item.notificationMode.equals("muted", ignoreCase = true)) {
                Icon(
                    painter = painterResource(R.drawable.ic_figma_channel_mute),
                    contentDescription = "Уведомления канала отключены",
                    tint = colors.iconBase,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(16.dp),
                )
            }
        }
    }
}

internal fun displayedUnreadForStream(
    stream: Stream,
    folderItem: FolderItem?,
): DisplayedUnreadCount? = if (folderItem != null) {
    folderItem.displayedUnreadCount()
} else {
    stream.displayedUnreadCount()
}

internal data class StreamUnreadBadgeState(
    val muted: Boolean,
    val mentioned: Boolean,
)

internal fun streamUnreadBadgeState(
    notificationMode: String,
    hasUnreadMention: Boolean,
    displayedUnreadIsPassive: Boolean = false,
    hasSplitCounters: Boolean = false,
): StreamUnreadBadgeState {
    if (displayedUnreadIsPassive) {
        return StreamUnreadBadgeState(muted = true, mentioned = false)
    }
    val mentionsOnly = notificationMode.equals("mentions_only", ignoreCase = true)
    // The split contract already puts unread mentions into the active counter.
    // Keep the legacy @ fallback only for snapshots produced before that
    // contract; otherwise it would hide the active numeric badge.
    val showMention = !hasSplitCounters && mentionsOnly && hasUnreadMention
    return StreamUnreadBadgeState(
        muted = !hasSplitCounters && (
            notificationMode.equals("muted", ignoreCase = true) ||
                (mentionsOnly && !showMention)
            ),
        mentioned = showMention,
    )
}

internal val STREAM_RAIL_CARD_WIDTH = 56.dp
internal val STREAM_RAIL_CARD_HEIGHT = 64.dp
internal val STREAM_RAIL_CARD_RADIUS = 8.dp
internal val STREAM_RAIL_AVATAR_SIZE = 40.dp

internal fun availableFoldersForStream(
    folders: List<FolderResponseData>,
    streamUuid: String,
): List<FolderResponseData> = folders.filter { folder ->
    folder.isUserManaged() && folder.items.none { it.streamUuid == streamUuid }
}

internal fun messagePreview(content: String): String =
    content
        .replace(Regex("""!\[[^\]]*]\([^)]+\)"""), "Изображение")
        .replace(Regex("""\[[^\]]+]\((urn:image:|https?://)[^)]+\)"""), "Вложение")
        .replace(Regex("""[`*_>#]"""), "")
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()

internal fun streamTitle(
    streamName: String,
    latestTopicName: String?,
    isDirect: Boolean,
): String = if (isDirect || latestTopicName.isNullOrBlank()) {
    streamName
} else {
    "$streamName  # ${latestTopicName.trim()}"
}

internal fun formatMessageTime(value: String): String {
    val instant = parseTime(value)
    if (instant == java.time.Instant.EPOCH) return ""
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
