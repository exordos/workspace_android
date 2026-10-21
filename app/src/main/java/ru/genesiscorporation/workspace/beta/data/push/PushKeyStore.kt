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

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspaceChatCrypto
import java.util.Base64
import java.util.UUID

@Serializable
internal data class PushRegistration(
    val serverId: String, val organizationUuid: String, val projectUuid: String, val userUuid: String,
    val deviceUuid: String, val keyUuid: String, val publicKey: String, val privateKey: String,
    val disabled: Boolean = false,
) {
    override fun toString() = "PushRegistration([REDACTED])"
}

/** Device-bound encrypted storage, excluded from both cloud backup and device transfer. */
internal class PushKeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "workspace_push_secrets_v1",
        MasterKey.Builder(context, "workspace_push_master_v1").setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun get(serverId: String): PushRegistration? = prefs.getString(serverId, null)?.let { Json.decodeFromString(it) }
    fun all(): List<PushRegistration> = prefs.all.keys.mapNotNull { runCatching { get(it) }.getOrNull() }
    fun save(value: PushRegistration) { check(prefs.edit().putString(value.serverId, Json.encodeToString(value)).commit()) }
    fun remove(serverId: String) { check(prefs.edit().remove(serverId).commit()) }
    fun getOrCreate(serverId: String, organizationUuid: String, projectUuid: String, userUuid: String): PushRegistration {
        get(serverId)?.takeIf { it.organizationUuid == organizationUuid && it.projectUuid == projectUuid && it.userUuid == userUuid }?.let { return it }
        val pair = WorkspaceChatCrypto().createRequestKeyPair()
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return try {
            PushRegistration(serverId, organizationUuid, projectUuid, userUuid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), encoder.encodeToString(pair.publicKey), encoder.encodeToString(pair.privateKey)).also(::save)
        } finally { pair.destroy() }
    }
}
