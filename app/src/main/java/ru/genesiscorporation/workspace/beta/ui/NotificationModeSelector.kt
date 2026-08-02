package ru.genesiscorporation.workspace.beta.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

enum class NotificationModeGlyph {
    MENTIONS,
    MUTED,
    DEFAULT,
    FOLLOW,
}

data class NotificationModeOption(
    val value: String,
    val contentDescription: String,
    val glyph: NotificationModeGlyph,
)

val STREAM_NOTIFICATION_MODE_OPTIONS = listOf(
    NotificationModeOption(
        value = "mentions_only",
        contentDescription = "Только упоминания",
        glyph = NotificationModeGlyph.MENTIONS,
    ),
    NotificationModeOption(
        value = "muted",
        contentDescription = "Без уведомлений",
        glyph = NotificationModeGlyph.MUTED,
    ),
    NotificationModeOption(
        value = "all_messages",
        contentDescription = "Все сообщения",
        glyph = NotificationModeGlyph.DEFAULT,
    ),
)

val TOPIC_NOTIFICATION_MODE_OPTIONS = listOf(
    NotificationModeOption(
        value = "mute",
        contentDescription = "Без уведомлений",
        glyph = NotificationModeGlyph.MUTED,
    ),
    NotificationModeOption(
        value = "default",
        contentDescription = "Как для всего чата",
        glyph = NotificationModeGlyph.DEFAULT,
    ),
    NotificationModeOption(
        value = "follow",
        contentDescription = "Отслеживать тему",
        glyph = NotificationModeGlyph.FOLLOW,
    ),
)

@Composable
fun NotificationModeSelector(
    options: List<NotificationModeOption>,
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val selected = notificationModeMatches(selectedMode, option.value)
            val foreground = when {
                !enabled -> colors.iconDisable
                selected -> colors.iconActive
                else -> colors.iconBase
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (selected) colors.cardBackgroundActive else Color.Transparent,
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onModeSelected(option.value) },
                    )
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected
                        contentDescription = option.contentDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                NotificationGlyph(
                    glyph = option.glyph,
                    tint = foreground,
                )
            }
        }
    }
}

internal fun notificationModeMatches(
    selectedMode: String,
    optionMode: String,
): Boolean {
    val selected = selectedMode.trim().lowercase()
    val option = optionMode.trim().lowercase()
    return selected == option || (selected == "unmute" && option == "follow")
}

@Composable
private fun NotificationGlyph(
    glyph: NotificationModeGlyph,
    tint: Color,
) {
    when (glyph) {
        NotificationModeGlyph.MENTIONS -> Text(
            text = "@",
            color = tint,
            fontSize = 19.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        )

        NotificationModeGlyph.MUTED -> NotificationVectorWithDecoration(
            icon = R.drawable.ic_notifications,
            tint = tint,
            drawDecoration = { color ->
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.18f),
                    end = Offset(size.width * 0.82f, size.height * 0.82f),
                    strokeWidth = 1.7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            },
        )

        NotificationModeGlyph.DEFAULT -> Icon(
            painter = painterResource(R.drawable.ic_notifications),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )

        NotificationModeGlyph.FOLLOW -> NotificationVectorWithDecoration(
            icon = R.drawable.ic_notifications,
            tint = tint,
            drawDecoration = { color ->
                drawArc(
                    color = color,
                    startAngle = -62f,
                    sweepAngle = 124f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.68f, size.height * 0.23f),
                    size = Size(size.width * 0.23f, size.height * 0.54f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            },
        )
    }
}

@Composable
private fun NotificationVectorWithDecoration(
    @DrawableRes icon: Int,
    tint: Color,
    drawDecoration: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawDecoration(tint)
        }
    }
}
