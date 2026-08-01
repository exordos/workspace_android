package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import ru.genesiscorporation.workspace.beta.data.remote.dto.FolderResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.FOLDER_TITLE_MAX_LENGTH
import ru.genesiscorporation.workspace.beta.modules.chatchannels.folderDraftError
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isAllChatsFolder
import ru.genesiscorporation.workspace.beta.modules.chatchannels.localizedTitle
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.UnreadBadge
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class FolderDisplayPage {
    LIST,
    CREATE,
}

@Composable
fun FolderDisplayScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit = onBack,
    onFolderSelected: () -> Unit,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val activeAccount by
        viewModel.userViewModel.activeAccount.collectAsStateWithLifecycle()
    val operationInProgress by
        viewModel.folderActionInProgress.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val creationResult by
        viewModel.folderCreationResult.collectAsStateWithLifecycle()
    var pageName by rememberSaveable {
        mutableStateOf(FolderDisplayPage.LIST.name)
    }
    val page = FolderDisplayPage.valueOf(pageName)
    var pendingRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }
    LaunchedEffect(creationResult, pendingRequestId) {
        val result = creationResult ?: return@LaunchedEffect
        if (result.requestId != pendingRequestId) return@LaunchedEffect
        pendingRequestId = null
        resultMessage = result.message
        if (result.folderCreated) {
            pageName = FolderDisplayPage.LIST.name
        }
    }
    BackHandler {
        if (page == FolderDisplayPage.CREATE && !operationInProgress) {
            pageName = FolderDisplayPage.LIST.name
        } else if (!operationInProgress) {
            onBack()
        }
    }

    when (page) {
        FolderDisplayPage.LIST -> FolderDisplayList(
            folders = folders,
            streamCount = streams.count { !it.isArchived },
            loading = operationInProgress,
            message = actionError ?: resultMessage,
            onDismissMessage = {
                resultMessage = null
                viewModel.clearActionError()
            },
            onBack = onBack,
            onClose = onClose,
            onCreateFolder = {
                resultMessage = null
                viewModel.clearActionError()
                pageName = FolderDisplayPage.CREATE.name
            },
            onFolderSelected = { folder ->
                viewModel.updateCurrentlySelectedFolder(folder)
                onFolderSelected()
            },
        )

        FolderDisplayPage.CREATE -> FolderCreateForm(
            streams = streams.filterNot(Stream::isArchived),
            activeAccount = activeAccount,
            operationInProgress = operationInProgress,
            error = actionError,
            onBack = { pageName = FolderDisplayPage.LIST.name },
            onClose = onClose,
            onDismissError = viewModel::clearActionError,
            onCreate = { name, selectedUuids ->
                pendingRequestId = viewModel.createFolderWithChats(
                    name = name,
                    selectedStreams = streams.filter {
                        it.uuid in selectedUuids
                    },
                )
            },
        )
    }
}

