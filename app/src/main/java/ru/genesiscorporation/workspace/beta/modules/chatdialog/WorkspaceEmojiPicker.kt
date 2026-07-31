package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.WorkspaceEmojiPickerEntry
import ru.genesiscorporation.workspace.beta.ui.WorkspaceEmojiShortcodeCatalog
import ru.genesiscorporation.workspace.beta.ui.filterWorkspaceEmojiPickerEntries
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

private sealed interface EmojiPickerCatalogState {
    data object Loading : EmojiPickerCatalogState
    data object Failed : EmojiPickerCatalogState
    data class Loaded(
        val entries: List<WorkspaceEmojiPickerEntry>,
    ) : EmojiPickerCatalogState
}

@Composable
internal fun WorkspaceEmojiPicker(
    open: Boolean,
    @StringRes paneTitleResource: Int,
    @StringRes titleResource: Int,
    @StringRes itemDescriptionResource: Int,
    onDismiss: () -> Unit,
    onEmoji: (WorkspaceEmojiPickerEntry) -> Unit,
) {
    if (!open) return

    val context = LocalContext.current.applicationContext
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }
    val catalogState by produceState<EmojiPickerCatalogState>(
        initialValue = EmojiPickerCatalogState.Loading,
        context,
        reloadKey,
    ) {
        value = EmojiPickerCatalogState.Loading
        val entries = withContext(Dispatchers.IO) {
            runCatching {
                WorkspaceEmojiShortcodeCatalog.pickerEntries(context)
            }.getOrDefault(emptyList())
        }
        value = if (entries.isEmpty()) {
            EmojiPickerCatalogState.Failed
        } else {
            EmojiPickerCatalogState.Loaded(entries)
        }
    }

    WorkspaceEmojiPickerDialog(
        catalogState = catalogState,
        paneTitle = stringResource(paneTitleResource),
        title = stringResource(titleResource),
        itemDescriptionResource = itemDescriptionResource,
        onRetry = { reloadKey++ },
        onDismiss = onDismiss,
        onEmoji = onEmoji,
    )
}

@Composable
private fun WorkspaceEmojiPickerDialog(
    catalogState: EmojiPickerCatalogState,
    paneTitle: String,
    title: String,
    @StringRes itemDescriptionResource: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onEmoji: (WorkspaceEmojiPickerEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredEntries = remember(catalogState, query) {
        val entries = (catalogState as? EmojiPickerCatalogState.Loaded)
            ?.entries
            .orEmpty()
        filterWorkspaceEmojiPickerEntries(entries, query)
    }
    val colors = LocalWorkspaceColorsPalette.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .semantics {
                    this.paneTitle = paneTitle
                },
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = colors.textHeaders,
                        fontSize = 18.sp,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.reaction_picker_close),
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it.take(MAX_EMOJI_SEARCH_CHARS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = {
                        Text(
                            stringResource(
                                R.string.reaction_picker_search_label,
                            ),
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                R.string.reaction_picker_search_hint,
                            ),
                        )
                    },
                    singleLine = true,
                )
                when (catalogState) {
                    EmojiPickerCatalogState.Loading ->
                        PickerCenteredState {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                            )
                        }

                    EmojiPickerCatalogState.Failed ->
                        PickerCenteredState {
                            Text(
                                text = stringResource(
                                    R.string.reaction_picker_load_failed,
                                ),
                                color = colors.textHeaders,
                            )
                            TextButton(onClick = onRetry) {
                                Text(
                                    stringResource(
                                        R.string.reaction_picker_retry,
                                    ),
                                )
                            }
                        }

                    is EmojiPickerCatalogState.Loaded ->
                        if (filteredEntries.isEmpty()) {
                            PickerCenteredState {
                                Text(
                                    text = stringResource(
                                        R.string.reaction_picker_empty,
                                    ),
                                    color = colors.textAdditional50,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(
                                    minSize = 64.dp,
                                ),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    vertical = 4.dp,
                                ),
                            ) {
                                items(
                                    items = filteredEntries,
                                    key = WorkspaceEmojiPickerEntry::glyph,
                                ) { entry ->
                                    val description = stringResource(
                                        itemDescriptionResource,
                                        entry.glyph,
                                        entry.primaryShortcode,
                                    )
                                    TextButton(
                                        onClick = {
                                            onEmoji(entry)
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 68.dp)
                                            .semantics(
                                                mergeDescendants = true,
                                            ) {
                                                contentDescription = description
                                            },
                                        contentPadding = PaddingValues(4.dp),
                                    ) {
                                        Column(
                                            horizontalAlignment =
                                                Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = entry.glyph,
                                                fontSize = 28.sp,
                                            )
                                            Text(
                                                text =
                                                    ":${entry.primaryShortcode}:",
                                                color = colors.textAdditional50,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow =
                                                    TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun PickerCenteredState(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private const val MAX_EMOJI_SEARCH_CHARS = 64
