package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

internal enum class ComposerFormattingAction {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    QUOTE,
    BULLETED_LIST,
    NUMBERED_LIST,
    CODE,
    SPOILER,
    CODE_BLOCK,
    LINK,
}

private data class ComposerSelectionMutation(
    val text: String,
    val selectionStartOffset: Int,
    val selectionEndOffset: Int,
)

internal fun applyComposerFormatting(
    value: TextFieldValue,
    action: ComposerFormattingAction,
    linkText: String,
): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val selected = value.text.substring(start, end)
    val mutation = when (action) {
        ComposerFormattingAction.BOLD -> wrapComposerSelection(selected, "**")
        ComposerFormattingAction.ITALIC -> wrapComposerSelection(selected, "*")
        ComposerFormattingAction.STRIKETHROUGH ->
            wrapComposerSelection(selected, "~~")
        ComposerFormattingAction.CODE -> wrapComposerSelection(selected, "`")
        ComposerFormattingAction.SPOILER -> wrapComposerSelection(selected, "||")
        ComposerFormattingAction.QUOTE -> prefixComposerLines(
            selected = selected,
            emptyPrefix = "> ",
            linePrefix = { "> " },
        )
        ComposerFormattingAction.BULLETED_LIST -> prefixComposerLines(
            selected = selected,
            emptyPrefix = "- ",
            linePrefix = { "- " },
        )
        ComposerFormattingAction.NUMBERED_LIST -> prefixComposerLines(
            selected = selected,
            emptyPrefix = "1. ",
            linePrefix = { index -> "${index + 1}. " },
        )
        ComposerFormattingAction.CODE_BLOCK -> {
            if (selected.isEmpty()) {
                ComposerSelectionMutation(
                    text = "```\n\n```",
                    selectionStartOffset = 4,
                    selectionEndOffset = 4,
                )
            } else {
                val block = "```\n$selected\n```"
                collapsedComposerMutation(block)
            }
        }
        ComposerFormattingAction.LINK -> {
            val label = selected.ifEmpty { linkText }
            val link = "[$label](https://)"
            val urlStart = label.length + 3
            ComposerSelectionMutation(
                text = link,
                selectionStartOffset = urlStart,
                selectionEndOffset = link.length - 1,
            )
        }
    }
    val nextText = buildString(
        value.text.length - (end - start) + mutation.text.length,
    ) {
        append(value.text, 0, start)
        append(mutation.text)
        append(value.text, end, value.text.length)
    }
    return value.copy(
        text = nextText,
        selection = TextRange(
            start + mutation.selectionStartOffset,
            start + mutation.selectionEndOffset,
        ),
        composition = null,
    )
}

internal fun reconcileComposerEditorValue(
    candidate: TextFieldValue,
    acceptedText: String,
): TextFieldValue {
    if (candidate.text == acceptedText) return candidate
    val selectionStart = candidate.selection.start
        .coerceIn(0, acceptedText.length)
    val selectionEnd = candidate.selection.end
        .coerceIn(0, acceptedText.length)
    return candidate.copy(
        text = acceptedText,
        selection = TextRange(selectionStart, selectionEnd),
        composition = null,
    )
}

private fun wrapComposerSelection(
    selected: String,
    marker: String,
): ComposerSelectionMutation {
    if (selected.isEmpty()) {
        return ComposerSelectionMutation(
            text = marker + marker,
            selectionStartOffset = marker.length,
            selectionEndOffset = marker.length,
        )
    }
    return collapsedComposerMutation(marker + selected + marker)
}

private fun prefixComposerLines(
    selected: String,
    emptyPrefix: String,
    linePrefix: (Int) -> String,
): ComposerSelectionMutation {
    if (selected.isEmpty()) {
        return ComposerSelectionMutation(
            text = emptyPrefix,
            selectionStartOffset = emptyPrefix.length,
            selectionEndOffset = emptyPrefix.length,
        )
    }
    val formatted = selected
        .split("\n")
        .mapIndexed { index, line -> linePrefix(index) + line }
        .joinToString("\n")
    return collapsedComposerMutation(formatted)
}

private fun collapsedComposerMutation(
    text: String,
): ComposerSelectionMutation = ComposerSelectionMutation(
    text = text,
    selectionStartOffset = text.length,
    selectionEndOffset = text.length,
)

@Composable
internal fun ComposerFormattingToolbar(
    enabled: Boolean,
    onAction: (ComposerFormattingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbarLabel = stringResource(
        R.string.message_composer_formatting_toolbar,
    )
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = toolbarLabel
            },
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = ComposerFormattingAction.entries,
            key = ComposerFormattingAction::name,
        ) { action ->
            ComposerFormattingButton(
                action = action,
                enabled = enabled,
                onClick = { onAction(action) },
            )
        }
    }
}

@Composable
private fun ComposerFormattingButton(
    action: ComposerFormattingAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val label = composerFormattingLabel(action)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = label
            },
        contentPadding = PaddingValues(horizontal = 6.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.textHeaders,
            disabledContentColor = colors.textAdditional30,
        ),
    ) {
        Text(
            text = composerFormattingGlyph(action),
            fontSize = if (
                action == ComposerFormattingAction.CODE ||
                action == ComposerFormattingAction.CODE_BLOCK
            ) {
                12.sp
            } else {
                15.sp
            },
            fontWeight = if (action == ComposerFormattingAction.BOLD) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            fontStyle = if (action == ComposerFormattingAction.ITALIC) {
                FontStyle.Italic
            } else {
                FontStyle.Normal
            },
            fontFamily = if (
                action == ComposerFormattingAction.CODE ||
                action == ComposerFormattingAction.SPOILER ||
                action == ComposerFormattingAction.CODE_BLOCK
            ) {
                FontFamily.Monospace
            } else {
                FontFamily.Default
            },
            textDecoration = if (
                action == ComposerFormattingAction.STRIKETHROUGH
            ) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
        )
    }
}

@Composable
private fun composerFormattingLabel(
    action: ComposerFormattingAction,
): String = stringResource(
    when (action) {
        ComposerFormattingAction.BOLD -> R.string.message_composer_bold
        ComposerFormattingAction.ITALIC -> R.string.message_composer_italic
        ComposerFormattingAction.STRIKETHROUGH ->
            R.string.message_composer_strikethrough
        ComposerFormattingAction.QUOTE -> R.string.message_composer_quote
        ComposerFormattingAction.BULLETED_LIST ->
            R.string.message_composer_bulleted_list
        ComposerFormattingAction.NUMBERED_LIST ->
            R.string.message_composer_numbered_list
        ComposerFormattingAction.CODE -> R.string.message_composer_code
        ComposerFormattingAction.SPOILER -> R.string.message_composer_spoiler
        ComposerFormattingAction.CODE_BLOCK ->
            R.string.message_composer_code_block
        ComposerFormattingAction.LINK -> R.string.message_composer_link
    },
)

private fun composerFormattingGlyph(
    action: ComposerFormattingAction,
): String = when (action) {
    ComposerFormattingAction.BOLD -> "B"
    ComposerFormattingAction.ITALIC -> "I"
    ComposerFormattingAction.STRIKETHROUGH -> "S"
    ComposerFormattingAction.QUOTE -> ">"
    ComposerFormattingAction.BULLETED_LIST -> "•"
    ComposerFormattingAction.NUMBERED_LIST -> "1."
    ComposerFormattingAction.CODE -> "</>"
    ComposerFormattingAction.SPOILER -> "||"
    ComposerFormattingAction.CODE_BLOCK -> "{ }"
    ComposerFormattingAction.LINK -> "🔗"
}
