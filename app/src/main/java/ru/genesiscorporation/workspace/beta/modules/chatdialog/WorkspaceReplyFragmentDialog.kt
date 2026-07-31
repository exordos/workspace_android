package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.LocalWorkspaceMentionCatalog
import ru.genesiscorporation.workspace.beta.ui.WorkspaceEmojiShortcodeCatalog
import ru.genesiscorporation.workspace.beta.ui.WorkspaceMentionCatalog
import ru.genesiscorporation.workspace.beta.ui.renderWorkspaceMarkdownInlineMetadata
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun WorkspaceReplyFragmentDialog(
    sourceMarkdown: String,
    adding: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalWorkspaceColorsPalette.current
    val mentionCatalog =
        LocalWorkspaceMentionCatalog.current ?: WorkspaceMentionCatalog.Empty
    val emojiResolver = remember(context.applicationContext) {
        WorkspaceEmojiShortcodeCatalog.resolver(context.applicationContext)
    }
    val sourceText = remember(sourceMarkdown, mentionCatalog, emojiResolver) {
        workspaceMarkdownPlainText(
            renderWorkspaceMarkdownInlineMetadata(
                markdown = sourceMarkdown,
                mentionCatalog = mentionCatalog,
                resolveEmoji = emojiResolver,
            ),
        )
    }
    val selectionAreaDescription = stringResource(
        R.string.workspace_reply_fragment_selection_area,
    )
    var value by rememberSaveable(
        sourceText,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(
            TextFieldValue(
                text = sourceText,
                selection = TextRange.Zero,
            ),
        )
    }
    val selectedText = selectedReplyFragment(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.workspace_reply_fragment_title))
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.workspace_reply_fragment_hint,
                    ),
                    color = colors.textAdditional50,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = { updated ->
                        value = updated.copy(text = sourceText)
                    },
                    readOnly = true,
                    textStyle = TextStyle(
                        color = colors.textHeaders,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .background(
                            colors.background,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp)
                        .semantics {
                            contentDescription = selectionAreaDescription
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedText?.let(onConfirm) },
                enabled = selectedText != null,
            ) {
                Text(
                    stringResource(
                        if (adding) {
                            R.string.workspace_reply_add_fragment_confirm
                        } else {
                            R.string.workspace_reply_fragment_confirm
                        },
                    ),
                )
            }
        },
    )
}

internal fun selectedReplyFragment(
    value: TextFieldValue,
): String? {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    if (start == end) return null
    return value.text
        .substring(start, end)
        .take(MAX_SELECTED_REPLY_FRAGMENT_CHARS)
        .takeIf(String::isNotBlank)
}

private const val MAX_SELECTED_REPLY_FRAGMENT_CHARS = 40_000
