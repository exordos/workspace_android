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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.exordos.workspace.data.crypto.WorkspacePushPayload
import java.util.UUID

@Serializable
data class PushRoute(val eventUuid: String, val serverId: String, val organizationUuid: String,
    val projectUuid: String, val userUuid: String, val streamUuid: String, val topicUuid: String, val messageUuid: String) {
    val identity: String get() = messageIdentity(serverId, organizationUuid, projectUuid, userUuid, messageUuid)
    val conversationIdentity: String get() = scopedId(serverId, organizationUuid, projectUuid, userUuid, streamUuid, topicUuid)
    companion object {
        internal fun messageIdentity(serverId: String, organizationUuid: String, projectUuid: String, userUuid: String, messageUuid: String) =
            scopedId(serverId, organizationUuid, projectUuid, userUuid, messageUuid)
        private fun scopedId(vararg fields: String): String = UUID.nameUUIDFromBytes(
            Json.encodeToString(fields.toList()).toByteArray(Charsets.UTF_8)
        ).toString()
        internal fun from(payload: WorkspacePushPayload, serverId: String): PushRoute {
            listOf(payload.uuid, payload.organizationUuid, payload.projectUuid, payload.userUuid, payload.streamUuid,
                payload.topicUuid, payload.messageUuid, payload.deviceUuid, payload.content.keyUuid).forEach {
                require(UUID.fromString(it).toString() == it)
            }
            return PushRoute(payload.uuid, serverId, payload.organizationUuid, payload.projectUuid, payload.userUuid,
                payload.streamUuid, payload.topicUuid, payload.messageUuid)
        }
    }
}
