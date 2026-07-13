package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ChatChannel(
    item: Stream,
    viewModel: ChatViewModel,
    showDetail: Boolean,
    currentlySelectedFolder: FolderResponseData?,
    onChatNumberToAddChange: (Stream?) -> Unit,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val palette = LocalWorkspaceColorsPalette.current
    val targetBackground = if (showDetail) palette.chatHeaderBackground.copy(alpha = 0f) else palette.chatHeaderBackground
    val animatedBackground by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 250),
        label = "chatHeaderBackground"
    )
    val scope = rememberCoroutineScope()
    val folderCreationFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val HHMMFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(animatedBackground)
            .padding(start = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    menuExpanded = true
                }
            )
    ) {
        val avatar = item.avatar
        if (avatar != null) {
            Avatar(
                avatar,
                viewModel.client.userViewModel.baseUrl.value ?: "",
                null,
                "",
                40,
                false
            )
        } else {
            val color = try {
                Color(0xFF000000 or item.color.toLong())
            } catch (e: IllegalArgumentException) {
                Color.Gray
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .padding(10.dp)
        ) {
            val lastMessage = item.lastMessage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lastMessage != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    val messageDate = LocalDateTime.parse(lastMessage.createdAt, folderCreationFormatter)
                    Text(
                        text = messageDate.format(HHMMFormatter),
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 12.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lastMessage != null) {
                    Text(
                        text = lastMessage.payload.content,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (item.unreadCount > 0) {
                    Text(
                        text = "${item.unreadCount}",
                        color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                color = LocalWorkspaceColorsPalette.current.noticeCounterBadge,
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp)
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
    }
}