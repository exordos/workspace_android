package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun WorkspaceReplyComposer(
    session: WorkspaceReplySession,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onClearAll: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val active = session.activeTab ?: return
    val tabListDescription =
        stringResource(R.string.workspace_reply_tabs_description)
    val moveLeftDescription =
        stringResource(R.string.workspace_reply_move_left)
    val moveRightDescription =
        stringResource(R.string.workspace_reply_move_right)
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = tabListDescription
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(session.tabs, key = WorkspaceReplyTab::id) { tab ->
                val isSelected = tab.id == session.activeTabId
                val excerpt = remember(tab) { replyTabExcerpt(tab) }
                val description = stringResource(
                    R.string.workspace_reply_tab_description,
                    tab.senderName,
                    excerpt,
                )
                Row(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .background(
                            color = if (isSelected) {
                                colors.primary.copy(alpha = 0.16f)
                            } else {
                                colors.background
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .semantics {
                            selected = isSelected
                            role = Role.Tab
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tab.senderName,
                        color = if (isSelected) {
                            colors.textHeaders
                        } else {
                            colors.textAdditional50
                        },
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(
                                enabled = enabled,
                                role = Role.Tab,
                                onClickLabel = description,
                            ) {
                                onSelect(tab.id)
                            }
                            .padding(
                                start = 12.dp,
                                end = 6.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                    )
                    IconButton(
                        onClick = { onRemove(tab.id) },
                        enabled = enabled,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_small),
                            contentDescription = stringResource(
                                R.string.workspace_reply_remove_tab,
                                tab.senderName,
                            ),
                            tint = colors.iconBase,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val activePreview = remember(
                active.selectedText,
                active.quotedContent,
            ) {
                active.selectedText
                    ?: workspaceMarkdownPlainText(active.quotedContent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.workspace_reply_active_title,
                        active.senderName,
                    ),
                    color = colors.primary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = activePreview,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val activeIndex = session.tabs.indexOfFirst {
                it.id == active.id
            }
            if (activeIndex > 0) {
                TextButton(
                    onClick = { onMove(active.id, -1) },
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = moveLeftDescription
                        },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "←",
                        color = colors.iconBase,
                        fontSize = 22.sp,
                    )
                }
            }
            if (activeIndex in 0 until session.tabs.lastIndex) {
                TextButton(
                    onClick = { onMove(active.id, 1) },
                    enabled = enabled,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = moveRightDescription
                        },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "→",
                        color = colors.iconBase,
                        fontSize = 22.sp,
                    )
                }
            }
            IconButton(
                onClick = onClearAll,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription =
                        stringResource(R.string.workspace_reply_clear_all),
                    tint = colors.iconBase,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

internal fun replyTabExcerpt(tab: WorkspaceReplyTab): String {
    val displayText = tab.selectedText
        ?: workspaceMarkdownPlainText(tab.quotedContent)
    val normalized = displayText
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (normalized.length <= REPLY_TAB_EXCERPT_CHARS) {
        normalized
    } else {
        normalized.take(REPLY_TAB_EXCERPT_CHARS).trimEnd() + "…"
    }
}

private const val REPLY_TAB_EXCERPT_CHARS = 36
