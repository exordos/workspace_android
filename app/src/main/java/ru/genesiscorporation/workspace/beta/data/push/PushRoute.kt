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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushPayload
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushCrypto
import java.util.UUID

@Serializable
data class PushRoute(val eventUuid: String, val serverId: String, val organizationUuid: String,
    val projectUuid: String, val userUuid: String, val streamUuid: String, val topicUuid: String, val messageUuid: String,
    val topicUpdate: Boolean = false) {
    val identity: String get() = if (topicUpdate) conversationIdentity else messageIdentity(serverId, organizationUuid, projectUuid, userUuid, messageUuid)
    val conversationIdentity: String get() = topicIdentity(serverId, organizationUuid, projectUuid, userUuid, streamUuid, topicUuid)
    companion object {
        internal fun messageIdentity(serverId: String, organizationUuid: String, projectUuid: String, userUuid: String, messageUuid: String) =
            scopedId(serverId, organizationUuid, projectUuid, userUuid, messageUuid)
        internal fun topicIdentity(serverId: String, organizationUuid: String, projectUuid: String, userUuid: String, streamUuid: String, topicUuid: String) =
            scopedId(serverId, organizationUuid, projectUuid, userUuid, streamUuid, topicUuid)
        private fun scopedId(vararg fields: String): String = UUID.nameUUIDFromBytes(
            Json.encodeToString(fields.toList()).toByteArray(Charsets.UTF_8)
        ).toString()
        internal fun from(payload: WorkspacePushPayload, serverId: String): PushRoute {
            require(payload.kind == WorkspacePushCrypto.TOPIC_UPDATE || payload.messageUuid != null)
            listOfNotNull(payload.uuid, payload.organizationUuid, payload.projectUuid, payload.userUuid, payload.streamUuid,
                payload.topicUuid, payload.messageUuid, payload.deviceUuid, payload.content.keyUuid).forEach {
                require(UUID.fromString(it).toString() == it)
            }
            return PushRoute(payload.uuid, serverId, payload.organizationUuid, payload.projectUuid, payload.userUuid,
                payload.streamUuid, payload.topicUuid, payload.messageUuid.orEmpty(), payload.kind == WorkspacePushCrypto.TOPIC_UPDATE)
        }
    }
}
