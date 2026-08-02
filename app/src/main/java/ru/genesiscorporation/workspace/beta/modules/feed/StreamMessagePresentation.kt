package ru.genesiscorporation.workspace.beta.modules.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatdialog.messageAccent
import ru.genesiscorporation.workspace.beta.modules.chatdialog.messageBubbleShape
import ru.genesiscorporation.workspace.beta.modules.chatchannels.formatMessageTime
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
internal fun StreamTimelineTopBar(
    title: String,
    busyDescription: String,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInfo: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(
                colors.chatHeaderBackground,
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Назад к темам",
                tint = colors.iconBase,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontFamily = NavigationFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(width = 3.dp, height = 14.dp)
                        .background(
                            colors.indicatorYellow,
                            RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    text = "# Все сообщения",
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(20.dp)
                    .semantics { contentDescription = busyDescription },
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Обновить сообщения канала",
                    tint = colors.iconBase,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        IconButton(
            onClick = onInfo,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_figma_profile_more),
                contentDescription = "Информация о канале",
                tint = colors.iconBase,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
internal fun StreamMessageRow(
    message: MessageResponse,
    topic: TopicsResponseData?,
    author: UserResponseData?,
    avatarBaseUrl: String,
    showAvatar: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val resolvedAuthor = author ?: message.user
    val authorName = resolvedAuthor
        ?.displayableName()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: if (message.isOwn) "Я" else "Участник"
    val topicName = topic
        ?.name
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "Тема"
    val body = streamMessageText(message.payload.content)
    val interactionModifier = Modifier
        .combinedClickable(
            enabled = !busy,
            role = Role.Button,
            onClick = onOpen,
            onLongClick = onForward,
        )
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription =
                "Сообщение от $authorName в теме $topicName. Нажмите, чтобы открыть в чате"
            onLongClick(label = "Переслать сообщение") {
                if (!busy) onForward()
                !busy
            }
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOwn) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!message.isOwn) {
            if (showAvatar) {
                Avatar(
                    avatarUrn = resolvedAuthor?.avatar,
                    baseUrl = avatarBaseUrl,
                    color = null,
                    name = authorName,
                    size = 34,
                    hasPadding = true,
                )
            } else {
                Spacer(Modifier.width(46.dp))
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .background(
                    if (message.isOwn) {
                        colors.messageOwnBackground
                    } else {
                        colors.messageBackground
                    },
                    messageBubbleShape(message.isOwn),
                )
                .then(interactionModifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = authorName,
                    color = messageAccent(message.authorUuid, message.isOwn, colors),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 128.dp),
                )
                Box(
                    Modifier
                        .padding(horizontal = 7.dp)
                        .size(width = 3.dp, height = 16.dp)
                        .background(
                            messageAccent(message.topicUuid, false, colors),
                            RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    text = "# $topicName",
                    color = colors.messageSecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = body,
                color = colors.textHeaders,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(12.dp),
                        color = colors.primary,
                        strokeWidth = 1.5.dp,
                    )
                }
                Text(
                    text = formatMessageTime(message.updatedAt),
                    color = colors.messageTimeColor,
                    fontSize = 11.sp,
                )
                if (message.isOwn) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_done_all),
                        contentDescription = "Доставлено",
                        tint = colors.messageTimeColor,
                        modifier = Modifier.size(width = 20.dp, height = 16.dp),
                    )
                }
            }
        }
    }
}

internal fun streamMessageGroupEndsAt(
    messages: List<MessageResponse>,
    index: Int,
): Boolean {
    val current = messages.getOrNull(index) ?: return true
    val next = messages.getOrNull(index + 1) ?: return true
    return next.authorUuid != current.authorUuid ||
        next.topicUuid != current.topicUuid ||
        next.isOwn != current.isOwn
}

internal fun streamMessageBottomSpacing(
    messages: List<MessageResponse>,
    index: Int,
): Int = if (streamMessageGroupEndsAt(messages, index)) {
    STREAM_MESSAGE_GROUP_GAP_DP
} else {
    STREAM_MESSAGE_INNER_GAP_DP
}

internal fun streamMessageText(markdown: String): String =
    feedMessageSummary(markdown, maxLength = STREAM_MESSAGE_TEXT_LIMIT)

private const val STREAM_MESSAGE_TEXT_LIMIT = 4_000
private const val STREAM_MESSAGE_GROUP_GAP_DP = 12
private const val STREAM_MESSAGE_INNER_GAP_DP = 4
