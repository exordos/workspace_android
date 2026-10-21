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

package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.*
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspacePushCrypto

@Serializable
data class PushDeviceEncryption(
    val kind: String = "HPKE",
    val algorithm: String = WorkspacePushCrypto.ALGORITHM,
    @SerialName("key_uuid") val keyUuid: String,
    @SerialName("public_key") val publicKey: String,
)
@Serializable
data class PushDeviceData(
    val transport: String = "fcm",
    val platform: String = "android",
    @SerialName("registration_token") val registrationToken: String,
    val encryption: PushDeviceEncryption,
) {
    override fun toString() = "PushDeviceData([REDACTED])"
}
@Serializable
data class PushDeviceRegistrationResult(val uuid: String, @SerialName("project_id") val projectUuid: String,
    @SerialName("user_uuid") val userUuid: String)
class PutPushDeviceRequest(deviceUuid: String, override val data: PushDeviceData) : ApiRequest<PushDeviceData, PushDeviceRegistrationResult, ApiError> {
    override val method = HTTPMethod.PUT
    override val url = "/api/workspace/v1/push_devices/$deviceUuid"
}
class RemovePushDeviceRequest(deviceUuid: String) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url = "/api/workspace/v1/push_devices/$deviceUuid"
    override val data = EmptyRequestData()
}

class PushStreamRequest(streamUuid: String) : ApiRequest<EmptyRequestData, Stream, ApiError> {
    override val method = HTTPMethod.GET
    override val url = "/api/workspace/v1/messenger/streams/$streamUuid"
    override val data = EmptyRequestData()
}
