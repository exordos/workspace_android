package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun ForwardMessageDialog(
    viewModel: ChatDialogViewModel,
    state: ForwardDialogState,
) {
    val streams by viewModel.repo.streams.collectAsStateWithLifecycle()
    val topicsByStream by viewModel.repo.streamTopics.collectAsStateWithLifecycle()
    val users by viewModel.repo.users.collectAsStateWithLifecycle()
    val colors = LocalWorkspaceColorsPalette.current
    val locale = LocalLocale.current.platformLocale
    val targetListState = rememberLazyListState()
    var query by rememberSaveable(state.sourceMessage.uuid) { mutableStateOf("") }
    var confirmRiskyRetry by remember(state.sourceMessage.uuid) {
        mutableStateOf(false)
    }
    val busy = state.submitting || state.verifying
    val targetEditingEnabled =
        !busy && state.deliveryStatus == ForwardDeliveryStatus.EDITING
    val normalizedQuery = query.trim().lowercase(locale)
    val availableStreams = remember(streams, normalizedQuery, locale) {
        forwardableStreams(streams).filter { stream ->
            normalizedQuery.isEmpty() ||
                stream.name.lowercase(locale).contains(normalizedQuery) ||
                stream.description.lowercase(locale).contains(normalizedQuery)
        }
    }
    val selectedTopics = remember(
        state.selectedStreamUuid,
        topicsByStream,
    ) {
        state.selectedStreamUuid
            ?.let { streamUuid ->
                forwardTopics(streamUuid, topicsByStream[streamUuid].orEmpty())
            }
            .orEmpty()
    }
    val availableUsers = remember(
        users,
        state.currentUserUuid,
        normalizedQuery,
        locale,
    ) {
        forwardUsers(users, state.currentUserUuid).filter { user ->
            normalizedQuery.isEmpty() ||
                user.displayableName()
                    .lowercase(locale)
                    .contains(normalizedQuery) ||
                user.username.lowercase(locale).contains(normalizedQuery) ||
                user.email
                    ?.lowercase(locale)
                    ?.contains(normalizedQuery) == true
        }
    }
    LaunchedEffect(
        state.targetKind,
        state.selectedStreamUuid,
        state.selectedTopicUuid,
        state.selectedUserUuid,
        state.catalogLoading,
        state.topicsLoading,
        availableStreams,
        selectedTopics,
        availableUsers,
    ) {
        val selectedIndex = when (state.targetKind) {
            ForwardTargetKind.CHANNEL -> {
                val topicIndex = selectedTopics.indexOfFirst {
                    it.uuid == state.selectedTopicUuid
                }
                if (!state.topicsLoading && topicIndex >= 0) {
                    val emptyStreamsRow =
                        if (!state.catalogLoading && availableStreams.isEmpty()) 1 else 0
                    availableStreams.size + emptyStreamsRow + 1 + topicIndex
                } else {
                    availableStreams.indexOfFirst {
                        it.uuid == state.selectedStreamUuid
                    }
                }
            }

            ForwardTargetKind.DIRECT -> availableUsers.indexOfFirst {
                it.uuid == state.selectedUserUuid
            }
        }
        if (selectedIndex >= 0) {
            targetListState.scrollToItem(selectedIndex)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!busy) viewModel.dismissForward()
        },
        title = {
            Column {
                Text(
                    text = when (state.deliveryStatus) {
                        ForwardDeliveryStatus.COMPLETED -> "Сообщение переслано"
                        else -> "Переслать сообщение"
                    },
                )
                if (state.deliveryStatus != ForwardDeliveryStatus.COMPLETED) {
                    Text(
                        text = state.sourceMessage.payload.content
                            .replace('\n', ' ')
                            .ifBlank { "Сообщение без текста" },
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        text = {
            when {
                state.deliveryStatus == ForwardDeliveryStatus.COMPLETED -> {
                    Text(
                        text = "Сервер подтвердил доставку в выбранный чат.",
                        color = colors.textHeaders,
                    )
                }

                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ForwardTargetTab(
                                text = "Канал",
                                selected =
                                    state.targetKind == ForwardTargetKind.CHANNEL,
                                enabled = targetEditingEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.selectForwardTargetKind(
                                        ForwardTargetKind.CHANNEL,
                                    )
                                },
                            )
                            ForwardTargetTab(
                                text = "Личный чат",
                                selected =
                                    state.targetKind == ForwardTargetKind.DIRECT,
                                enabled = targetEditingEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.selectForwardTargetKind(
                                        ForwardTargetKind.DIRECT,
                                    )
                                },
                            )
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(MAX_FORWARD_SEARCH_CHARS) },
                            enabled = targetEditingEnabled,
                            label = {
                                Text(
                                    if (state.targetKind == ForwardTargetKind.CHANNEL) {
                                        "Найти канал"
                                    } else {
                                        "Найти пользователя"
                                    },
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.catalogLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "Обновляю получателей…",
                                    color = colors.textAdditional50,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        LazyColumn(
                            state = targetListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 330.dp),
                        ) {
                            when (state.targetKind) {
                                ForwardTargetKind.CHANNEL -> {
                                    if (
                                        !state.catalogLoading &&
                                        availableStreams.isEmpty()
                                    ) {
                                        item(key = "empty-streams") {
                                            EmptyForwardTarget(
                                                if (normalizedQuery.isEmpty()) {
                                                    "Нет доступных каналов"
                                                } else {
                                                    "Каналы не найдены"
                                                },
                                            )
                                        }
                                    }
                                    items(
                                        items = availableStreams,
                                        key = Stream::uuid,
                                    ) { stream ->
                                        ForwardChoiceRow(
                                            title = stream.name,
                                            subtitle = stream.description,
                                            selected =
                                                state.selectedStreamUuid == stream.uuid,
                                            enabled = targetEditingEnabled,
                                            onClick = {
                                                viewModel.selectForwardStream(stream.uuid)
                                            },
                                        )
                                    }
                                    state.selectedStreamUuid?.let {
                                        item(key = "topic-heading") {
                                            Text(
                                                text = "Топик",
                                                color = colors.textHeaders,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(
                                                    top = 10.dp,
                                                    bottom = 3.dp,
                                                ),
                                            )
                                        }
                                        if (state.topicsLoading) {
                                            item(key = "topic-loading") {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment =
                                                        Alignment.CenterVertically,
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                    Text(
                                                        text = "Загружаю топики…",
                                                        fontSize = 12.sp,
                                                        modifier =
                                                            Modifier.padding(start = 8.dp),
                                                    )
                                                }
                                            }
                                        } else {
                                            items(
                                                items = selectedTopics,
                                                key = TopicsResponseData::uuid,
                                            ) { topic ->
                                                ForwardChoiceRow(
                                                    title = forwardTopicLabel(
                                                        topic,
                                                        selectedTopics,
                                                    ),
                                                    subtitle = if (topic.isDone) {
                                                        "Завершён"
                                                    } else {
                                                        ""
                                                    },
                                                    selected =
                                                        state.selectedTopicUuid ==
                                                            topic.uuid,
                                                    enabled = targetEditingEnabled,
                                                    onClick = {
                                                        viewModel.selectForwardTopic(
                                                            topic.uuid,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                ForwardTargetKind.DIRECT -> {
                                    if (
                                        !state.catalogLoading &&
                                        availableUsers.isEmpty()
                                    ) {
                                        item(key = "empty-users") {
                                            EmptyForwardTarget(
                                                if (normalizedQuery.isEmpty()) {
                                                    "Нет доступных пользователей"
                                                } else {
                                                    "Пользователи не найдены"
                                                },
                                            )
                                        }
                                    }
                                    items(
                                        items = availableUsers,
                                        key = UserResponseData::uuid,
                                    ) { user ->
                                        ForwardChoiceRow(
                                            title = user.displayableName(),
                                            subtitle = user.email ?: user.username,
                                            selected =
                                                state.selectedUserUuid == user.uuid,
                                            enabled = targetEditingEnabled,
                                            onClick = {
                                                viewModel.selectForwardUser(user.uuid)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        state.error?.let { error ->
                            Text(
                                text = error,
                                color = colors.indicatorRed,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        colors.infoCardBackground,
                                        RoundedCornerShape(7.dp),
                                    )
                                    .padding(9.dp),
                            )
                        }
                        if (
                            state.targetKind == ForwardTargetKind.CHANNEL &&
                            state.selectedStreamUuid != null &&
                            !state.topicsLoading &&
                            selectedTopics.isEmpty()
                        ) {
                            TextButton(
                                onClick = viewModel::retryForwardTopics,
                                enabled = targetEditingEnabled,
                            ) {
                                Text("Повторить загрузку топиков")
                            }
                        }
                        if (state.deliveryStatus == ForwardDeliveryStatus.UNCERTAIN) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    onClick = viewModel::verifyForwardDelivery,
                                    enabled = !busy,
                                ) {
                                    Text("Проверить ещё раз")
                                }
                                if (state.canRetryUncertainSend) {
                                    TextButton(
                                        onClick = {
                                            confirmRiskyRetry = true
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text("Отправить ещё раз")
                                    }
                                }
                            }
                        }
                        if (confirmRiskyRetry) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        colors.infoCardBackground,
                                        RoundedCornerShape(7.dp),
                                    )
                                    .padding(9.dp),
                            ) {
                                Text(
                                    text =
                                        "Сервер не подтвердил предыдущую попытку. " +
                                            "Повтор может создать дубликат.",
                                    color = colors.indicatorRed,
                                    fontSize = 12.sp,
                                )
                                Row {
                                    TextButton(
                                        onClick = {
                                            confirmRiskyRetry = false
                                            viewModel.retryUncertainForward()
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text("Да, отправить")
                                    }
                                    TextButton(
                                        onClick = { confirmRiskyRetry = false },
                                        enabled = !busy,
                                    ) {
                                        Text("Отмена")
                                    }
                                }
                            }
                        }
                        if (busy) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = if (state.verifying) {
                                        "Проверяю результат…"
                                    } else {
                                        "Пересылаю…"
                                    },
                                    color = colors.textAdditional50,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (state.deliveryStatus != ForwardDeliveryStatus.COMPLETED) {
                TextButton(
                    onClick = viewModel::dismissForward,
                    enabled = !busy,
                ) {
                    Text("Отмена")
                }
            }
        },
        confirmButton = {
            if (state.deliveryStatus == ForwardDeliveryStatus.COMPLETED) {
                TextButton(onClick = viewModel::dismissForward) {
                    Text("Готово")
                }
            } else {
                val hasTarget = when (state.targetKind) {
                    ForwardTargetKind.CHANNEL ->
                        state.selectedStreamUuid != null &&
                            state.selectedTopicUuid != null

                    ForwardTargetKind.DIRECT -> state.selectedUserUuid != null
                }
                TextButton(
                    onClick = viewModel::submitForward,
                    enabled =
                        hasTarget &&
                            !busy &&
                            !state.catalogLoading &&
                            !state.topicsLoading &&
                            state.deliveryStatus != ForwardDeliveryStatus.UNCERTAIN,
                ) {
                    Text("Переслать")
                }
            }
        },
    )
}

@Composable
private fun ForwardTargetTab(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .background(
                if (selected) colors.primary.copy(alpha = 0.18f) else colors.background,
                RoundedCornerShape(8.dp),
            )
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colors.primary else colors.textHeaders,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ForwardChoiceRow(
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
            .padding(vertical = 4.dp),
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
                .padding(start = 4.dp),
        ) {
            Text(
                text = title,
                color = colors.textHeaders,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = colors.textAdditional50,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyForwardTarget(text: String) {
    Text(
        text = text,
        color = LocalWorkspaceColorsPalette.current.textAdditional50,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

private const val MAX_FORWARD_SEARCH_CHARS = 200
