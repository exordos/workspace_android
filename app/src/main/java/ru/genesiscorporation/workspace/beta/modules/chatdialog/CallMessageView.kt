package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URL

@Composable
fun CallMessageView(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val openMenu = {
        menuExpanded = true
        viewModel.openMessageMenu(item.uuid)
    }
    val closeMenu = {
        menuExpanded = false
        viewModel.closeMessageMenu(item.uuid)
    }
    val deletingMessages by
        viewModel.deletingMessageUuids.collectAsStateWithLifecycle()
    val hasReplySession by
        viewModel.hasReplySession.collectAsStateWithLifecycle()
    val colors = LocalWorkspaceColorsPalette.current
    val context = LocalContext.current
    val itemUrl = runCatching { URL(item.payload.content) }.getOrNull()
    val callServerUrl = viewModel.repo.jitsiServerUrl
        .takeIf(String::isNotBlank)
        ?.let { runCatching { URL(it) }.getOrNull() }
    val roomName = itemUrl?.path?.trim('/')
    val joinTarget = if (
        callServerUrl != null &&
        itemUrl?.protocol == "https" &&
        itemUrl.host == callServerUrl.host &&
        !roomName.isNullOrBlank()
    ) {
        callServerUrl to roomName
    } else {
        null
    }
    val canJoin = joinTarget != null
    val messageBubble: @Composable (Boolean) -> Unit = { interactive ->
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .background(
                    colors.messageActiveCallBackground,
                    messageBubbleShape(item.isOwn),
                )
                .then(
                    if (interactive) {
                        Modifier.combinedClickable(
                            onClick = {
                                joinTarget?.let { (serverUrl, targetRoomName) ->
                                    val options = JitsiMeetConferenceOptions.Builder()
                                        .setServerURL(serverUrl)
                                        .setRoom(targetRoomName)
                                        .build()
                                    JitsiMeetActivity.launch(context, options)
                                } ?: openMenu()
                            },
                            onLongClick = { openMenu() },
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Звонок",
                    color = colors.indicatorGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = itemUrl?.path?.drop(1).orEmpty().ifBlank { "Workspace" },
                    color = colors.textHeaders,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.call),
                    contentDescription = if (canJoin) {
                        "Присоединиться к звонку"
                    } else {
                        "Звонок недоступен"
                    },
                    tint = if (canJoin) {
                        colors.indicatorGreen
                    } else {
                        colors.iconDisable
                    },
                )
            }
            Spacer(Modifier.padding(top = 2.dp))
            MessageHeader(item, viewModel)
            MessageFooter(item)
        }
    }

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Box {
            messageBubble(!selectionMode)
            MessageActionsMenu(
                expanded = menuExpanded,
                item = item,
                onDismiss = closeMenu,
                onReaction = { reaction ->
                    viewModel.onMessageReactionTap(
                        messageUuid = item.uuid,
                        emojiName = reaction.emojiName,
                        equivalentEmojiNames =
                            reaction.equivalentEmojiNames,
                    )
                    closeMenu()
                },
                onOpenReactionPicker = {
                    closeMenu()
                    viewModel.openMessageReactionPicker(item.uuid)
                },
                onEdit = {
                    viewModel.onEditMessageClicked(item)
                    closeMenu()
                },
                isDeleting = item.uuid in deletingMessages,
                onDelete = {
                    viewModel.deleteMessage(item)
                    closeMenu()
                },
                onCopy = {
                    viewModel.copyMessageText(context, item)
                    closeMenu()
                },
                onQuote = {
                    viewModel.onQuoteMessageClicked(item)
                    closeMenu()
                },
                onQuoteFragment = { fragment ->
                    viewModel.onQuoteMessageClicked(item, fragment)
                },
                canAddReply = hasReplySession,
                onAddQuote = {
                    viewModel.onAddQuoteMessageClicked(item)
                    closeMenu()
                },
                onAddQuoteFragment = { fragment ->
                    viewModel.onAddQuoteMessageClicked(item, fragment)
                },
                onForward = {
                    viewModel.beginForward(item)
                    closeMenu()
                },
                isSelected = isSelected,
                onToggleSelection = {
                    onToggleSelection()
                    closeMenu()
                },
            )
        }
    }
}
