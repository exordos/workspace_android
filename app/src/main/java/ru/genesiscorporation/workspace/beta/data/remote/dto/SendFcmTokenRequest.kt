package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class SendFcmTokenRequest(
    val token: String
): ApiRequest<SendFcmTokenRequestData, SendFcmTokenResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/users/me/android_gcm_reg_id"
    override val data = SendFcmTokenRequestData(
        token
    )
}

@Serializable
data class SendFcmTokenRequestData(
    val token: String
)

@Serializable
data class SendFcmTokenResponse(
    val msg: String
)