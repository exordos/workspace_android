package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

const val PUSH_DEVICE_HPKE_KIND = "HPKE"
const val PUSH_DEVICE_HPKE_ALGORITHM =
    "HPKE-v1-BASE-X25519-HKDF-SHA256-AES-256-GCM"

@Serializable
data class PushDeviceEncryptionData(
    val kind: String,
    val algorithm: String,
    @SerialName("key_uuid")
    val keyUuid: String,
    @SerialName("public_key")
    val publicKey: String,
)

@Serializable
data class PushDeviceRequestData(
    val transport: String,
    val platform: String,
    @SerialName("registration_token")
    val registrationToken: String,
    val encryption: PushDeviceEncryptionData,
)

@Serializable
data class PushDeviceResponse(
    val uuid: String,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("user_uuid")
    val userUuid: String,
    val transport: String,
    val platform: String,
    @SerialName("registration_token")
    val registrationToken: String,
    val encryption: PushDeviceEncryptionData,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

data class PutPushDeviceRequest(
    private val registrationUuid: String,
    override val data: PushDeviceRequestData,
) : ApiRequest<PushDeviceRequestData, PushDeviceResponse, ApiError> {
    override val method = HTTPMethod.PUT
    override val url = "/api/workspace/v1/push_devices/$registrationUuid"
}

data class DeletePushDeviceRequest(
    private val registrationUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url = "/api/workspace/v1/push_devices/$registrationUuid"
    override val data = EmptyRequestData()
}
