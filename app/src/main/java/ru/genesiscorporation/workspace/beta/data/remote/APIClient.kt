package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.http.content.PartData
import kotlinx.serialization.Serializable

interface APIClient {

}

@Serializable
data class ApiError(
    val errorMessage: String,
    val code: String,
    val httpStatusCode: Int? = code.toIntOrNull(),
) : Throwable()

interface ApiRequest<RequestData, Response, ResponseError> {
    val url: String
    val method: HTTPMethod
    val requiresApiKey: Boolean
        get() = true
    val isAbsoluteUrl: Boolean
        get() = false

    val shouldReturnUrl: Boolean
        get() = false
    val hasSessionCookie: Boolean
        get() = false
    val multipartParts: List<PartData>?
        get() = null
    val isJson: Boolean
        get() = true
    val data: RequestData

    val additionalHeaders: Map<String, String>
            get() = emptyMap()
}

@Serializable
data class EmptyRequestData(
    val emptyString: String = ""
)

enum class HTTPMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PATCH("PATCH"),
    DELETE("DELETE"),
    PUT("PUT")
}
