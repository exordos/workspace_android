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

package com.exordos.workspace.modules.creationbase

import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.remote.WorkspaceAPIClient

internal class StreamActionAccount(
    val server: ServerConfig,
    private val userUuid: String,
    private val client: WorkspaceAPIClient,
    private val repository: EventsRepository,
) {
    fun isCurrent(): Boolean {
        val selected = client.requireUserViewModel().selectedServer.value ?: return false
        return repository.matchesServerConfiguration(server) && selected.id == server.id &&
            selected.baseUrl == server.baseUrl && selected.projectUuid == server.projectUuid &&
            !selected.needsToRelogin && repository.currentUser.value?.uuid == userUuid
    }
}

internal fun streamActionAccount(client: WorkspaceAPIClient, repository: EventsRepository): StreamActionAccount? {
    val server = client.requireUserViewModel().selectedServer.value?.copy() ?: return null
    val user = repository.currentUser.value ?: return null
    return StreamActionAccount(server, user.uuid, client, repository).takeIf { it.isCurrent() }
}
