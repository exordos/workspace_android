package com.exordos.workspace.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.exordos.workspace.ui.RedirectEngine
import java.io.IOException

/** The same client factory as the application, with the real Ktor pipeline and a capturing engine. */
@RunWith(AndroidJUnit4::class)
class WorkspaceTransportTest {
    @Test fun closingTheDefaultClientAlsoClosesItsEngine() = runBlocking {
        val client = workspaceHttpClient()
        val engineJob = client.engine.coroutineContext[Job]!!
        try {
            client.close()
            withTimeout(5_000) { engineJob.join() }
            assertTrue(engineJob.isCompleted)
        } finally { client.close(); client.engine.close() }
    }

    @Test fun remoteHttpIsRejectedBeforeCredentialsReachTheEngine() = runBlocking { withTimeout(10_000) {
        for (host in listOf("workspace.invalid", "localhost.invalid", "127.0.0.1.invalid", "192.0.2.1")) {
            val engine = RedirectEngine("https://workspace.invalid/final")
            val client = workspaceHttpClient(engine)
            try {
                val failure = runCatching {
                    client.post("http://$host/login") {
                        header("Authorization", "Bearer cassi-fixture")
                        setBody("login=cassi-fixture&password=synthetic-password")
                    }
                }.exceptionOrNull()
                assertTrue("Cleartext authentication was accepted for $host", failure is IOException)
                assertTrue("Credentials reached the transport", engine.requests.isEmpty())
            } finally { client.close(); engine.close() }
        }
    } }

    @Test fun loopbackRedirectCannotSendCredentialsToRemoteHttp() = runBlocking { withTimeout(10_000) {
        val engine = RedirectEngine("http://workspace.invalid/final")
        val client = workspaceHttpClient(engine)
        try {
            val failure = runCatching { client.get("http://127.0.0.1/start") {
                header("Authorization", "Bearer cassi-fixture")
            } }.exceptionOrNull()
            assertTrue("Cleartext redirect was followed", failure is IOException)
            assertEquals(1, engine.requests.size)
            assertEquals("127.0.0.1", engine.requests.single().url.host)
        } finally { client.close(); engine.close() }
    } }

    @Test fun httpsDowngradeIsBlockedEvenWhenTheRedirectPluginAllowsIt() = runBlocking { withTimeout(10_000) {
        val engine = RedirectEngine("http://workspace.invalid/final")
        val client = workspaceHttpClient(engine) { install(HttpRedirect) { allowHttpsDowngrade = true } }
        try {
            val failure = runCatching { client.get("https://workspace.invalid/start") }.exceptionOrNull()
            assertTrue("HTTPS downgrade was followed", failure is IOException)
            assertEquals(1, engine.requests.size)
        } finally { client.close(); engine.close() }
    } }

    @Test fun httpsAndExactLoopbackHostsStillReachTheEngine() = runBlocking { withTimeout(10_000) {
        for (url in listOf("https://workspace.invalid", "http://localhost", "http://127.0.0.1", "http://[::1]")) {
            val engine = RedirectEngine("$url/final")
            val client = workspaceHttpClient(engine)
            try {
                assertEquals(200, client.get("$url/start").status.value)
                assertEquals(2, engine.requests.size)
            } finally { client.close(); engine.close() }
        }
    } }

    @Test fun blockedErrorDoesNotExposeUrlOrCredentials() = runBlocking { withTimeout(10_000) {
        val engine = RedirectEngine("https://workspace.invalid/final")
        val client = workspaceHttpClient(engine)
        try {
            val failure = runCatching {
                client.get("http://cassi-fixture:synthetic-password@workspace.invalid/private?token=synthetic-query")
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            for (secret in listOf("synthetic-password", "synthetic-query", "workspace.invalid")) {
                assertFalse("Blocked URL leaked in the error", failure!!.message.orEmpty().contains(secret))
            }
            assertTrue(engine.requests.isEmpty())
        } finally { client.close(); engine.close() }
    } }
}
