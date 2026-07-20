package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun Avatar(
    avatarUrn: String?,
    baseUrl: String,
    color: Int?,
    name: String,
    size: Int,
    hasPadding: Boolean
) {
    val avatarUrl = UrnParser.parseUrl(avatarUrn, baseUrl)
    if (avatarUrl != null) {
        val imageRequest = ImageRequest.Builder(LocalContext.current)
            .data(avatarUrl)
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
    } else {
        val avatarColor = try {
            Color(0xFF000000 or (color?.toLong() ?: 0))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color = avatarColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = name.firstOrNull()?.titlecase() ?: "",
                color = Color.White,
                fontSize = 24.sp
            )
        }
    }
}