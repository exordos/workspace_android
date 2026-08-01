package ru.genesiscorporation.workspace.beta.data.remote

import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

interface APIClient {

}

@Serializable
data class ApiError(
    val errorMessage: String,
    val code: String,
    val kind: ApiErrorKind = ApiErrorKind.UNKNOWN,
    val httpStatus: Int? = null,
    @Transient val conflictBody: String? = null,
    @Transient val entityTag: String? = null,
) : Throwable(errorMessage)

enum class ApiErrorKind {
    VALIDATION,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    SERVER,
    TIMEOUT,
    NETWORK,
    MALFORMED_RESPONSE,
    UNKNOWN,
}

interface ApiRequest<RequestData, Response, ResponseError> {
    val url: String
    val method: HTTPMethod
    val requiresApiKey: Boolean
        get() = true
    val isAbsoluteUrl: Boolean
        get() = false
    val expectedResponseOrigin: String?
        get() = null

    val shouldReturnUrl: Boolean
        get() = false
    val shouldApplySuffix: Boolean
        get() = false
    val hasSessionCookie: Boolean
        get() = false
    val isJson: Boolean
        get() = true
    val encodeExplicitNulls: Boolean
        get() = false
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
