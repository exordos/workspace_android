package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
fun SendMessageView(viewModel: ChatDialogViewModel) {
    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val uploadStatus by viewModel.uploadStatus.collectAsStateWithLifecycle()
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
    val defaultLinkText = stringResource(
        R.string.message_composer_link_text,
    )
    val emojiButtonLabel = stringResource(
        R.string.message_composer_choose_emoji,
    )
    var composerMode by rememberSaveable {
        mutableStateOf(ComposerMode.WRITE)
    }
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
    val mentionQuery = if (composerMode == ComposerMode.WRITE) {
        detectComposerMentionQuery(editorValue)
    } else {
        null
    }
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
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                items(attachments, key = { it.uri.toString() }) { attachment ->
                    SelectedAttachmentPreview(
                        attachment = attachment,
                        enabled = !sending,
                        onRemove = {
                            viewModel.removeAttachment(context, attachment.uri)
                        },
                    )
                }
            }
        }
        uploadStatus?.let { status ->
            Text(
                text = status,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
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
        ComposerModeTabs(
            mode = composerMode,
            onModeChange = { nextMode ->
                composerMode = nextMode
                emojiPickerOpen = false
                if (nextMode == ComposerMode.PREVIEW) {
                    focusManager.clearFocus()
                } else {
                    editorFocusRequester.requestFocus()
                }
            },
        )
        if (composerMode == ComposerMode.WRITE) {
            ComposerFormattingToolbar(
                enabled = conversationStateReady,
                onAction = { action ->
                    val formatted = applyComposerFormatting(
                        value = editorValue,
                        action = action,
                        linkText = defaultLinkText,
                    )
                    val acceptedText =
                        viewModel.onMessageChange(formatted.text)
                    editorValue = reconcileComposerEditorValue(
                        candidate = formatted,
                        acceptedText = acceptedText,
                    )
                    editorFocusRequester.requestFocus()
                },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 112.dp)
                    .background(colors.background, RoundedCornerShape(14.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (composerMode == ComposerMode.WRITE) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            emojiPickerOpen = true
                        },
                        enabled = conversationStateReady && !sending,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics {
                                contentDescription = emojiButtonLabel
                            },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = colors.iconBase,
                            disabledContentColor =
                                colors.iconBase.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(
                            text = "☺",
                            fontSize = 24.sp,
                        )
                    }
                }
                Button(
                    onClick = { launcher.launch(arrayOf("*/*")) },
                    enabled = conversationStateReady &&
                        !sending &&
                        viewModel.editingMessage == null,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = colors.iconBase,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.attach_file),
                        contentDescription = stringResource(
                            R.string.message_composer_attach_files,
                        ),
                    )
                }
                if (composerMode == ComposerMode.WRITE) {
                    BasicTextField(
                        value = editorValue,
                        onValueChange = { updated ->
                            val acceptedText =
                                viewModel.onMessageChange(updated.text)
                            editorValue = reconcileComposerEditorValue(
                                candidate = updated,
                                acceptedText = acceptedText,
                            )
                        },
                        enabled = conversationStateReady,
                        textStyle = TextStyle(
                            color = colors.textHeaders,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .focusRequester(editorFocusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (editorValue.text.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.message_composer_placeholder,
                                        ),
                                        color = colors.textAdditional30,
                                        fontSize = 14.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                } else {
                    ComposerMarkdownPreview(
                        markdown = buildComposerPreviewMarkdown(
                            messageText = messageText,
                            replySession = replySession,
                        ),
                        hasAttachments = attachments.isNotEmpty(),
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        viewModel.onSendClicked(context)
                        composerMode = ComposerMode.WRITE
                    },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSend) colors.primary else Color.Transparent,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = colors.iconBase,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            if (viewModel.editingMessage == null) R.drawable.send else R.drawable.ic_check,
                        ),
                        contentDescription = if (viewModel.editingMessage == null) {
                            stringResource(R.string.message_composer_send)
                        } else {
                            stringResource(R.string.message_composer_save)
                        },
                    )
                }
            }
        }
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
private fun SelectedAttachmentPreview(
    attachment: SelectedLocalAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(96.dp)
            .clip(RoundedCornerShape(9.dp))
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
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(44.dp)
                .clickable(
                    enabled = enabled,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = stringResource(
                    R.string.message_composer_remove_attachment,
                    attachment.fileName,
                ),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(5.dp),
            )
        }
    }
}

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
