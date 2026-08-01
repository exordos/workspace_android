package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun SendMessageView(
    viewModel: ChatDialogViewModel,
    onOpenDrafts: () -> Unit,
) {
    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val uploadingAttachmentUri by
        viewModel.uploadingAttachmentUri.collectAsStateWithLifecycle()
    val editingMessageBackupText by
        viewModel.editingMessageBackupText.collectAsStateWithLifecycle()
    val replySession by viewModel.replySession.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val conversationStateReady by
        viewModel.conversationStateReady.collectAsStateWithLifecycle()
    val mentionUsers by viewModel.repo.users.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = LocalWorkspaceColorsPalette.current
    val editorFocusRequester = remember { FocusRequester() }
    var emojiPickerOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var editorValue by rememberSaveable(
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(
            TextFieldValue(
                text = messageText,
                selection = TextRange(messageText.length),
            ),
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != editorValue.text) {
            editorValue = TextFieldValue(
                text = messageText,
                selection = TextRange(messageText.length),
            )
        }
    }
    val hasSendableContent = if (viewModel.editingMessage != null) {
        if (replySession.tabs.isEmpty()) {
            messageText.isNotBlank()
        } else {
            replySession.hasAnswer
        }
    } else if (replySession.tabs.isNotEmpty()) {
        replySession.hasAnswer || attachments.isNotEmpty()
    } else {
        messageText.isNotBlank() ||
            attachments.isNotEmpty()
    }
    val canSend = conversationStateReady && !sending && hasSendableContent
    val mentionQuery = detectComposerMentionQuery(editorValue)
    val mentionCandidates = remember(mentionUsers) {
        composerMentionCandidates(mentionUsers)
    }
    val mentionSuggestions = remember(mentionCandidates, mentionQuery) {
        mentionQuery?.let { query ->
            filterComposerMentionSuggestions(
                mentionCandidates,
                query.query,
            )
        }.orEmpty()
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.addAttachments(context, uris)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chatHeaderBackground, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .navigationBarsPadding()
            .padding(top = 5.dp),
    ) {
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(attachments, key = { it.uri.toString() }) { attachment ->
                    val uploading = uploadingAttachmentUri == attachment.uri
                    SelectedAttachmentPreview(
                        attachment = attachment,
                        uploading = uploading,
                        enabled = !sending || uploading,
                        onRemove = {
                            viewModel.dismissAttachment(context, attachment.uri)
                        },
                    )
                }
            }
        }
        if (editingMessageBackupText != null) {
            ComposerContext(
                title = stringResource(R.string.message_composer_editing),
                text = if (replySession.tabs.isEmpty()) {
                    editingMessageBackupText.orEmpty()
                } else {
                    stringResource(
                        R.string.workspace_reply_editing_summary,
                        replySession.tabs.size,
                    )
                },
                onClose = viewModel::clearEditingMessage,
            )
        }
        if (replySession.tabs.isNotEmpty()) {
            WorkspaceReplyComposer(
                session = replySession,
                enabled = conversationStateReady && !sending,
                onSelect = viewModel::selectReplyTab,
                onRemove = viewModel::removeReplyTab,
                onMove = viewModel::moveReplyTab,
                onClearAll = viewModel::clearQuotedMessage,
            )
        }
        ComposerMentionSuggestions(
            suggestions = mentionSuggestions,
            onSelect = { suggestion ->
                val query = mentionQuery
                    ?: return@ComposerMentionSuggestions
                val insertion = insertComposerMention(
                    value = editorValue,
                    query = query,
                    suggestion = suggestion,
                )
                val acceptedText =
                    viewModel.onMessageChange(insertion.text)
                editorValue = reconcileComposerEditorValue(
                    candidate = insertion,
                    acceptedText = acceptedText,
                )
                editorFocusRequester.requestFocus()
            },
        )
        CompactMessageComposer(
            value = editorValue,
            onValueChange = { updated ->
                val acceptedText = viewModel.onMessageChange(updated.text)
                editorValue = reconcileComposerEditorValue(
                    candidate = updated,
                    acceptedText = acceptedText,
                )
            },
            editorEnabled = conversationStateReady,
            actionsEnabled = conversationStateReady && !sending,
            attachmentEnabled = conversationStateReady &&
                !sending &&
                viewModel.editingMessage == null,
            canSend = canSend,
            editing = viewModel.editingMessage != null,
            onAttach = { launcher.launch(arrayOf("*/*")) },
            onEmoji = {
                focusManager.clearFocus()
                emojiPickerOpen = true
            },
            onOpenDrafts = onOpenDrafts,
            onSend = { viewModel.onSendClicked(context) },
            editorFocusRequester = editorFocusRequester,
        )
    }
    ComposerEmojiPicker(
        open = emojiPickerOpen,
        onDismiss = {
            emojiPickerOpen = false
            editorFocusRequester.requestFocus()
        },
        onEmoji = { glyph ->
            val insertion = insertComposerEmoji(
                value = editorValue,
                glyph = glyph,
            )
            val acceptedText =
                viewModel.onMessageChange(insertion.text)
            editorValue = reconcileComposerEditorValue(
                candidate = insertion,
                acceptedText = acceptedText,
            )
            emojiPickerOpen = false
            editorFocusRequester.requestFocus()
        },
    )
}

