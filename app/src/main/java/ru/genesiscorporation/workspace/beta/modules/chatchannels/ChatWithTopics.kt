package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.AnimatedGif
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import java.time.OffsetDateTime

@Composable
fun ChatWithTopics(
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    showDetail: Boolean,
    onShowDetailChange: (Boolean) -> Unit,
) {
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val currentlySelectedFolder by chatViewModel.currentlySelectedFolder.collectAsState()
    val currentlySelectedStream by chatViewModel.currentlySelectedStream.collectAsState()
    val streamTopics by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val state by chatViewModel.queryState.collectAsStateWithLifecycle()
    val baseUrl by chatViewModel.userViewModel.baseUrl.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val visibleStreams = remember(searchQuery, streams, currentlySelectedFolder) {
        val folderItems = currentlySelectedFolder?.items
        val folderStreams = if (folderItems == null || currentlySelectedFolder?.systemType == "all") {
            streams
        } else {
            folderItems.mapNotNull { item ->
                streams.firstOrNull { it.uuid == item.streamUuid }
            }
        }
        folderStreams
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .sortedByDescending(::streamSortTime)
    }

    if (visibleStreams.isEmpty()) {
        EmptyMessengerState(
            loading = state is QueryState.Loading,
            text = if (searchQuery.isBlank()) "Список чатов пуст" else "Ничего не найдено",
        )
        return
    }

    val selectedStream = currentlySelectedStream
    if (!showDetail || selectedStream == null) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(visibleStreams, key = { it.uuid }) { stream ->
                ChatChannel(
                    item = stream,
                    viewModel = chatViewModel,
                    baseUrl = baseUrl.orEmpty(),
                    showDetail = false,
                    currentlySelectedFolder = currentlySelectedFolder,
                    onChatNumberToAddChange = chatViewModel::onChatToAddChange,
                    onClick = {
                        val defaultTopic = stream.defaultTopicUuid
                        if (stream.isPrivate && defaultTopic != null) {
                            navController.navigate(
                                ChatFlow.ChatDialog(
                                    stream.name,
                                    stream.uuid,
                                    null,
                                    defaultTopic,
                                    true,
                                    null,
                                ),
                            )
                        } else {
                            scope.launch {
                                chatViewModel.updateSelectedChat(stream)
                                onShowDetailChange(true)
                            }
                        }
                    },
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 2.dp),
    ) {
        StreamRail(
            streams = visibleStreams,
            selected = selectedStream,
            baseUrl = baseUrl.orEmpty(),
            onSelected = { stream ->
                scope.launch {
                    chatViewModel.updateSelectedChat(stream)
                }
            },
        )
        val topics = streamTopics[selectedStream.uuid].orEmpty()
        if (topics.isEmpty()) {
            EmptyMessengerState(
                loading = state is QueryState.Loading,
                text = "Список топиков пуст",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    topics.sortedByDescending {
                        parseTime(it.lastMessage?.createdAt ?: it.updatedAt)
                    },
                    key = { it.uuid },
                ) { topic ->
                    ChatTopic(
                        viewModel = chatViewModel,
                        item = topic,
                        stream = selectedStream,
                        navController = navController,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamRail(
    streams: List<Stream>,
    selected: Stream,
    baseUrl: String,
    onSelected: (Stream) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    LazyColumn(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(streams, key = { it.uuid }) { stream ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .background(
                            if (stream.uuid == selected.uuid) colors.textHeaders
                            else androidx.compose.ui.graphics.Color.Transparent,
                        ),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(stream) }
                        .padding(vertical = 3.dp)
                        .height(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(
                        avatarUrn = streamAvatar(stream),
                        baseUrl = baseUrl,
                        color = stream.color,
                        name = stream.name,
                        size = 40,
                        hasPadding = false,
                    )
                    if (stream.unreadCount > 0) {
                        UnreadBadge(
                            count = stream.unreadCount,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessengerState(
    loading: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            AnimatedGif(Modifier.size(72.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    color = colors.textAdditional50,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

private fun streamAvatar(stream: Stream): String? =
    if (stream.isPrivate) stream.lastMessage?.user?.avatar ?: stream.avatar else stream.avatar

private fun streamSortTime(stream: Stream): Instant =
    parseTime(stream.lastMessage?.createdAt ?: stream.updatedAt)

internal fun parseTime(value: String?): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrDefault(Instant.EPOCH)
