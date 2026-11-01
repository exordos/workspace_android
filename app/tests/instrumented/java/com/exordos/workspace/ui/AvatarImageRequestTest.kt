package com.exordos.workspace.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.network.httpHeaders
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.CachePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.data.remote.AuthHeader
import com.exordos.workspace.ui.theme.WokspaceTheme
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AvatarImageRequestTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    // Deliberately synthetic; these tests never read a saved session.
    private val credentials = listOf(AuthHeader("Authorization", "Bearer cassi-avatar-fixture"),
        AuthHeader("X-Workspace-Session", "cassi-avatar-fixture"))

    @Test fun externalUrlContainingImageGetsNoCredentialsOnItsFirstCoilRequest() = runBlocking {
        AvatarServer().use { external ->
            execute("urn:url:${external.url}/path/:image:/avatar.png", "https://workspace.invalid")
            assertNull(external.requests.single().authorization)
            assertEquals("/path/:image:/avatar.png", external.requests.single().path)
        }
    }

    @Test fun externalUrlOnWorkspaceOriginStillGetsNoCredentials() = runBlocking {
        AvatarServer().use { workspace ->
            execute("urn:url:${workspace.url}/:image:/avatar.png", workspace.url)
            assertNull(workspace.requests.single().authorization)
        }
    }

    @Test fun imageNamespaceGetsCredentialsAndDecodesThroughCoilKtor() = runBlocking {
        AvatarServer().use { workspace ->
            execute("urn:image:00000000-0000-4000-8000-000000000057", workspace.url)
            assertEquals(credentials.first().value, workspace.requests.single().authorization)
            assertEquals("/api/workspace/v1/messenger/files/00000000-0000-4000-8000-000000000057/actions/download",
                workspace.requests.single().path)
        }
    }

    @Test fun crossOriginPortRedirectDropsAuthorization() = runBlocking {
        AvatarServer().use { external ->
            AvatarServer(redirect = "${external.url}/redirected.png").use { workspace ->
                execute("urn:image:redirect", workspace.url)
                assertEquals(credentials.first().value, workspace.requests.single().authorization)
                assertNull(external.requests.single().authorization)
                assertNull(external.requests.single().session)
            }
        }
    }

    @Test fun crossOriginHostRedirectDropsAuthorization() = runBlocking {
        AvatarServer().use { external ->
            AvatarServer(redirect = "${external.url.replace("127.0.0.1", "localhost")}/redirected.png").use { workspace ->
                execute("urn:image:redirect", workspace.url)
                assertEquals(credentials.first().value, workspace.requests.single().authorization)
                assertNull(external.requests.single().authorization)
                assertNull(external.requests.single().session)
            }
        }
    }

    @Test fun sameOriginRedirectPreservesAuthorization() = runBlocking {
        AvatarServer(redirect = "/redirected.png").use { workspace ->
            execute("urn:image:redirect", workspace.url)
            assertEquals(2, workspace.requests.size)
            assertTrue(workspace.requests.all { it.authorization == credentials.first().value })
            assertTrue(workspace.requests.all { it.session == credentials.last().value })
        }
    }

    @Test fun gravatarAndEmptyCredentialsRemainUnauthenticated() {
        val gravatar = requireNotNull(avatarImageRequest(context, "urn:gravatar:hash", "https://workspace.invalid", credentials))
        assertEquals("https://secure.gravatar.com/avatar/hash?d=identicon", gravatar.data.toString())
        assertTrue(gravatar.httpHeaders.asMap().isEmpty())
        val noSession = requireNotNull(avatarImageRequest(context, "urn:image:id", "https://workspace.invalid", emptyList()))
        assertTrue(noSession.httpHeaders.asMap().isEmpty())
        val invalidOrigin = requireNotNull(avatarImageRequest(context, "urn:image:id", "https://user@workspace.invalid", credentials))
        assertTrue(invalidOrigin.httpHeaders.asMap().isEmpty())
    }

    @Test fun avatarComposableUsesUnauthenticatedExternalRequest() {
        AvatarServer().use { external ->
            compose.setContent {
                WokspaceTheme {
                    Avatar("urn:url:${external.url}/:image:/avatar.png", "https://workspace.invalid",
                        credentials, null, "Example", Modifier.size(48.dp))
                }
            }
            compose.waitUntil(5000) { external.requests.isNotEmpty() }
            assertNull(external.requests.single().authorization)
        }
    }

    @Test fun sharedLoaderUsesRotatedCredentialsAfterFailureWithoutReusingThemForExternalUrls() = runBlocking {
        AvatarServer(unauthorizedFirst = true).use { workspace ->
            AvatarServer().use { external ->
                val loader = avatarImageLoader(context)
                fun request(urn: String, headers: List<AuthHeader>) = requireNotNull(
                    avatarImageRequest(context, urn, workspace.url, headers),
                ).newBuilder().memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
                val urn = "urn:image:00000000-0000-4000-8000-000000000057"
                assertTrue(loader.execute(request(urn, credentials)) is ErrorResult)
                val rotated = listOf(AuthHeader("Authorization", "Bearer cassi-rotated-fixture"))
                assertTrue(loader.execute(request(urn, rotated)) is SuccessResult)
                assertEquals(listOf(credentials.first().value, rotated.single().value),
                    workspace.requests.map { it.authorization })
                assertTrue(loader.execute(request("urn:url:${external.url}/:image:/avatar.png", rotated)) is SuccessResult)
                assertNull(external.requests.single().authorization)
                assertNull(external.requests.single().session)
            }
        }
    }

    private suspend fun execute(urn: String, origin: String) {
        val loader = createAvatarImageLoader(context)
        try {
            val request = requireNotNull(avatarImageRequest(context, urn, origin, credentials)).newBuilder()
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
            val result = loader.execute(request)
            if (result !is SuccessResult) throw AssertionError("Coil avatar load failed: $result")
        } finally { loader.shutdown() }
    }
}

private data class AvatarRequest(val path: String, val authorization: String?, val session: String?)

/** Real loopback sockets exercise the Avatar Coil -> Ktor -> CIO path, including HttpRedirect. */
private class AvatarServer(private val redirect: String? = null, private val unauthorizedFirst: Boolean = false) : AutoCloseable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor()
    val url = "http://127.0.0.1:${socket.localPort}"
    val requests = CopyOnWriteArrayList<AvatarRequest>()
    private val png = ByteArrayOutputStream().also { out ->
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
    }.toByteArray()

    init {
        executor.submit {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: java.net.SocketException) { break }
                client.use {
                    it.soTimeout = 5000
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine().split(' ')[1]
                    var authorization: String? = null
                    var session: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) authorization = line.substringAfter(':').trim()
                        if (line.startsWith("X-Workspace-Session:", ignoreCase = true)) session = line.substringAfter(':').trim()
                    }
                    requests += AvatarRequest(path, authorization, session)
                    val unauthorized = unauthorizedFirst && requests.size == 1
                    val isRedirect = redirect != null && path != "/redirected.png"
                    val headers = if (unauthorized) "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n"
                        else if (isRedirect) "HTTP/1.1 302 Found\r\nLocation: $redirect\r\nContent-Length: 0\r\n"
                        else "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${png.size}\r\n"
                    it.getOutputStream().apply {
                        write((headers + "Connection: close\r\n\r\n").toByteArray())
                        if (!isRedirect && !unauthorized) write(png)
                        flush()
                    }
                }
            }
        }
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