@Composable
internal fun CompactMessageComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    editorEnabled: Boolean,
    actionsEnabled: Boolean,
    attachmentEnabled: Boolean,
    canSend: Boolean,
    editing: Boolean,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onOpenDrafts: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    editorFocusRequester: FocusRequester? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val focusRequester = editorFocusRequester ?: remember { FocusRequester() }
    val attachLabel = stringResource(R.string.message_composer_attach_files)
    val emojiLabel = stringResource(R.string.message_composer_choose_emoji)
    val draftsLabel = stringResource(R.string.message_composer_open_drafts)
    val sendLabel = stringResource(
        if (editing) {
            R.string.message_composer_save
        } else {
            R.string.message_composer_send
        },
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 48.dp, max = 112.dp)
            .background(colors.background, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp)
            .testTag(COMPACT_COMPOSER_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onAttach,
            enabled = attachmentEnabled,
            modifier = Modifier
                .size(40.dp)
                .testTag(COMPACT_COMPOSER_ATTACH_TAG),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colors.iconBase,
                disabledContentColor = colors.iconBase.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_figma_composer_attach),
                contentDescription = attachLabel,
                modifier = Modifier.size(28.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = editorEnabled,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontSize = 16.sp,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .padding(horizontal = 4.dp, vertical = 10.dp)
                .focusRequester(focusRequester)
                .testTag(COMPACT_COMPOSER_EDITOR_TAG),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.message_composer_placeholder,
                            ),
                            color = colors.textAdditional30,
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        ComposerIconAction(
            icon = R.drawable.ic_figma_composer_emoji,
            label = emojiLabel,
            enabled = actionsEnabled,
            testTag = COMPACT_COMPOSER_EMOJI_TAG,
            onClick = onEmoji,
        )
        ComposerIconAction(
            icon = R.drawable.ic_figma_composer_history,
            label = draftsLabel,
            enabled = actionsEnabled,
            testTag = COMPACT_COMPOSER_HISTORY_TAG,
            onClick = onOpenDrafts,
        )
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(40.dp)
                .testTag(COMPACT_COMPOSER_SEND_TAG),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colors.onPrimary,
                disabledContentColor = colors.iconBase,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (canSend) colors.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (editing) R.drawable.ic_check else R.drawable.send,
                    ),
                    contentDescription = sendLabel,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposerIconAction(
    icon: Int,
    label: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = colors.iconBase,
            disabledContentColor = colors.iconBase.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
internal fun SelectedAttachmentPreview(
    attachment: SelectedLocalAttachment,
    uploading: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val uploadingDescription = stringResource(
        R.string.message_composer_attachment_uploading,
        attachment.fileName,
    )
    val dismissDescription = stringResource(
        if (uploading) {
            R.string.message_composer_cancel_attachment_upload
        } else {
            R.string.message_composer_remove_attachment
        },
        attachment.fileName,
    )
    Box(
        modifier = Modifier
            .width(144.dp)
            .height(101.dp)
            .testTag(SELECTED_ATTACHMENT_PREVIEW_TAG)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.background),
    ) {
        if (attachment.contentType.startsWith("image/")) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.attach_file),
                    contentDescription = null,
                    tint = colors.iconBase,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = attachment.fileName,
                    color = colors.textHeaders,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        if (uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.45f))
                    .semantics {
                        contentDescription = uploadingDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = colors.iconBase,
                    strokeWidth = 2.dp,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .testTag(SELECTED_ATTACHMENT_REMOVE_TAG)
                .clickable(
                    enabled = enabled,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = dismissDescription,
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .background(colors.iconBase, CircleShape)
                    .padding(4.dp),
            )
        }
    }
}

internal const val SELECTED_ATTACHMENT_PREVIEW_TAG =
    "selected-attachment-preview"
internal const val SELECTED_ATTACHMENT_REMOVE_TAG =
    "selected-attachment-remove"
internal const val COMPACT_COMPOSER_ROW_TAG = "compact-composer-row"
internal const val COMPACT_COMPOSER_ATTACH_TAG = "compact-composer-attach"
internal const val COMPACT_COMPOSER_EDITOR_TAG = "compact-composer-editor"
internal const val COMPACT_COMPOSER_EMOJI_TAG = "compact-composer-emoji"
internal const val COMPACT_COMPOSER_HISTORY_TAG = "compact-composer-history"
internal const val COMPACT_COMPOSER_SEND_TAG = "compact-composer-send"

@Composable
private fun ComposerContext(
    title: String,
    text: String,
    onClose: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.primary,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                text = text,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = stringResource(R.string.common_close),
                tint = colors.iconBase,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
