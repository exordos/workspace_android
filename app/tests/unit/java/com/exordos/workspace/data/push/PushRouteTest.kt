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

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.exordos.workspace.data.crypto.WorkspacePushCrypto
import com.exordos.workspace.data.crypto.WorkspacePushPayload
import com.google.crypto.tink.subtle.X25519
import java.security.GeneralSecurityException

class PushRouteTest {
    @Test fun topicSnapshotDecryptsAndKeepsOneIdentityWhenItsAnchorChanges() {
        val fixture = Json.parseToJsonElement(javaClass.classLoader!!.getResource("topic_push_events.json")!!.readText()).jsonObject
        val payloads = fixture.getValue("payloads").jsonObject
        val first = Json.decodeFromJsonElement<WorkspacePushPayload>(payloads.getValue("first"))
        val second = Json.decodeFromJsonElement<WorkspacePushPayload>(payloads.getValue("second"))
        val key = fixture.getValue("recipient_private_key_hex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val plaintext = WorkspacePushCrypto.decrypt(key, X25519.publicFromPrivate(key), first)
        val content = Json.parseToJsonElement(plaintext.decodeToString()).jsonObject
        assertEquals("Первое непрочитанное сообщение", content.getValue("text").jsonPrimitive.content)
        assertEquals("2", content.getValue("unread_count").jsonPrimitive.content)
        val firstRoute = PushRoute.from(first, "saved-server")
        val secondRoute = PushRoute.from(second, "saved-server")
        assertTrue(firstRoute.topicUpdate)
        assertEquals(firstRoute.identity, secondRoute.identity)
        assertNotEquals(firstRoute.messageUuid, secondRoute.messageUuid)
        assertNotEquals(firstRoute.identity, firstRoute.copy(serverId = "other-account").identity)
        key.fill(0); plaintext.fill(0)
    }
    @Test fun legacyCreateAndCancelCannotDecryptOrCreateRoutes() {
        val fixture = Json.parseToJsonElement(javaClass.classLoader!!.getResource("mobile_push_event.json")!!.readText()).jsonObject
        val key = fixture.getValue("recipient_private_key_hex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        try {
            val publicKey = X25519.publicFromPrivate(key)
            for (kind in listOf("payload", "cancel_payload")) {
                val payload = Json.decodeFromJsonElement<WorkspacePushPayload>(fixture.getValue(kind))
                assertTrue(runCatching { WorkspacePushCrypto.decrypt(key, publicKey, payload) }.exceptionOrNull() is GeneralSecurityException)
                assertTrue(runCatching { PushRoute.from(payload, "saved-server") }.isFailure)
            }
        } finally { key.fill(0) }
    }
}
