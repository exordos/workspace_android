package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.displayedUnreadCount
import ru.genesiscorporation.workspace.beta.modules.chatchannels.messagePreview
import ru.genesiscorporation.workspace.beta.modules.chatdialog.FullscreenZoomableImage
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.copyPlainWorkspaceText
import ru.genesiscorporation.workspace.beta.ui.rememberWorkspaceAvatarImageRequest
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
fun ChatUserInfoScreen(
    viewModel: ChatUserInfoViewModel,
    navController: NavHostController,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val topics by viewModel.repo.streamTopics.collectAsStateWithLifecycle()
    val baseUrl by viewModel.client.userViewModel.baseUrl.collectAsState()
    val currentUserUuid by
        viewModel.client.userViewModel.userId.collectAsStateWithLifecycle()
    val profileLoading by viewModel.profileLoading.collectAsStateWithLifecycle()
    val profileLoaded by viewModel.profileLoaded.collectAsStateWithLifecycle()
    val sharedChannelsLoaded by
        viewModel.sharedChannelsLoaded.collectAsStateWithLifecycle()
    val profileError by viewModel.profileError.collectAsStateWithLifecycle()
    val directChatOpening by
        viewModel.directChatOpening.collectAsStateWithLifecycle()
    val directChatError by
        viewModel.directChatError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accessibilityManager = LocalAccessibilityManager.current
    val copyFeedbackMillis = accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = PROFILE_COPY_FEEDBACK_MILLIS,
        containsIcons = false,
        containsText = true,
        containsControls = false,
    ) ?: PROFILE_COPY_FEEDBACK_MILLIS
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.Profile) }
    var copyFeedback by remember { mutableStateOf<ProfileCopyFeedback?>(null) }
    val canOpenDirectChat = canOpenDirectChatWith(
        profile = profile,
        currentUserUuid = currentUserUuid,
    )

    fun copyProfileValue(
        label: String,
        value: String,
    ) {
        val eventId = (copyFeedback?.eventId ?: 0L) + 1L
        val copied = copyPlainProfileText(context, label, value)
        copyFeedback = ProfileCopyFeedback(
            eventId = eventId,
            message = if (copied) {
                "$label: скопировано"
            } else {
                "Не удалось скопировать: $label"
            },
            success = copied,
        )
    }

    LaunchedEffect(copyFeedback?.eventId) {
        val acceptedEventId = copyFeedback?.eventId ?: return@LaunchedEffect
        delay(copyFeedbackMillis)
        if (copyFeedback?.eventId == acceptedEventId) {
            copyFeedback = null
        }
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.openDirectChatEvents.collect { destination ->
            navController.navigate(
                ChatFlow.ChatDialog(
                    title = destination.title,
                    chatId = destination.streamUuid,
                    topicName = null,
                    topicUuid = destination.topicUuid,
                    isDirectMessages = true,
                    userId = null,
                ),
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 24.dp,
        ),
    ) {
        item {
            ProfileBackRow(
                showClose = selectedTab == ProfileTab.Channels,
                refreshing = profileLoading,
                onRefresh = { viewModel.refreshProfile() },
                onClick = navController::popBackStack,
            )
        }
        profileError?.let { error ->
            item(key = "profile-error") {
                ProfileErrorCard(
                    message = error,
                    retryEnabled = !profileLoading,
                    onRetry = { viewModel.refreshProfile() },
                    onDismiss = viewModel::clearProfileError,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            ProfileHeader(
                profile = profile,
                fallbackName = viewModel.userName,
                fallbackAvatar = viewModel.avatarUrl,
                baseUrl = baseUrl.orEmpty(),
                onCopyName = { name ->
                    copyProfileValue("Имя", name)
                },
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        copyFeedback?.let { feedback ->
            item(key = "copy-feedback-${feedback.eventId}") {
                ProfileCopyFeedback(
                    feedback = feedback,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (canOpenDirectChat) {
            item(key = "open-direct-chat") {
                Button(
                    onClick = { viewModel.openDirectChat() },
                    enabled = !directChatOpening && profileLoaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = 12.dp),
                ) {
                    if (directChatOpening) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_figma_new_chat),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (directChatOpening) {
                            "Открываем чат…"
                        } else {
                            "Открыть личный чат"
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        directChatError?.let { error ->
            item(key = "direct-chat-error") {
                ProfileErrorCard(
                    message = error,
                    retryEnabled = !directChatOpening && canOpenDirectChat,
                    onRetry = { viewModel.openDirectChat() },
                    onDismiss = viewModel::clearDirectChatError,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            ProfileTabs(
                selected = selectedTab,
                onSelected = { selectedTab = it },
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        when (selectedTab) {
            ProfileTab.Profile -> {
                if (profileLoading && profile == null) {
                    item(key = "profile-loading") {
                        ProfileLoadingState(
                            label = "Загружаем профиль…",
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                } else {
                    items(
                        items = viewModel.profileFields(),
                        key = ProfileField::title,
                    ) { field ->
                        ProfileRow(
                            field = field,
                            onCopy = if (field.copyable) {
                                {
                                    copyProfileValue(
                                        field.title,
                                        field.value,
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            ProfileTab.Channels -> {
                when {
                    profileLoading && !sharedChannelsLoaded -> {
                        item(key = "channels-loading") {
                            ProfileLoadingState(
                                label = "Загружаем общие каналы…",
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                    sharedChannelsLoaded && channels.isEmpty() -> {
                        item(key = "channels-empty") {
                            ProfileEmptyState(
                                text = "Общих каналов нет",
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                    !sharedChannelsLoaded -> {
                        item(key = "channels-unavailable") {
                            ProfileErrorCard(
                                message = "Не удалось загрузить общие каналы",
                                retryEnabled = !profileLoading,
                                onRetry = { viewModel.refreshProfile() },
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                    else -> {
                        items(
                            items = channels,
                            key = Stream::uuid,
                        ) { stream ->
                            val topic = topics[stream.uuid]
                                .orEmpty()
                                .singleOrNull {
                                    it.uuid == stream.defaultTopicUuid
                                }
                            ProfileChannelCard(
                                stream = stream,
                                topicName = topic?.name,
                                baseUrl = baseUrl.orEmpty(),
                                onClick = {
                                    navController.navigate(
                                        ChatFlow.ChatTopic(
                                            channelName = stream.name,
                                            channelId = stream.uuid,
                                        ),
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ProfileTab(val title: String) {
    Profile("Профиль"),
    Channels("Каналы"),
}

private data class ProfileCopyFeedback(
    val eventId: Long,
    val message: String,
    val success: Boolean,
)

@Composable
private fun ProfileCopyFeedback(
    feedback: ProfileCopyFeedback,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Text(
        text = feedback.message,
        color = if (feedback.success) colors.primary else colors.indicatorRed,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ProfileErrorCard(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                colors.infoCardBackground,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            color = colors.indicatorRed,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
            TextButton(
                enabled = retryEnabled,
                onClick = onRetry,
            ) {
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun ProfileLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = colors.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = colors.textAdditional50,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ProfileEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Text(
        text = text,
        color = colors.textAdditional50,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    )
}

@Composable
private fun ProfileBackRow(
    showClose: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Назад"
                }
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Назад",
                color = colors.textHeaders.copy(alpha = 0.8f),
                fontFamily = NavigationFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier
                .size(48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (refreshing) {
                        "Профиль обновляется"
                    } else {
                        "Обновить профиль"
                    }
                },
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = colors.iconBase,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (showClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = "Закрыть",
                tint = colors.iconBase,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onClick)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserResponseData?,
    fallbackName: String,
    fallbackAvatar: String,
    baseUrl: String,
    onCopyName: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val name = profile?.displayableName()?.takeIf(String::isNotBlank) ?: fallbackName
    val avatarUrn = profile?.avatar ?: fallbackAvatar
    var showAvatarPreview by rememberSaveable(avatarUrn) {
        mutableStateOf(false)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = avatarUrn,
            baseUrl = baseUrl,
            color = null,
            name = name,
            size = 64,
            hasPadding = false,
            contentDescription = "Открыть фото профиля",
            onClick = { showAvatarPreview = true },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_figma_profile_status),
                    contentDescription = null,
                    tint = colors.iconBase,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp),
                )
                Text(
                    text = name,
                    color = colors.textHeaders,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (name.isNotBlank()) {
                    IconButton(
                        onClick = { onCopyName(name) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = "Скопировать имя",
                            tint = colors.iconBase,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            externalIdentityLabel(profile)?.let { identityLabel ->
                Text(
                    text = identityLabel,
                    color = colors.primary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            presenceLabel(profile?.status)?.let { statusLabel ->
                Text(
                    text = statusLabel,
                    color = colors.textAdditional30,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
    if (showAvatarPreview) {
        val avatarRequest = rememberWorkspaceAvatarImageRequest(
            avatarUrn = avatarUrn,
            baseUrl = baseUrl,
        )
        if (avatarRequest == null) {
            LaunchedEffect(avatarUrn, baseUrl) {
                showAvatarPreview = false
            }
        } else {
            FullscreenZoomableImage(
                model = avatarRequest,
                contentDescription = "Фото профиля пользователя $name",
                onDismiss = { showAvatarPreview = false },
            )
        }
    }
}

@Composable
private fun ProfileTabs(
    selected: ProfileTab,
    onSelected: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            thickness = 1.dp,
            color = colors.iconDisable,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProfileTab.entries.forEach { tab ->
                Column(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .width(IntrinsicSize.Min)
                        .selectable(
                            selected = tab == selected,
                            role = Role.Tab,
                            onClick = { onSelected(tab) },
                        ),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = tab.title,
                        color = if (tab == selected) {
                            colors.textHeaders
                        } else {
                            colors.textAdditional50
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (tab == selected) colors.textHeaders else Color.Transparent,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    field: ProfileField,
    onCopy: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(field.icon),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(32.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = field.title,
                color = colors.textAdditional30,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = field.value,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Скопировать ${field.title.lowercase()}",
                    tint = colors.iconBase,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileChannelCard(
    stream: Stream,
    topicName: String?,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val lastMessage = stream.lastMessage
    val displayedUnread = stream.displayedUnreadCount()
    val messagePreview = lastMessage?.payload?.content
        ?.let(::messagePreview)
        ?.takeIf(String::isNotBlank)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.searchBackground)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = stream.avatar,
            baseUrl = baseUrl,
            color = stream.color,
            name = stream.name,
            size = 40,
            hasPadding = false,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = if (topicName.isNullOrBlank()) {
                    stream.name
                } else {
                    "${stream.name}  # $topicName"
                },
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            lastMessage?.user?.displayableName()
                ?.takeIf(String::isNotBlank)
                ?.let { senderName ->
                    Text(
                        text = senderName,
                        color = colors.primary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                messagePreview?.let { preview ->
                    Text(
                        text = preview,
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (messagePreview == null) {
                    Spacer(Modifier.weight(1f))
                }
                UnreadBadge(
                    count = displayedUnread?.count ?: 0,
                    muted = displayedUnread?.passive == true,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

internal fun presenceLabel(status: String?): String? = when (status) {
    "active" -> "В сети"
    "idle" -> "Нет на месте"
    "dnd", "do_not_disturb" -> "Не беспокоить"
    else -> null
}

internal fun externalIdentityLabel(profile: UserResponseData?): String? {
    if (!profile?.identityKind.equals("external", ignoreCase = true)) {
        return null
    }
    val provider = profile?.provider?.kind
        ?.trim()
        ?.take(PROFILE_PROVIDER_LABEL_CHARS)
        ?.takeIf(String::isNotBlank)
    return if (provider == null) {
        "Внешний профиль"
    } else {
        "Внешний профиль · $provider"
    }
}

internal fun copyPlainProfileText(
    context: Context,
    label: String,
    value: String,
): Boolean = copyPlainWorkspaceText(context, label, value)

private const val PROFILE_COPY_FEEDBACK_MILLIS = 2_000L
private const val PROFILE_PROVIDER_LABEL_CHARS = 32
