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

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushCrypto
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushPayload
import java.io.File
import java.util.Base64

/** Diagnoses an actual encrypted server snapshot without injecting a receipt or displaying an alert. */
class LivePushWireCompatibilityTest {
    @Test fun capturedServerEnvelopeMatchesSavedDeviceKey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("allowWireProbe") == "true")
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "live-push-wire.json")
        try {
            val json = Json { ignoreUnknownKeys = true }
            val payload = json.decodeFromString<WorkspacePushPayload>(file.readText())
            check(file.delete())
            val registration = requireNotNull(PushKeyStore(context).all().singleOrNull {
                !it.disabled && it.deviceUuid == payload.deviceUuid && it.keyUuid == payload.content.keyUuid
            }) { "Captured envelope has no matching current device key" }
            assertEquals(registration.organizationUuid, payload.organizationUuid)
            assertEquals(registration.projectUuid, payload.projectUuid)
            assertEquals(registration.userUuid, payload.userUuid)
            PushRoute.from(payload, registration.serverId)
            val decoder = Base64.getUrlDecoder()
            val privateKey = decoder.decode(registration.privateKey)
            val plaintext = try {
                WorkspacePushCrypto.decrypt(privateKey, decoder.decode(registration.publicKey), payload)
            } finally { privateKey.fill(0) }
            try {
                val preview = json.decodeFromString<PushPreview>(plaintext.decodeToString())
                assertEquals(if (payload.kind == WorkspacePushCrypto.TOPIC_UPDATE) "topic" else "message", preview.kind)
                assertEquals(requireNotNull(arguments.getString("expectedMessageText")), preview.text)
            } finally { plaintext.fill(0) }
        } catch (failure: Throwable) {
            // Exception messages can contain an encrypted or decrypted server snapshot.
            throw AssertionError("Captured envelope compatibility failed: ${failure.javaClass.simpleName}")
        } finally { file.delete() }
    }
}
