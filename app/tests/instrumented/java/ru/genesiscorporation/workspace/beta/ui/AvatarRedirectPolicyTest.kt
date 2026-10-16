package ru.genesiscorporation.workspace.beta.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Scheme transitions on one authority cannot be served by ordinary HTTP-only loopback fixtures.
 * Keep Ktor's real redirect/plugin pipeline and capture the final engine requests without TLS/DNS. */
@RunWith(AndroidJUnit4::class)
class AvatarRedirectPolicyTest {
    @Test fun schemeUpgradeOnTheSameAuthorityDropsEverySuppliedCredential() = runBlocking { withTimeout(10_000) {
        val engine = RedirectEngine("https://workspace.invalid:8443/final")
        val client = HttpClient(engine) { install(avatarCredentialBoundary) }
        try {
            assertEquals(HttpStatusCode.OK, client.get("http://workspace.invalid:8443/start") {
                header("Authorization", "Bearer cassi-fixture")
                header("Cookie", "session=cassi-fixture")
                header("X-Workspace-Session", "cassi-fixture")
            }.status)
            assertEquals(2, engine.requests.size)
            assertNotNull(engine.requests.first().headers["Authorization"])
            for (header in listOf("Authorization", "Cookie", "X-Workspace-Session")) {
                assertNull(header, engine.requests.last().headers[header])
            }
        } finally { client.close(); engine.close() }
    } }

    @Test fun httpsDowngradeIsNotSentToTheEngine() = runBlocking { withTimeout(10_000) {
        val engine = RedirectEngine("http://workspace.invalid:8443/final")
        val client = HttpClient(engine) { install(avatarCredentialBoundary) }
        try {
            assertEquals(HttpStatusCode.Found, client.get("https://workspace.invalid:8443/start") {
                header("Authorization", "Bearer cassi-fixture")
            }.status)
            assertEquals(1, engine.requests.size)
        } finally { client.close(); engine.close() }
    } }

    @Test fun queryStringsDoNotChangeOriginOrStripSameOriginHeaders() = runBlocking { withTimeout(10_000) {
        val engine = RedirectEngine("https://workspace.invalid/final?size=32")
        val client = HttpClient(engine) { install(avatarCredentialBoundary) }
        try {
            assertEquals(HttpStatusCode.OK, client.get("https://workspace.invalid/start?avatar=1") {
                header("Authorization", "Bearer cassi-fixture")
                header("If-None-Match", "cassi-fixture-etag")
            }.status)
            assertEquals(2, engine.requests.size)
            assertTrue(engine.requests.all { it.headers["Authorization"] == "Bearer cassi-fixture" })
            assertTrue(engine.requests.all { it.headers["If-None-Match"] == "cassi-fixture-etag" })
        } finally { client.close(); engine.close() }
    } }
}

@OptIn(InternalAPI::class)
internal class RedirectEngine(private val location: String) : HttpClientEngineBase("cassi-avatar-redirect") {
    override val config = HttpClientEngineConfig()
    val requests = mutableListOf<HttpRequestData>()
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        requests += data
        return HttpResponseData(
            if (requests.size == 1) HttpStatusCode.Found else HttpStatusCode.OK,
            GMTDate(),
            if (requests.size == 1) Headers.build { append("Location", location) } else Headers.Empty,
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel(ByteArray(0)),
            callContext(),
        )
    }
}
