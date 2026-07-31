package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid
import ru.genesiscorporation.workspace.beta.modules.chatchannels.formatMessageTime
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun TextMessageView(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val deletingMessages by
        viewModel.deletingMessageUuids.collectAsStateWithLifecycle()
    val colors = LocalWorkspaceColorsPalette.current

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .background(
                        if (item.isOwn) colors.messageOwnBackground else colors.messageBackground,
                        messageBubbleShape(item.isOwn),
                    )
                    .pointerInput(item.uuid) {
                        detectTapGestures(
                            onLongPress = { menuExpanded = true },
                        )
                    }
                    .semantics {
                        onLongClick(label = "Действия с сообщением") {
                            menuExpanded = true
                            true
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                MessageHeader(item, viewModel)
                EnhancedMarkdown(
                    markdown = item.payload.content,
                    style = TextStyle(
                        color = colors.textHeaders,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    navController = navController,
                    viewModel = viewModel,
                )
                MessageFooter(item)
            }
            MessageActionsMenu(
                expanded = menuExpanded,
                item = item,
                onDismiss = { menuExpanded = false },
                onReaction = { reaction ->
                    viewModel.onMessageReactionTap(
                        messageUuid = item.uuid,
                        emojiName = reaction.emojiName,
                        equivalentEmojiNames =
                            reaction.equivalentEmojiNames,
                    )
                    menuExpanded = false
                },
                onOpenReactionPicker = {
                    menuExpanded = false
                    viewModel.openMessageReactionPicker(item.uuid)
                },
                onEdit = {
                    viewModel.onEditMessageClicked(item)
                    menuExpanded = false
                },
                isDeleting = item.uuid in deletingMessages,
                onDelete = {
                    viewModel.deleteMessage(item)
                    menuExpanded = false
                },
                onCopy = {
                    viewModel.copyMessageText(context, item)
                    menuExpanded = false
                },
                onQuote = {
                    viewModel.onQuoteMessageClicked(item)
                    menuExpanded = false
                },
                onForward = {
                    viewModel.beginForward(item)
                    menuExpanded = false
                },
            )
        }
    }
}

@Composable
internal fun MessageRow(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val baseUrl by viewModel.userViewModel.baseUrl.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!item.isOwn && !viewModel.isDirectMessages) {
            if (shouldShowMessageAvatar(item, viewModel)) {
                Box(
                    modifier = Modifier.clickable {
                        navController.navigate(
                            ChatFlow.ChatUserInfo(
                                item.user?.displayableName().orEmpty(),
                                item.authorUuid,
                                item.user?.avatar.orEmpty(),
                                item.user?.email.orEmpty(),
                            ),
                        )
                    },
                ) {
                    Avatar(
                        avatarUrn = item.user?.avatar,
                        baseUrl = baseUrl.orEmpty(),
                        color = null,
                        name = item.user?.displayableName().orEmpty(),
                        size = 34,
                        hasPadding = true,
                    )
                }
            } else {
                Spacer(Modifier.width(44.dp))
            }
        }
        content()
    }
}

internal fun shouldShowMessageAvatar(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
): Boolean {
    val next = viewModel.nextMessageByUuid(item.uuid)
    return next == null || next.authorUuid != item.authorUuid || next.topicUuid != item.topicUuid
}

