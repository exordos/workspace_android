package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.R

@Composable
fun ChatTopic(
    viewModel: ChatViewModel,
    item: TopicsResponseData,
    stream: Stream,
    navController: NavHostController
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val lastMessage = item.lastMessage
    val scope = rememberCoroutineScope()
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            viewModel.updateSelectedChat(null)
                        }
                        viewModel.currentTopicName = item.name
                        navController.navigate(
                            ChatFlow.ChatDialog(
                                stream.name,
                                stream.uuid,
                                item.name,
                                item.uuid,
                                stream.isPrivate,
                                null
                            )
                        )
                    },
                    onLongClick = {
                        menuExpanded = true
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(3.dp)
                    .height(47.dp)
                    .background(Color(0xFF000000 or item.color.toLong()))
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                    style = TextStyle(textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None)
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
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.unreadCount > 0) {
                    Text(
                        text = "${item.unreadCount}",
                        color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                        modifier = Modifier
                            .background(
                                color = if (item.notificationMode == "mute") LocalWorkspaceColorsPalette.current.noticeDisable else LocalWorkspaceColorsPalette.current.noticeCounterBadge,
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(15.dp))
                }
                IconButton(onClick = {
                    scope.launch {
                        viewModel.setNextNotificationMode(item)
                    }
                }) {
                    val imageName = when (item.notificationMode) {
                        "mute" -> R.drawable.ic_notifications_off_small
                        "follow" -> R.drawable.ic_volume_small
                        else -> R.drawable.ic_notifications_small
                    }
                    Image(
                        painter = painterResource(id = imageName),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(
                    LocalWorkspaceColorsPalette.current.background,
                    RoundedCornerShape(8.dp)
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (item.notificationMode != "mute") {
                        scope.launch {
                            viewModel.setTopicNotificationMode(item.uuid, "mute")
                        }
                    }
                },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_notifications_off),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                        .background(
                            if (item.notificationMode == "mute") LocalWorkspaceColorsPalette.current.cardBackgroundBase else LocalWorkspaceColorsPalette.current.background,
                            RoundedCornerShape(8.dp)
                        )
                )
            }
            IconButton(
                onClick = {
                    if (item.notificationMode != "default") {
                        scope.launch {
                            viewModel.setTopicNotificationMode(item.uuid, "default")
                        }
                    }
                },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_notifications),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                        .background(
                            if (item.notificationMode == "default") LocalWorkspaceColorsPalette.current.cardBackgroundBase else LocalWorkspaceColorsPalette.current.background,
                            RoundedCornerShape(8.dp)
                        )
                )
            }
            IconButton(
                onClick = {
                    if (item.notificationMode != "follow") {
                        scope.launch {
                            viewModel.setTopicNotificationMode(item.uuid, "follow")
                        }
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_volume),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                        .background(
                            if (item.notificationMode == "follow") LocalWorkspaceColorsPalette.current.cardBackgroundBase else LocalWorkspaceColorsPalette.current.background,
                            RoundedCornerShape(8.dp)
                        )
                )
            }
        }
        val actionText = if (item.isDone) "Убрать отметку выполненной темы" else "Отметить тему как выполненную"
        val imageName = if (item.isDone) R.drawable.ic_theme_uncomplete else R.drawable.ic_theme_complete
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = imageName),
                        contentDescription = ""
                    )
                    Text(actionText)
                }
                   },
            onClick = {
                scope.launch {
                    viewModel.toggleTopicDone(item.uuid)
                }
                menuExpanded = false
            }
        )
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = LocalWorkspaceColorsPalette.current.divider,
    )
}