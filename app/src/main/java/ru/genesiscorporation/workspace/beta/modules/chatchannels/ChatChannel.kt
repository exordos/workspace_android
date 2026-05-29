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
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChatChannel(
    item: ChatHeader,
    viewModel: ChatViewModel,
    showDetail: Boolean,
    currentlySelectedFolder: FolderResponseData?,
    onChatNumberToAddChange: (ChatHeader?) -> Unit,
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
        if (item.gravatar != null) {
            Avatar(
                item.gravatar,
                viewModel.client.userViewModel.baseUrl.value ?: "",
                40,
                false
            )
        } else if (item.color != null) {
            val color = try {
                Color(item.color.toColorInt())
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isDirectMessages) {
                    Spacer(modifier = Modifier.weight(1f))
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
                } else {
                    val lastMessage = item.lastMessage
                    if (lastMessage != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = lastMessage.timestamp.formatHHmm(),
                            color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (item.isDirectMessages) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastMessage = item.lastMessage
                    if (lastMessage != null) {
                        Text(
                            text = lastMessage.content,
                            color = LocalWorkspaceColorsPalette.current.textAdditional50,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
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
                        if (currentlySelectedFolder != null) {
                            scope.launch {
                                viewModel.deleteChatFromFolder(
                                    item.chatId,
                                    currentlySelectedFolder
                                )
                            }
                        }
                        menuExpanded = false
                    }
                )
            }
        }
    }
}