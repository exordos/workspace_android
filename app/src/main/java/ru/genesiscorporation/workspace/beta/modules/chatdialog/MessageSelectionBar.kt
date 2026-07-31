package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun MessageSelectionBar(
    selectedCount: Int,
    onForward: () -> Unit,
    onCancel: () -> Unit,
) {
    if (selectedCount <= 0) return
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.message_selected_count,
                selectedCount,
                selectedCount,
            ),
            color = colors.textAdditional50,
            fontSize = 13.sp,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                },
        )
        TextButton(
            onClick = onForward,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.message_selection_forward))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.message_selection_cancel))
        }
    }
}
