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

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushCrypto
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushPayload

class PushRouteTest {
    @Test fun pythonProducerPayloadDecryptsAndHasExactMessageRoute() {
        val fixture = Json.parseToJsonElement(javaClass.classLoader!!.getResource("mobile_push_event.json")!!.readText()).jsonObject
        val payload = Json.decodeFromJsonElement(WorkspacePushPayload.serializer(), fixture.getValue("payload"))
        val key = fixture.getValue("recipient_private_key_hex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val publicKey = com.google.crypto.tink.subtle.X25519.publicFromPrivate(key)
        val plaintext = WorkspacePushCrypto.decrypt(key, publicKey, payload)
        val content = Json.parseToJsonElement(plaintext.decodeToString()).jsonObject
        assertEquals("Привет из Workspace", content.getValue("text").jsonPrimitive.content)
        val route = PushRoute.from(payload, "saved-server")
        assertEquals(payload.messageUuid, route.messageUuid)
        assertEquals(payload.streamUuid, route.streamUuid)
        assertEquals(payload.topicUuid, route.topicUuid)
        assertNotEquals(route.identity, route.copy(userUuid = "other-user").identity)
        assertNotEquals(route.identity, route.copy(organizationUuid = "other-org").identity)
        // Two saved connections to the same account must own independent alerts.
        assertNotEquals(route.identity, route.copy(serverId = "second-saved-connection").identity)
        // Another delivery kind/event for the same message is the same alert.
        assertEquals(route.identity, route.copy(eventUuid = "another-event").identity)
    }
}
