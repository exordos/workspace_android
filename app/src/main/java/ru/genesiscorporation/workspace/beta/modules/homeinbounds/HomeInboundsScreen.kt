package ru.genesiscorporation.workspace.beta.modules.homeinbounds

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.HomeFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.creationbase.CreationBaseViewModel
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.FullScreenError
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeInboundsScreen(
    viewModel: HomeInboundsViewModel,
    navController: NavHostController
) {
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val streamTopics by viewModel.streamTopics.collectAsStateWithLifecycle()
    val topicsQueryState by viewModel.topicsQueryState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Входящие",
                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back),
                            contentDescription = "Back"
                        )
                    }
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
        when (topicsQueryState) {
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
                        viewModel.loadTopicsIfNeeded()
                    }
                }
            }
            QueryState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        val unreadStreams = streams.filter { it.unreadCount > 0 }
                        items(
                            items = unreadStreams
                        ) { item ->
                            StreamInboundView(item, viewModel, navController)
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
fun StreamInboundView(
    stream: Stream,
    viewModel: HomeInboundsViewModel,
    navController: NavHostController
) {
    val streamTopics by viewModel.streamTopics.collectAsStateWithLifecycle()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .background(LocalWorkspaceColorsPalette.current.cardBackgroundBase, RoundedCornerShape(8.dp))
            .padding(end = 12.dp, bottom = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarUrn = stream.directUser?.avatar
            Avatar(
                avatarUrn,
                viewModel.client.userViewModel.baseUrl.value ?: "",
                viewModel.client.authHeaders(),
                stream.color,
                stream.name,
                Modifier
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                    .size(40.dp)
            )
            Text(
                text = stream.name,
                color = LocalWorkspaceColorsPalette.current.textHeaders,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val unreadCount =
                if (stream.activeUnreadCount > 0) stream.activeUnreadCount else stream.passiveUnreadCount
            if (stream.unreadCount > 0) {
                val backgroundColor =
                    if (stream.activeUnreadCount > 0) LocalWorkspaceColorsPalette.current.noticeBase else LocalWorkspaceColorsPalette.current.noticeDisable
                Text(
                    text = "${unreadCount}",
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
        val unreadTopics = streamTopics[stream.uuid]?.filter { it.unreadCount > 0 }
        if (unreadTopics != null) {
            for (unreadTopic in unreadTopics) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = LocalWorkspaceColorsPalette.current.divider
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    navController.navigate(
                                        HomeFlow.ChatDialog(
                                            stream.name,
                                            stream.uuid,
                                            unreadTopic.name,
                                            unreadTopic.uuid,
                                            stream.isPrivate,
                                            null
                                        )
                                    )
                                }
                            )
                        ,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .width(3.dp)
                                .height(47.dp)
                                .background(Color(0xFF000000 or unreadTopic.color.toLong()))
                        )
                        Text(
                            text = unreadTopic.name,
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp)
                                .weight(1f),
                            style = TextStyle(textDecoration = if (unreadTopic.isDone) TextDecoration.LineThrough else TextDecoration.None)
                        )

                        if (unreadTopic.unreadCount > 0) {
                            Text(
                                text = "${unreadTopic.unreadCount}",
                                color = LocalWorkspaceColorsPalette.current.noticeOnBadge,
                                fontSize = 14.sp,
                                fontFamily = InterFontFamily,
                                modifier = Modifier
                                    .background(
                                        color = if (unreadTopic.notificationMode == "mute") LocalWorkspaceColorsPalette.current.noticeDisable else LocalWorkspaceColorsPalette.current.noticeBase,
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}