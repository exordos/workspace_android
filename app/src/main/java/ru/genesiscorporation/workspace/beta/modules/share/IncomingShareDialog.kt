package ru.genesiscorporation.workspace.beta.modules.share

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.modules.chatdialog.forwardTopicLabel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun IncomingShareDialog(
    request: IncomingShareRequest,
    viewModel: ChatViewModel,
    conversationStateStore: ConversationStateStore,
    onDismiss: () -> Unit,
    onCommitted: (IncomingShareDraftTarget) -> Unit,
) {
    val context = LocalContext.current
    val compactHeight =
        LocalConfiguration.current.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
    val locale = LocalLocale.current.platformLocale
    val colors = LocalWorkspaceColorsPalette.current
    val scope = rememberCoroutineScope()
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by viewModel.streamTopics.collectAsStateWithLifecycle()
    val catalogState by viewModel.queryState.collectAsStateWithLifecycle()
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
    val availableStreams = remember(streams) {
        incomingShareStreams(streams)
    }
    val selectedStream = availableStreams.firstOrNull {
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
                stream.name.lowercase(locale)
                    .contains(normalizedQuery) ||
                stream.description.lowercase(locale)
                    .contains(normalizedQuery)
        }
    }
    val filteredTopics = remember(
        selectedTopics,
        normalizedQuery,
        locale,
    ) {
        selectedTopics.filter { topic ->
            normalizedQuery.isEmpty() ||
                topic.name.lowercase(locale)
                    .contains(normalizedQuery)
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

    AlertDialog(
        onDismissRequest = {
            if (!submitting) onDismiss()
        },
        title = {
            Text(
                if (compactHeight) {
                    "Поделиться"
                } else if (selectedStream == null) {
                    "Поделиться в Workspace"
                } else {
                    "Выберите топик"
                },
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    if (compactHeight) 5.dp else 10.dp,
                ),
            ) {
                IncomingSharePreview(request, compactHeight)
                error?.let { message ->
                    Text(
                        text = message,
                        color = colors.indicatorRed,
                        fontSize = 13.sp,
                    )
                }
                if (selectedStream != null) {
                    SelectedShareStreamHeader(
                        stream = selectedStream,
                        enabled = !submitting && !topicsLoading,
                        onChange = {
                            selectedStreamUuid = null
                            selectedTopicUuid = null
                            query = ""
                            error = request.validationError
                        },
                    )
                }
                if (
                    selectedStream == null ||
                    !selectedStream.isDirectProviderChat()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it.take(MAX_SHARE_SEARCH_CHARS)
                        },
                        enabled = !submitting && !topicsLoading,
                        label = {
                            Text(
                                if (selectedStream == null) {
                                    "Найти чат"
                                } else {
                                    "Найти топик"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (
                    (
                        selectedStream == null &&
                            catalogState is QueryState.Loading
                    ) ||
                    topicsLoading
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = if (selectedStream == null) {
                                "Загружаю чаты…"
                            } else {
                                "Загружаю топики…"
                            },
                            color = colors.textAdditional50,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = if (compactHeight) 92.dp else 0.dp,
                            max = if (compactHeight) 150.dp else 330.dp,
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
                            ShareDestinationRow(
                                title = stream.name,
                                subtitle = if (stream.isDirectProviderChat()) {
                                    "Личный чат"
                                } else {
                                    stream.description.ifBlank { "Канал" }
                                },
                                selected = false,
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
                            ShareDestinationRow(
                                title = forwardTopicLabel(
                                    topic,
                                    selectedTopics,
                                ),
                                subtitle = if (topic.isDefault) {
                                    "Основной топик"
                                } else if (topic.isDone) {
                                    "Завершён"
                                } else {
                                    ""
                                },
                                selected =
                                    selectedTopicUuid == topic.uuid,
                                enabled = !submitting && !topicsLoading,
                                onClick = {
                                    selectedTopicUuid = topic.uuid
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (submitting) return@TextButton
                    val destination = target ?: return@TextButton
                    submitting = true
                    scope.launch {
                        error = request.validationError
                        val credentials =
                            viewModel.userViewModel.repo
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
                                conversationStateStore =
                                    conversationStateStore,
                            )
                        ) {
                            is IncomingShareCommitResult.Accepted ->
                                onCommitted(destination)

                            is IncomingShareCommitResult.Rejected ->
                                error = result.message
                        }
                        submitting = false
                    }
                },
                enabled =
                    target != null &&
                        request.validationError == null &&
                        !topicsLoading &&
                        !submitting,
            ) {
                Text(if (submitting) "Сохраняю…" else "Добавить в черновик")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !submitting,
            ) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun IncomingSharePreview(
    request: IncomingShareRequest,
    compactHeight: Boolean,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colors.cardBackgroundBase,
                RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (request.text.isNotBlank()) {
            Text(
                text = request.text.replace('\n', ' '),
                color = colors.textHeaders,
                fontSize = 13.sp,
                maxLines = if (compactHeight) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (request.attachmentUris.isNotEmpty()) {
            Text(
                text = "Файлов: ${request.attachmentUris.size}",
                color = colors.textAdditional50,
                fontSize = 12.sp,
            )
        }
        Text(
            text = if (compactHeight) {
                "Черновик · без автоотправки"
            } else {
                "Сообщение не будет отправлено автоматически"
            },
            color = colors.textAdditional50,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SelectedShareStreamHeader(
    stream: Stream,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stream.name,
                color = colors.textHeaders,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (stream.isDirectProviderChat()) {
                    "Личный чат"
                } else {
                    "Канал"
                },
                color = colors.textAdditional50,
                fontSize = 12.sp,
            )
        }
        TextButton(
            onClick = onChange,
            enabled = enabled,
        ) {
            Text("Изменить")
        }
    }
}

@Composable
private fun ShareDestinationRow(
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
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyShareDestination(message: String) {
    Text(
        text = message,
        color = LocalWorkspaceColorsPalette.current.textAdditional50,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 12.dp),
    )
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
