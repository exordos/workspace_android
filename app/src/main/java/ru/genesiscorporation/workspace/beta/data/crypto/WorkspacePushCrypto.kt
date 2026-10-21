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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.util.Base64

@Serializable
data class EncryptedPushContent(
    val kind: String,
    val algorithm: String,
    @SerialName("key_uuid") val keyUuid: String,
    val enc: String,
    val ciphertext: String,
)

@Serializable
data class WorkspacePushPayload(
    val uuid: String,
    val kind: String,
    @SerialName("organization_uuid") val organizationUuid: String,
    @SerialName("project_uuid") val projectUuid: String,
    @SerialName("stream_uuid") val streamUuid: String,
    @SerialName("topic_uuid") val topicUuid: String,
    @SerialName("message_uuid") val messageUuid: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("device_uuid") val deviceUuid: String,
    val content: EncryptedPushContent,
) {
    fun associatedData(): ByteArray = Json.encodeToString(ListSerializer(String.serializer()), listOf(
        uuid, kind, organizationUuid, projectUuid, streamUuid, topicUuid, messageUuid,
        userUuid, deviceUuid, content.keyUuid,
    )).toByteArray(Charsets.US_ASCII)
}

/** Push keys are separate from chat keys; only the registered device can decrypt. */
object WorkspacePushCrypto {
    const val ALGORITHM = "HPKE-v1-BASE-X25519-HKDF-SHA256-AES-256-GCM"
    const val MESSAGE_CREATE = "notification_message_create"
    const val MESSAGE_CANCELLED = "notification_message_cancel"
    private val info = "workspace-mobile-push".toByteArray(Charsets.US_ASCII)

    fun decrypt(privateKey: ByteArray, publicKey: ByteArray, payload: WorkspacePushPayload): ByteArray {
        if (payload.content.kind != "encrypted" || payload.content.algorithm != ALGORITHM ||
            payload.kind !in setOf(MESSAGE_CREATE, MESSAGE_CANCELLED)) {
            throw GeneralSecurityException("Unsupported push content")
        }
        val decoder = Base64.getUrlDecoder()
        val enc = decoder.decode(payload.content.enc)
        if (enc.size != 32) throw GeneralSecurityException("Invalid push encapsulation")
        return HpkeBase.openAes256Gcm(privateKey, publicKey, enc, info,
            payload.associatedData(), decoder.decode(payload.content.ciphertext))
    }
}
