package ru.genesiscorporation.workspace.beta.modules.comingsoon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

enum class ComingSoonDestination(
    val displayName: String,
    val description: String,
    @get:DrawableRes val iconRes: Int,
) {
    CALENDAR(
        displayName = "Календарь",
        description = "Готовим встречи и расписание в одном месте.",
        iconRes = R.drawable.ic_nav_calendar,
    ),
    MAIL(
        displayName = "Почта",
        description = "Готовим удобную работу с письмами прямо в Workspace.",
        iconRes = R.drawable.ic_mail,
    ),
}

@Composable
fun WorkspaceComingSoonScreen(
    destination: ComingSoonDestination,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.22f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(colors.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = destination.displayName,
                        tint = colors.primary,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = destination.displayName.uppercase(),
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Уже скоро!",
                color = colors.textHeaders,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "В разработке!",
                color = colors.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardBackgroundBase)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = destination.description,
                    color = colors.textAdditional50,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
