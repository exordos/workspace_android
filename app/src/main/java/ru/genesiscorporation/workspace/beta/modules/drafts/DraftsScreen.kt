package ru.genesiscorporation.workspace.beta.modules.drafts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.PersistedDraftSyncStatus
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DraftsScreen(
    draftsViewModel: DraftsViewModel,
    chatViewModel: ChatViewModel,
    navController: NavHostController,
) {
    val state by draftsViewModel.state.collectAsStateWithLifecycle()
    val streams by chatViewModel.streams.collectAsStateWithLifecycle()
    val topicsByStream by chatViewModel.streamTopics.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var openingKey by remember { mutableStateOf<String?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }

    fun openDraft(item: DraftListItem) {
        if (openingKey != null || item.key in state.busyKeys) return
        openingKey = item.key
        actionError = null
        scope.launch {
            try {
                val resolution = chatViewModel.resolveDraftNavigation(
                    item.streamUuid,
                    item.topicUuid,
                )
                val route = resolution.route
                if (route == null) {
                    actionError = resolution.error
                        ?: "Не удалось открыть черновик"
                    return@launch
                }
                val exactRoute = route.copy(
                    draftStorageSlot = item.storageSlot,
                )
                val prepareError = draftsViewModel.prepareOpen(
                    item,
                    exactRoute,
                )
                if (prepareError != null) {
                    actionError = prepareError
                    return@launch
                }
                navController.navigate(exactRoute)
            } finally {
                openingKey = null
            }
        }
    }

    BackHandler { navController.popBackStack() }
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        DraftsTopBar(
            busy = state.initialLoading || state.refreshing,
            onBack = { navController.popBackStack() },
            onRefresh = {
                if (!state.initialLoading && !state.refreshing) {
                    actionError = null
                    draftsViewModel.refresh()
                }
            },
        )
        if (state.refreshing && state.items.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.searchBackground,
            )
        }
        val visibleError = actionError ?: state.error
        if (visibleError != null && state.items.isNotEmpty()) {
            DraftsErrorBanner(
                message = visibleError,
                onRetry = {
                    actionError = null
                    draftsViewModel.refresh()
                },
            )
        }
        when {
            state.items.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.items,
                        key = DraftListItem::key,
                    ) { draft ->
                        val stream = streams
                            .filter { it.uuid == draft.streamUuid }
                            .singleOrNull()
                        val topic = topicsByStream[draft.streamUuid]
                            .orEmpty()
                            .filter { it.uuid == draft.topicUuid }
                            .singleOrNull()
                        val persistedRoute = draft.localState?.route
                        val isDirectMessages =
                            stream?.isDirectProviderChat()
                                ?: persistedRoute?.isDirectMessages
                                ?: false
                        DraftCard(
                            item = draft,
                            streamName = stream?.name
                                ?: persistedRoute?.chatTitle
                                ?: "Чат ${draft.streamUuid}",
                            topicName = if (isDirectMessages) {
                                null
                            } else {
                                topic?.name
                                    ?: persistedRoute?.topicName
                                    ?: "Топик ${draft.topicUuid}"
                            },
                            isDirectMessages = isDirectMessages,
                            busy = draft.key in state.busyKeys ||
                                openingKey == draft.key,
                            onOpen = { openDraft(draft) },
                            onDelete = {
                                actionError = null
                                draftsViewModel.deleteDraft(draft)
                            },
                            onRetry = {
                                actionError = null
                                draftsViewModel.retryDraft(draft)
                            },
                            onAcceptServer = {
                                actionError = null
                                draftsViewModel.acceptServerVersion(draft)
                            },
                            onKeepLocal = {
                                actionError = null
                                draftsViewModel.keepLocalVersion(draft)
                            },
                        )
                    }
                }
            }

            state.initialLoading || (!state.hasLoaded && state.error == null) -> {
                DraftsStateCard("Загрузка черновиков…", loading = true)
            }

            state.error != null -> {
                DraftsStateCard(
                    message = state.error.orEmpty(),
                    action = "Повторить",
                    onAction = draftsViewModel::refresh,
                )
            }

            else -> DraftsStateCard("Черновиков пока нет")
        }
    }
}

