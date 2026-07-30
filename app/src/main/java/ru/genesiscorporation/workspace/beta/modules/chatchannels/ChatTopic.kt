package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChatTopic(
    viewModel: ChatViewModel,
    item: TopicsResponseData,
    displayName: String,
    stream: Stream,
    navController: NavHostController,
    onLongClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val lastMessage = item.lastMessage

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 65.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.messageBackground)
            .combinedClickable(
                onClick = {
                    viewModel.currentTopicName = item.name
                    navController.navigate(
                        ChatFlow.ChatDialog(
                            stream.name,
                            stream.uuid,
                            item.name,
                            item.uuid,
                            stream.isDirectProviderChat(),
                            null,
                        ),
                    )
                },
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .width(3.dp)
                .height(42.dp)
                .background(topicIndicatorColor(item.color), RoundedCornerShape(10.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "# $displayName",
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
            Text(
                text = lastMessage?.user?.displayableName() ?: "Нет сообщений",
                color = colors.primary,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lastMessage?.payload?.content?.let(::messagePreview).orEmpty(),
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
                }
            }
        }
    }
}

internal fun topicIndicatorColor(value: Int?): Color =
    value
        ?.let { runCatching { Color(0xFF000000 or it.toLong()) }.getOrNull() }
        ?: Color(0xFFFFCC00)
