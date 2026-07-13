package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun Avatar(
    avatarUrlString: String?,
    baseUrl: String,
    color: Int?,
    name: String,
    size: Int,
    hasPadding: Boolean
) {
    if (avatarUrlString != null) {
        val finalAvatarUrl: String
        if (avatarUrlString.startsWith("https://", ignoreCase = true)) {
            finalAvatarUrl = avatarUrlString
        } else {
            finalAvatarUrl = "${baseUrl}${avatarUrlString}"
        }
        val imageRequest = ImageRequest.Builder(LocalContext.current)
            .data(finalAvatarUrl)
            .build()
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(end = if (hasPadding) 12.dp else 0.dp)
                .size(size.dp)
                .clip(CircleShape),
        )
    }
}