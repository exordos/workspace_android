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

package com.exordos.workspace.data.push

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.WorkspaceApplication
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.ServerSettingsRequest

/** Opt-in public connection preflight; sends no credentials and changes no account. */
class WorkspaceConnectionSmokeTest {
    @Test fun deviceCanReadSelectedWorkspaceSettings() = runBlocking {
        val base = requireNotNull(InstrumentationRegistry.getArguments().getString("workspaceBaseUrl"))
        require(base.startsWith("https://"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WorkspaceApplication
        val store = ViewModelStore()
        val state = UserViewModel(app.serverRepository)
        store.put("connection-preflight", state)
        try {
            try {
                withTimeout(15_000) { app.workspaceApiClient.client.get("$base/api/workspace/v1/messenger/server_settings/") }
            } catch (error: Exception) {
                val classes = generateSequence(error as Throwable?) { it.cause }.take(8)
                    .joinToString(" -> ") { it.javaClass.simpleName }
                throw AssertionError("Public Workspace transport failed: $classes")
            }
            val client = WorkspaceAPIClient(app.workspaceApiClient.client).apply { attachUserViewModel(state) }
            when (val result = withTimeout(15_000) { client.performRequest(ServerSettingsRequest(base)) }) {
                is ApiResult.Success -> check(result.value.organizationUuid != null)
                is ApiResult.Error -> throw AssertionError("Workspace connection failed: ${result.error.code} ${result.error.message?.take(300)}")
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { store.clear() }
        }
    }
}
