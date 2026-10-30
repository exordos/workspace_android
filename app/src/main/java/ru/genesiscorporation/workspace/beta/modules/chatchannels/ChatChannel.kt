package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.WorkspaceDateTime
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.encryptedchat.EncryptedChatAvatarBadge
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ChatChannel(
    item: Stream,
    viewModel: ChatViewModel,
    showDetail: Boolean,
    currentlySelectedFolder: FolderResponseData?,
    currentlySelectedStream: Stream?,
    onChatNumberToAddChange: (Stream?) -> Unit,
    onClick: () -> Unit
) {
    val previewContext = LocalContext.current
    val encryptedPreviews by viewModel.encryptedPreviews.collectAsState()
    DisposableEffect(viewModel, item.uuid, item.encryption) {
        if (item.encryption) {
            viewModel.attachEncryptedPreviews(previewContext)
            viewModel.showEncryptedPreview(item.uuid, true)
        }
        onDispose { if (item.encryption) viewModel.showEncryptedPreview(item.uuid, false) }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    val palette = LocalWorkspaceColorsPalette.current
    val scope = rememberCoroutineScope()
    val targetBackground = if (showDetail) palette.chatHeaderBackground.copy(alpha = 0f) else palette.chatHeaderBackground
//    val animatedBackground by animateColorAsState(
//        targetValue = targetBackground,
//        animationSpec = tween(durationMillis = 250),
//        label = "chatHeaderBackground"
//    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
//            .background(animatedBackground)
//            .padding(start = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    menuExpanded = true
                }
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarUrn = item.directUser?.avatar
            val lastMessage = item.lastMessage
            val encryptedPreview = if (item.encryption && encryptedPreviews.containsKey(item.uuid)) viewModel.encryptedPreview(item.uuid)
                ?.takeIf { it.catalogMessageUuid == lastMessage?.uuid } else null
            Box(Modifier.padding(start = 8.dp, top = 12.dp, end = 12.dp, bottom = 12.dp).size(40.dp)) {
                Avatar(
                    avatarUrn,
                    viewModel.client.userViewModel?.baseUrl?.value ?: "",
                    viewModel.client.authHeaders(),
                    item.color,
                    item.displayableName(),
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (currentlySelectedStream?.uuid == item.uuid) palette.cardBackgroundBase else Color.Transparent)
                )
                if (item.encryption) EncryptedChatAvatarBadge(Modifier.align(Alignment.Center))
            }
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.displayableName(),
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                }
                if (lastMessage != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lastMessageAuthor = encryptedPreview?.authorName ?: lastMessage.user?.displayableName()
                        if (lastMessageAuthor != null) {
                            Text(
                                text = lastMessageAuthor,
                                color = LocalWorkspaceColorsPalette.current.primary,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val previewDescription = when {
                            !item.encryption -> lastMessage.description(stringResource(R.string.quote_forwarded_message))
                            encryptedPreview != null -> lastMessage.copy(payload = ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload("markdown", encryptedPreview.text))
                                .description(stringResource(R.string.quote_forwarded_message))
                            lastMessage.payload.kind in setOf("key_request", "key_grant", "key_reject", "key_announced") -> lastMessage.description()
                            else -> stringResource(R.string.encrypted_chat_locked)
                        }
                        MarkdownText(
                            markdown = previewDescription,
                            modifier = Modifier.weight(1f),
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
                    .padding(8.dp, 4.dp, 0.dp, 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val unreadCount = if (item.activeUnreadCount > 0) item.activeUnreadCount else item.passiveUnreadCount
                if (item.unreadCount > 0) {
                    val backgroundColor = if (item.activeUnreadCount > 0) LocalWorkspaceColorsPalette.current.noticeBase else LocalWorkspaceColorsPalette.current.noticeDisable
                    Text(
                        text = "${unreadCount}",
                        color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        modifier = Modifier
                            .background(
                                color = backgroundColor,
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (lastMessage != null) {
                    val messageTime = WorkspaceDateTime.formatTime(encryptedPreview?.createdAt ?: lastMessage.createdAt)
                    Text(
                        text = messageTime,
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                    )
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
                        if (item.notificationMode != "mentions_only") {
                            scope.launch {
                                viewModel.setStreamNotificationMode(item.uuid,"mentions_only")
                            }
                        }
                    },
                    modifier = Modifier
                        .background(
                            if (item.notificationMode == "mentions_only") LocalWorkspaceColorsPalette.current.cardBackgroundBase else LocalWorkspaceColorsPalette.current.background,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mentions_small),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (item.notificationMode != "muted") {
                            scope.launch {
                                viewModel.setStreamNotificationMode(item.uuid,"muted")
                            }
                        }
                    },
                    modifier = Modifier
                        .background(
                            if (item.notificationMode == "muted") LocalWorkspaceColorsPalette.current.cardBackgroundBase else LocalWorkspaceColorsPalette.current.background,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_notifications_off_small),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (item.notificationMode != "all_messages") {
                            scope.launch {
                                viewModel.setStreamNotificationMode(item.uuid,"all_messages")
                            }
                        }
                    },
                    modifier = Modifier
                        .background(
                            if (item.notificationMode == "all_messages") LocalWorkspaceColorsPalette.current.cardBackgroundBase else LocalWorkspaceColorsPalette.current.background,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_notifications_small),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (item.unreadCount > 0) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = ""
                            )
                            Text("Отметить всё как прочитанное")
                        }
                    },
                    onClick = {
                        scope.launch {
                            viewModel.markStreamMessagesRead(item.uuid)
                        }
                        menuExpanded = false
                    }
                )
            }
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
        if (currentlySelectedStream == null) {
            Column {
                Spacer(
                    Modifier.weight(1f)
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = LocalWorkspaceColorsPalette.current.divider
                )
            }
        }
    }
}
