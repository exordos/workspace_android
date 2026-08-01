package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.request.CachePolicy
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.data.workspaceStorageKey
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun ImageMessageView(
    text: String,
    imageUrls: List<String>,
    viewModel: ChatDialogViewModel,
    item: MessageResponse,
    navController: NavHostController,
    onImageLoad: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    var fullscreenImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val openMenu = {
        menuExpanded = true
        viewModel.openMessageMenu(item.uuid)
    }
    val closeMenu = {
        menuExpanded = false
        viewModel.closeMessageMenu(item.uuid)
    }
    val deletingMessages by
        viewModel.deletingMessageUuids.collectAsStateWithLifecycle()
    val hasReplySession by
        viewModel.hasReplySession.collectAsStateWithLifecycle()
    val colors = LocalWorkspaceColorsPalette.current
    val accessToken by viewModel.userViewModel.repo.accessTokenFlow.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val baseUrl by viewModel.userViewModel.repo.baseUrlFlow.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val accountId by viewModel.userViewModel.activeAccountId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val headers = remember(accessToken) {
        NetworkHeaders.Builder()
            .apply {
                accessToken
                    ?.takeIf(String::isNotBlank)
                    ?.let { set("Authorization", "Bearer $it") }
            }
            .build()
    }
    val imageRequests = remember(imageUrls, baseUrl, accountId, headers, context) {
        imageUrls.map { imageUrl ->
            val parsedUrl = UrnParser.parseUrl(imageUrl, baseUrl.orEmpty()).orEmpty()
            val resolvedUrl = if (parsedUrl.startsWith("/")) {
                "${baseUrl.orEmpty()}$parsedUrl"
            } else {
                parsedUrl
            }
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .httpHeaders(headers)
                .apply {
                    if (accountId.isNullOrBlank()) {
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    } else {
                        val cacheKey = workspaceStorageKey(
                            "${accountId.orEmpty()}\u0000$resolvedUrl",
                        )
                        memoryCacheKey(cacheKey)
                        diskCacheKey(cacheKey)
                    }
                }
                .build()
        }
    }
    val messageBubble: @Composable (Boolean) -> Unit = { interactive ->
        val interactionModifier = if (interactive) {
            Modifier
                .pointerInput(item.uuid) {
                    detectTapGestures(onLongPress = { openMenu() })
                }
                .semantics {
                    onLongClick(label = "Действия с сообщением") {
                        openMenu()
                        true
                    }
                }
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (item.isOwn) {
                        colors.messageOwnBackground
                    } else {
                        colors.messageBackground
                    },
                    messageBubbleShape(item.isOwn),
                )
                .then(interactionModifier)
                .padding(10.dp),
        ) {
            MessageHeader(item, viewModel)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                imageRequests.forEachIndexed { index, imageRequest ->
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Изображение в сообщении",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(230.dp)
                            .then(
                                if (imageRequests.size == 1) {
                                    Modifier.heightIn(min = 120.dp, max = 280.dp)
                                } else {
                                    Modifier.height(150.dp)
                                },
                            )
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.background, RoundedCornerShape(9.dp))
                            .then(
                                if (interactive) {
                                    Modifier.combinedClickable(
                                        onClick = { fullscreenImageIndex = index },
                                        onLongClick = { openMenu() },
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        onState = { state ->
                            if (
                                interactive &&
                                state is AsyncImagePainter.State.Success
                            ) {
                                onImageLoad()
                            }
                        },
                    )
                }
            }
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    color = colors.textHeaders,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            MessageFooter(item)
        }
    }

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Box {
            messageBubble(!selectionMode)
            MessageActionsMenu(
                expanded = menuExpanded,
                item = item,
                onDismiss = closeMenu,
                onReaction = { reaction ->
                    viewModel.onMessageReactionTap(
                        messageUuid = item.uuid,
                        emojiName = reaction.emojiName,
                        equivalentEmojiNames =
                            reaction.equivalentEmojiNames,
                    )
                    closeMenu()
                },
                onOpenReactionPicker = {
                    closeMenu()
                    viewModel.openMessageReactionPicker(item.uuid)
                },
                onEdit = {
                    viewModel.onEditMessageClicked(item)
                    closeMenu()
                },
                isDeleting = item.uuid in deletingMessages,
                onDelete = {
                    viewModel.deleteMessage(item)
                    closeMenu()
                },
                onCopy = {
                    viewModel.copyMessageText(context, item)
                    closeMenu()
                },
                onQuote = {
                    viewModel.onQuoteMessageClicked(item)
                    closeMenu()
                },
                onQuoteFragment = { fragment ->
                    viewModel.onQuoteMessageClicked(item, fragment)
                },
                canAddReply = hasReplySession,
                onAddQuote = {
                    viewModel.onAddQuoteMessageClicked(item)
                    closeMenu()
                },
                onAddQuoteFragment = { fragment ->
                    viewModel.onAddQuoteMessageClicked(item, fragment)
                },
                onForward = {
                    viewModel.beginForward(item)
                    closeMenu()
                },
                isSelected = isSelected,
                onToggleSelection = {
                    onToggleSelection()
                    closeMenu()
                },
            )
        }
    }

    fullscreenImageIndex
        ?.let(imageRequests::getOrNull)
        ?.let { imageRequest ->
            FullscreenZoomableImage(
                model = imageRequest,
                contentDescription = "Изображение в сообщении",
                onDismiss = { fullscreenImageIndex = null },
            )
        }
}

@Composable
fun FullscreenZoomableImage(
    model: Any?,
    contentDescription: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        fun resetZoom() {
            scale = 1f
            offset = Offset.Zero
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { resetZoom() },
                        onTap = {
                            if (scale <= 1.01f) onDismiss()
                        },
                    )
                },
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { resetZoom() })
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_small),
                    contentDescription = "Закрыть",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
fun TappableAsyncImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var showFullscreen by rememberSaveable { mutableStateOf(false) }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.clickable { showFullscreen = true },
    )
    if (showFullscreen) {
        FullscreenZoomableImage(
            model = model,
            contentDescription = contentDescription,
            onDismiss = { showFullscreen = false },
        )
    }
}
