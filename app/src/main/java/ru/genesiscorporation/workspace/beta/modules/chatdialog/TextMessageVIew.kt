package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.QuotedMessagePart
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.EnhancedMarkdown
import ru.genesiscorporation.workspace.beta.ui.ReferenceMessageBase
import ru.genesiscorporation.workspace.beta.ui.theme.InterFontFamily
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.context

@Composable
fun TextMessageView(
    item: MessageResponse,
    viewModel: ChatDialogViewModel,
    navController: NavHostController,
    onImageLoad: () -> Unit
) {
    val quotedMessages by viewModel.quotedMessages.collectAsState()
    val zone = ZoneId.systemDefault()
    val hhmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showFullscreen by remember { mutableStateOf(false) }
    val bubbleShape = if (item.isOwn) {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 8.dp,
            bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 0.dp,
            bottomEnd = 8.dp
        )
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        val nextMessage = viewModel.nextMessageByUuid(item.uuid)
        if (!viewModel.isDirectMessages && !item.isOwn) {
            val bottomPadding = if (item.reactions.isEmpty()) 0.dp else 8.dp
            if (nextMessage != null) {
                if (nextMessage.authorUuid != item.authorUuid) {
                    Box(
                        Modifier
                            .clickable(
                                onClick = {
                                    navController.navigate(
                                        ChatFlow.ChatUserInfo(
                                            item.user?.displayableName() ?: "",
                                            item.authorUuid,
                                            item.user?.avatar ?: "",
                                            ""
                                        )
                                    )
                                }
                            )
                    ) {
                        Avatar(
                            item.user?.avatar,
                            viewModel.userViewModel.baseUrl.value ?: "",
                            viewModel.client.authHeaders(),
                            null,
                            item.user?.displayableName() ?: "",
                            Modifier
                                .padding(end = 4.dp, bottom = bottomPadding)
                                .size(30.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(color = Color.Transparent, shape = CircleShape)
                    )
                }
            } else {
                Box(
                    Modifier
                        .clickable(
                            onClick = {
                                navController.navigate(
                                    ChatFlow.ChatUserInfo(
                                        item.user?.displayableName() ?: "",
                                        item.authorUuid,
                                        item.user?.avatar ?: "",
                                        item.user?.email ?: ""
                                    )
                                )
                            }
                        )
                ) {
                    Avatar(
                        item.user?.avatar,
                        viewModel.userViewModel.baseUrl.value ?: "",
                        viewModel.client.authHeaders(),
                        null,
                        item.user?.displayableName() ?: "",
                        Modifier
                            .padding(end = 4.dp, bottom = bottomPadding)
                            .size(30.dp)
                    )
                }
            }
        }
        Column {
            Box {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .background(
                            if (item.isOwn)
                                LocalWorkspaceColorsPalette.current.messageOwnBackground
                            else LocalWorkspaceColorsPalette.current.messageBackground,
                            shape = bubbleShape
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                menuExpanded = true
                            }
                        )
                        .padding(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .weight(2f, fill = false)
                    ) {
                        val defaultName = if (item.isOwn) "Я" else "Собеседник"
                        Text(
                            text = item.user?.displayableName() ?: defaultName,
                            color = if (item.isOwn) LocalWorkspaceColorsPalette.current.indicatorBlue else LocalWorkspaceColorsPalette.current.indicatorPurple,
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        val messageElements = MarkdownPayloadParser.parse(item.payload.content)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            messageElements.forEach { element ->
                                when (element) {
                                    is MessageElement.Image -> {
                                        val baseUrl by viewModel.userViewModel.repo.baseUrlFlow.collectAsStateWithLifecycle(
                                            initialValue = ""
                                        )
                                        val authHeaders = viewModel.client.authHeaders()
                                        val headers = NetworkHeaders.Builder()
                                            .set(authHeaders.first().title, authHeaders.first().value)
                                            .build()
                                        val imageUrl = "$baseUrl/api/workspace/v1/messenger/files/${element.uuid}/actions/download"
                                        if (imageUrl != null) {
                                            val imageRequest = ImageRequest.Builder(LocalContext.current)
                                                .data(imageUrl)
                                                .httpHeaders(headers)
                                                .build()
                                            AsyncImage(
                                                model = imageRequest,
                                                contentDescription = null,
                                                modifier = Modifier.clickable { showFullscreen = true },
                                                onState = { state ->
                                                    when (state) {
                                                        is AsyncImagePainter.State.Success -> {
                                                            onImageLoad()
                                                        }

                                                        else -> Unit
                                                    }
                                                }
                                            )
                                            if (showFullscreen) {
                                                FullscreenZoomableImage(
                                                    model = imageRequest,
                                                    contentDescription = null,
                                                    onDismiss = { showFullscreen = false },
                                                )
                                            }
                                        }
                                    }

                                    is MessageElement.File -> FileAttachmentRow(
                                        element = element,
                                        viewModel = viewModel,
                                        onOpenFile = {  },
                                    )

                                    is MessageElement.Quote -> QuotedMessagePartView(
                                        element.uuid,
                                        item.isOwn,
                                        viewModel,
                                        navController
                                    )

                                    is MessageElement.PlainText -> EnhancedMarkdown(
                                        markdown = element.text,
                                        style = TextStyle(
                                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                                            fontSize = 14.sp,
                                            fontFamily = InterFontFamily,
                                        ),
                                        navController = navController,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.widthIn(min = 20.dp))
                    val instant = Instant.parse(item.createdAt)
                    Text(
                        text = instant.atZone(zone).format(hhmmFormatter),
                        color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                        fontSize = 14.sp,
                        fontFamily = InterFontFamily,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("👍", "❤️", "😂", "😮", "😢").forEach { emoji ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.onReactionTap(item.uuid, emoji)
                                    }
                                    menuExpanded = false
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_quote),
                                    contentDescription = ""
                                )
                                Text(
                                    "Ответить",
                                    color = LocalWorkspaceColorsPalette.current.textHeaders,
                                    fontSize = 14.sp,
                                    fontFamily = InterFontFamily
                                )
                            }
                        },
                        onClick = {
                            viewModel.onQuoteMessageClicked(item)
                            menuExpanded = false
                        }
                    )
                    if (quotedMessages.count() > 0) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_add_quote),
                                        contentDescription = ""
                                    )
                                    Text(
                                        "Добавить цитату",
                                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onAddQuoteMessageClicked(item)
                                menuExpanded = false
                            }
                        )
                    }
                    if (item.isOwn) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_edit),
                                        contentDescription = ""
                                    )
                                    Text(
                                        "Изменить",
                                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onEditMessageClicked(item)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
            if (!item.reactions.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (reaction in item.reactions) {
                        val hasMyReaction = viewModel.hasMyReaction(reaction.key, item.uuid)
                        val unicodeEmoji = remember(reaction.key) { toUnicodeEmoji(reaction.key) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .padding(top = 4.dp, bottom = 8.dp)
                                .background(
                                    if (hasMyReaction) LocalWorkspaceColorsPalette.current.primary else LocalWorkspaceColorsPalette.current.cardBackgroundActive,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable(
                                    onClick = {
                                        scope.launch {
                                            viewModel.onMessageReactionTap(item.uuid, reaction.key)
                                        }
                                    }
                                ),
                        ) {
                            Text(unicodeEmoji)
                            Text(text ="${reaction.value}",
                                color = LocalWorkspaceColorsPalette.current.textHeaders,
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable fun QuotedMessagePartView(
    quotedMessageUuid: String,
    isOwn: Boolean,
    viewModel: ChatDialogViewModel,
    navController: NavHostController
) {
    val streamTopicMessages by viewModel.streamTopicMessages.collectAsStateWithLifecycle()
    Column() {
        if (quotedMessageUuid != null) {
            val messages = streamTopicMessages["${viewModel.chatId}.${viewModel.topicUuid ?: ""}"]
            val message = messages?.firstOrNull { it.uuid == quotedMessageUuid }
            ReferenceMessageBase(
                Modifier
                    .padding(vertical = 4.dp),
                shouldClose = false, onCloseTap = {}
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOwn) LocalWorkspaceColorsPalette.current.messageOwnSelectedBg else LocalWorkspaceColorsPalette.current.messageOwnBackground)
                        .padding(8.dp)
                ) {
                    Text(
                        message?.user?.displayableName() ?: "Цитируемое сообщение",
                        color = LocalWorkspaceColorsPalette.current.indicatorOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val messageContent = message?.payload?.content
                    val messageElements = MarkdownPayloadParser.parse(messageContent ?: "")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        messageElements.forEach { element ->
                            when (element) {
                                is MessageElement.Image -> {
                                    Text("image")
//                                    val baseUrl by viewModel.userViewModel.repo.baseUrlFlow.collectAsStateWithLifecycle(
//                                        initialValue = ""
//                                    )
//                                    val authHeaders = viewModel.client.authHeaders()
//                                    val headers = NetworkHeaders.Builder()
//                                        .set(authHeaders.first().title, authHeaders.first().value)
//                                        .build()
//                                    val imageUrl = "$baseUrl/api/workspace/v1/messenger/files/${element.uuid}/actions/download"
//                                    if (imageUrl != null) {
//                                        val imageRequest = ImageRequest.Builder(LocalContext.current)
//                                            .data(imageUrl)
//                                            .httpHeaders(headers)
//                                            .build()
//                                        AsyncImage(
//                                            model = imageRequest,
//                                            contentDescription = null,
//                                            modifier = Modifier.clickable { showFullscreen = true },
//                                            onState = { state ->
//                                                when (state) {
//                                                    is AsyncImagePainter.State.Success -> {
//                                                        onImageLoad()
//                                                    }
//
//                                                    else -> Unit
//                                                }
//                                            }
//                                        )
//                                    }
                                }

                                is MessageElement.File -> FileAttachmentRow(
                                    element = element,
                                    viewModel = viewModel,
                                    onOpenFile = { },
                                )

                                is MessageElement.Quote -> QuotedMessagePartView(
                                    element.uuid,
                                    false,
                                    viewModel,
                                    navController
                                )

                                is MessageElement.PlainText -> EnhancedMarkdown(
                                    markdown = element.text,
                                    style = TextStyle(
                                        color = LocalWorkspaceColorsPalette.current.textHeaders,
                                        fontSize = 14.sp,
                                        fontFamily = InterFontFamily,
                                    ),
                                    navController = navController,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                "Не удалось загрузить сообщение",
                color = LocalWorkspaceColorsPalette.current.textAdditional30,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileAttachmentRow(
    element: MessageElement.File,
    viewModel: ChatDialogViewModel,
    onOpenFile: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by viewModel
        .stateFor(element.uuid, element.fileName)
        .collectAsStateWithLifecycle()
    LaunchedEffect(element.uuid, element.fileName) {
        if (state is FileUiState.Idle) {
            viewModel.load(element.uuid, element.fileName)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = state is FileUiState.Ready) {
                scope.launch {
                    when (val result = viewModel.storage.saveToDownloads(element.uuid, element.fileName)) {
                        is ApiResult.Success -> openDownloads(context)
                        is ApiResult.Error -> {
                            Toast.makeText(context, result.error.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file_attachment),
            contentDescription = null,
            tint = LocalWorkspaceColorsPalette.current.primary
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text = element.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state is FileUiState.Loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

suspend fun AttachmentStorage.saveToDownloads(
    uuid: String,
    fileName: String,
): ApiResult<Uri, ApiError> {
    val local = when (val result = loadOrDownload(uuid, fileName)) {
        is ApiResult.Success -> result.value.localFile
        is ApiResult.Error -> return result
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, guessMimeType(fileName))
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: return ApiResult.Error(ApiError("Cannot create download", "SAVE_FAILED"))
    resolver.openOutputStream(uri)?.use { out ->
        local.inputStream().use { it.copyTo(out) }
    } ?: return ApiResult.Error(ApiError("Cannot write download", "SAVE_FAILED"))
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return ApiResult.Success(uri)
}

fun openDownloads(context: Context) {
    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun guessMimeType(fileName: String): String =
    MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
        ?: "*/*"

sealed interface FileUiState {
    data object Idle : FileUiState
    data object Loading : FileUiState
    data class Ready(val attachment: LocalAttachment) : FileUiState
    data class Error(val message: String) : FileUiState
}