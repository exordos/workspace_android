package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.util.Locale

@Composable
fun AttachmentMessageView(
    text: String,
    attachments: List<WorkspaceAttachment>,
    viewModel: ChatDialogViewModel,
    item: MessageResponse,
    navController: NavHostController,
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
    val downloadingUuid by viewModel.downloadingAttachmentUuid.collectAsStateWithLifecycle()
    val deletingMessages by
        viewModel.deletingMessageUuids.collectAsStateWithLifecycle()
    val hasReplySession by
        viewModel.hasReplySession.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    val messageBubble: @Composable (Boolean) -> Unit = { interactive ->
        val interactionModifier = if (interactive) {
            Modifier
                .pointerInput(item.uuid) {
                    detectTapGestures(onLongPress = { openMenu() })
                }
                .semantics {
                    onLongClick(label = "Действия с сообщением") {
                        openMenu()
                        true
                    }
                }
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .background(
                    if (item.isOwn) {
                        colors.messageOwnBackground
                    } else {
                        colors.messageBackground
                    },
                    messageBubbleShape(item.isOwn),
                )
                .then(interactionModifier)
                .padding(10.dp),
        ) {
            MessageHeader(item, viewModel)
            attachments.forEach { attachment ->
                val downloading = downloadingUuid == attachment.uuid
                Row(
                    modifier = Modifier
                        .padding(vertical = 3.dp)
                        .background(colors.background, RoundedCornerShape(9.dp))
                        .then(
                            if (interactive) {
                                Modifier.combinedClickable(
                                    enabled = downloadingUuid == null,
                                    onClick = {
                                        viewModel.openAttachment(context, attachment)
                                    },
                                    onLongClick = { openMenu() },
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.attach_file),
                        contentDescription = null,
                        tint = colors.iconBase,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.fileName,
                            color = colors.textHeaders,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (downloading) {
                                "Загрузка…"
                            } else {
                                attachment.sizeBytes?.let(::formatAttachmentSize)
                                    ?: attachment.contentType
                            },
                            color = colors.textAdditional50,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    color = colors.textHeaders,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            MessageFooter(item)
        }
    }

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        androidx.compose.foundation.layout.Box {
            messageBubble(true)
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
                highlightedMessage = {
                    MessageRow(
                        item = item,
                        viewModel = viewModel,
                        navController = navController,
                    ) {
                        messageBubble(false)
                    }
                },
            )
        }
    }
}

internal fun formatAttachmentSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return String.format(Locale.getDefault(), "%.1f KB", kib)
    return String.format(Locale.getDefault(), "%.1f MB", kib / 1024.0)
}
