package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthContrastTest {
    @Test
    fun lightAuthenticationTokensMeetContrastRequirements() {
        assertAuthContrast(LightAuthColors)
    }

    @Test
    fun darkAuthenticationTokensMeetContrastRequirements() {
        assertAuthContrast(DarkAuthColors)
    }

    private fun assertAuthContrast(colors: AuthColors) {
        assertContrast(colors.text, colors.background, "primary text")
        assertContrast(colors.text, colors.field, "field text")
        assertContrast(colors.mutedText, colors.background, "secondary text")
        assertContrast(colors.mutedText, colors.field, "field placeholder")
        assertContrast(colors.labelText, colors.background, "field label")
        assertContrast(colors.error, colors.background, "field error")
        assertContrast(
            colors.onErrorContainer,
            colors.errorContainer,
            "error banner",
        )
        assertContrast(Color(0xFF171719), colors.accent, "primary button")
        assertContrast(
            colors.onDisabled,
            colors.disabled,
            "disabled button",
            minimum = MIN_NON_TEXT_CONTRAST,
        )
    }

    private fun assertContrast(
        foreground: Color,
        background: Color,
        label: String,
        minimum: Double = MIN_NORMAL_TEXT_CONTRAST,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio", ratio >= minimum)
    }
}

private fun contrastRatio(first: Color, second: Color): Double {
    val lighter = maxOf(first.relativeLuminance(), second.relativeLuminance())
    val darker = minOf(first.relativeLuminance(), second.relativeLuminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double =
    0.2126 * red.linearized() +
        0.7152 * green.linearized() +
        0.0722 * blue.linearized()

private fun Float.linearized(): Double {
    val component = toDouble()
    return if (component <= 0.04045) {
        component / 12.92
    } else {
        Math.pow((component + 0.055) / 1.055, 2.4)
    }
}

private const val MIN_NORMAL_TEXT_CONTRAST = 4.5
private const val MIN_NON_TEXT_CONTRAST = 3.0
