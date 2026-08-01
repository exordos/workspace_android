package ru.genesiscorporation.workspace.beta.modules.chatchannels

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

internal data class CreateChannelInput(
    val name: String,
    val description: String,
    val inviteOnly: Boolean,
    val announce: Boolean,
    val memberUserUuids: Set<String>,
)

@Composable
internal fun CreateStreamFlowScreen(
    users: List<UserResponseData>,
    catalogState: QueryState,
    createState: QueryState,
    currentUserUuid: String?,
    baseUrl: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSubmit: (CreateChannelInput) -> Unit,
    memberAvatar: @Composable (UserResponseData) -> Unit = { user ->
        Avatar(
            user.avatar,
            baseUrl,
            null,
            user.displayableName(),
            38,
            false,
        )
    },
) {
    val colors = LocalWorkspaceColorsPalette.current
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedUserUuids by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val creating = createState is QueryState.Loading
    val candidates = remember(users, currentUserUuid, query) {
        streamCreationCandidates(
            users = users,
            currentUserUuid = currentUserUuid,
            query = query,
        )
    }
    val input = remember(name, selectedUserUuids, users, currentUserUuid) {
        buildCreateStreamInput(
            name = name,
            selectedUserUuids = selectedUserUuids,
            users = users,
            currentUserUuid = currentUserUuid,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .testTag(STREAM_CREATION_ROOT_TAG),
    ) {
        ChatCreationTitleHeader(
            title = "Создать стрим",
            enabled = !creating,
            onBack = onBack,
            onClose = onClose,
        )
        Text(
            text = "Название стрима",
            color = colors.textAdditional50,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp),
        )
        StreamNameField(
            value = name,
            enabled = !creating,
            onValueChange = { name = it },
        )
        (createState as? QueryState.Error)?.let { error ->
            Text(
                text = error.message,
                color = colors.indicatorRed,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text(
            text = "Добавить участников",
            color = colors.textAdditional50,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
        )
        ComposerStyleSearchField(
            value = query,
            enabled = !creating,
            testTag = STREAM_MEMBER_SEARCH_TAG,
            onValueChange = { query = it.take(MAX_STREAM_MEMBER_SEARCH_CHARS) },
        )
        when (catalogState) {
            QueryState.Idle,
            QueryState.Loading,
            -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }

            is QueryState.Error -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = catalogState.message,
                    color = colors.indicatorRed,
                    fontSize = 13.sp,
                )
                TextButton(onClick = onRetry, enabled = !creating) {
                    Text("Повторить")
                }
            }

            QueryState.Success -> {
                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                "Нет доступных пользователей"
                            } else {
                                "Ничего не найдено"
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
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        items(candidates, key = UserResponseData::uuid) { user ->
                            StreamMemberRow(
                                user = user,
                                checked = user.uuid in selectedUserUuids,
                                enabled = !creating,
                                avatar = { memberAvatar(user) },
                                onCheckedChange = { checked ->
                                    selectedUserUuids = if (checked) {
                                        (selectedUserUuids + user.uuid).distinct()
                                    } else {
                                        selectedUserUuids - user.uuid
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        StreamCreationActions(
            creating = creating,
            canSubmit = input != null,
            onCancel = onClose,
            onSubmit = { input?.let(onSubmit) },
        )
    }
}

@Composable
private fun StreamNameField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
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
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(38.dp)
            .background(colors.searchBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp)
            .testTag(STREAM_NAME_FIELD_TAG),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Название канала",
                        color = colors.textAdditional30,
                        fontSize = 14.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun StreamMemberRow(
    user: UserResponseData,
    checked: Boolean,
    enabled: Boolean,
    avatar: @Composable () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) },
            )
            .testTag("$STREAM_MEMBER_ROW_TAG_PREFIX${user.uuid}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                checkmarkColor = colors.onPrimary,
                uncheckedColor = colors.iconBase,
            ),
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(4.dp))
        avatar()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = user.displayableName(),
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.email ?: user.username,
                color = colors.textAdditional50,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(
        color = colors.textAdditional50.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 34.dp),
    )
}

@Composable
private fun StreamCreationActions(
    creating: Boolean,
    canSubmit: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onCancel,
            enabled = !creating,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.cardBackgroundActive,
                contentColor = colors.primary,
                disabledContainerColor = colors.cardBackgroundActive,
                disabledContentColor = colors.textAdditional30,
            ),
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .testTag(STREAM_CANCEL_TAG),
        ) {
            Text("Отмена", fontSize = 13.sp)
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit && !creating,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.iconDisable,
                disabledContentColor = colors.textAdditional50,
            ),
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .testTag(STREAM_CREATE_SUBMIT_TAG),
        ) {
            if (creating) {
                CircularProgressIndicator(
                    color = colors.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = "Создать",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal fun streamCreationCandidates(
    users: List<UserResponseData>,
    currentUserUuid: String?,
    query: String,
): List<UserResponseData> {
    val normalizedQuery = query.trim()
    return users
        .asSequence()
        .filterNot { it.uuid == currentUserUuid }
        .filter { user ->
            normalizedQuery.isEmpty() ||
                user.displayableName().contains(normalizedQuery, ignoreCase = true) ||
                user.username.contains(normalizedQuery, ignoreCase = true) ||
                user.email?.contains(normalizedQuery, ignoreCase = true) == true
        }
        .sortedBy { it.displayableName().lowercase() }
        .toList()
}

internal fun buildCreateStreamInput(
    name: String,
    selectedUserUuids: Collection<String>,
    users: List<UserResponseData>,
    currentUserUuid: String?,
): CreateChannelInput? {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return null
    val selectableUserUuids = users
        .asSequence()
        .map(UserResponseData::uuid)
        .filterNot { it == currentUserUuid }
        .toSet()
    return CreateChannelInput(
        name = normalizedName,
        description = "",
        inviteOnly = false,
        announce = false,
        memberUserUuids = selectedUserUuids
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter { it in selectableUserUuids }
            .toSet(),
    )
}

internal const val STREAM_CREATION_ROOT_TAG = "stream-creation-root"
internal const val STREAM_NAME_FIELD_TAG = "stream-name-field"
internal const val STREAM_MEMBER_SEARCH_TAG = "stream-member-search"
internal const val STREAM_MEMBER_ROW_TAG_PREFIX = "stream-member-row-"
internal const val STREAM_CANCEL_TAG = "stream-creation-cancel"
internal const val STREAM_CREATE_SUBMIT_TAG = "stream-creation-submit"

private const val MAX_STREAM_MEMBER_SEARCH_CHARS = 200
