package ru.genesiscorporation.workspace.beta.modules.homedrafts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.HomeFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.creationbase.CreationBaseViewModel
import ru.genesiscorporation.workspace.beta.modules.home.HomeMenuElement
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.FullScreenError
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDraftsScreen(
    viewModel: HomeDraftsViewModel,
    navController: NavHostController
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val topicsPool by viewModel.topicsPool.collectAsStateWithLifecycle()
    val topicsQueryState by viewModel.topicsQueryState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Черновики",
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
                if (drafts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Черновиков нет",
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            items(
                                items = drafts.sortedByDescending {
                                    LocalDateTime.parse(
                                        it.updatedAt, dateFormatter
                                    )
                                }
                            ) { item ->
                                val topic = topicsPool.firstOrNull { it.uuid == item.topicUuid }
                                val stream = streams.firstOrNull { it.uuid == item.streamUuid }
                                if (topic != null && stream != null) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(12.dp)
                                            .clickable(
                                                onClick = {
                                                    navController.navigate(
                                                        HomeFlow.ChatDialog(
                                                            stream.name,
                                                            stream.uuid,
                                                            topic.name,
                                                            topic.uuid,
                                                            stream.isPrivate,
                                                            null
                                                        )
                                                    )
                                                }
                                            )
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Канал",
                                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                                fontSize = 14.sp,
                                                modifier = Modifier
                                                    .background(
                                                        LocalWorkspaceColorsPalette.current.surface,
                                                        RoundedCornerShape(100.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                            Text(
                                                stream.name,
                                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                topic.name,
                                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Text(
                                                item.payload.content,
                                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                                fontSize = 14.sp,
                                                fontFamily = InterFontFamily,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        viewModel.deleteCurrentTopicDraft(item)
                                                    }
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = Color.Transparent,
                                                    contentColor = LocalWorkspaceColorsPalette.current.indicatorRed
                                                )
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_item_delete),
                                                    contentDescription = "More"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {}
        }
    }
}