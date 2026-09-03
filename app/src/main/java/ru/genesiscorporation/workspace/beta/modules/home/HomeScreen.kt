package ru.genesiscorporation.workspace.beta.modules.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.airbnb.lottie.model.content.RectangleShape
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.HomeFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.FullScreenError
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navController: NavHostController
) {
    val streamsQueryState by viewModel.streamsQueryState.collectAsStateWithLifecycle()
    val streams by viewModel.streams.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Моя активность",
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.background,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                    navigationIconContentColor = LocalWorkspaceColorsPalette.current.textHeaders
                )
            )
        },
        containerColor = LocalWorkspaceColorsPalette.current.background
    ) { innerPadding ->
        when (streamsQueryState) {
            QueryState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    AnimatedGif(Modifier.size(80.dp))
                }
            }

            is QueryState.Error -> {
                FullScreenError {
                    scope.launch {
                        viewModel.loadServerSettings()
                    }
                }
            }

            QueryState.Success -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp)
                ) {
                    val streamsUnreadCount = streams.map { it.unreadCount }.sum()
                    val streamsUnreadCountString = if (streamsUnreadCount > 0) "$streamsUnreadCount" else null
                    HomeMenuElement("Входящие", R.drawable.ic_home_inbounds, streamsUnreadCountString) {
                        navController.navigate(HomeFlow.HomeInbounds)
                    }
                    HomeMenuElement("Упоминания", R.drawable.ic_home_mentions) {
                        navController.navigate(HomeFlow.HomeMentions)
                    }
                    HomeMenuElement("Черновики", R.drawable.ic_home_drafts) {
                        navController.navigate(HomeFlow.HomeDrafts)
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
fun HomeMenuElement(
    name: String,
    imageId: Int,
    badgeString: String? = null,
    onTap: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(
                onClick = onTap
            )
            .background(LocalWorkspaceColorsPalette.current.cardBackgroundBase, RoundedCornerShape(8.dp))
            .padding(end = 8.dp)
    ) {
        Image(
            painter = painterResource(id = imageId),
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
        )
        Text(
            name,
            color = LocalWorkspaceColorsPalette.current.textHeaders,
            fontSize = 14.sp,
            fontFamily = InterFontFamily,
            modifier = Modifier.weight(1f)
        )
        if (badgeString != null) {
            val backgroundColor = LocalWorkspaceColorsPalette.current.noticeBase
            Text(
                text = badgeString,
                color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                fontSize = 12.sp,
                fontFamily = InterFontFamily,
                modifier = Modifier
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(100.dp)
                    )
                    .padding(horizontal = 8.dp)
            )
        }
    }
}