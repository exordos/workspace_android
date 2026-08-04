package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.displayedUnreadCount
import ru.genesiscorporation.workspace.beta.data.remote.dto.isEffectivelyMuted
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.TopicActionsDialog
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun AllTopicsRow(
    stream: Stream,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Все темы канала ${stream.name}"
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.searchBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_feed),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Все темы",
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "Общий поток канала",
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ChatTopic(
    viewModel: ChatViewModel,
    item: TopicsResponseData,
    displayName: String,
    stream: Stream,
    navController: NavHostController,
    selected: Boolean,
    actionsExpanded: Boolean,
    actionsBusy: Boolean,
    onLongClick: () -> Unit,
    onDismissActions: () -> Unit,
    onRename: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleDone: () -> Unit,
    onSetNotificationMode: (String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val lastMessage = item.lastMessage
    val effectivelyMuted = item.isEffectivelyMuted(stream.notificationMode)
    val displayedUnread = item.displayedUnreadCount()

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .alpha(if (effectivelyMuted) 0.7f else 1f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) colors.cardBackgroundActive else Color.Transparent,
                )
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
                )
                .semantics {
                    stateDescription = if (item.isDone) {
                        "Тема завершена"
                    } else {
                        "Тема активна"
                    }
                }
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(47.dp)
                    .background(topicIndicatorColor(item.color), RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(9.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
            ) {
                Text(
                    text = "# $displayName",
                    color = if (item.isDone) colors.textAdditional50 else colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isDone) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lastMessage?.user?.displayableName() ?: "Нет сообщений",
                        color = colors.primary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 82.dp),
                    )
                    val preview = lastMessage?.payload?.content
                        ?.let(::messagePreview)
                        .orEmpty()
                    if (preview.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = preview,
                            color = colors.textAdditional50,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .width(24.dp)
                    .height(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    UnreadBadge(
                        count = displayedUnread?.count ?: 0,
                        muted = displayedUnread?.passive == true ||
                            (
                                item.activeUnreadCount == null &&
                                    item.passiveUnreadCount == null &&
                                    effectivelyMuted
                                ),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = topicNotificationDescription(item.notificationMode),
                    tint = if (effectivelyMuted) {
                        colors.iconDisable
                    } else {
                        colors.iconBase
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        TopicActionsDialog(
            expanded = actionsExpanded,
            topic = item,
            streamNotificationMode = stream.notificationMode,
            busy = actionsBusy,
            onDismiss = onDismissActions,
            onRename = onRename,
            onMarkRead = onMarkRead,
            onToggleDone = onToggleDone,
            onSetNotificationMode = onSetNotificationMode,
        )
    }
}

internal fun topicNotificationDescription(mode: String): String = when (mode) {
    "mute" -> "Уведомления темы отключены"
    "unmute" -> "Уведомления темы включены только для упоминаний"
    "follow" -> "Все уведомления темы включены"
    else -> "Настройки уведомлений темы"
}

internal fun topicIndicatorColor(value: Int?): Color =
    value
        ?.let { runCatching { Color(0xFF000000 or it.toLong()) }.getOrNull() }
        ?: Color(0xFFFFCC00)
