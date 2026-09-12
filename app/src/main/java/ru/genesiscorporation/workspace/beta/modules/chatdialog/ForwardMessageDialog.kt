package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.ForwardMessageRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun ForwardMessagesDialog(
    sources: List<MessageResponse>,
    client: WorkspaceAPIClient,
    repo: EventsRepository,
    userViewModel: UserViewModel,
    sessionStore: ForwardSessionStore,
    onDismiss: () -> Unit,
    onForwarded: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val colors = LocalWorkspaceColorsPalette.current
    val baseUrl by userViewModel.baseUrl.collectAsStateWithLifecycle()
    val currentUser by repo.currentUser.collectAsStateWithLifecycle()
    val cachedTopics by repo.streamTopics.collectAsStateWithLifecycle()
    val cachedBindings by repo.streamBindings.collectAsStateWithLifecycle()
    val users by repo.users.collectAsState()
    val folders by repo.folders.collectAsState()
    var streams by remember { mutableStateOf(forwardableStreams(repo.streams.value)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Int?>(null) }
    var selectedStream by remember { mutableStateOf<Stream?>(null) }
    var topics by remember { mutableStateOf(emptyList<TopicsResponseData>()) }
    var topicsLoading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<ForwardDestination?>(null) }
    var destinationLabel by remember { mutableStateOf("") }
    val currentUserUuid = currentUser?.uuid ?: userViewModel.userData?.uuid.orEmpty()
    val owner = "$baseUrl|$currentUserUuid"
    val forwarding = remember(sources, client, owner, sessionStore) {
        sessionStore.getOrCreate(owner) {
        val preparation = createForwardPreparation(context, sources, client, currentUserUuid, repo)
        MessageForwarding(
            sources = sources,
            currentUserUuid = currentUserUuid,
            post = { uuid, target, markdown ->
                client.performRequest(ForwardMessageRequest(uuid, target.streamUuid, target.topicUuid, markdown))
            },
            read = { client.performRequest(MessagesByIdsRequest(listOf(it))) },
            onConfirmed = {
                repo.addMessageToStreamTopic(it)
                repo.updateMessagesPool(listOf(it))
            },
            prepare = preparation::prepare,
        )
        }
    }
    val delivery by forwarding.state.collectAsState()

    suspend fun loadStreams() {
        loading = true
        error = null
        when (val response = client.performRequest(StreamsRequest())) {
            is ApiResult.Success -> streams = forwardableStreams(response.value)
            is ApiResult.Error -> error = R.string.forward_load_chats_failed
        }
        loading = false
    }

    fun chooseStream(stream: Stream) {
        if (!delivery.canEdit || topicsLoading) return
        selectedStream = stream
        topics = emptyList()
        query = ""
        error = null
        if (stream.isPrivate && !stream.directUserUuid.isNullOrBlank() &&
            stream.defaultTopicUuid?.let(::isCanonicalForwardUuid) == true
        ) {
            destination = ForwardDestination(stream.uuid, requireNotNull(stream.defaultTopicUuid))
            destinationLabel = forwardStreamTitle(stream, users)
        } else {
            scope.launch {
                topicsLoading = true
                when (val response = client.performRequest(TopicsRequest(listOf(stream.uuid)))) {
                    is ApiResult.Success -> {
                        topics = response.value.filter {
                            it.streamUuid == stream.uuid && isCanonicalForwardUuid(it.uuid)
                        }.distinctBy(TopicsResponseData::uuid).sortedWith(
                            compareByDescending<TopicsResponseData> { it.isDefault }.thenBy { it.name.lowercase() },
                        )
                        repo.addStreamTopics(stream.uuid, topics)
                        if (stream.isPrivate && !stream.directUserUuid.isNullOrBlank()) {
                            topics.singleOrNull { it.isDefault }?.let { topic ->
                                destination = ForwardDestination(stream.uuid, topic.uuid)
                                destinationLabel = forwardStreamTitle(stream, users)
                            }
                        }
                    }
                    is ApiResult.Error -> error = R.string.forward_load_topics_failed
                }
                topicsLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadStreams() }
    LaunchedEffect(delivery.status) {
        if (delivery.status == ForwardDeliveryStatus.COMPLETED) {
            if (forwarding.sources.map { it.uuid } == sources.map { it.uuid }) onForwarded() else onDismiss()
        }
    }
    val back = {
        if (!delivery.busy) {
            if (selectedStream != null && delivery.canEdit) {
                selectedStream = null
                topics = emptyList()
                query = ""
                error = null
            } else onDismiss()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.background).pointerInput(Unit) { detectTapGestures(onTap = {}) }) {
        BackHandler(enabled = true, onBack = back)
        Column(
            Modifier.fillMaxSize().background(colors.background),
        ) {
            val memberIds = selectedStream?.uuid?.let { cachedBindings[it] }?.map { it.userUuid }?.distinct()
            ForwardRecipientPicker(
                streams = streams,
                folders = folders,
                users = users,
                selectedStream = selectedStream,
                topics = topics,
                query = query,
                selectedFolder = selectedFolder,
                loading = loading || topicsLoading,
                enabled = delivery.canEdit,
                error = (error ?: delivery.error)?.let { stringResource(it) },
                baseUrl = baseUrl.orEmpty(),
                client = client,
                onCancel = { if (!delivery.busy) onDismiss() },
                onBack = back,
                onSearch = { query = it.take(200) },
                onFolder = { selectedFolder = it },
                onStream = ::chooseStream,
                onTopic = { topic ->
                    destination = ForwardDestination(topic.streamUuid, topic.uuid)
                    destinationLabel = "${forwardStreamTitle(requireNotNull(selectedStream), users)} · ${topic.name}"
                },
                onRetry = {
                    if (selectedStream != null) chooseStream(requireNotNull(selectedStream))
                    else scope.launch { loadStreams() }
                },
                modifier = Modifier.weight(1f),
                participantCount = memberIds?.size,
                onlineCount = memberIds?.takeIf { ids -> ids.all { id -> users.any { it.uuid == id } } }
                    ?.count { id -> users.first { it.uuid == id }.status == "active" },
            )
            if (!delivery.canEdit) {
                forwarding.pendingDestination?.let { pending ->
                    val chat = streams.firstOrNull { it.uuid == pending.streamUuid }
                    val topic = cachedTopics[pending.streamUuid]?.firstOrNull { it.uuid == pending.topicUuid }
                    Text(
                        pluralStringResource(
                            R.plurals.forward_message_destination, forwarding.sources.size, forwarding.sources.size,
                            (chat?.let { forwardStreamTitle(it, users) } ?: stringResource(R.string.forward_selected_chat)) +
                                (topic?.let { " · ${it.name}" } ?: ""),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = colors.textAdditional50, fontSize = 13.sp, fontFamily = InterFontFamily,
                    )
                }
            }
            if (delivery.busy) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.forward_verifying), Modifier.padding(start = 12.dp), color = colors.textHeaders)
                }
            } else if (delivery.status == ForwardDeliveryStatus.UNCERTAIN) {
                ForwardUncertainActions(
                    onVerify = { scope.launch { forwarding.verify() } },
                    onAbandon = { if (sessionStore.abandon(owner, forwarding)) onDismiss() },
                )
            }
        }
        destination?.let { target ->
            val cancelDestination = {
                destination = null
                if (selectedStream?.isPrivate == true && !selectedStream?.directUserUuid.isNullOrBlank()) {
                    selectedStream = null
                }
            }
            AlertDialog(
                onDismissRequest = { if (!delivery.busy) cancelDestination() },
                containerColor = colors.messageBackground,
                title = { Text(stringResource(R.string.forward_confirm_title), color = colors.textHeaders, fontFamily = InterFontFamily) },
                text = {
                    Text(pluralStringResource(R.plurals.forward_message_destination, forwarding.sources.size, forwarding.sources.size, destinationLabel),
                        color = colors.textHeaders, fontFamily = InterFontFamily)
                },
                dismissButton = {
                    TextButton(onClick = cancelDestination) { Text(stringResource(R.string.message_delete_cancel), color = colors.textHeaders) }
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("forward-confirm"),
                        onClick = {
                            destination = null
                            scope.launch { forwarding.send(target) }
                        },
                        enabled = delivery.canEdit,
                    ) { Text(stringResource(R.string.messages_forward), color = colors.textHeaders) }
                },
            )
        }
    }
}

