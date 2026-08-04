package ru.genesiscorporation.workspace.beta.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

/** Compact context-menu surface shared by stream and topic actions. */
@Composable
fun WorkspaceContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    width: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return

    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(marginPx) {
        WorkspaceMenuPositionProvider(marginPx)
    }
    val colors = LocalWorkspaceColorsPalette.current
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(width),
            shape = RoundedCornerShape(8.dp),
            color = colors.contextMenuBackground,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

@Composable
fun WorkspaceMenuActionRow(
    text: String,
    @DrawableRes iconRes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: DpSize = DpSize(24.dp, 24.dp),
    @DrawableRes trailingIconRes: Int? = null,
    trailingIconSize: DpSize = DpSize(8.dp, 16.dp),
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Spacer(Modifier.size(iconSize))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = colors.textHeaders,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontFamily = NavigationFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingIconRes?.let { drawable ->
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(drawable),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(trailingIconSize),
            )
        }
    }
}

private class WorkspaceMenuPositionProvider(
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val maximumX = (windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        val x = preferredX.coerceIn(marginPx, maximumX)

        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val y = if (below + popupContentSize.height <= windowSize.height - marginPx) {
            below
        } else {
            above.coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}
