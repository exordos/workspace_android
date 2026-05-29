package ru.genesiscorporation.workspace.beta.data.remote.dto

import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class OidcCompleteRequest(
    val baseUrl: String
): ApiRequest<EmptyRequestData, String, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = false
    override val isAbsoluteUrl: Boolean = true
    override val hasSessionCookie: Boolean = true
    override val url: String = baseUrl
    override val data = EmptyRequestData()
}