package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatTopic(
    viewModel: ChatViewModel,
    item: TopicsResponseData,
    stream: Stream,
    hasUnreadMention: Boolean,
    navController: NavHostController
) {
    val zone = ZoneId.systemDefault()
    val HHMMFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
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
                .clickable(
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
                    modifier = Modifier.padding(end = 8.dp)
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
                    .padding(8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                UnreadBadge(
                    count = item.unreadCount,
                    muted = item.notificationMode.equals("mute", ignoreCase = true),
                    mentioned = hasUnreadMention,
                )
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
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = LocalWorkspaceColorsPalette.current.divider,
    )
}
