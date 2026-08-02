package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    mentioned: Boolean = false,
) {
    if (count <= 0) return

    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .background(
                if (muted && !mentioned) {
                    colors.noticeDisable
                } else {
                    colors.noticeCounterBadge
                },
                CircleShape,
            )
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = unreadBadgeLabel(count, mentioned),
            color = colors.noticeOnBadge,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

internal fun unreadBadgeLabel(count: Int, mentioned: Boolean = false): String =
    if (mentioned) "@" else count.coerceIn(0, 999).toString()
