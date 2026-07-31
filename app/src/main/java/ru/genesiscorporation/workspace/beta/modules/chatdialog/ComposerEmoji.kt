package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import ru.genesiscorporation.workspace.beta.R

internal fun insertComposerEmoji(
    value: TextFieldValue,
    glyph: String,
): TextFieldValue {
    if (
        glyph.isBlank() ||
        glyph.length > MAX_COMPOSER_EMOJI_UTF16_CHARS ||
        glyph.any(Char::isISOControl)
    ) {
        return value
    }

    val selectionStart = minOf(
        value.selection.start,
        value.selection.end,
    ).coerceIn(0, value.text.length)
    val selectionEnd = maxOf(
        value.selection.start,
        value.selection.end,
    ).coerceIn(selectionStart, value.text.length)
    val updatedText = value.text.replaceRange(
        selectionStart,
        selectionEnd,
        glyph,
    )
    val cursor = selectionStart + glyph.length
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(cursor),
    )
}

@Composable
internal fun ComposerEmojiPicker(
    open: Boolean,
    onDismiss: () -> Unit,
    onEmoji: (String) -> Unit,
) {
    WorkspaceEmojiPicker(
        open = open,
        paneTitleResource =
            R.string.message_composer_emoji_picker_pane_title,
        titleResource = R.string.message_composer_emoji_picker_title,
        itemDescriptionResource =
            R.string.message_composer_emoji_item_description,
        onDismiss = onDismiss,
        onEmoji = { entry ->
            onEmoji(entry.glyph)
        },
    )
}

private const val MAX_COMPOSER_EMOJI_UTF16_CHARS = 64
