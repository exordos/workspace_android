package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
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
    currentlySelectedFolder: FolderResponseData?,
    onChatNumberToAddChange: (Stream?) -> Unit,
    onClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = LocalWorkspaceColorsPalette.current
    val lastMessage = item.lastMessage
    val avatarUrn = if (item.isPrivate) {
        lastMessage?.user?.avatar ?: item.avatar
    } else {
        item.avatar
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (showDetail) colors.cardBackgroundActive else colors.cardBackgroundBase,
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                avatarUrn = avatarUrn,
                baseUrl = baseUrl,
                color = item.color,
                name = item.name,
                size = 40,
                hasPadding = false,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPrivate) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(6.dp)
                                .background(colors.iconBase, CircleShape),
                        )
                    } else {
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
                        text = item.name,
                        color = colors.textHeaders,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (lastMessage != null) {
                        Text(
                            text = formatMessageTime(lastMessage.createdAt),
                        color = colors.messageTimeColor,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (!item.isPrivate && lastMessage?.user != null) {
                    Text(
                        text = lastMessage.user?.displayableName().orEmpty(),
                        color = colors.primary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lastMessage?.payload?.content?.let(::messagePreview)
                            ?: "Сообщений пока нет",
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.unreadCount > 0) {
                        UnreadBadge(
                            count = item.unreadCount,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (currentlySelectedFolder?.systemType == "all") {
                DropdownMenuItem(
                    text = { Text("Добавить в папку…") },
                    onClick = {
                        onChatNumberToAddChange(item)
                        menuExpanded = false
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Удалить из папки") },
                    onClick = { menuExpanded = false },
                )
            }
        }
    }
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

internal fun formatMessageTime(value: String): String {
    val instant = parseTime(value)
    if (instant == java.time.Instant.EPOCH) return ""
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