@Composable
internal fun FolderDisplayList(
    folders: List<FolderResponseData>,
    streamCount: Int,
    loading: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onCreateFolder: () -> Unit,
    onFolderSelected: (FolderResponseData) -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 12.dp)
            .testTag(FOLDER_DISPLAY_ROOT_TAG),
    ) {
        FolderTopBar(
            title = "Отображение папок",
            onBack = onBack,
            onClose = onClose,
            enabled = !loading,
            backContentDescription = "Назад к профилю",
            closeContentDescription = "Закрыть отображение папок",
            showProgress = loading,
        )
        Text(
            text = "МОИ ПАПКИ",
            color = colors.textAdditional50,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
        message?.let {
            FolderInlineMessage(it, onDismissMessage)
        }
        folders.forEach { folder ->
            val displayTitle = folderDisplayTitle(folder)
            val itemCount = if (folder.isAllChatsFolder()) {
                streamCount
            } else {
                folder.items.size
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clickable(
                        enabled = !loading,
                        role = Role.Button,
                        onClickLabel = "Открыть папку $displayTitle",
                        onClick = { onFolderSelected(folder) },
                    )
                    .testTag("$FOLDER_DISPLAY_ROW_TAG_PREFIX${folder.uuid}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$displayTitle ($itemCount)",
                    color = colors.textHeaders,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                color = colors.cardBackgroundActive,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Text(
            text = "+ Создать папку",
            color = colors.primary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(
                    enabled = !loading,
                    role = Role.Button,
                    onClick = onCreateFolder,
                )
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .testTag(FOLDER_CREATE_OPEN_TAG),
        )
    }
}

@Composable
internal fun FolderCreateForm(
    streams: List<Stream>,
    activeAccount: WorkspaceAccount?,
    operationInProgress: Boolean,
    error: String?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDismissError: () -> Unit,
    onCreate: (String, Set<String>) -> Unit,
    streamAvatar: @Composable (Stream) -> Unit = { stream ->
        Avatar(
            avatarUrn = stream.avatar ?: stream.lastMessage?.user?.avatar,
            baseUrl = activeAccount?.baseUrl.orEmpty(),
            color = stream.color,
            name = stream.name,
            size = 40,
            hasPadding = false,
            ownerAccountId = activeAccount?.accountId,
        )
    },
) {
    val colors = LocalWorkspaceColorsPalette.current
    var folderName by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedUuids by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val filteredStreams = remember(streams, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            streams
        } else {
            streams.filter { stream ->
                stream.name.contains(query, ignoreCase = true) ||
                    stream.description.contains(query, ignoreCase = true)
            }
        }
    }
    val validationError = remember(folderName) {
        folderDraftError(folderName)
    }
    val nameValid = folderName.trim().isNotEmpty() &&
        folderName.trim().length <= FOLDER_TITLE_MAX_LENGTH

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .padding(horizontal = 12.dp)
            .testTag(FOLDER_CREATE_ROOT_TAG),
    ) {
        FolderTopBar(
            title = "Создать папку",
            onBack = onBack,
            onClose = onClose,
            enabled = !operationInProgress,
            backContentDescription = "Назад к папкам",
            closeContentDescription = "Закрыть создание папки",
            showProgress = false,
        )
        Text(
            text = "Название папки",
            color = colors.textAdditional50,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        BasicTextField(
            value = folderName,
            onValueChange = { folderName = it },
            enabled = !operationInProgress,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(colors.searchBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp)
                .testTag(FOLDER_NAME_FIELD_TAG),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (folderName.isEmpty()) {
                        Text(
                            text = "Название",
                            color = colors.textAdditional30,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (folderName.isNotEmpty() && !nameValid) {
            Text(
                text = validationError,
                color = colors.indicatorRed,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Добавить чаты",
                color = colors.textAdditional50,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = colors.iconBase,
                modifier = Modifier.size(18.dp),
            )
        }
        FolderChatSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            enabled = !operationInProgress,
        )
        error?.let {
            FolderInlineMessage(it, onDismissError)
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(FOLDER_CHAT_LIST_TAG),
        ) {
            items(filteredStreams, key = Stream::uuid) { stream ->
                val checked = stream.uuid in selectedUuids
                FolderSelectableChatRow(
                    stream = stream,
                    checked = checked,
                    enabled = !operationInProgress,
                    onCheckedChange = { selected ->
                        selectedUuids = if (selected) {
                            (selectedUuids + stream.uuid).distinct()
                        } else {
                            selectedUuids - stream.uuid
                        }
                    },
                    avatar = { streamAvatar(stream) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBack,
                enabled = !operationInProgress,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.cardBackgroundActive,
                    contentColor = colors.primary,
                    disabledContainerColor = colors.cardBackgroundActive,
                    disabledContentColor = colors.textAdditional30,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
            ) {
                Text("Отмена", fontSize = 14.sp)
            }
            Button(
                onClick = { onCreate(folderName, selectedUuids.toSet()) },
                enabled = nameValid && !operationInProgress,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.iconDisable,
                    disabledContentColor = colors.textAdditional50,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag(FOLDER_CREATE_SUBMIT_TAG),
            ) {
                if (operationInProgress) {
                    CircularProgressIndicator(
                        color = colors.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("Создать", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FolderTopBar(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    enabled: Boolean,
    backContentDescription: String,
    closeContentDescription: String,
    showProgress: Boolean,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = backContentDescription,
                tint = colors.iconBase,
                modifier = Modifier.size(width = 10.dp, height = 20.dp),
            )
        }
        Text(
            text = title,
            color = colors.textHeaders,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        if (showProgress) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 15.dp)
                    .size(18.dp),
            )
        } else {
            IconButton(
                onClick = onClose,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = closeContentDescription,
                    tint = colors.iconBase,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderChatSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(colors.searchBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.iconBase,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .weight(1f)
                .testTag(FOLDER_CHAT_SEARCH_TAG),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Поиск пользователей...",
                            color = colors.textAdditional30,
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
private fun FolderSelectableChatRow(
    stream: Stream,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    avatar: @Composable () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) },
            )
            .testTag("$FOLDER_CHAT_ROW_TAG_PREFIX${stream.uuid}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                checkmarkColor = colors.onPrimary,
                uncheckedColor = colors.iconBase,
            ),
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(6.dp))
        avatar()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = stream.name,
                color = colors.textHeaders,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folderStreamPreview(stream),
                color = colors.textAdditional50,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (stream.unreadCount > 0) {
                UnreadBadge(count = stream.unreadCount)
            }
            Text(
                text = folderStreamTime(stream),
                color = colors.textAdditional50,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
    HorizontalDivider(
        color = colors.cardBackgroundActive,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun FolderInlineMessage(
    message: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalWorkspaceColorsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(colors.infoCardBackground, RoundedCornerShape(8.dp))
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = colors.textAdditional50,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        Text(
            text = "Закрыть",
            color = colors.primary,
            fontSize = 12.sp,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(10.dp),
        )
    }
}

internal fun folderStreamPreview(stream: Stream): String =
    stream.lastMessage?.payload?.content
        ?.replace(Regex("<[^>]+>"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: stream.description.trim().takeIf(String::isNotEmpty)
        ?: "Сообщений пока нет"

internal fun folderDisplayTitle(folder: FolderResponseData): String =
    folder.localizedTitle()

internal fun folderStreamTime(stream: Stream): String {
    val source = stream.lastMessage?.createdAt ?: stream.updatedAt
    return runCatching {
        OffsetDateTime.parse(source)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
}

internal const val FOLDER_DISPLAY_ROOT_TAG = "folder-display-root"
internal const val FOLDER_CREATE_ROOT_TAG = "folder-create-root"
internal const val FOLDER_CREATE_OPEN_TAG = "folder-create-open"
internal const val FOLDER_NAME_FIELD_TAG = "folder-name-field"
internal const val FOLDER_CHAT_SEARCH_TAG = "folder-chat-search"
internal const val FOLDER_CHAT_LIST_TAG = "folder-chat-list"
internal const val FOLDER_CREATE_SUBMIT_TAG = "folder-create-submit"
internal const val FOLDER_DISPLAY_ROW_TAG_PREFIX = "folder-display-row-"
internal const val FOLDER_CHAT_ROW_TAG_PREFIX = "folder-chat-row-"
