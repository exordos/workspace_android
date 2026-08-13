package ru.genesiscorporation.workspace.beta.modules.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isAllChatsFolder
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.modules.chatchannels.localizedTitle
import ru.genesiscorporation.workspace.beta.modules.chatdialog.forwardTopicLabel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
internal fun IncomingShareDialog(
    request: IncomingShareRequest,
    viewModel: ChatViewModel,
    conversationStateStore: ConversationStateStore,
    onDismiss: () -> Unit,
    onCommitted: (IncomingShareDraftTarget) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val colors = LocalWorkspaceColorsPalette.current
    val scope = rememberCoroutineScope()
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val topicsByStream by viewModel.streamTopics.collectAsStateWithLifecycle()
    val catalogState by viewModel.queryState.collectAsStateWithLifecycle()
    val baseUrl by viewModel.userViewModel.baseUrl.collectAsStateWithLifecycle()
    var selectedFolderUuid by rememberSaveable(request.requestId) {
        mutableStateOf<String?>(null)
    }
    var selectedStreamUuid by rememberSaveable(request.requestId) {
        mutableStateOf<String?>(null)
    }
    var selectedTopicUuid by rememberSaveable(request.requestId) {
        mutableStateOf<String?>(null)
    }
    var query by rememberSaveable(request.requestId) { mutableStateOf("") }
    var topicsLoading by remember(request.requestId) { mutableStateOf(false) }
    var submitting by remember(request.requestId) { mutableStateOf(false) }
    var error by remember(request.requestId) {
        mutableStateOf(request.validationError)
    }
    val availableFolders = remember(folders) {
        folders.distinctBy(FolderResponseData::uuid)
    }
    val usersByUuid = remember(users) {
        users.associateBy(UserResponseData::uuid)
    }
    val selectedFolder = availableFolders.firstOrNull {
        it.uuid == selectedFolderUuid
    }
    val availableStreams = remember(streams, selectedFolder) {
        incomingShareStreamsInFolder(
            streams = incomingShareStreams(streams),
            folder = selectedFolder,
        )
    }
    val selectedStream = incomingShareStreams(streams).firstOrNull {
        it.uuid == selectedStreamUuid
    }
    val selectedTopics = selectedStream
        ?.let { stream ->
            incomingShareTopics(
                stream,
                topicsByStream[stream.uuid].orEmpty(),
            )
        }
        .orEmpty()
    val selectedTopic = selectedTopics.firstOrNull {
        it.uuid == selectedTopicUuid
    }
    val target = resolveIncomingShareTarget(selectedStream, selectedTopic)
    val normalizedQuery = query.trim().lowercase(locale)
    val filteredStreams = remember(
        availableStreams,
        normalizedQuery,
        locale,
    ) {
        availableStreams.filter { stream ->
            normalizedQuery.isEmpty() ||
                stream.name.lowercase(locale).contains(normalizedQuery) ||
                stream.description.lowercase(locale).contains(normalizedQuery)
        }
    }
    val filteredTopics = remember(
        selectedTopics,
        normalizedQuery,
        locale,
    ) {
        selectedTopics.filter { topic ->
            normalizedQuery.isEmpty() ||
                topic.name.lowercase(locale).contains(normalizedQuery)
        }
    }

    LaunchedEffect(availableFolders) {
        if (
            selectedFolderUuid == null ||
            availableFolders.none { it.uuid == selectedFolderUuid }
        ) {
            selectedFolderUuid = availableFolders
                .firstOrNull(FolderResponseData::isAllChatsFolder)
                ?.uuid
                ?: availableFolders.firstOrNull()?.uuid
        }
    }

    LaunchedEffect(selectedStreamUuid) {
        val stream = selectedStream ?: return@LaunchedEffect
        selectedTopicUuid = null
        if (
            stream.isDirectProviderChat() &&
            !stream.defaultTopicUuid.isNullOrBlank()
        ) {
            selectedTopicUuid = stream.defaultTopicUuid
            return@LaunchedEffect
        }
        val cachedTopics = incomingShareTopics(
            stream,
            topicsByStream[stream.uuid].orEmpty(),
        )
        if (cachedTopics.isNotEmpty()) {
            if (stream.isDirectProviderChat()) {
                selectedTopicUuid = directIncomingTopicUuid(
                    stream,
                    cachedTopics,
                )
                if (selectedTopicUuid == null) {
                    error = "Личный чат не содержит основного топика"
                }
            }
            return@LaunchedEffect
        }
        topicsLoading = true
        error = request.validationError
        try {
            viewModel.loadTopics(stream)
            val loadedTopics = incomingShareTopics(
                stream,
                viewModel.streamTopics.value[stream.uuid].orEmpty(),
            )
            if (loadedTopics.isEmpty()) {
                error = "В выбранном чате нет доступных топиков"
            } else if (stream.isDirectProviderChat()) {
                selectedTopicUuid = directIncomingTopicUuid(
                    stream,
                    loadedTopics,
                )
                if (selectedTopicUuid == null) {
                    error = "Личный чат не содержит основного топика"
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            error = "Не удалось загрузить топики выбранного чата"
        } finally {
            topicsLoading = false
        }
    }

    fun commitToDraft() {
        if (submitting) return
        val destination = target ?: return
        submitting = true
        scope.launch {
            error = request.validationError
            val credentials = viewModel.userViewModel.repo
                .activeCredentialSnapshot()
            val ownerKey = credentials.ownerKey
            if (ownerKey.isNullOrBlank()) {
                error = "Активный аккаунт недоступен"
                submitting = false
                return@launch
            }
            when (
                val result = commitIncomingShareToDraft(
                    context = context,
                    request = request,
                    ownerKey = ownerKey,
                    target = destination,
                    repository = viewModel.userViewModel.repo,
                    conversationStateStore = conversationStateStore,
                )
            ) {
                is IncomingShareCommitResult.Accepted ->
                    onCommitted(destination)

                is IncomingShareCommitResult.Rejected ->
                    error = result.message
            }
            submitting = false
        }
    }

    fun leavePicker() {
        if (selectedStream == null) {
            onDismiss()
        } else {
            selectedStreamUuid = null
            selectedTopicUuid = null
            query = ""
            error = request.validationError
        }
    }

    BackHandler(enabled = !submitting, onBack = ::leavePicker)
    Surface(
        color = colors.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
                IncomingShareTopBar(
                    title = selectedStream?.name ?: "Поделиться",
                    backLabel = if (selectedStream == null) "Отмена" else "Назад",
                    enabled = !submitting && !topicsLoading,
                    onBack = ::leavePicker,
                )
                IncomingSharePreview(request)
                error?.let { message ->
                    Text(
                        text = message,
                        color = colors.indicatorRed,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
                if (
                    selectedStream == null ||
                    !selectedStream.isDirectProviderChat()
                ) {
                    IncomingShareSearchField(
                        value = query,
                        placeholder = if (selectedStream == null) {
                            "Найти"
                        } else {
                            "Найти топик"
                        },
                        enabled = !submitting && !topicsLoading,
                        onValueChange = {
                            query = it.take(MAX_SHARE_SEARCH_CHARS)
                        },
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                    )
                }
                if (selectedStream == null && availableFolders.isNotEmpty()) {
                    IncomingShareFolderTabs(
                        folders = availableFolders,
                        selectedFolderUuid = selectedFolderUuid,
                        enabled = !submitting,
                        onSelected = { folder ->
                            selectedFolderUuid = folder.uuid
                            selectedStreamUuid = null
                            selectedTopicUuid = null
                            query = ""
                            error = request.validationError
                        },
                    )
                }
                if (
                    (
                        selectedStream == null &&
                            catalogState is QueryState.Loading
                    ) ||
                    topicsLoading
                ) {
                    IncomingShareLoadingRow(
                        text = if (selectedStream == null) {
                            "Загружаю чаты…"
                        } else {
                            "Загружаю топики…"
                        },
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    if (selectedStream == null) {
                        if (
                            catalogState !is QueryState.Loading &&
                            filteredStreams.isEmpty()
                        ) {
                            item("empty-share-streams") {
                                EmptyShareDestination(
                                    if (normalizedQuery.isEmpty()) {
                                        "Нет доступных чатов"
                                    } else {
                                        "Чаты не найдены"
                                    },
                                )
                            }
                        }
                        items(
                            items = filteredStreams,
                            key = Stream::uuid,
                        ) { stream ->
                            ShareStreamDestinationRow(
                                stream = stream,
                                avatarUrn = incomingShareAvatarUrn(
                                    stream,
                                    usersByUuid,
                                ),
                                baseUrl = baseUrl.orEmpty(),
                                enabled = !submitting,
                                onClick = {
                                    query = ""
                                    error = request.validationError
                                    selectedStreamUuid = stream.uuid
                                },
                            )
                        }
                    } else if (!selectedStream.isDirectProviderChat()) {
                        if (!topicsLoading && filteredTopics.isEmpty()) {
                            item("empty-share-topics") {
                                EmptyShareDestination(
                                    if (normalizedQuery.isEmpty()) {
                                        "Нет доступных топиков"
                                    } else {
                                        "Топики не найдены"
                                    },
                                )
                            }
                        }
                        items(
                            items = filteredTopics,
                            key = TopicsResponseData::uuid,
                        ) { topic ->
                            ShareTopicDestinationRow(
                                title = forwardTopicLabel(
                                    topic,
                                    selectedTopics,
                                ),
                                subtitle = if (topic.isDefault) {
                                    "Основной топик"
                                } else if (topic.isDone) {
                                    "Завершён"
                                } else {
                                    "Топик"
                                },
                                selected = selectedTopicUuid == topic.uuid,
                                enabled = !submitting && !topicsLoading,
                                onClick = {
                                    selectedTopicUuid = topic.uuid
                                },
                            )
                        }
                    }
                }
                IncomingShareCommitButton(
                    enabled =
                        target != null &&
                            request.validationError == null &&
                            !topicsLoading &&
                            !submitting,
                    submitting = submitting,
                    onClick = ::commitToDraft,
                )
        }
    }
}

@Composable
private fun IncomingShareTopBar(
    title: String,
    backLabel: String,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(96.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            TextButton(
                onClick = onBack,
                enabled = enabled,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cardBackgroundActive),
            ) {
                Text(
                    text = backLabel,
                    color = if (enabled) {
                        colors.textHeaders
                    } else {
                        colors.textAdditional30
                    },
                    fontFamily = NavigationFontFamily,
                    fontSize = 14.sp,
                )
            }
        }
        Text(
            text = title,
            color = colors.textHeaders,
            fontFamily = NavigationFontFamily,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(96.dp))
    }
}

@Composable
private fun IncomingSharePreview(request: IncomingShareRequest) {
    val colors = LocalWorkspaceColorsPalette.current
    val contentLabel = when {
        request.text.isNotBlank() && request.attachmentUris.isNotEmpty() ->
            "Текст и файлов: ${request.attachmentUris.size}"

        request.attachmentUris.isNotEmpty() ->
            "Файлов: ${request.attachmentUris.size}"

        else -> "Текст"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackgroundBase)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = contentLabel,
            color = colors.primary,
            fontFamily = NavigationFontFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (request.text.isNotBlank()) {
            Text(
                text = request.text.replace('\n', ' '),
                color = colors.textHeaders,
                fontFamily = NavigationFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Сначала откроется черновик для проверки",
            color = colors.textAdditional50,
            fontFamily = NavigationFontFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun IncomingShareSearchField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.searchBackground)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontFamily = NavigationFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textAdditional30,
                            fontFamily = NavigationFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun IncomingShareFolderTabs(
    folders: List<FolderResponseData>,
    selectedFolderUuid: String?,
    enabled: Boolean,
    onSelected: (FolderResponseData) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(folders, key = FolderResponseData::uuid) { folder ->
            val selected = folder.uuid == selectedFolderUuid
            Column(
                modifier = Modifier
                    .height(48.dp)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelected(folder) },
                    )
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.localizedTitle(),
                        color = if (selected) {
                            colors.textHeaders
                        } else {
                            colors.textAdditional30
                        },
                        fontFamily = NavigationFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (selected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Medium
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (folder.unreadCount > 0) {
                        UnreadBadge(
                            count = folder.unreadCount,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(72.dp)
                        .background(
                            if (selected) {
                                colors.textHeaders
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun IncomingShareLoadingRow(text: String) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = colors.primary,
            strokeWidth = 2.dp,
        )
        Text(
            text = text,
            color = colors.textAdditional50,
            fontFamily = NavigationFontFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ShareStreamDestinationRow(
    stream: Stream,
    avatarUrn: String?,
    baseUrl: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val direct = stream.isDirectProviderChat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = avatarUrn,
            baseUrl = baseUrl,
            color = stream.color,
            name = stream.name,
            size = 40,
            hasPadding = false,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = stream.name,
                color = colors.textHeaders,
                fontFamily = NavigationFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (direct) {
                    "Личный чат"
                } else {
                    stream.description.ifBlank { "Канал" }
                },
                color = colors.textAdditional50,
                fontFamily = NavigationFontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_menu_chevron_right),
            contentDescription = "Выбрать ${stream.name}",
            tint = colors.iconBase,
            modifier = Modifier.size(20.dp),
        )
    }
}

internal fun incomingShareAvatarUrn(
    stream: Stream,
    usersByUuid: Map<String, UserResponseData>,
): String? {
    val streamAvatar = stream.avatar
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    if (!stream.isDirectProviderChat()) return streamAvatar

    return stream.directUserUuid
        ?.let(usersByUuid::get)
        ?.avatar
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: streamAvatar
}

@Composable
private fun ShareTopicDestinationRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.cardBackgroundActive)
                .semantics {
                    contentDescription = "Топик $title"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "#",
                color = colors.primary,
                fontFamily = NavigationFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontFamily = NavigationFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.textAdditional50,
                fontFamily = NavigationFontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primary,
                unselectedColor = colors.iconBase,
                disabledSelectedColor = colors.iconDisable,
                disabledUnselectedColor = colors.iconDisable,
            ),
        )
    }
}

@Composable
private fun IncomingShareCommitButton(
    enabled: Boolean,
    submitting: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.cardBackgroundActive,
            disabledContentColor = colors.textAdditional30,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(48.dp),
    ) {
        Text(
            text = if (submitting) {
                "Сохраняю…"
            } else {
                "Добавить в черновик"
            },
            fontFamily = NavigationFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyShareDestination(message: String) {
    Text(
        text = message,
        color = LocalWorkspaceColorsPalette.current.textAdditional50,
        fontFamily = NavigationFontFamily,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

internal fun incomingShareStreamsInFolder(
    streams: List<Stream>,
    folder: FolderResponseData?,
): List<Stream> {
    if (folder == null || folder.isAllChatsFolder()) return streams
    val includedStreamUuids = folder.items
        .asSequence()
        .map { it.streamUuid }
        .filter(String::isNotBlank)
        .toSet()
    return streams.filter { it.uuid in includedStreamUuids }
}

internal fun directIncomingTopicUuid(
    stream: Stream,
    topics: List<TopicsResponseData>,
): String? =
    stream.defaultTopicUuid
        ?.takeIf(String::isNotBlank)
        ?.takeIf { candidate -> topics.any { it.uuid == candidate } }
        ?: topics.firstOrNull(TopicsResponseData::isDefault)?.uuid

private const val MAX_SHARE_SEARCH_CHARS = 256
