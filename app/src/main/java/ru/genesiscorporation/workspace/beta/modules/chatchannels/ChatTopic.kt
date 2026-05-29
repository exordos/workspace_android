package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.modules.topics.TopicHeader
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChatTopic(
    viewModel: ChatViewModel,
    item: TopicHeader,
    navController: NavHostController
) {
    val currentlySelectedSubscription by viewModel.currentlySelectedSubscription.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(LocalWorkspaceColorsPalette.current.chatHeaderBackground)
            .padding(start = 16.dp)
            .clickable(
                onClick = {
                    viewModel.currentTopicName = item.title
                    val subscription = currentlySelectedSubscription
                    if (subscription != null) {
                        navController.navigate(ChatFlow.ChatDialog(item.channelName, item.channelId, item.title, false, subscription.streamId.toInt()))
                    }
                }
            )
    ) {
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
}