@Composable
private fun DraftsTopBar(
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.background)
            .padding(horizontal = 4.dp),
    ) {
        DraftIconButton(
            drawable = R.drawable.arrow_back,
            description = "Назад к чатам",
            enabled = true,
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = "Черновики",
            color = colors.textHeaders,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontFamily = NavigationFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(20.dp)
                    .semantics {
                        contentDescription = "Обновление черновиков"
                    },
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        } else {
            DraftIconButton(
                drawable = R.drawable.ic_refresh,
                description = "Обновить черновики",
                enabled = true,
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun DraftCard(
    item: DraftListItem,
    streamName: String,
    topicName: String?,
    isDirectMessages: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onAcceptServer: () -> Unit,
    onKeepLocal: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val context = draftContextLabel(
        streamName = streamName,
        topicName = topicName,
        isDirectMessages = isDirectMessages,
    )
    val deletionFailed =
        item.status == PersistedDraftSyncStatus.FAILED &&
            item.localState?.serverDraft?.deleteRequested == true
    val canOpen = !deletionFailed &&
        item.status != PersistedDraftSyncStatus.DELETING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(
                colors.infoCardBackground,
                RoundedCornerShape(12.dp),
            )
            .then(
                if (canOpen && !busy) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onOpen,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                if (canOpen) {
                    role = Role.Button
                    contentDescription = "Открыть черновик: $context"
                }
            }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDraftTime(item.updatedAt),
                color = colors.textAdditional50,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = draftPreview(item.content),
            color = colors.textHeaders,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val status = draftStatus(item)
        if (status != null) {
            Text(
                text = status,
                color = if (
                    item.status == PersistedDraftSyncStatus.FAILED ||
                    item.status == PersistedDraftSyncStatus.CONFLICT
                ) {
                    colors.indicatorRed
                } else {
                    colors.textAdditional50
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        when {
            busy -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .size(20.dp)
                        .semantics {
                            contentDescription = "Изменение черновика"
                        },
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
            }

            item.status == PersistedDraftSyncStatus.CONFLICT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(onClick = onAcceptServer) {
                        Text("Версия сервера")
                    }
                    TextButton(onClick = onKeepLocal) {
                        Text("Оставить мою")
                    }
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = colors.indicatorRed)
                    }
                }
            }

            item.status == PersistedDraftSyncStatus.FAILED -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(onClick = onRetry) {
                        Text(
                            if (deletionFailed) {
                                "Повторить удаление"
                            } else {
                                "Повторить синхронизацию"
                            },
                        )
                    }
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = colors.indicatorRed)
                    }
                }
            }

            else -> {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Удалить", color = colors.indicatorRed)
                }
            }
        }
    }
}

@Composable
private fun DraftsErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = colors.indicatorRed,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun DraftsStateCard(
    message: String,
    loading: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(28.dp),
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = message,
            color = if (action == null) {
                colors.textAdditional50
            } else {
                colors.indicatorRed
            },
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action)
            }
        }
    }
}

@Composable
private fun DraftIconButton(
    drawable: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = null,
            tint = if (enabled) colors.primary else colors.textAdditional50,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun draftPreview(value: String): String =
    value.replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Пустой черновик" }
        .let { normalized ->
            if (normalized.length <= 220) normalized
            else "${normalized.take(219).trimEnd()}…"
        }

private fun draftStatus(item: DraftListItem): String? = when (item.status) {
    PersistedDraftSyncStatus.LOCAL -> "Ожидает синхронизации"
    PersistedDraftSyncStatus.SAVING -> "Сохраняется…"
    PersistedDraftSyncStatus.SAVED -> null
    PersistedDraftSyncStatus.FAILED ->
        item.localState?.serverDraft?.errorMessage
            ?: "Не удалось синхронизировать"
    PersistedDraftSyncStatus.CONFLICT ->
        "Изменён на другом устройстве"
    PersistedDraftSyncStatus.DELETING -> "Удаляется…"
}

private fun formatDraftTime(value: String?): String {
    val instant = parseDraftListInstant(value)
    if (instant == java.time.Instant.EPOCH) return ""
    return DRAFT_TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
}

private val DRAFT_TIME_FORMAT =
    DateTimeFormatter.ofPattern("dd.MM, HH:mm")
