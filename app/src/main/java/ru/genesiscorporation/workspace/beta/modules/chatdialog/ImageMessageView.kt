package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import ru.genesiscorporation.workspace.beta.data.UrnParser
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
) {
    var fullscreenModel by remember { mutableStateOf<ImageRequest?>(null) }
    val colors = LocalWorkspaceColorsPalette.current
    val accessToken by viewModel.userViewModel.repo.accessTokenFlow.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val email by viewModel.userViewModel.repo.emailFlow.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val baseUrl by viewModel.userViewModel.repo.baseUrlFlow.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val authHeaders = viewModel.client.authHeaders(accessToken.orEmpty(), email.orEmpty())
    val headers = NetworkHeaders.Builder()
        .set(authHeaders.first().title, authHeaders.first().value)
        .build()
    val imageRequests = imageUrls.map { imageUrl ->
        val parsedUrl = UrnParser.parseUrl(imageUrl, baseUrl.orEmpty()).orEmpty()
        val resolvedUrl = if (parsedUrl.startsWith("/")) {
            "${baseUrl.orEmpty()}$parsedUrl"
        } else {
            parsedUrl
        }
        ImageRequest.Builder(LocalContext.current)
            .data(resolvedUrl)
            .httpHeaders(headers)
            .build()
    }

    MessageRow(
        item = item,
        viewModel = viewModel,
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (item.isOwn) colors.messageOwnBackground else colors.messageBackground,
                    messageBubbleShape(item.isOwn),
                )
                .padding(10.dp),
        ) {
            MessageHeader(item, viewModel)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                imageRequests.forEach { imageRequest ->
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
                            .clickable { fullscreenModel = imageRequest },
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) onImageLoad()
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

    fullscreenModel?.let { imageRequest ->
        FullscreenZoomableImage(
            model = imageRequest,
            contentDescription = "Изображение в сообщении",
            onDismiss = { fullscreenModel = null },
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
                Text(
                    text = "Закрыть",
                    color = Color.White,
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
