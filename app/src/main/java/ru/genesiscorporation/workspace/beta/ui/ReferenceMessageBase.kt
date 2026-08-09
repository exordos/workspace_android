package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ReferenceMessageBase(
    modifier: Modifier = Modifier,
    shouldClose: Boolean,
    onCloseTap: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(LocalWorkspaceColorsPalette.current.indicatorOrange)
                .padding(start = 2.dp)
        ) {
            content()
        }
        if (shouldClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = "Close",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onCloseTap() },
            )
        }
    }
}