@Composable
internal fun MessageHeader(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Text(
            text = item.user?.displayableName() ?: if (item.isOwn) "Я" else "Собеседник",
            color = messageAccent(item.authorUuid, item.isOwn, colors),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        viewModel.topicName?.takeIf { it.isNotBlank() }?.let { topic ->
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .width(3.dp)
                    .size(width = 3.dp, height = 18.dp)
                    .background(
                        messageAccent(item.topicUuid, false, colors),
                        RoundedCornerShape(4.dp),
                    ),
            )
            Text(
                text = "# $topic",
                color = colors.messageSecondaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun MessageFooter(item: MessageResponse) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMessageTime(item.updatedAt),
            color = colors.messageTimeColor,
            fontSize = 11.sp,
        )
        if (item.isOwn) {
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

@Composable
internal fun MessageActionsMenu(
    expanded: Boolean,
    item: MessageResponse,
    onDismiss: () -> Unit,
    onReaction: (WorkspaceReactionSelection) -> Unit,
    onOpenReactionPicker: () -> Unit,
    onEdit: () -> Unit,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onForward: () -> Unit,
) {
    var confirmDelete by remember(item.uuid) { mutableStateOf(false) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QUICK_REACTIONS.take(4).forEach { reaction ->
                    QuickReactionButton(
                        reaction = reaction,
                        enabled = !isDeleting,
                        onReaction = onReaction,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QUICK_REACTIONS.drop(4).forEach { reaction ->
                    QuickReactionButton(
                        reaction = reaction,
                        enabled = !isDeleting,
                        onReaction = onReaction,
                    )
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenReactionPicker()
                    },
                    enabled = !isDeleting,
                    modifier = Modifier.size(
                        width = 104.dp,
                        height = 48.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.message_reaction_more))
                }
            }
        }
        HorizontalDivider()
        if (canMutateNativeMessage(item)) {
            DropdownMenuItem(
                text = { Text("Редактировать") },
                onClick = onEdit,
                enabled = !isDeleting,
            )
            DropdownMenuItem(
                text = {
                    Text(if (isDeleting) "Удаляется…" else "Удалить")
                },
                onClick = {
                    onDismiss()
                    confirmDelete = true
                },
                enabled = !isDeleting,
            )
        }
        if (item.payload.content.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("Копировать текст") },
                onClick = onCopy,
                enabled = !isDeleting,
            )
        }
        DropdownMenuItem(
            text = { Text("Цитировать") },
            onClick = onQuote,
            enabled = !isDeleting,
        )
        if (canForwardMessage(item)) {
            DropdownMenuItem(
                text = { Text("Переслать") },
                onClick = onForward,
                enabled = !isDeleting,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить сообщение?") },
            text = {
                Text(
                    "Сообщение исчезнет у всех участников. " +
                        "Это действие нельзя отменить.",
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !isDeleting,
                ) {
                    Text("Удалить")
                }
            },
        )
    }
}

@Composable
private fun QuickReactionButton(
    reaction: QuickReaction,
    enabled: Boolean,
    onReaction: (WorkspaceReactionSelection) -> Unit,
) {
    val description = stringResource(reaction.descriptionRes)
    TextButton(
        onClick = {
            onReaction(
                WorkspaceReactionSelection(
                    emojiName = reaction.emojiName,
                    equivalentEmojiNames = reaction.aliases,
                ),
            )
        },
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
            },
    ) {
        Text(text = reaction.glyph, fontSize = 20.sp)
    }
}

private data class QuickReaction(
    val glyph: String,
    val emojiName: String,
    val aliases: Set<String>,
    @param:StringRes val descriptionRes: Int,
)

private val QUICK_REACTIONS = listOf(
    QuickReaction(
        glyph = "❤️",
        emojiName = "heart",
        aliases = setOf("heart", "red_heart"),
        descriptionRes = R.string.message_reaction_heart,
    ),
    QuickReaction(
        glyph = "👍",
        emojiName = "thumbs_up",
        aliases = setOf("+1", "thumbs_up", "thumbsup", "yes"),
        descriptionRes = R.string.message_reaction_like,
    ),
    QuickReaction(
        glyph = "😂",
        emojiName = "joy",
        aliases = setOf("joy", "lmao", "tears_of_joy"),
        descriptionRes = R.string.message_reaction_laughter,
    ),
    QuickReaction(
        glyph = "😮",
        emojiName = "open_mouth",
        aliases = setOf("face_with_open_mouth", "open_mouth"),
        descriptionRes = R.string.message_reaction_surprise,
    ),
    QuickReaction(
        glyph = "😢",
        emojiName = "cry",
        aliases = setOf("cry", "crying_face"),
        descriptionRes = R.string.message_reaction_sadness,
    ),
    QuickReaction(
        glyph = "👏",
        emojiName = "clap",
        aliases = setOf("clap", "clapping_hands"),
        descriptionRes = R.string.message_reaction_applause,
    ),
)

internal fun canMutateNativeMessage(item: MessageResponse): Boolean =
        item.isOwn &&
        item.provider == null &&
        parseCanonicalMessageUuid(item.uuid) != null

internal fun canDeleteMessage(item: MessageResponse): Boolean =
    canMutateNativeMessage(item)

internal fun messageBubbleShape(isOwn: Boolean): RoundedCornerShape =
    if (isOwn) {
        RoundedCornerShape(
            topStart = 11.dp,
            topEnd = 11.dp,
            bottomStart = 11.dp,
            bottomEnd = 2.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 11.dp,
            topEnd = 11.dp,
            bottomStart = 2.dp,
            bottomEnd = 11.dp,
        )
    }

internal fun messageAccent(
    seed: String,
    own: Boolean,
    colors: ru.genesiscorporation.workspace.beta.ui.theme.WorkspaceColorsPalette,
): Color {
    if (own) return colors.messageOwnAccent
    val accents = listOf(
        colors.messageAccentYellow,
        colors.messageAccentPink,
        colors.messageAccentPurple,
        colors.messageAccentGreen,
        colors.messageAccentBlue,
    )
    return accents[(seed.hashCode() and Int.MAX_VALUE) % accents.size]
}
