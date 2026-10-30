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

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.WorkspaceApplication
import java.io.File

/** Observe an external FCM zero snapshot while this connection's realtime scope is closed. */
class LiveTopicPushNoRealtimeTest {
    @Test fun externallyDeliveredZeroDoesNotRequireRealtime() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        require(args.getString("allowRealtimePause") == "true")
        val context = instrumentation.targetContext
        val app = context.applicationContext as WorkspaceApplication
        val serverId = requireNotNull(args.getString("serverId"))
        val initialEvent = requireNotNull(args.getString("eventUuid"))
        val server = app.serverRepository.serversFlow.first().single { it.id == serverId }
        require(server.baseUrl.trimEnd('/') == args.getString("workspaceBaseUrl")?.trimEnd('/'))
        val route = requireNotNull(livePushRoute(context, initialEvent, serverId))
        require(route.topicUpdate && route.topicUuid == args.getString("topicUuid"))
        val registration = requireNotNull(PushKeyStore(context).get(serverId))
        require(registration.userUuid == route.userUuid && !registration.disabled)
        val ready = File(context.cacheDir, "cassi-topic-zero-ready")
        ready.delete()
        try {
            app.eventsRepositoryStore.getOrCreate(server).close()
            delay(500)
            ready.writeText(route.topicUuid)
            withTimeout(45_000) {
                while (true) {
                    val updated = SQLiteDatabase.openDatabase(context.getDatabasePath("workspace_push_inbox.db").path,
                        null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery("SELECT route,state,unread_count FROM push_receipts WHERE identity=?", arrayOf(route.identity)).use { row ->
                            if (!row.moveToFirst()) false else {
                                val current = Json.decodeFromString<PushRoute>(row.getString(0))
                                row.getString(1) == "closed" && row.getInt(2) == 0 && current.eventUuid != initialEvent
                            }
                        }
                    }
                    if (updated) break
                    delay(100)
                }
            }
            assertEquals(registration, PushKeyStore(context).get(serverId))
        } finally {
            ready.delete()
            val current = app.serverRepository.serversFlow.first()
            app.eventsRepositoryStore.syncWith(current.filter { it.id != serverId })
            app.eventsRepositoryStore.syncWith(current)
            app.eventsRepositoryStore.get(serverId)?.loadServerSettings()
        }
    }
}
