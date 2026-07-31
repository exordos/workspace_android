package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

internal enum class ComposerMode {
    WRITE,
    PREVIEW,
}

internal fun buildComposerPreviewMarkdown(
    messageText: String,
    replySession: WorkspaceReplySession,
): String = if (replySession.tabs.isEmpty()) {
    messageText
} else {
    buildWorkspaceReplyMarkdown(replySession).orEmpty()
}

@Composable
internal fun ComposerModeTabs(
    mode: ComposerMode,
    onModeChange: (ComposerMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = 10.dp),
    ) {
        ComposerModeTab(
            label = stringResource(R.string.message_composer_write),
            selected = mode == ComposerMode.WRITE,
            onClick = { onModeChange(ComposerMode.WRITE) },
        )
        ComposerModeTab(
            label = stringResource(R.string.message_composer_preview),
            selected = mode == ComposerMode.PREVIEW,
            onClick = { onModeChange(ComposerMode.PREVIEW) },
        )
    }
}

@Composable
private fun ComposerModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
    ) {
        Text(
            text = label,
            color = if (selected) colors.primary else colors.textAdditional50,
        )
    }
}

@Composable
internal fun ComposerMarkdownPreview(
    markdown: String,
    hasAttachments: Boolean,
    viewModel: ChatDialogViewModel?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val emptyLabel = stringResource(
        if (hasAttachments) {
            R.string.message_composer_preview_attachments_only
        } else {
            R.string.message_composer_preview_empty
        },
    )
    val previewRegionLabel = stringResource(
        R.string.message_composer_preview_region,
    )
    Box(
        modifier = modifier
            .heightIn(min = 46.dp, max = 112.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .semantics {
                contentDescription = previewRegionLabel
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.TopStart,
    ) {
        if (markdown.isBlank()) {
            Text(
                text = emptyLabel,
                color = colors.textAdditional30,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        } else {
            EnhancedMarkdown(
                markdown = markdown,
                style = TextStyle(
                    color = colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
                navController = null,
                viewModel = viewModel,
            )
        }
    }
}
