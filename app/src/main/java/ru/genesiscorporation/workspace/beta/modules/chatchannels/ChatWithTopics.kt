package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.mapNotNull
import kotlin.collections.sortedByDescending
import kotlin.math.roundToInt

@Composable
fun ChatWithTopics(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    showDetail: Boolean,
    onShowDetailChange: (Boolean) -> Unit
) {
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val currentlySelectedStream by chatViewModel.currentlySelectedStream.collectAsState()
    val messageFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val scope = rememberCoroutineScope()
    val streamTopics by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val state by chatViewModel.queryState.collectAsStateWithLifecycle()

    val filteredSubscriptions = remember(searchQuery, streams, currentlySelectedFolder) {
        val folderItems = currentlySelectedFolder?.items
        val folderSubscriptions = if (folderItems != null) {
            folderItems.mapNotNull { folderItem ->
                streams.firstOrNull { it.uuid == folderItem.streamUuid }
            }
        } else {
            streams
        }
        if (searchQuery.isBlank()) {
            folderSubscriptions
        } else {
            folderSubscriptions.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    if (filteredSubscriptions.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (state is QueryState.Loading) {
                AnimatedGif(Modifier.size(80.dp))
            } else {
                Text("Список каналов пуст")
            }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val openOffsetPx =
                with(density) { 55.dp.toPx() }      // left edge when open
            val closedOffsetPx =
                screenWidthPx + 20                    // fully off-screen right
            val offsetX = remember { Animatable(closedOffsetPx) }
            // Animate open / close when showDetail changes
            LaunchedEffect(showDetail) {
                offsetX.animateTo(
                    targetValue = if (showDetail) openOffsetPx else closedOffsetPx,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(
                    items = filteredSubscriptions.sortedByDescending {
                        LocalDateTime.parse(
                            it.lastMessage?.createdAt ?: it.updatedAt, messageFormatter
                        )
                    }
                ) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.uuid == chatViewModel.currentlySelectedStream.collectAsState().value?.uuid) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(56.dp)
                                    .background(LocalWorkspaceColorsPalette.current.primary)
                            )
                        }
                        ChatChannel(
                            item,
                            chatViewModel,
                            showDetail,
                            currentlySelectedFolder,
                            onChatNumberToAddChange = { chatViewModel.onChatToAddChange(it) },
                            onClick = {
                                val defaultTopicUuid = item.defaultTopicUuid
                                if (defaultTopicUuid != null) {
                                    chatViewModel.currentStreamId = item.uuid
                                    navController.navigate(
                                        ChatFlow.ChatDialog(
                                            item.name,
                                            item.uuid,
                                            null,
                                            defaultTopicUuid,
                                            true,
                                            null
                                        )
                                    )
                                } else {
                                    scope.launch {
                                        onShowDetailChange(true)
                                        chatViewModel.updateSelectedChat(item)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            val scope = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth - 55.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .background(LocalWorkspaceColorsPalette.current.surface)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    val shouldOpen =
                                        offsetX.value < (openOffsetPx + closedOffsetPx) / 2f
                                    onShowDetailChange(shouldOpen)
                                    offsetX.animateTo(
                                        if (shouldOpen) openOffsetPx else closedOffsetPx
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    offsetX.snapTo(
                                        (offsetX.value + dragAmount)
                                            .coerceIn(openOffsetPx, closedOffsetPx)
                                    )
                                }
                            }
                        )
                    }
            ) {
                val selectedStream = currentlySelectedStream
                if (selectedStream != null) {
                    val topics = streamTopics[selectedStream.uuid]
                    if (topics?.isEmpty() ?: true) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state is QueryState.Loading) {
                                AnimatedGif(Modifier.size(80.dp))
                            } else {
                                Text("Список топиков пуст")
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            items(
                                items = topics.sortedByDescending {
                                    LocalDateTime.parse(
                                        it.lastMessage?.createdAt ?: it.updatedAt, messageFormatter
                                    )
                                }
                            ) { item ->
                                ChatTopic(
                                    chatViewModel,
                                    item,
                                    selectedStream,
                                    navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}