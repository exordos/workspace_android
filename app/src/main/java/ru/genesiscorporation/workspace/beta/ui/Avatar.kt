package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.genesiscorporation.workspace.beta.LocalUserState
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.data.workspaceStorageKey
import ru.genesiscorporation.workspace.beta.ui.theme.LocalWorkspaceColorsPalette
import java.net.URI

@Composable
fun Avatar(
    avatarUrn: String?,
    baseUrl: String,
    color: Int?,
    name: String,
    size: Int,
    hasPadding: Boolean,
    ownerAccountId: String? = null,
) {
    val user = LocalUserState.current
    val activeAccountId by user.activeAccountId.collectAsStateWithLifecycle()
    val accessToken by user.accessToken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imageSource = remember(avatarUrn, baseUrl) {
        resolveWorkspaceAvatarSource(avatarUrn, baseUrl)
    }
    val imageRequest = remember(
        imageSource,
        ownerAccountId,
        activeAccountId,
        accessToken,
        context,
    ) {
        imageSource?.let { source ->
            if (
                source.requiresAuthentication &&
                (
                    activeAccountId.isNullOrBlank() ||
                        ownerAccountId != null && ownerAccountId != activeAccountId ||
                        accessToken.isNullOrBlank()
                    )
            ) {
                return@let null
            }
            ImageRequest.Builder(context)
                .data(source.url)
                .apply {
                    if (source.requiresAuthentication) {
                        httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Authorization", "Bearer $accessToken")
                                .build(),
                        )
                        val cacheKey = workspaceStorageKey(
                            "${activeAccountId.orEmpty()}\u0000${source.url}",
                        )
                        memoryCacheKey(cacheKey)
                        diskCacheKey(cacheKey)
                    } else if (source.url.isBlank()) {
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
                .build()
        }
    }
    if (imageRequest != null) {
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
                .padding(end = if (hasPadding) 12.dp else 0.dp)
                .size(size.dp)
                .background(color = avatarColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = name.firstOrNull()?.titlecase() ?: "",
                color = Color.White,
                fontSize = (size * 0.44f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal data class WorkspaceAvatarSource(
    val url: String,
    val requiresAuthentication: Boolean,
)

internal fun resolveWorkspaceAvatarSource(
    avatarUrn: String?,
    baseUrl: String,
): WorkspaceAvatarSource? {
    val parsedUrl = UrnParser.parseUrl(avatarUrn, baseUrl) ?: return null
    val requiresAuthentication = parsedUrl.startsWith("/")
    val resolvedUrl = if (requiresAuthentication) {
        val canonicalBaseUrl = baseUrl.trim().trimEnd('/')
        if (canonicalBaseUrl.isBlank()) return null
        "$canonicalBaseUrl$parsedUrl"
    } else {
        parsedUrl
    }
    val uri = runCatching { URI(resolvedUrl) }.getOrNull() ?: return null
    if (
        !uri.scheme.equals("https", ignoreCase = true) &&
        !uri.scheme.equals("http", ignoreCase = true)
    ) {
        return null
    }
    if (uri.host.isNullOrBlank()) return null
    return WorkspaceAvatarSource(
        url = resolvedUrl,
        requiresAuthentication = requiresAuthentication,
    )
}