@Composable
internal fun ForwardUncertainActions(onVerify: () -> Unit, onAbandon: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    var confirmAbandon by remember { mutableStateOf(false) }
    TextButton(onClick = onVerify, modifier = Modifier.fillMaxWidth().testTag("forward-verify")) {
        Text(stringResource(R.string.forward_verify_again), color = colors.textHeaders)
    }
    TextButton(
        onClick = { confirmAbandon = true },
        modifier = Modifier.fillMaxWidth().testTag("forward-abandon"),
    ) {
        Text(stringResource(R.string.forward_stop_verifying), color = colors.textHeaders)
    }
    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            containerColor = colors.messageBackground,
            title = { Text(stringResource(R.string.forward_stop_verifying_title), color = colors.textHeaders, fontFamily = InterFontFamily) },
            text = {
                Text(
                    stringResource(R.string.forward_stop_verifying_body),
                    color = colors.textHeaders, fontFamily = InterFontFamily,
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }, modifier = Modifier.testTag("forward-abandon-cancel")) {
                    Text(stringResource(R.string.message_delete_cancel), color = colors.textHeaders)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmAbandon = false; onAbandon() },
                    modifier = Modifier.testTag("forward-abandon-confirm"),
                ) { Text(stringResource(R.string.forward_stop_verifying), color = colors.textHeaders) }
            },
        )
    }
}

