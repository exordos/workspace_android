package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.R

@Composable
fun AnimatedGif(
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(R.drawable.loader)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}