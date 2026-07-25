package ru.genesiscorporation.workspace.beta.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8138),
    secondary = Color(0xFFFFCC00),
    tertiary = Color(0xFFF458D2),
    background = Color(0xFF1B1B1D),
    surface = Color(0xFF242426),
    onPrimary = Color(0xFF1B1B1D),
    onBackground = Color(0xFFF8F8F9),
    onSurface = Color(0xFFF8F8F9),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE96520),
    secondary = Color(0xFFFFC400),
    tertiary = Color(0xFFD43DB2),
    background = Color(0xFFF6F6F8),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF1B1B1D),
    onBackground = Color(0xFF1B1B1D),
    onSurface = Color(0xFF1B1B1D),
)

@Composable
fun WokspaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customColorsPalette =
        if (darkTheme) DarkWorkspaceColorsPalette
        else LightWorkspaceColorsPalette

    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalWorkspaceColorsPalette provides customColorsPalette // our custom palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.bodyLarge,
                content = content,
            )
        }
    }
}
