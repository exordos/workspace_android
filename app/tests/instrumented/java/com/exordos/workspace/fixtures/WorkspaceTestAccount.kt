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

package com.exordos.workspace.fixtures

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.*
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import java.io.File
import java.util.UUID

/** One isolated DataStore, token namespace and production HTTP client per test account. */
class WorkspaceTestAccount private constructor(
    val index: Int,
    private val directory: File,
    private val scope: CoroutineScope,
    private val http: HttpClient,
    val api: WorkspaceAPIClient,
    val events: EventsRepositoryStore,
    val preferences: ServerRepository,
    val user: UserViewModel,
    val config: ServerConfig,
    val repo: EventsRepository,
) {
    suspend fun close() {
        preferences.removeServer(config.id)
        user.viewModelScope.cancel()
        events.clear()
        http.close()
        scope.cancel()
        directory.deleteRecursively()
    }

    companion object {
        suspend fun create(url: String, index: Int = 0): WorkspaceTestAccount {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "cassi-account-${UUID.randomUUID()}").apply { mkdirs() }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") }
            val http = HttpClient(CIO) {
                install(HttpTimeout) { requestTimeoutMillis = 8_000 }
                install(ContentNegotiation) { json() }
            }
            val api = WorkspaceAPIClient(http)
            val tokens = SecureTokenStore(context)
            val events = EventsRepositoryStore(tokens, api)
            val preferences = ServerRepository(store, tokens, events, scope)
            val config = ServerConfig("cassi-account-${UUID.randomUUID()}", url, "", "CASSI test", projectUuid = "project")
            preferences.addServer(config, TokenPair("cassi-fixture-$index", "cassi-fixture-refresh"))
            val user = UserViewModel(preferences)
            api.attachUserViewModel(user)
            withTimeout(5_000) {
                user.selectedServerId.first { it == config.id }
                user.selectedServer.first { it?.id == config.id }
                user.accessToken.first { !it.isNullOrBlank() }
            }
            return WorkspaceTestAccount(index, directory, scope, http, api, events, preferences, user, config, events.getOrCreate(config))
        }
    }
}
