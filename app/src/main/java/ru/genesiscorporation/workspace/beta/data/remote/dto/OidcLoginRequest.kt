package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class OidcLoginRequest(
    val methodUrlSuffix: String
): ApiRequest<EmptyRequestData, String, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = false
    override val shouldReturnUrl: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val url: String = methodUrlSuffix
    override val data = EmptyRequestData()
}