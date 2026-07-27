package ru.genesiscorporation.workspace.beta.modules.channelinfo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import ru.genesiscorporation.workspace.beta.ui.theme.NavigationFontFamily

@Composable
fun ChannelInfoScreen(
    viewModel: ChannelInfoViewModel,
    navController: NavHostController,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val stream by viewModel.stream.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val baseUrl by viewModel.client.userViewModel.baseUrl.collectAsState()
    val onlineCount = members.count { it.user.status != "offline" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            BackRow(onClick = navController::popBackStack)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarUrn = stream?.avatar,
                    baseUrl = baseUrl.orEmpty(),
                    color = stream?.color,
                    name = stream?.name.orEmpty(),
                    size = 64,
                    hasPadding = false,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stream?.name ?: "Информация о канале",
                        color = colors.textHeaders,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${members.size} участников, $onlineCount в сети",
                        color = colors.textAdditional30,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoActionButton(
                    icon = R.drawable.ic_figma_channel_mute,
                    description = if (stream?.notificationMode == "muted") {
                        "Включить уведомления"
                    } else {
                        "Отключить уведомления"
                    },
                    onClick = viewModel::toggleMuted,
                    modifier = Modifier.weight(1f),
                )
                InfoActionButton(
                    icon = R.drawable.ic_figma_channel_settings,
                    description = "Настройки канала",
                    modifier = Modifier.weight(1f),
                )
                InfoActionButton(
                    icon = R.drawable.ic_figma_channel_search,
                    description = "Поиск по каналу",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelInfoLink(R.drawable.ic_figma_channel_photo, "Фотографии")
                ChannelInfoLink(R.drawable.ic_figma_channel_video, "Видео")
                ChannelInfoLink(R.drawable.ic_figma_channel_file, "Файлы")
                ChannelInfoLink(R.drawable.ic_figma_channel_link, "Ссылки")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Комнаты со звонками")
                DemoCallRoomCard(
                    title = "Еженедельный созвон",
                    topic = "Общий чат",
                    duration = "24:59",
                    members = members.take(4),
                    baseUrl = baseUrl.orEmpty(),
                )
                DemoCallRoomCard(
                    title = "Дизайн-ревью",
                    topic = "Интерфейс",
                    duration = "12:08",
                    members = members.drop(1).take(4),
                    baseUrl = baseUrl.orEmpty(),
                )
                Text(
                    text = "и ещё 8 звонков",
                    color = colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(
                        text = "Участники",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_figma_channel_add),
                        contentDescription = "Добавить участника",
                        tint = colors.iconBase,
                        modifier = Modifier.size(32.dp),
                    )
                }
                members.forEach { member ->
                    ChannelMemberCard(member, baseUrl.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun BackRow(onClick: () -> Unit) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .height(20.dp)
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
}

@Composable
private fun InfoActionButton(
    @DrawableRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.infoCardBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = colors.iconBase,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ChannelInfoLink(
    @DrawableRes icon: Int,
    title: String,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = LocalWorkspaceColorsPalette.current.textHeaders,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun DemoCallRoomCard(
    title: String,
    topic: String,
    duration: String,
    members: List<ChannelMember>,
    baseUrl: String,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.infoCardBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .width(3.dp)
                        .height(16.dp)
                        .background(colors.indicatorYellow, RoundedCornerShape(3.dp)),
                )
                Text(
                    text = "# $topic",
                    color = colors.textAdditional50,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OverlappingAvatars(members, baseUrl)
        }
        Text(
            text = duration,
            color = colors.textAdditional50,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_figma_channel_call),
            contentDescription = "Звонок",
            tint = colors.indicatorGreen,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun OverlappingAvatars(
    members: List<ChannelMember>,
    baseUrl: String,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
        members.forEach { member ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .border(1.dp, colors.infoCardBackground, CircleShape)
                    .padding(1.dp),
            ) {
                Avatar(
                    avatarUrn = member.user.avatar,
                    baseUrl = baseUrl,
                    color = null,
                    name = member.user.displayableName(),
                    size = 28,
                    hasPadding = false,
                )
            }
        }
    }
}

@Composable
private fun ChannelMemberCard(
    member: ChannelMember,
    baseUrl: String,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.infoCardBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = member.user.avatar,
            baseUrl = baseUrl,
            color = null,
            name = member.user.displayableName(),
            size = 30,
            hasPadding = false,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = member.user.displayableName(),
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.user.statusText?.takeIf(String::isNotBlank)
                    ?: presenceLabel(member.user.status),
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (member.role != "member") {
            Text(
                text = roleLabel(member.role),
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 20.sp,
            )
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "owner" -> "Владелец"
    "administrator" -> "Администратор"
    "moderator" -> "Модератор"
    "guest" -> "Гость"
    else -> ""
}

private fun presenceLabel(status: String): String = when (status) {
    "active" -> "В сети"
    "idle" -> "Нет на месте"
    "dnd" -> "Не беспокоить"
    else -> "Не в сети"
}
