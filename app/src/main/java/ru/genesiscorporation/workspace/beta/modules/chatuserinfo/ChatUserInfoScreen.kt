package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.messagePreview
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
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
    var selectedTab by rememberSaveable { mutableStateOf(ProfileTab.Profile) }

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
                onClick = navController::popBackStack,
            )
        }
        item {
            ProfileHeader(
                profile = profile,
                fallbackName = viewModel.userName,
                fallbackAvatar = viewModel.avatarUrl,
                baseUrl = baseUrl.orEmpty(),
                modifier = Modifier.padding(top = 20.dp),
            )
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
                items(
                    items = viewModel.profileFields(),
                    key = ProfileField::title,
                ) { field ->
                    ProfileRow(
                        field = field,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            ProfileTab.Channels -> {
                items(
                    items = channels,
                    key = Stream::uuid,
                ) { stream ->
                    val topic = topics[stream.uuid]
                        .orEmpty()
                        .firstOrNull { it.uuid == stream.defaultTopicUuid }
                        ?: topics[stream.uuid].orEmpty().firstOrNull()
                    ProfileChannelCard(
                        stream = stream,
                        topicName = topic?.name,
                        baseUrl = baseUrl.orEmpty(),
                        onClick = {
                            navController.navigate(
                                ChatFlow.ChatDialog(
                                    title = stream.name,
                                    chatId = stream.uuid,
                                    topicName = topic?.name,
                                    topicUuid = topic?.uuid
                                        ?: stream.defaultTopicUuid.orEmpty(),
                                    isDirectMessages = stream.isDirectProviderChat(),
                                    userId = null,
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

private enum class ProfileTab(val title: String) {
    Profile("Профиль"),
    Channels("Каналы"),
}

@Composable
private fun ProfileBackRow(
    showClose: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(20.dp),
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
        if (showClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close_small),
                contentDescription = "Закрыть",
                tint = colors.iconBase,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onClick),
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val name = profile?.displayableName()?.takeIf(String::isNotBlank) ?: fallbackName
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = profile?.avatar ?: fallbackAvatar,
            baseUrl = baseUrl,
            color = null,
            name = name,
            size = 64,
            hasPadding = false,
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
                )
            }
            Text(
                text = presenceLabel(profile?.status),
                color = colors.textAdditional30,
                fontSize = 14.sp,
                lineHeight = 16.sp,
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
                        .height(36.dp)
                        .width(IntrinsicSize.Min)
                        .clickable { onSelected(tab) },
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
                if (stream.unreadCount > 0) {
                    UnreadBadge(
                        count = stream.unreadCount,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun presenceLabel(status: String?): String = when (status) {
    "active" -> "В сети"
    "idle" -> "Нет на месте"
    "dnd" -> "Не беспокоить"
    else -> "Не в сети"
}
