package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.ChatFlow
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.ui.Avatar
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.encoding.Base64

@Composable
fun ImageMessageView(
    text: String,
    imageUrn: String?,
    viewModel: ChatDialogViewModel,
    item: MessageResponse,
    navController: NavHostController,
    onImageLoad: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val hhmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        val nextMessage = viewModel.nextMessageByUuid(item.uuid)
        if (!viewModel.isDirectMessages && !item.isOwn) {
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
                            null,
                            item.user?.displayableName() ?: "",
                            30,
                            true
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(30.dp)
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
                        null,
                        item.user?.displayableName() ?: "",
                        30,
                        true
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .background(
                    if (item.isOwn)
                        LocalWorkspaceColorsPalette.current.messageOwnBackground
                    else LocalWorkspaceColorsPalette.current.messageBackground,
                    shape = bubbleShape
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
                    fontWeight = FontWeight.Medium
                )
                val accessToken by viewModel.userViewModel.repo.accessTokenFlow.collectAsStateWithLifecycle(initialValue = "")
                val email by viewModel.userViewModel.repo.emailFlow.collectAsStateWithLifecycle(initialValue = "")
                val baseUrl by viewModel.userViewModel.repo.baseUrlFlow.collectAsStateWithLifecycle(initialValue = "")
                val authHeaders = viewModel.client.authHeaders(accessToken ?: "", email ?: "")
                val headers = NetworkHeaders.Builder()
                    .set(authHeaders.first().title, authHeaders.first().value)
                    .build()
                val imageUrl = UrnParser.parseUrl(imageUrn, baseUrl ?: "")
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
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            color = LocalWorkspaceColorsPalette.current.textHeaders,
                            fontSize = 14.sp
                        )
                    }
                }
            }
//                TappableAsyncImage(
//                    model = imageRequest,
//                    contentDescription = null,
//                )
            Spacer(modifier = Modifier.widthIn(min = 20.dp))
            val instant = Instant.parse(item.createdAt)
            Text(
                text = instant.atZone(zone).format(hhmmFormatter),
                color = LocalWorkspaceColorsPalette.current.messageTimeColor,
                fontSize = 14.sp,
            )
        }
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
                            // Tap background when not zoomed → dismiss
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
                            if (newScale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { resetZoom() },
                        )
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Text("Закрыть")
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
    var showFullscreen by remember { mutableStateOf(false) }
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
