package ru.genesiscorporation.workspace.beta.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMessageContrastTest {
    @Test
    fun lightMessageTextMeetsWcagNormalTextContrast() {
        assertMessageContrast(LightWorkspaceColorsPalette)
    }

    @Test
    fun darkMessageTextMeetsWcagNormalTextContrast() {
        assertMessageContrast(DarkWorkspaceColorsPalette)
    }

    @Test
    fun lightApplicationTextTokensMeetWcagNormalTextContrast() {
        assertApplicationContrast(LightWorkspaceColorsPalette)
    }

    @Test
    fun darkApplicationTextTokensMeetWcagNormalTextContrast() {
        assertApplicationContrast(DarkWorkspaceColorsPalette)
    }

    private fun assertMessageContrast(colors: WorkspaceColorsPalette) {
        assertContrast(colors.textHeaders, colors.messageBackground, "message text")
        assertContrast(colors.textHeaders, colors.messageOwnBackground, "own message text")
        assertContrast(
            colors.messageSecondaryText,
            colors.messageBackground,
            "message secondary text",
        )
        assertContrast(
            colors.messageSecondaryText,
            colors.messageOwnBackground,
            "own message secondary text",
        )
        assertContrast(colors.messageTimeColor, colors.messageBackground, "message time")
        assertContrast(colors.messageTimeColor, colors.messageOwnBackground, "own message time")
        assertContrast(
            colors.markdownCodeText,
            colors.markdownCodeBackground,
            "inline code",
        )
        assertContrast(colors.messageOwnAccent, colors.messageOwnBackground, "own author")
        listOf(
            colors.messageAccentYellow,
            colors.messageAccentPink,
            colors.messageAccentPurple,
            colors.messageAccentGreen,
            colors.messageAccentBlue,
        ).forEachIndexed { index, accent ->
            assertContrast(accent, colors.messageBackground, "author accent $index")
        }
        assertContrast(
            colors.messageAccentBlue,
            colors.background,
            "first-unread marker",
        )
    }

    private fun assertApplicationContrast(colors: WorkspaceColorsPalette) {
        listOf(
            colors.background,
            colors.surface,
            colors.cardBackgroundBase,
            colors.cardBackgroundActive,
            colors.infoCardBackground,
        ).forEachIndexed { index, background ->
            assertContrast(colors.textHeaders, background, "primary text $index")
            assertContrast(colors.textAdditional50, background, "secondary text $index")
            assertContrast(colors.primary, background, "interactive text $index")
            assertContrast(colors.indicatorRed, background, "destructive text $index")
        }
        assertContrast(colors.onPrimary, colors.primary, "primary control text")
    }

    private fun assertContrast(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio", ratio >= MIN_NORMAL_TEXT_CONTRAST)
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
