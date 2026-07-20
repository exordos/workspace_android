package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ChatTopic(
    viewModel: ChatViewModel,
    item: TopicsResponseData,
    stream: Stream,
    navController: NavHostController
) {
    val folderCreationFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val HHMMFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(LocalWorkspaceColorsPalette.current.chatHeaderBackground)
            .padding(start = 16.dp)
            .clickable(
                onClick = {
                    viewModel.currentTopicName = item.name
                    navController.navigate(ChatFlow.ChatDialog(stream.name, stream.uuid, item.name, item.uuid, stream.isPrivate, null))
                }
            )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
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
                    lineHeight = 20.sp,
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
                verticalAlignment = Alignment.Bottom
            ) {
                val lastMessage = item.lastMessage
                if (lastMessage != null) {
                    Column (
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        val lastMessageUser = lastMessage.user
                        if (lastMessageUser != null) {
                            Text(
                                text = lastMessageUser.displayableName(),
                                color = LocalWorkspaceColorsPalette.current.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MarkdownText(
                            markdown = lastMessage.payload.content,
                            modifier = Modifier.weight(1f)
                                .fillMaxHeight(),
                            maxLines = 1,
                            style = TextStyle(
                                color = LocalWorkspaceColorsPalette.current.textAdditional50,
                                fontSize = 12.sp,
                                lineHeight = 20.sp
                            ),
                        )
                    }
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
    }
}