package ru.genesiscorporation.workspace.beta.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R

val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_regular, weight = FontWeight.Normal),
    Font(R.font.montserrat_medium, weight = FontWeight.Medium),
    Font(R.font.montserrat_semibold, weight = FontWeight.SemiBold),
)

// Figma uses Helvetica Neue Medium for navigation labels. Android's sans-serif
// family is the platform-safe metric fallback for that licensed typeface.
val NavigationFontFamily = FontFamily.SansSerif

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)
