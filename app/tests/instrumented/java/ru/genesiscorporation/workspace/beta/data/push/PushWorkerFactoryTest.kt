/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.genesiscorporation.workspace.beta.data.push

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import ru.genesiscorporation.workspace.beta.fixtures.WorkspaceTestAccount
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class PushWorkerFactoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun createsFreshWorkersAndPreservesDefaultFactoryFallback() = runBlocking {
        val account = WorkspaceTestAccount.create("http://127.0.0.1:1")
        try {
            val factory = PushWorkerFactory(account.api, account.preferences)
            val first = TestListenableWorkerBuilder<PushRegistrationWorker>(context)
                .setWorkerFactory(factory).build()
            val second = TestListenableWorkerBuilder<PushRegistrationWorker>(context)
                .setWorkerFactory(factory).build()
            assertNotSame(first, second)
            val reconciliation = TestListenableWorkerBuilder<PushReconciliationWorker>(context)
                .setWorkerFactory(factory).build()
            assertEquals(ListenableWorker.Result.success(), reconciliation.doWork())
            val other = TestListenableWorkerBuilder<OtherWorker>(context)
                .setWorkerFactory(factory).build()
            assertEquals(ListenableWorker.Result.success(), other.doWork())
        } finally {
            account.close()
        }
    }

    @Test fun reconciliationUsesTheInjectedAccountAndClient() = runBlocking {
        val owner = UUID.randomUUID().toString()
        val project = UUID.randomUUID().toString()
        val stream = UUID.randomUUID().toString()
        val topic = UUID.randomUUID().toString()
        val message = UUID.randomUUID().toString()
        val requests = CopyOnWriteArrayList<LoopbackWorkspaceServer.Request>()
        LoopbackWorkspaceServer { request ->
            requests += request
            when {
                request.path.endsWith("/messenger/me/") -> LoopbackWorkspaceServer.Response(
                    """{"username":"cassi-fixture","uuid":"$owner","status":"active","avatar":""}"""
                )
                request.path.endsWith("/messenger/messages/") -> LoopbackWorkspaceServer.Response(
                    """[{"uuid":"$message","updated_at":"2026-10-01T00:00:00Z","created_at":"2026-10-01T00:00:00Z","stream_uuid":"$stream","topic_uuid":"$topic","user_uuid":"$owner","author_uuid":"$owner","payload":{"kind":"markdown","content":"Synthetic worker fixture"},"is_own":false,"reactions":{},"read":true,"project_id":"$project"}]"""
                )
                else -> LoopbackWorkspaceServer.Response("{}")
            }
        }.use { server ->
            val account = WorkspaceTestAccount.create(server.url)
            val keys = PushKeyStore(context)
            try {
                account.preferences.updateServerProjectUuid(account.config.id, project)
                val registration = keys.getOrCreate(account.config.id, UUID.randomUUID().toString(), project, owner)
                val route = PushRoute(UUID.randomUUID().toString(), account.config.id,
                    registration.organizationUuid, project, owner, stream, topic, message)
                val inbox = PushInbox(context)
                assertTrue(inbox.accept(route, System.currentTimeMillis()))
                val worker = TestListenableWorkerBuilder<PushReconciliationWorker>(context)
                    .setWorkerFactory(PushWorkerFactory(account.api, account.preferences)).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
                assertTrue(requests.any { it.path.endsWith("/messenger/me/") })
                assertTrue(requests.any { it.path.endsWith("/messenger/messages/") })
                assertTrue(requests.all { it.headers["authorization"] == "Bearer cassi-fixture-0" })
                assertNull(inbox.resolve(route.identity))
            } finally {
                keys.remove(account.config.id)
                WorkspacePushNotifications(context).cancelServer(account.config.id)
                account.close()
            }
        }
    }

    @Test fun existingTopicApiRepairsLostZeroUpdatesWithoutClosingUnreadTopics() = runBlocking {
        val owner = UUID.randomUUID().toString()
        val project = UUID.randomUUID().toString()
        val stream = UUID.randomUUID().toString()
        val read = UUID.randomUUID().toString()
        val unread = UUID.randomUUID().toString()
        val requests = CopyOnWriteArrayList<LoopbackWorkspaceServer.Request>()
        fun topic(uuid: String, count: Int) = """{"uuid":"$uuid","name":"Recovery fixture","color":0,"stream_uuid":"$stream","updated_at":"2026-01-01T00:00:00Z","unread_count":$count,"active_unread_count":$count,"is_done":false,"is_default":false,"notification_mode":"default"}"""
        LoopbackWorkspaceServer { request ->
            requests += request
            when {
                request.path.endsWith("/messenger/me/") -> LoopbackWorkspaceServer.Response(
                    """{"username":"cassi-fixture","uuid":"$owner","status":"active","avatar":""}""")
                request.path.endsWith("/messenger/stream_topics/") -> LoopbackWorkspaceServer.Response(
                    "[${topic(read, 0)},${topic(unread, 1)}]")
                else -> LoopbackWorkspaceServer.Response("{}")
            }
        }.use { server ->
            val account = WorkspaceTestAccount.create(server.url)
            val keys = PushKeyStore(context)
            try {
                account.preferences.updateServerProjectUuid(account.config.id, project)
                val registration = keys.getOrCreate(account.config.id, UUID.randomUUID().toString(), project, owner)
                fun route(topic: String) = PushRoute(UUID.randomUUID().toString(), account.config.id,
                    registration.organizationUuid, project, owner, stream, topic, UUID.randomUUID().toString(), true)
                val routes = listOf(route(read), route(unread), route(UUID.randomUUID().toString()))
                val inbox = PushInbox(context)
                routes.forEach { inbox.updateTopic(it, System.currentTimeMillis(), 1, 1, 100, UUID.randomUUID().toString()) }
                val worker = TestListenableWorkerBuilder<PushReconciliationWorker>(context)
                    .setWorkerFactory(PushWorkerFactory(account.api, account.preferences)).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
                assertNull(inbox.resolve(routes[0].identity))
                assertNotNull(inbox.resolve(routes[1].identity))
                assertNull(inbox.resolve(routes[2].identity))
                assertTrue(requests.any { it.path.endsWith("/messenger/stream_topics/") })
                assertTrue(requests.none { it.path.endsWith("/messenger/messages/") })
            } finally {
                keys.remove(account.config.id)
                WorkspacePushNotifications(context).cancelServer(account.config.id)
                account.close()
            }
        }
    }

    @Test fun workManagerRunsWithTheApplicationFactoryWithoutLaunchingActivity() = runBlocking {
        val manager = WorkManager.getInstance(context)
        val work = OneTimeWorkRequestBuilder<PushReconciliationWorker>().build()
        try {
            manager.enqueue(work).result.get(10, TimeUnit.SECONDS)
            val info = withTimeout(20_000) {
                var state = manager.getWorkInfoById(work.id).get(5, TimeUnit.SECONDS)
                while (state?.state?.isFinished != true) {
                    delay(50)
                    state = manager.getWorkInfoById(work.id).get(5, TimeUnit.SECONDS)
                }
                requireNotNull(state)
            }
            assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        } finally {
            manager.cancelWorkById(work.id).result.get(10, TimeUnit.SECONDS)
        }
    }

    class OtherWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result = Result.success()
    }
}
