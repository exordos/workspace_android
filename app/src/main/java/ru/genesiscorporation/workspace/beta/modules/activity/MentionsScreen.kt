package ru.genesiscorporation.workspace.beta.modules.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun MentionsScreen(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
) {
    val messages by chatViewModel.mentionedMessages.collectAsStateWithLifecycle()
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val users by chatViewModel.users.collectAsStateWithLifecycle()
    val state by chatViewModel.mentionsQueryState.collectAsStateWithLifecycle()
    val streamUuids = remember(messages) {
        messages.map(MessageResponse::streamUuid).distinct()
    }

    LaunchedEffect(streamUuids, streams) {
        streamUuids.forEach { streamUuid ->
            if (streamUuid !in topicsByStream) {
                streams.firstOrNull { it.uuid == streamUuid }
                    ?.let { chatViewModel.loadTopics(it) }
            }
        }
    }

    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            IconButton(
                onClick = navController::popBackStack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Назад",
                    tint = colors.iconBase,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "Упоминания",
                color = colors.textHeaders,
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        when {
            state is QueryState.Loading && messages.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
            state is QueryState.Error && messages.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (state as QueryState.Error).message,
                    color = colors.textAdditional50,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                )
            }
            messages.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Упоминаний пока нет",
                    color = colors.textAdditional50,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 4.dp,
                    end = 12.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = MessageResponse::uuid) { message ->
                    val stream = streams.firstOrNull { it.uuid == message.streamUuid }
                    val topic = topicsByStream[message.streamUuid]
                        .orEmpty()
                        .firstOrNull { it.uuid == message.topicUuid }
                    val author = users.firstOrNull { it.uuid == message.authorUuid }
                    MentionMessageRow(
                        message = message,
                        authorName = author?.displayableName() ?: "Участник Workspace",
                        streamName = stream?.name ?: "Канал",
                        topicName = topic?.name ?: "Тема",
                        onClick = {
                            if (stream != null) {
                                navController.navigate(
                                    ChatFlow.ChatDialog(
                                        title = stream.name,
                                        chatId = stream.uuid,
                                        topicName = topic?.name,
                                        topicUuid = message.topicUuid,
                                        isDirectMessages = stream.isPrivate,
                                        userId = null,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionMessageRow(
    message: MessageResponse,
    authorName: String,
    streamName: String,
    topicName: String,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val timestamp = remember(message.createdAt) {
        runCatching {
            OffsetDateTime.parse(message.createdAt)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM, HH:mm"))
        }.getOrDefault("")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.cardBackgroundBase)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.indicatorYellow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "@",
                color = Color.White,
                fontFamily = InterFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = authorName,
                    color = colors.textHeaders,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timestamp,
                    color = colors.messageTimeColor,
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "$streamName · $topicName",
                color = colors.primary,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MarkdownText(
                markdown = message.payload.content,
                maxLines = 4,
                style = TextStyle(
                    color = colors.textAdditional50,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}
