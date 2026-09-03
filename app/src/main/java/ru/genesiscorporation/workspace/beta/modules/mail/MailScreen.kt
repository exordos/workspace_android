package ru.genesiscorporation.workspace.beta.modules.mail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.creationbase.CreationBaseViewModel
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(
    viewModel: MailViewModel,
    navController: NavHostController
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(LocalWorkspaceColorsPalette.current.cardBackgroundBase),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_calendar_tab),
                    contentDescription = null,
                    tint = LocalWorkspaceColorsPalette.current.primary,
                    modifier = Modifier
                        .size(52.dp)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "ПОЧТА",
                color = LocalWorkspaceColorsPalette.current.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Уже скоро!",
                color = LocalWorkspaceColorsPalette.current.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "В разработке",
                color = LocalWorkspaceColorsPalette.current.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalWorkspaceColorsPalette.current.cardBackgroundBase)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    "Готовим удобную работу с письмами прямо в Workspace",
                    color = LocalWorkspaceColorsPalette.current.textAdditional50,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}