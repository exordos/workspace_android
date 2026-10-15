package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddStreamRequest(
    val name: String,
    val description: String,
    val directUserUuid: String?,
    val encryption: Boolean = false,
    val currentEncryptionKeyUuid: String? = null,
    val canonicalUuid: String? = null,
): ApiRequest<AddStreamRequestData, Stream, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/"
    override val data = AddStreamRequestData(
        name, description, directUserUuid, "native", encryption = encryption,
        currentEncryptionKeyUuid = currentEncryptionKeyUuid, uuid = canonicalUuid
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AddStreamRequestData(
    val name: String,
    val description: String,
    @SerialName("direct_user_uuid") val directUserUuid: String?,
    @SerialName("source_name") val sourceName: String?,
    val source: AddStreamRequestSource = AddStreamRequestSource(),
    val encryption: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_encryption_key_uuid") val currentEncryptionKeyUuid: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val uuid: String? = null,
)
@Serializable
data class AddStreamRequestSource(
    val kind: String = "native"
)