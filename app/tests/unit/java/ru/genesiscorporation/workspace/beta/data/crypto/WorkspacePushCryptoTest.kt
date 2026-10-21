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

package ru.genesiscorporation.workspace.beta.data.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.GeneralSecurityException

class WorkspacePushCryptoTest {
    @Test fun decryptsPublicRfc9180Aes256GcmVector() {
        // Public CFRG test keys, not deployment credentials.
        val fixture = javaClass.classLoader!!.getResourceAsStream("mobile_push_hpke_rfc9180.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        val vector = fixture.getValue("vector").jsonObject
        val message = vector.getValue("encryption").jsonObject
        fun value(name: String) = hex(vector.getValue(name).jsonPrimitive.content)
        fun encrypted(name: String) = hex(message.getValue(name).jsonPrimitive.content)
        assertArrayEquals(encrypted("pt"), HpkeBase.openAes256Gcm(
            value("skRm"), value("pkRm"), value("enc"), value("info"),
            encrypted("aad"), encrypted("ct"),
        ))
    }

    @Test fun associatedDataChangesWhenCancellationReplacesCreation() {
        val content = EncryptedPushContent("encrypted", WorkspacePushCrypto.ALGORITHM,
            "key", "enc", "ciphertext")
        val payload = WorkspacePushPayload("event", WorkspacePushCrypto.MESSAGE_CREATE,
            "organization", "project", "stream", "topic", "message", "user", "device", content)
        assertNotEquals(payload.associatedData().toList(),
            payload.copy(kind = WorkspacePushCrypto.MESSAGE_CANCELLED).associatedData().toList())
    }

    @Test(expected = GeneralSecurityException::class)
    fun rejectsUnsupportedEncryptionBeforeAttemptingToDisplayContent() {
        val payload = WorkspacePushPayload("event", WorkspacePushCrypto.MESSAGE_CREATE,
            "organization", "project", "stream", "topic", "message", "user", "device",
            EncryptedPushContent("message", "plaintext", "key", "enc", "text"))
        WorkspacePushCrypto.decrypt(ByteArray(32), ByteArray(32), payload)
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
