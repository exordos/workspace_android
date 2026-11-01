package com.exordos.workspace.ui

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
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import com.exordos.workspace.data.remote.AuthHeader
import com.exordos.workspace.ui.theme.InterFontFamily
import com.exordos.workspace.ui.theme.LocalWorkspaceColorsPalette

@Composable
fun Avatar(
    avatarUrn: String?,
    baseUrl: String,
    authHeaders: List<AuthHeader>,
    color: Int?,
    name: String,
    modifier: Modifier,
    initialFontSize: TextUnit = 24.sp,
) {
    val imageRequest = avatarImageRequest(LocalContext.current, avatarUrn, baseUrl, authHeaders)
    if (imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            imageLoader = avatarImageLoader(LocalContext.current),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(CircleShape),
        )
    } else {
        val avatarColor = try {
            Color(0xFF000000 or (color?.toLong() ?: 0))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
        Box(
            modifier = modifier
                .background(color = avatarColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = name.firstOrNull()?.titlecase() ?: "",
                color = Color.White,
                fontSize = initialFontSize,
                fontFamily = InterFontFamily,
            )
        }
    }
}
