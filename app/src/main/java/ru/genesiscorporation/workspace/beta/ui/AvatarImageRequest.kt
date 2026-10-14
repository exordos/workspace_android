package ru.genesiscorporation.workspace.beta.ui

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.util.AttributeKey
import ru.genesiscorporation.workspace.beta.data.UrnParser
import ru.genesiscorporation.workspace.beta.data.remote.AuthHeader
import java.net.URI

private data class AvatarCredentialScope(val origin: Triple<String, String, Int>?, val headerNames: Set<String>)
private val credentialScope = AttributeKey<AvatarCredentialScope>("AvatarCredentialScope")
internal val avatarCredentialBoundary = createClientPlugin("AvatarCredentialBoundary") {
    onRequest { request, _ ->
        request.attributes.put(credentialScope, AvatarCredentialScope(
            httpOrigin(request.url.buildString()), request.headers.names().toSet(),
        ))
    }
    // This hook runs for every redirect as well as the initial request. Ktor's default
    // redirect handling only removes Authorization on an authority change, not all
    // supplied credentials or a scheme change on the same host/port.
    on(SendingRequest) { request, _ ->
        val scope = request.attributes[credentialScope]
        if (scope.origin == null || scope.origin != httpOrigin(request.url.buildString())) {
            scope.headerNames.forEach { request.headers.remove(it) }
        }
    }
}

@Volatile private var sharedAvatarImageLoader: ImageLoader? = null

internal fun avatarImageLoader(context: Context): ImageLoader = sharedAvatarImageLoader
    ?: synchronized(credentialScope) {
        sharedAvatarImageLoader ?: createAvatarImageLoader(context).also { sharedAvatarImageLoader = it }
    }

internal fun createAvatarImageLoader(context: Context): ImageLoader =
    SingletonImageLoader.get(context).newBuilder()
        .components {
            add(KtorNetworkFetcherFactory(httpClient = {
                HttpClient { install(avatarCredentialBoundary) }
            }))
        }
        .build()

internal fun avatarImageRequest(
    context: Context,
    avatarUrn: String?,
    baseUrl: String,
    authHeaders: List<AuthHeader>,
): ImageRequest? {
    val url = UrnParser.parseUrl(avatarUrn, baseUrl) ?: return null
    return ImageRequest.Builder(context)
        .data(url)
        .apply {
            if (UrnParser.namespace(avatarUrn) == "image" && isWorkspaceImageOrigin(baseUrl, url)) {
                httpHeaders(NetworkHeaders.Builder().apply {
                    authHeaders.forEach { set(it.title, it.value) }
                }.build())
            }
        }
        .build()
}

/** Fail closed on malformed origins; scheme and effective port are part of the credential boundary. */
internal fun isWorkspaceImageOrigin(baseUrl: String, imageUrl: String): Boolean {
    val base = runCatching { URI(baseUrl) }.getOrNull() ?: return false
    if (base.rawQuery != null || base.rawFragment != null) return false
    val trusted = httpOrigin(baseUrl) ?: return false
    return trusted == httpOrigin(imageUrl)
}

private fun httpOrigin(value: String): Triple<String, String, Int>? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (uri.rawUserInfo != null || uri.port < -1 || uri.port > 65535) return null
    val port = if (uri.port == -1) (if (scheme == "https") 443 else 80) else uri.port
    return Triple(scheme, host, port)
}
