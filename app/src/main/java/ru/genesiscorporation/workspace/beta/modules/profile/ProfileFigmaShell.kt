package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.ChatListDensity
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.WorkspaceNotificationSound
import ru.genesiscorporation.workspace.beta.data.WorkspaceThemeMode
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
internal fun ProfileFigmaTopBar(
    title: String,
    onBack: () -> Unit,
    loading: Boolean = false,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PROFILE_FIGMA_TOP_BAR_HEIGHT)
            .testTag(PROFILE_FIGMA_TOP_BAR_TAG),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = "Назад",
                tint = colors.iconBase,
                modifier = Modifier.size(width = 10.dp, height = 20.dp),
            )
        }
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (loading) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
internal fun ProfileFigmaSummary(
    account: WorkspaceAccount,
    displayName: String,
    avatarUrn: String?,
    statusText: String?,
    presence: ProfilePresencePresentation,
    enabled: Boolean,
    onOpen: (() -> Unit)?,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onOpen != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "Открыть личную информацию",
                        onClick = onOpen,
                    )
                } else {
                    Modifier
                },
            )
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrn = avatarUrn,
            baseUrl = account.baseUrl,
            color = null,
            name = displayName,
            size = 64,
            hasPadding = false,
            ownerAccountId = account.accountId,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = displayName,
                color = colors.textHeaders,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_figma_profile_business),
                    contentDescription = null,
                    tint = colors.iconBase,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = statusText?.trim()?.takeIf(String::isNotEmpty) ?: "Статус",
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            Text(
                text = presence.label,
                color = when (presence.tone) {
                    ProfilePresenceTone.ONLINE -> colors.indicatorGreen
                    ProfilePresenceTone.AWAY -> colors.indicatorYellow
                    ProfilePresenceTone.BUSY -> colors.indicatorRed
                    ProfilePresenceTone.NEUTRAL -> colors.textAdditional50
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun ProfileFigmaOrganizationSection(
    accounts: List<WorkspaceAccount>,
    activeAccountId: String?,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    accountAvatar: @Composable (WorkspaceAccount) -> Unit = { account ->
        Avatar(
            avatarUrn = account.avatarUrn,
            baseUrl = account.baseUrl,
            color = null,
            name = account.organizationName ?: account.projectName,
            size = 38,
            hasPadding = false,
            ownerAccountId = account.accountId,
        )
    },
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag(PROFILE_FIGMA_ORGANIZATIONS_TAG),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = if (expanded) {
                        "Свернуть организации"
                    } else {
                        "Показать организации"
                    },
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_business),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Организации",
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier
                    .size(width = 8.dp, height = 16.dp)
                    .graphicsLayer(rotationZ = if (expanded) 90f else -90f),
            )
        }
        if (expanded) {
            accounts.forEach { account ->
                val selected = account.accountId == activeAccountId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) {
                                colors.cardBackgroundActive
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(
                            enabled = enabled && !selected,
                            role = Role.Button,
                            onClickLabel = "Переключить аккаунт",
                            onClick = { onSwitchAccount(account.accountId) },
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    accountAvatar(account)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    ) {
                        Text(
                            text = account.organizationName
                                ?.takeIf(String::isNotBlank)
                                ?: account.projectName,
                            color = colors.textHeaders,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = account.login,
                            color = colors.textAdditional50,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Text(
                            text = "Текущая",
                            color = colors.indicatorGreen,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "Добавить организацию",
                        onClick = onAddAccount,
                    )
                    .padding(start = 8.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+",
                    color = colors.primary,
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = "Добавить организацию",
                    color = colors.primary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
internal fun ProfileFigmaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Text(
        text = title.uppercase(),
        color = colors.textAdditional30,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun ProfileFigmaSettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
    testTag: String? = null,
    minHeight: Dp = if (subtitle == null) 44.dp else 52.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalWorkspaceColorsPalette.current
    val foreground = tint ?: if (enabled) colors.textHeaders else colors.textAdditional30
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(
                    if (testTag != null) Modifier.testTag(testTag) else Modifier,
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) tint ?: colors.iconBase else colors.iconDisable,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = title,
                    color = foreground,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = colors.textAdditional50,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            value?.let {
                Text(
                    text = it,
                    color = colors.textAdditional50,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    tint = colors.iconBase,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 8.dp, height = 16.dp)
                        .graphicsLayer(rotationZ = 180f),
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = colors.cardBackgroundActive,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun ProfileFigmaServerRow(
    server: String,
    accountLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .testTag(PROFILE_FIGMA_SERVER_ROW_TAG)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = "Выбрать организацию",
                    onClick = onClick,
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = if (enabled) colors.iconBase else colors.iconDisable,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = "Текущий сервер",
                    color = if (enabled) colors.textHeaders else colors.textAdditional30,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = server,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = accountLabel,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier
                    .size(width = 8.dp, height = 16.dp)
                    .graphicsLayer(rotationZ = 180f),
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = colors.cardBackgroundActive,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun ProfileFigmaInformationRow(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    copyable: Boolean,
    onCopy: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (copyable) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Скопировать $label",
                        onClick = onCopy,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = label,
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            Text(
                text = value,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (copyable) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun ProfileFigmaSwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) colors.iconBase else colors.iconDisable,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
            ) {
                Text(
                    text = title,
                    color = if (enabled) colors.textHeaders else colors.textAdditional30,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = subtitle,
                    color = colors.textAdditional50,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = colors.cardBackgroundActive,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun <T> ProfileFigmaChoiceDialog(
    title: String,
    description: String? = null,
    selected: T,
    choices: List<Pair<T, String>>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                description?.let {
                    Text(
                        text = it,
                        color = colors.textAdditional50,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                choices.forEach { (choice, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == choice,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelected(choice) },
                            )
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == choice,
                            enabled = enabled,
                            onClick = null,
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

internal data class ProfilePresencePresentation(
    val label: String,
    val tone: ProfilePresenceTone,
)

internal enum class ProfilePresenceTone {
    ONLINE,
    AWAY,
    BUSY,
    NEUTRAL,
}

internal fun profilePresencePresentation(status: String?): ProfilePresencePresentation =
    when (status?.lowercase()) {
        "active" -> ProfilePresencePresentation("В сети", ProfilePresenceTone.ONLINE)
        "idle" -> ProfilePresencePresentation("Нет на месте", ProfilePresenceTone.AWAY)
        "do_not_disturb" ->
            ProfilePresencePresentation("Не беспокоить", ProfilePresenceTone.BUSY)
        "offline" -> ProfilePresencePresentation("Не в сети", ProfilePresenceTone.NEUTRAL)
        else -> ProfilePresencePresentation("Статус неизвестен", ProfilePresenceTone.NEUTRAL)
    }

internal fun WorkspaceNotificationSound.profileSoundLabel(): String = when (this) {
    WorkspaceNotificationSound.DEFAULT -> "Обычный"
    WorkspaceNotificationSound.SUBTLE -> "Мягкий"
    WorkspaceNotificationSound.DIGITAL -> "Цифровой"
    WorkspaceNotificationSound.GLASS -> "Стекло"
    WorkspaceNotificationSound.PULSE -> "Импульс"
    WorkspaceNotificationSound.NONE -> "Без звука"
}

internal fun WorkspaceThemeMode.profileThemeLabel(): String = when (this) {
    WorkspaceThemeMode.SYSTEM -> "Системная"
    WorkspaceThemeMode.LIGHT -> "Светлая"
    WorkspaceThemeMode.DARK -> "Тёмная"
}

internal fun ChatListDensity.profileDensityLabel(): String = when (this) {
    ChatListDensity.STANDARD -> "Стандартная"
    ChatListDensity.COMPACT -> "Компактная"
}

internal val PROFILE_FIGMA_TOP_BAR_HEIGHT = 44.dp
internal const val PROFILE_FIGMA_TOP_BAR_TAG = "profile.figma.top_bar"
internal const val PROFILE_FIGMA_ORGANIZATIONS_TAG = "profile.figma.organizations"
internal const val PROFILE_FIGMA_SERVER_ROW_TAG = "profile.figma.server"
internal const val PROFILE_FIGMA_ROOT_TAG = "profile.figma.root"
internal const val PROFILE_FIGMA_PERSONAL_INFO_TAG = "profile.figma.personal_info"
internal const val PROFILE_FIGMA_SOUND_ROW_TAG = "profile.figma.sound"
internal const val PROFILE_FIGMA_THEME_ROW_TAG = "profile.figma.theme"
internal const val PROFILE_FIGMA_FOLDER_ROW_TAG = "profile.figma.folders"
