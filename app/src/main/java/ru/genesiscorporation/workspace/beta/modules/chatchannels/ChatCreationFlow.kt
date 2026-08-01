package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

internal enum class ChatCreationPage {
    CHOOSER,
    DIRECT,
    STREAM,
}

internal enum class ChannelBrowseFilter {
    SUBSCRIBED,
    UNSUBSCRIBED,
    ALL,
}

@Composable
internal fun NewChatChooserScreen(
    streams: List<Stream>,
    streamBindings: List<StreamBindingResponseData>,
    currentUserUuid: String?,
    onBack: () -> Unit,
    onStartDirect: () -> Unit,
    onCreateChannel: () -> Unit,
    onOpenChannel: (Stream) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    var filter by rememberSaveable {
        mutableStateOf(ChannelBrowseFilter.UNSUBSCRIBED)
    }
    val subscribedStreamUuids = remember(streamBindings, currentUserUuid) {
        currentUserUuid
            ?.let { userUuid ->
                streamBindings
                    .asSequence()
                    .filter { it.userUuid == userUuid }
                    .map(StreamBindingResponseData::streamUuid)
                    .toSet()
            }
            .orEmpty()
    }
    val channels = remember(streams, subscribedStreamUuids, filter) {
        channelBrowseChannels(streams, subscribedStreamUuids, filter)
    }
    val memberCounts = remember(streamBindings) {
        streamBindings.groupingBy(StreamBindingResponseData::streamUuid).eachCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(CHAT_CREATION_CHOOSER_TAG),
    ) {
        ChatCreationBackHeader(onBack = onBack)
        ChatCreationAction(
            icon = R.drawable.ic_figma_channel_add,
            label = "Начать чат",
            onClick = onStartDirect,
        )
        ChatCreationAction(
            icon = R.drawable.group,
            label = "Создать стрим",
            onClick = onCreateChannel,
        )
        Text(
            text = "Каналы",
            color = colors.textHeaders,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, top = 26.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.chatHeaderBackground),
        ) {
            ChannelFilterButton(
                text = "С подпиской",
                selected = filter == ChannelBrowseFilter.SUBSCRIBED,
                modifier = Modifier.weight(1f),
                onClick = { filter = ChannelBrowseFilter.SUBSCRIBED },
            )
            ChannelFilterButton(
                text = "Без подписки",
                selected = filter == ChannelBrowseFilter.UNSUBSCRIBED,
                modifier = Modifier.weight(1f),
                onClick = { filter = ChannelBrowseFilter.UNSUBSCRIBED },
            )
            ChannelFilterButton(
                text = "Все",
                selected = filter == ChannelBrowseFilter.ALL,
                modifier = Modifier.weight(1f),
                onClick = { filter = ChannelBrowseFilter.ALL },
            )
        }
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (filter) {
                        ChannelBrowseFilter.SUBSCRIBED -> "Нет каналов с подпиской"
                        ChannelBrowseFilter.UNSUBSCRIBED -> "Нет каналов без подписки"
                        ChannelBrowseFilter.ALL -> "Нет доступных каналов"
                    },
                    color = colors.textAdditional50,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp),
            ) {
                items(channels, key = Stream::uuid) { stream ->
                    ChannelBrowseRow(
                        stream = stream,
                        memberCount = memberCounts[stream.uuid] ?: 0,
                        onClick = { onOpenChannel(stream) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DirectChatPickerScreen(
    users: List<UserResponseData>,
    catalogState: QueryState,
    createState: QueryState,
    baseUrl: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onUserSelected: (UserResponseData) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredUsers = remember(users, normalizedQuery) {
        directChatCandidates(users, normalizedQuery)
    }
    val creating = createState is QueryState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .testTag(DIRECT_CHAT_PICKER_TAG),
    ) {
        ChatCreationTitleHeader(
            title = "Начать чат",
            enabled = !creating,
            onBack = onBack,
            onClose = onClose,
        )
        ComposerStyleSearchField(
            value = query,
            enabled = !creating,
            onValueChange = { query = it.take(MAX_USER_SEARCH_CHARS) },
        )
        (createState as? QueryState.Error)?.let { error ->
            Text(
                text = error.message,
                color = colors.indicatorRed,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when (catalogState) {
            QueryState.Idle,
            QueryState.Loading,
            -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }

            is QueryState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = catalogState.message,
                    color = colors.indicatorRed,
                )
                TextButton(onClick = onRetry) {
                    Text("Повторить")
                }
            }

            QueryState.Success -> {
                if (filteredUsers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                "Нет доступных пользователей"
                            } else {
                                "Ничего не найдено"
                            },
                            color = colors.textAdditional50,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(filteredUsers, key = UserResponseData::uuid) { user ->
                            DirectChatUserRow(
                                user = user,
                                baseUrl = baseUrl,
                                enabled = !creating,
                                onClick = { onUserSelected(user) },
                            )
                        }
                    }
                }
            }
        }
        if (creating) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.chatHeaderBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Открываю чат…",
                    color = colors.textAdditional50,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatCreationBackHeader(onBack: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(width = 10.dp, height = 20.dp),
        )
        Text(
            text = "Назад",
            color = colors.textHeaders,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
internal fun ChatCreationTitleHeader(
    title: String,
    enabled: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Назад",
                tint = colors.iconBase,
            )
        }
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onClose,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = "Закрыть",
                tint = colors.iconBase,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ChatCreationAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = label,
            color = colors.textHeaders,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    HorizontalDivider(
        color = colors.textAdditional50.copy(alpha = 0.18f),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun ChannelFilterButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (selected) colors.iconBase.copy(alpha = 0.18f) else colors.chatHeaderBackground,
            )
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colors.textHeaders else colors.textAdditional50,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChannelBrowseRow(
    stream: Stream,
    memberCount: Int,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .semantics {
                contentDescription = "${stream.name}, $memberCount участников"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.iconBase.copy(alpha = 0.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stream.name.trim().take(1).uppercase(),
                color = colors.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = stream.name,
                color = colors.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$memberCount участников",
                color = colors.textAdditional50,
                fontSize = 11.sp,
            )
        }
        if (stream.isPrivate || stream.inviteOnly) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "Закрытый канал",
                tint = colors.iconBase,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    HorizontalDivider(
        color = colors.textAdditional50.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 60.dp, end = 12.dp),
    )
}

@Composable
internal fun ComposerStyleSearchField(
    value: String,
    enabled: Boolean,
    testTag: String = DIRECT_CHAT_SEARCH_TAG,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(40.dp)
            .background(colors.searchBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .testTag(testTag),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Поиск пользователей…",
                            color = colors.textAdditional50,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun DirectChatUserRow(
    user: UserResponseData,
    baseUrl: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            user.avatar,
            baseUrl,
            null,
            user.displayableName(),
            40,
            false,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = user.displayableName(),
                color = colors.textHeaders,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.email ?: user.username,
                color = colors.textAdditional50,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(
        color = colors.textAdditional50.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 58.dp, end = 8.dp),
    )
}

internal const val CHAT_CREATION_CHOOSER_TAG = "chat-creation-chooser"
internal const val DIRECT_CHAT_PICKER_TAG = "direct-chat-picker"
internal const val DIRECT_CHAT_SEARCH_TAG = "direct-chat-search"

private const val MAX_USER_SEARCH_CHARS = 200

internal fun channelBrowseChannels(
    streams: List<Stream>,
    subscribedStreamUuids: Set<String>,
    filter: ChannelBrowseFilter,
): List<Stream> =
    streams
        .asSequence()
        .filterNot(Stream::isDirectProviderChat)
        .filterNot(Stream::isArchived)
        .filter { stream ->
            when (filter) {
                ChannelBrowseFilter.SUBSCRIBED ->
                    stream.uuid in subscribedStreamUuids

                ChannelBrowseFilter.UNSUBSCRIBED ->
                    stream.uuid !in subscribedStreamUuids

                ChannelBrowseFilter.ALL -> true
            }
        }
        .sortedBy { it.name.lowercase() }
        .toList()

internal fun directChatCandidates(
    users: List<UserResponseData>,
    query: String,
): List<UserResponseData> {
    val normalizedQuery = query.trim()
    return users
        .asSequence()
        .filter { user ->
            normalizedQuery.isEmpty() ||
                user.displayableName().contains(normalizedQuery, ignoreCase = true) ||
                user.username.contains(normalizedQuery, ignoreCase = true) ||
                user.email?.contains(normalizedQuery, ignoreCase = true) == true
        }
        .sortedBy { it.displayableName().lowercase() }
        .toList()
}
