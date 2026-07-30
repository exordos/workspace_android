package ru.genesiscorporation.workspace.beta.data.remote.dto

import android.text.EmojiConsistency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class UsersRequest(): ApiRequest<EmptyRequestData, List<UserResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/users/"
    override val data = EmptyRequestData()
}

class UpdateOwnPresenceRequest(
    userUuid: String,
    status: String,
    emoji: String?,
    text: String?,
) : ApiRequest<UpdateOwnPresenceRequestData, UserResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val encodeExplicitNulls = true
    override val url =
        "/api/workspace/v1/messenger/users/$userUuid/actions/presence/invoke"
    override val data = UpdateOwnPresenceRequestData(
        status = status,
        emoji = emoji,
        text = text,
    )
}

@Serializable
data class UpdateOwnPresenceRequestData(
    val status: String,
    val emoji: String?,
    val text: String?,
)

class ResetOwnAvatarRequest(
    userUuid: String,
) : ApiRequest<EmptyRequestData, UserResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val url =
        "/api/workspace/v1/messenger/users/$userUuid/actions/avatar_reset/invoke"
    override val data = EmptyRequestData()
}

@Serializable
data class UserResponseData(
    var email: String? = null,
    @SerialName("first_name") var firstName: String? = null,
    @SerialName("last_name") var lastName: String? = null,
    val username: String,
    val uuid: String,
    @SerialName("status_emoji") var statusEmoji: String? = null,
    @SerialName("status_text") var statusText: String? = null,
    var status: String,
    var avatar: String,
    @SerialName("identity_kind") val identityKind: String? = null,
    val provider: ProviderReference? = null,
) {
    fun displayableName(): String {
        var displayName = ""
        if (firstName != null) {
            displayName += firstName
        }

        if (lastName != null) {
            if (!displayName.isEmpty()) {
                displayName += " "
            }
            displayName += lastName
        }

        if (displayName.isEmpty()) {
            displayName += username
        }

        return  displayName
    }
}
