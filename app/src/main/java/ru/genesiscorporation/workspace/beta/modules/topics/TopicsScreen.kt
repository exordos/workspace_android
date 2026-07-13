package ru.genesiscorporation.workspace.beta.modules.topics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chatchannels.TopicHeader
import ru.genesiscorporation.workspace.beta.modules.chatdialog.formatHHmm
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    topicsViewModel: TopicsViewModel,
    navController: NavHostController
) {
    val subscriptions by topicsViewModel.subscriptions.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val cameBack = backStackEntry?.savedStateHandle
        ?.getStateFlow("from_detail_back", false)
        ?.collectAsState()

    LaunchedEffect(cameBack?.value) {
        if (cameBack?.value == true) {
            topicsViewModel.currentTopicName = ""
            backStackEntry?.savedStateHandle?.set("from_detail_back", false) // consume one-shot event
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocalWorkspaceColorsPalette.current.surface,
                    titleContentColor = LocalWorkspaceColorsPalette.current.textHeaders,
                ),
                title = {
                    Text(topicsViewModel.channelName)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .background(LocalWorkspaceColorsPalette.current.surface)
            ) {
                if (subscriptions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading"
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(items = subscriptions) { item ->
                            ChatOldTopic(topicsViewModel,item, navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatOldTopic(
    topicsViewModel: TopicsViewModel,
    item: TopicHeader,
    navController: NavHostController
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(LocalWorkspaceColorsPalette.current.chatHeaderBackground)
            .padding(start = 16.dp)
            .clickable(
                onClick = {
                    topicsViewModel.currentTopicName = item.title
                    navController.navigate(ChatFlow.ChatDialog(item.channelName, item.channelId, item.title, item.uuid,false, topicsViewModel.channelStreamId.toInt()))
                }
            )
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val lastMessage = item.lastMessage
                if (lastMessage != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = lastMessage.timestamp.formatHHmm(),
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 12.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastMessage = item.lastMessage
                if (lastMessage != null) {
                    Text(
                        text = lastMessage.content,
                        color = LocalWorkspaceColorsPalette.current.textAdditional50,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (item.unreadCount > 0) {
                    Text(
                        text = "${item.unreadCount}",
                        color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                color = LocalWorkspaceColorsPalette.current.noticeCounterBadge,
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}