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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatChannel(
    item: Stream,
    viewModel: ChatViewModel,
    baseUrl: String,
    showDetail: Boolean,
    topicRailOpen: Boolean,
    currentlySelectedFolder: FolderResponseData?,
    latestTopicName: String?,
    density: ChatListDensity,
    onChatNumberToAddChange: (Stream?) -> Unit,
    onClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = LocalWorkspaceColorsPalette.current
    val compact = density == ChatListDensity.COMPACT
    val lastMessage = item.lastMessage
    val isDirect = item.isDirectProviderChat()
    val folderMenuAction = folderChatMenuAction(currentlySelectedFolder)
    val folderItem = currentlySelectedFolder?.items
        ?.firstOrNull { it.streamUuid == item.uuid }
    val pinned = folderItem?.pinnedAt != null
    val hasMenuActions =
        folderMenuAction != null || folderItem != null || item.unreadCount > 0
    val avatarUrn = if (isDirect) {
        lastMessage?.user?.avatar ?: item.avatar
    } else {
        item.avatar
    }

    Box {
        if (topicRailOpen) {
            StreamRailCard(
                item = item,
                baseUrl = baseUrl,
                avatarUrn = avatarUrn,
                selected = showDetail,
                onClick = onClick,
                onLongClick = if (hasMenuActions) {
                    { menuExpanded = true }
                } else null,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 52.dp else 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (showDetail) colors.cardBackgroundActive else colors.background,
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (hasMenuActions) {
                            { menuExpanded = true }
                        } else null,
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
                        if (pinned && item.unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                        }
                        UnreadBadge(count = item.unreadCount)
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
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (item.unreadCount > 0) {
                DropdownMenuItem(
                    text = { Text("Отметить чат прочитанным") },
                    onClick = {
                        menuExpanded = false
                        viewModel.markStreamRead(item)
                    },
                )
            }
            if (folderMenuAction == FolderChatMenuAction.ADD) {
                DropdownMenuItem(
                    text = { Text("Добавить в папку…") },
                    onClick = {
                        onChatNumberToAddChange(item)
                        menuExpanded = false
                    },
                )
            } else if (
                folderMenuAction == FolderChatMenuAction.REMOVE &&
                currentlySelectedFolder != null
            ) {
                DropdownMenuItem(
                    text = { Text("Удалить из папки") },
                    onClick = {
                        menuExpanded = false
                        viewModel.deleteChatFromFolder(
                            chatId = item.uuid,
                            folder = currentlySelectedFolder,
                        )
                    },
                )
            }
            if (currentlySelectedFolder != null && folderItem != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (folderItem.pinnedAt == null) {
                                "Закрепить в папке"
                            } else {
                                "Открепить от начала"
                            },
                        )
                    },
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
        }
    }
}

@Composable
internal fun StreamRailCard(
    item: Stream,
    baseUrl: String,
    avatarUrn: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    avatarContent: (@Composable () -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .width(STREAM_RAIL_CARD_WIDTH)
            .height(STREAM_RAIL_CARD_HEIGHT)
            .testTag("stream-rail-${item.uuid}")
            .clip(RoundedCornerShape(STREAM_RAIL_CARD_RADIUS))
            .background(
                if (selected) colors.cardBackgroundActive else Color.Transparent,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = buildString {
                    append("Открыть канал ${item.name}")
                    if (item.unreadCount > 0) {
                        append(", непрочитанных: ${item.unreadCount}")
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
                count = item.unreadCount,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .testTag("stream-rail-unread-${item.uuid}"),
            )
        }
    }
}

internal val STREAM_RAIL_CARD_WIDTH = 56.dp
internal val STREAM_RAIL_CARD_HEIGHT = 64.dp
internal val STREAM_RAIL_CARD_RADIUS = 8.dp
internal val STREAM_RAIL_AVATAR_SIZE = 40.dp

internal enum class FolderChatMenuAction {
    ADD,
    REMOVE,
}

internal fun folderChatMenuAction(
    folder: FolderResponseData?,
): FolderChatMenuAction? = when {
    folder == null -> null
    folder.isAllChatsFolder() -> FolderChatMenuAction.ADD
    folder.isUserManaged() -> FolderChatMenuAction.REMOVE
    else -> null
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
