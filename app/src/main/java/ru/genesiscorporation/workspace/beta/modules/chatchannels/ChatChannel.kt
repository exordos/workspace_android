package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import dev.jeziellago.compose.markdowntext.MarkdownText
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatChannel(
    item: Stream,
    viewModel: ChatViewModel,
    showDetail: Boolean,
    currentlySelectedFolder: FolderResponseData?,
    currentlySelectedStream: Stream?,
    onChatNumberToAddChange: (Stream?) -> Unit,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val palette = LocalWorkspaceColorsPalette.current
    val targetBackground = if (showDetail) palette.chatHeaderBackground.copy(alpha = 0f) else palette.chatHeaderBackground
//    val animatedBackground by animateColorAsState(
//        targetValue = targetBackground,
//        animationSpec = tween(durationMillis = 250),
//        label = "chatHeaderBackground"
//    )
    val zone = ZoneId.systemDefault()
    val HHMMFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
//            .background(animatedBackground)
//            .padding(start = 8.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        menuExpanded = true
                    }
                )
        ) {
            val avatarUrn = item.directUser?.avatar
            val lastMessage = item.lastMessage
            Avatar(
                avatarUrn,
                viewModel.client.userViewModel.baseUrl.value ?: "",
                viewModel.client.authHeaders(),
                item.color,
                item.name,
                Modifier
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
                    .background( if (currentlySelectedStream?.uuid == item.uuid) LocalWorkspaceColorsPalette.current.cardBackgroundBase else Color.Transparent)
                    .padding(start = 8.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                    .size(40.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = item.name,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lastMessage != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lastMessageUser = lastMessage.user
                        if (lastMessageUser != null) {
                            Text(
                                text = lastMessageUser.displayableName(),
                                color = LocalWorkspaceColorsPalette.current.primary,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MarkdownText(
                            markdown = lastMessage.payload.content,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            style = TextStyle(
                                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                            ),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (item.unreadCount > 0) {
                    Text(
                        text = "${item.unreadCount}",
                        color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                color = LocalWorkspaceColorsPalette.current.noticeCounterBadge,
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp)
                    )
                }
                if (lastMessage != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    val instant = Instant.parse(lastMessage.createdAt)
                    Text(
                        text = instant.atZone(zone).format(HHMMFormatter),
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (currentlySelectedFolder?.systemType == "all") {
                DropdownMenuItem(
                    text = { Text("Добавить в папку...") },
                    onClick = {
                        onChatNumberToAddChange(item)
                        menuExpanded = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Удалить из папки") },
                    onClick = {
//                        if (currentlySelectedFolder != null) {
//                            scope.launch {
//                                viewModel.deleteChatFromFolder(
//                                    item.chatId,
//                                    currentlySelectedFolder
//                                )
//                            }
//                        }
                        menuExpanded = false
                    }
                )
            }
        }
        if (currentlySelectedStream == null) {
            HorizontalDivider(
                thickness = 1.dp,
                color = LocalWorkspaceColorsPalette.current.divider,
            )
        }
    }
}