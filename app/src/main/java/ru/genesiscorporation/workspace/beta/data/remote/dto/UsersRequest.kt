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

@Serializable
data class UserResponseData(
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val username: String,
    val uuid: String,
    @SerialName("status_emoji") val statusEmoji: String? = null,
    @SerialName("status_text") val statusText: String? = null,
    val status: String,
    val avatar: String
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
