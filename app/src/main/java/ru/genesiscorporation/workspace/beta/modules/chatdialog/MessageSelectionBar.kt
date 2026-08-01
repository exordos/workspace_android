package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onCancel,
        ) {
            Text(stringResource(R.string.message_selection_cancel))
        }
        TextButton(
            onClick = onForward,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_message_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "${stringResource(R.string.message_selection_forward)} " +
                    "($selectedCount)",
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
