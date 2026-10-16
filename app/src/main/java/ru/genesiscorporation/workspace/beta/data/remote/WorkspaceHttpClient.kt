package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import java.io.IOException

internal fun permitsWorkspaceTransport(url: Url): Boolean =
    url.protocol == URLProtocol.HTTPS || url.protocol == URLProtocol.WSS ||
        ((url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.WS) &&
            url.host.lowercase().removeSurrounding("[", "]") in setOf("localhost", "127.0.0.1", "::1"))

private val secureWorkspaceTransport = createClientPlugin("SecureWorkspaceTransport") {
    // SendingRequest also covers redirects; onRequest alone only checks the initial URL.
    on(SendingRequest) { request, _ ->
        if (!permitsWorkspaceTransport(request.url.build())) {
            // Never include the URL: it may contain credentials or private query parameters.
            throw IOException("Используйте HTTPS/WSS. Незащищённое подключение разрешено только к localhost.")
        }
    }
}

/** Shared network boundary for Workspace API, realtime and image requests. */
internal fun workspaceHttpClient(
    engine: HttpClientEngine? = null,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val securedConfiguration: HttpClientConfig<*>.() -> Unit = {
        install(secureWorkspaceTransport)
        configure()
    }
    // The factory constructor retains Ktor's ownership of the default engine.
    return if (engine == null) HttpClient(CIO, securedConfiguration)
    else HttpClient(engine, securedConfiguration)
}
