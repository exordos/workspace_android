package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

internal const val CREATE_TOPIC_DIALOG_TAG = "create-topic-dialog"
internal const val CREATE_TOPIC_NAME_FIELD_TAG = "create-topic-name-field"
internal const val CREATE_TOPIC_CANCEL_TAG = "create-topic-cancel"
internal const val CREATE_TOPIC_SUBMIT_TAG = "create-topic-submit"

private val CreateTopicDialogWidth = 366.dp
private val CreateTopicDialogHeight = 160.dp
private val CreateTopicControlHeight = 38.dp
private val CreateTopicGap = 12.dp
private val CreateTopicCornerRadius = 8.dp
private val CreateTopicButtonGradient = Brush.horizontalGradient(
    listOf(
        Color(0xFFFF8138),
        Color(0xFFFF6838),
    ),
)

@Composable
fun CreateTopicDialog(
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val normalizedName = name.trim()
    val canSubmit = normalizedName.isNotEmpty() && !busy
    val colors = LocalWorkspaceColorsPalette.current

    Dialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = CreateTopicDialogWidth)
                .fillMaxWidth()
                .height(CreateTopicDialogHeight)
                .clip(RoundedCornerShape(CreateTopicCornerRadius))
                .background(colors.contextMenuBackground)
                .testTag(CREATE_TOPIC_DIALOG_TAG)
                .padding(horizontal = 8.dp, vertical = 20.dp),
        ) {
            Text(
                text = "Название темы",
                color = colors.textHeaders,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.height(CreateTopicGap))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                enabled = !busy,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canSubmit) onSubmit(normalizedName)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CreateTopicControlHeight)
                    .clip(RoundedCornerShape(CreateTopicCornerRadius))
                    .background(colors.cardBackgroundActive)
                    .padding(horizontal = 10.dp)
                    .testTag(CREATE_TOPIC_NAME_FIELD_TAG),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                text = "Название темы",
                                color = colors.textAdditional30,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.height(CreateTopicGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                CreateTopicDialogButton(
                    text = "Отмена",
                    enabled = !busy,
                    containerColor = colors.bottomNavigationSelectedBackground,
                    contentColor = colors.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(CREATE_TOPIC_CANCEL_TAG),
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(8.dp))
                CreateTopicDialogButton(
                    text = if (busy) "Создание…" else "Создать",
                    enabled = canSubmit,
                    containerColor = colors.iconDisable,
                    contentColor = colors.onPrimary,
                    enabledBrush = CreateTopicButtonGradient,
                    disabledContentColor = colors.textAdditional30,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(CREATE_TOPIC_SUBMIT_TAG),
                    onClick = { onSubmit(normalizedName) },
                )
            }
        }
    }
}

@Composable
private fun CreateTopicDialogButton(
    text: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabledBrush: Brush? = null,
    disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
    height: Dp = CreateTopicControlHeight,
    onClick: () -> Unit,
) {
    val backgroundModifier = if (enabled && enabledBrush != null) {
        Modifier.background(enabledBrush)
    } else {
        Modifier.background(containerColor)
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(CreateTopicCornerRadius))
            .then(backgroundModifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else disabledContentColor,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun TopicNameDialog(
    title: String,
    initialName: String,
    busy: Boolean,
    submitLabel: String = "Сохранить",
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val normalizedName = name.trim()
    val colors = LocalWorkspaceColorsPalette.current
    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = {
            Text(
                text = title,
                color = colors.textHeaders,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = { Text("Название темы") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotEmpty() && !busy,
                onClick = { onSubmit(normalizedName) },
            ) {
                Text(if (busy) "Сохранение…" else submitLabel)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Отмена")
            }
        },
    )
}

@Composable
fun TopicActionsDialog(
    expanded: Boolean,
    topic: TopicsResponseData,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMarkRead: () -> Unit,
    onToggleDone: () -> Unit,
    onSetNotificationMode: (String) -> Unit,
) {
    WorkspaceContextMenu(
        expanded = expanded,
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        width = 278.dp,
    ) {
        NotificationModeSelector(
            options = topicNotificationModeOptions(topic.notificationMode),
            selectedMode = topic.notificationMode,
            onModeSelected = onSetNotificationMode,
            enabled = !busy,
            modifier = Modifier.padding(4.dp),
        )
        if (topic.unreadCount > 0) {
            WorkspaceMenuActionRow(
                text = "Отметить всё как прочитанное",
                iconRes = R.drawable.ic_menu_check,
                onClick = onMarkRead,
                enabled = !busy,
            )
        }
        WorkspaceMenuActionRow(
            text = if (topic.isDone) {
                "Убрать отметку выполненной темы"
            } else {
                "Отметить тему как выполненную"
            },
            iconRes = R.drawable.ic_menu_flag,
            onClick = onToggleDone,
            enabled = !busy,
        )
        WorkspaceMenuActionRow(
            text = "Переименовать тему",
            iconRes = R.drawable.ic_menu_pen,
            onClick = onRename,
            enabled = !busy,
        )
    }
}
