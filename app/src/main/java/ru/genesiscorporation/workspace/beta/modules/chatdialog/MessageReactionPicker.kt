package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.Composable
import ru.genesiscorporation.workspace.beta.R

internal data class WorkspaceReactionSelection(
    val emojiName: String,
    val equivalentEmojiNames: Set<String>,
)

@Composable
internal fun MessageReactionPicker(
    open: Boolean,
    onDismiss: () -> Unit,
    onReaction: (WorkspaceReactionSelection) -> Unit,
) {
    WorkspaceEmojiPicker(
        open = open,
        paneTitleResource = R.string.reaction_picker_pane_title,
        titleResource = R.string.reaction_picker_title,
        itemDescriptionResource =
            R.string.reaction_picker_item_description,
        onDismiss = onDismiss,
        onEmoji = { entry ->
            onReaction(
                WorkspaceReactionSelection(
                    emojiName = entry.primaryShortcode,
                    equivalentEmojiNames = entry.aliases.toSet(),
                ),
            )
        },
    )
}
