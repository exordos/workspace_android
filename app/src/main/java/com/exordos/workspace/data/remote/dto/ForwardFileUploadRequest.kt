package com.exordos.workspace.data.remote.dto

import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import kotlinx.io.asSource
import kotlinx.io.buffered
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod
import com.exordos.workspace.modules.chatdialog.PreparedForwardFile

internal class ForwardFileUploadRequest(
    file: PreparedForwardFile,
    streamUuid: String,
) : ApiRequest<EmptyRequestData, UploadFileResponseData, ApiError> {
    override val method = HTTPMethod.POST
    override val url = "/api/workspace/v1/messenger/files/"
    override val data = EmptyRequestData()
    override val multipartParts: List<PartData> = formData {
        append(
            "file",
            InputProvider(size = file.file.length()) {
                file.file.inputStream().asSource().buffered()
            },
            Headers.build {
                append(HttpHeaders.ContentType, file.contentType)
                append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
            },
        )
        append("stream_uuid", streamUuid)
    }
}