@Composable
internal fun ForwardRecipientPicker(
    streams: List<Stream>,
    folders: List<FolderResponseData>,
    users: List<UserResponseData>,
    selectedStream: Stream?,
    topics: List<TopicsResponseData>,
    query: String,
    selectedFolder: String?,
    loading: Boolean,
    enabled: Boolean,
    error: String?,
    baseUrl: String,
    client: WorkspaceAPIClient?,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onFolder: (String?) -> Unit,
    onStream: (Stream) -> Unit,
    onTopic: (TopicsResponseData) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    participantCount: Int? = null,
    onlineCount: Int? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val actualFolders = folders.filter { it.systemType != "all" && it.items.isNotEmpty() }
    val folderIds = folders.firstOrNull { it.uuid == selectedFolder }?.items?.map { it.streamUuid }?.toSet()
    val visibleStreams = streams.filter { stream ->
        (folderIds == null || stream.uuid in folderIds) &&
            (selectedStream != null || forwardStreamTitle(stream, users).contains(query, ignoreCase = true))
    }
    val railState = rememberLazyListState()
    LaunchedEffect(selectedStream?.uuid, visibleStreams.map(Stream::uuid)) {
        val selectedUuid = selectedStream?.uuid ?: return@LaunchedEffect
        val selectedIndex = visibleStreams.indexOfFirst { it.uuid == selectedUuid }
        if (selectedIndex >= 0 && railState.layoutInfo.visibleItemsInfo.none { it.key == selectedUuid }) {
            railState.scrollToItem(selectedIndex)
        }
    }
    Column(modifier.fillMaxWidth().testTag("forward-recipient-picker")) {
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 94.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (selectedStream == null) stringResource(R.string.messages_forward) else forwardStreamTitle(selectedStream, users),
                    color = colors.textHeaders, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium,
                    fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                if (selectedStream != null && participantCount != null) {
                    val participants = pluralStringResource(R.plurals.participants_count, participantCount, participantCount)
                    Text(
                        text = if (onlineCount != null) stringResource(R.string.forward_members_online, participants, onlineCount) else participants,
                        color = colors.textAdditional50, fontFamily = InterFontFamily,
                        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ForwardSmallButton(stringResource(R.string.message_delete_cancel), onCancel, Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        }
        Row(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(36.dp)
                .background(colors.searchBackground, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_message_search), contentDescription = null, tint = colors.textAdditional50, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query,
                onValueChange = onSearch,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f).padding(start = 10.dp).testTag("forward-search"),
                textStyle = TextStyle(color = colors.textHeaders, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text(stringResource(R.string.forward_search), color = colors.textAdditional50, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontFamily = InterFontFamily)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(32.dp)) {
            ForwardFolderTab(stringResource(R.string.forward_all_chats), selectedFolder == null, enabled, streams.sumOf { it.unreadCount }) { onFolder(null) }
            actualFolders.forEach { folder ->
                ForwardFolderTab(folder.title, selectedFolder == folder.uuid, enabled, folder.unreadCount) { onFolder(folder.uuid) }
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        error?.let {
            Text(it, Modifier.padding(12.dp), color = colors.indicatorRed, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontFamily = InterFontFamily)
            if (enabled) TextButton(onClick = onRetry) { Text(stringResource(R.string.forward_retry_loading)) }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (selectedStream == null) {
            if (visibleStreams.isEmpty()) ForwardEmpty(stringResource(R.string.forward_no_chats), Modifier.weight(1f))
            else LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                contentPadding = PaddingValues(vertical = 20.dp),
            ) {
                items(visibleStreams, key = Stream::uuid) { stream ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button) { onStream(stream) }
                            .testTag("forward-stream-${stream.uuid}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ForwardAvatar(stream, users, baseUrl, client, Modifier.size(40.dp))
                        Text(forwardStreamTitle(stream, users), Modifier.weight(1f).padding(start = 12.dp),
                            color = colors.textHeaders, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ForwardUnreadBadge(stream.unreadCount)
                    }
                    HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.width(74.dp), state = railState, contentPadding = PaddingValues(vertical = 20.dp)) {
                    items(visibleStreams, key = Stream::uuid) { stream ->
                        Box(
                            Modifier.padding(horizontal = 12.dp).size(52.dp, 64.dp)
                                .background(if (stream.uuid == selectedStream.uuid) colors.messageBackground else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled, role = Role.Button) { onStream(stream) }
                                .semantics {
                                    selected = stream.uuid == selectedStream.uuid
                                    contentDescription = forwardStreamTitle(stream, users)
                                }
                                .testTag("forward-rail-${stream.uuid}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            ForwardAvatar(stream, users, baseUrl, client, Modifier.size(40.dp))
                            if (stream.unreadCount > 0) {
                                Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp)) {
                                    ForwardUnreadBadge(stream.unreadCount)
                                }
                            }
                        }
                    }
                }
                VerticalDivider(color = colors.divider, thickness = 0.5.dp)
                val visibleTopics = topics.filter { it.name.contains(query, ignoreCase = true) }
                if (visibleTopics.isEmpty()) ForwardEmpty(stringResource(R.string.forward_no_topics), Modifier.weight(1f))
                else LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 20.dp)) {
                    items(visibleTopics, key = TopicsResponseData::uuid) { topic ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .background(if (topic.isDefault) colors.messageBackground else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled, role = Role.Button) { onTopic(topic) }
                                .padding(horizontal = 12.dp).testTag("forward-topic-${topic.uuid}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(3.dp).height(46.dp).background(Color(0xFF000000L or topic.color.toLong()), RoundedCornerShape(2.dp)))
                            Text("# ${topic.name}", Modifier.weight(1f).padding(start = 10.dp),
                                color = colors.textHeaders, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ForwardUnreadBadge(topic.unreadCount)
                        }
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

internal fun forwardStreamTitle(stream: Stream, users: List<UserResponseData>): String =
    users.firstOrNull { it.uuid == stream.directUserUuid }?.displayableName() ?: stream.name

@Composable
private fun ForwardAvatar(stream: Stream, users: List<UserResponseData>, baseUrl: String, client: WorkspaceAPIClient?, modifier: Modifier) {
    val user = users.firstOrNull { it.uuid == stream.directUserUuid }
    Avatar(user?.avatar?.takeIf { client != null }, baseUrl, client?.authHeaders().orEmpty(),
        stream.color, forwardStreamTitle(stream, users), modifier)
}

@Composable
private fun ForwardFolderTab(title: String, selected: Boolean, enabled: Boolean, unread: Int, onClick: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        Modifier.padding(horizontal = 12.dp)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .drawBehind {
                if (selected) {
                    val thickness = 1.dp.toPx()
                    drawLine(
                        color = colors.textHeaders,
                        start = Offset(0f, size.height - thickness / 2),
                        end = Offset(size.width, size.height - thickness / 2),
                        strokeWidth = thickness,
                    )
                }
            },
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (selected) colors.textHeaders else colors.textAdditional50, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
            if (unread > 0) { Spacer(Modifier.width(4.dp)); ForwardUnreadBadge(unread) }
        }
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun ForwardUnreadBadge(count: Int) {
    if (count <= 0) return
    val colors = LocalWorkspaceColorsPalette.current
    Text(count.toString(), Modifier.background(colors.noticeCounterBadge, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
        color = colors.noticeOnBadge, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.sp, fontFamily = InterFontFamily)
}

@Composable
private fun ForwardSmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, Modifier.background(colors.messageBackground, RoundedCornerShape(8.dp))
            .border(1.dp, colors.indicatorGrey, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
            color = colors.textHeaders, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    }
}

@Composable
private fun ForwardEmpty(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, Modifier.padding(16.dp), color = LocalWorkspaceColorsPalette.current.textAdditional50, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    }
}
