package ru.genesiscorporation.workspace.beta.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
        contentDescription = "Все сообщения",
        glyph = NotificationModeGlyph.FOLLOW,
    ),
)

private val TOPIC_UNMUTE_NOTIFICATION_MODE_OPTION = NotificationModeOption(
    value = "unmute",
    contentDescription = "Только упоминания",
    glyph = NotificationModeGlyph.MENTIONS,
)

fun topicNotificationModeOptions(
    streamNotificationMode: String,
    selectedMode: String? = null,
): List<NotificationModeOption> = if (
    streamNotificationMode.equals("muted", ignoreCase = true) ||
    selectedMode.equals("unmute", ignoreCase = true)
) {
    TOPIC_NOTIFICATION_MODE_OPTIONS.toMutableList().apply {
        add(index = 2, element = TOPIC_UNMUTE_NOTIFICATION_MODE_OPTION)
    }
} else {
    TOPIC_NOTIFICATION_MODE_OPTIONS
}

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
    return selected == option
}

@Composable
private fun NotificationGlyph(
    glyph: NotificationModeGlyph,
    tint: Color,
) {
    val visual = notificationModeGlyphVisual(glyph)
    Icon(
        painter = painterResource(visual.drawableRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(visual.size),
    )
}

internal data class NotificationModeGlyphVisual(
    @param:DrawableRes val drawableRes: Int,
    val size: DpSize,
)

internal fun notificationModeGlyphVisual(
    glyph: NotificationModeGlyph,
): NotificationModeGlyphVisual = when (glyph) {
    NotificationModeGlyph.MENTIONS -> NotificationModeGlyphVisual(
        drawableRes = R.drawable.ic_topic_notification_mentions,
        size = DpSize(width = 18.dp, height = 18.dp),
    )

    NotificationModeGlyph.MUTED -> NotificationModeGlyphVisual(
        drawableRes = R.drawable.ic_topic_notification_muted,
        size = DpSize(width = 18.dp, height = 19.dp),
    )

    NotificationModeGlyph.DEFAULT -> NotificationModeGlyphVisual(
        drawableRes = R.drawable.ic_topic_notification_inherit,
        size = DpSize(width = 14.dp, height = 19.dp),
    )

    NotificationModeGlyph.FOLLOW -> NotificationModeGlyphVisual(
        drawableRes = R.drawable.ic_topic_notification_follow,
        size = DpSize(width = 16.dp, height = 16.dp),
    )
}
