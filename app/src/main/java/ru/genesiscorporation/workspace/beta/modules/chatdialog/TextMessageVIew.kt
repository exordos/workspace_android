package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
    val context = LocalContext.current
    val deletingMessages by
        viewModel.deletingMessageUuids.collectAsStateWithLifecycle()
    val hasReplySession by
        viewModel.hasReplySession.collectAsStateWithLifecycle()

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Box {
            TextMessageBubble(
                item = item,
                viewModel = viewModel,
                navController = navController,
                onOpenMenu = if (selectionMode) null else openMenu,
            )
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

@Composable
private fun TextMessageBubble(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    onOpenMenu: (() -> Unit)?,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val interactionModifier = if (onOpenMenu == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(item.uuid) {
                detectTapGestures(onLongPress = { onOpenMenu() })
            }
            .semantics {
                onLongClick(label = "Действия с сообщением") {
                    onOpenMenu()
                    true
                }
            }
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
    onQuoteFragment: (String) -> Unit,
    canAddReply: Boolean,
    onAddQuote: () -> Unit,
    onAddQuoteFragment: (String) -> Unit,
    onForward: () -> Unit,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
) {
    var confirmDelete by remember(item.uuid) { mutableStateOf(false) }
    var fragmentAdding by remember(item.uuid) {
        mutableStateOf<Boolean?>(null)
    }
    val moreReactionDescription =
        stringResource(R.string.message_reaction_more)
    if (expanded) {
        DisposableEffect(item.uuid) {
            onDispose { onDismiss() }
        }
        val colors = LocalWorkspaceColorsPalette.current
        val actionEnabled = !isDeleting
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
        ) {
            MessageMenuDialogWindowEffects()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = MESSAGE_ACTION_OVERLAY_HORIZONTAL_PADDING,
                        vertical = 24.dp,
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = if (item.isOwn) {
                    Alignment.End
                } else {
                    Alignment.Start
                },
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            if (item.isOwn) {
                                Modifier
                            } else {
                                Modifier.padding(start = MESSAGE_AVATAR_SPACE)
                            },
                        )
                        .width(MESSAGE_ACTION_MENU_WIDTH)
                        .clip(MESSAGE_ACTION_MENU_SHAPE)
                        .background(colors.cardBackgroundActive)
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QUICK_REACTIONS.forEach { reaction ->
                            QuickReactionButton(
                                reaction = reaction,
                                enabled = actionEnabled,
                                onReaction = onReaction,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 34.dp, height = 40.dp)
                                .clickable(
                                    enabled = actionEnabled,
                                    role = Role.Button,
                                ) {
                                    onDismiss()
                                    onOpenReactionPicker()
                                }
                                .semantics {
                                    contentDescription = moreReactionDescription
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                color = colors.iconBase,
                                fontSize = 22.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }

                    MessageMenuActionRow(
                        label = stringResource(R.string.workspace_reply_action),
                        iconResource = R.drawable.ic_message_reply,
                        enabled = actionEnabled,
                        onClick = {
                            onDismiss()
                            onQuote()
                        },
                    )
                    if (canMutateNativeMessage(item)) {
                        MessageMenuActionRow(
                            label = "Изменить",
                            iconResource = R.drawable.ic_message_edit,
                            enabled = actionEnabled,
                            onClick = {
                                onDismiss()
                                onEdit()
                            },
                        )
                    }
                    if (item.payload.content.isNotBlank()) {
                        MessageMenuActionRow(
                            label = "Копировать текст",
                            iconResource = R.drawable.ic_copy,
                            enabled = actionEnabled,
                            onClick = {
                                onDismiss()
                                onCopy()
                            },
                        )
                    }
                    if (canForwardMessage(item)) {
                        MessageMenuActionRow(
                            label = stringResource(
                                R.string.message_selection_forward,
                            ),
                            iconResource = R.drawable.ic_message_forward,
                            enabled = actionEnabled,
                            onClick = {
                                onDismiss()
                                onForward()
                            },
                        )
                    }
                    if (item.payload.content.isNotBlank()) {
                        MessageMenuActionRow(
                            label = stringResource(
                                R.string.workspace_reply_fragment_action,
                            ),
                            iconResource = R.drawable.ic_message_reply,
                            enabled = actionEnabled,
                            onClick = {
                                onDismiss()
                                fragmentAdding = false
                            },
                        )
                    }
                    if (canAddReply) {
                        MessageMenuActionRow(
                            label = stringResource(
                                R.string.workspace_reply_add_action,
                            ),
                            iconResource = R.drawable.ic_message_reply,
                            enabled = actionEnabled,
                            onClick = {
                                onDismiss()
                                onAddQuote()
                            },
                        )
                        if (item.payload.content.isNotBlank()) {
                            MessageMenuActionRow(
                                label = stringResource(
                                    R.string.workspace_reply_add_fragment_action,
                                ),
                                iconResource = R.drawable.ic_message_reply,
                                enabled = actionEnabled,
                                onClick = {
                                    onDismiss()
                                    fragmentAdding = true
                                },
                            )
                        }
                    }
                    if (canMutateNativeMessage(item)) {
                        MessageMenuActionRow(
                            label = if (isDeleting) "Удаляется…" else "Удалить",
                            iconResource = R.drawable.ic_message_delete,
                            enabled = actionEnabled,
                            iconTint = colors.indicatorRed,
                            onClick = {
                                onDismiss()
                                confirmDelete = true
                            },
                        )
                    }
                    if (canForwardMessage(item)) {
                        MessageMenuActionRow(
                            label = stringResource(
                                if (isSelected) {
                                    R.string.message_unselect
                                } else {
                                    R.string.message_select
                                },
                            ),
                            iconResource = R.drawable.ic_message_select,
                            enabled = actionEnabled,
                            onClick = {
                                onDismiss()
                                onToggleSelection()
                            },
                        )
                    }
                }
            }
        }
    }
    fragmentAdding?.let { adding ->
        WorkspaceReplyFragmentDialog(
            sourceMarkdown = item.payload.content,
            adding = adding,
            onDismiss = { fragmentAdding = null },
            onConfirm = { fragment ->
                fragmentAdding = null
                if (adding) {
                    onAddQuoteFragment(fragment)
                } else {
                    onQuoteFragment(fragment)
                }
            },
        )
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
private fun MessageMenuDialogWindowEffects() {
    val dialogView = LocalView.current
    DisposableEffect(dialogView) {
        val window = (dialogView.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val attributes = window.attributes
            attributes.dimAmount = 0f
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = attributes
        }
        onDispose {}
    }
}

@Composable
private fun MessageMenuActionRow(
    label: String,
    iconResource: Int,
    enabled: Boolean,
    iconTint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconResource),
            contentDescription = null,
            tint = if (iconTint == Color.Unspecified) {
                colors.iconBase
            } else {
                iconTint
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (enabled) colors.textHeaders else colors.textAdditional30,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
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
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 40.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
            ) {
            onReaction(
                WorkspaceReactionSelection(
                    emojiName = reaction.emojiName,
                    equivalentEmojiNames = reaction.aliases,
                ),
            )
            }
            .semantics {
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = reaction.glyph, fontSize = 22.sp)
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
        glyph = "😁",
        emojiName = "grin",
        aliases = setOf("grin", "beaming_face_with_smiling_eyes"),
        descriptionRes = R.string.message_reaction_laughter,
    ),
    QuickReaction(
        glyph = "👌",
        emojiName = "ok_hand",
        aliases = setOf("ok_hand", "okay"),
        descriptionRes = R.string.message_reaction_ok,
    ),
    QuickReaction(
        glyph = "😢",
        emojiName = "cry",
        aliases = setOf("cry", "crying_face"),
        descriptionRes = R.string.message_reaction_sadness,
    ),
)

private val MESSAGE_ACTION_MENU_WIDTH = 226.dp
private val MESSAGE_ACTION_MENU_SHAPE = RoundedCornerShape(10.dp)
private val MESSAGE_ACTION_OVERLAY_HORIZONTAL_PADDING = 14.dp
private val MESSAGE_AVATAR_SPACE = 44.dp
internal val MESSAGE_BACKGROUND_BLUR_RADIUS = 18.dp

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
