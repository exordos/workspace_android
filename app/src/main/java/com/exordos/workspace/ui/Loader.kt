package com.exordos.workspace.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.exordos.workspace.R

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
