package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.Context
import io.ktor.client.call.body
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadFileResponseData

/** A recipient receives the captured text and target-scoped file copies, never a source ACL dependency. */
internal class ForwardContentPreparation(
    private val sources: List<MessageResponse>,
    private val resolve: suspend (String) -> MessageResponse?,
    private val copyFile: suspend (String, String, String) -> String,
) {
    private val resolved = mutableMapOf<String, MessageResponse>()
    private val prepared = mutableMapOf<String, String>()

    suspend fun prepare(target: ForwardDestination): String {
        prepared[target.streamUuid]?.let { return it }
        var nodes = 0
        suspend fun render(content: String, visited: Set<String>, depth: Int): String {
            if (depth > 8) throw ForwardPreparationFailure(R.string.forward_quotes_too_deep)
            val result = mutableListOf<String>()
            for (element in MarkdownPayloadParser.parse(content)) {
                if (++nodes > 256) throw ForwardPreparationFailure(R.string.forward_too_much_data)
                result += when (element) {
                    is MessageElement.PlainText -> element.text
                    is MessageElement.Image -> "![${escapeForwardFileLabel(element.fileName)}](urn:image:${copyFile(element.uuid, element.fileName, target.streamUuid)})"
                    is MessageElement.File -> "[${escapeForwardFileLabel(element.fileName)}](urn:file:${copyFile(element.uuid, element.fileName, target.streamUuid)})"
                    is MessageElement.SnapshotQuote -> buildForwardSnapshotBlock(element.displayName, render(element.text, visited, depth + 1))
                    is MessageElement.Quote -> {
                        if (element.text.isNotEmpty()) {
                            buildForwardSnapshotBlock(element.displayName, render(element.text, visited + element.uuid, depth + 1))
                        } else {
                            if (element.uuid in visited) throw ForwardPreparationFailure(R.string.forward_nested_quote_failed)
                            val source = resolved[element.uuid] ?: resolve(element.uuid)?.also { resolved[element.uuid] = it }
                                ?: throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable)
                            buildForwardSnapshotBlock(element.displayName, render(source.payload.content, visited + element.uuid, depth + 1))
                        }
                    }
                }
            }
            return result.joinToString("\n\n").also {
                if (it.length > 100_000) throw ForwardPreparationFailure(R.string.forward_too_much_text)
            }
        }
        val snapshots = sources.map { source ->
            source.copy(payload = source.payload.copy(content = render(source.payload.content, setOf(source.uuid), 0)))
        }
        return requireNotNull(buildWorkspaceForwardMarkdown(snapshots)).also {
            if (it.length > 100_000) throw ForwardPreparationFailure(R.string.forward_too_much_text)
            prepared[target.streamUuid] = it
        }
    }
}

/** Upload acknowledgements and ambiguous uploads survive closing and reopening the picker. */
internal class DestinationFileCopies(
    private val find: suspend (streamUuid: String, hash: String, name: String) -> ApiResult<List<ForwardFileRecord>, ApiError>,
    private val load: suspend (sourceUuid: String, name: String) -> PreparedForwardFile,
    private val upload: suspend (file: PreparedForwardFile, streamUuid: String) -> ApiResult<String, ApiError>,
    private val currentUserUuid: String,
) {
    private val loaded = mutableMapOf<String, PreparedForwardFile>()
    private val copies = mutableMapOf<String, String>()
    private val startedUploads = mutableSetOf<String>()
    suspend fun copy(sourceUuid: String, name: String, targetStream: String): String {
        if (!isCanonicalForwardUuid(sourceUuid) || !isCanonicalForwardUuid(targetStream)) {
            throw ForwardPreparationFailure(R.string.forward_attachment_invalid)
        }
        val key = "$targetStream:$sourceUuid"
        copies[key]?.let { return it }
        if (loaded.size >= 32 && sourceUuid !in loaded) throw ForwardPreparationFailure(R.string.forward_attachment_limit)
        val file = loaded[sourceUuid] ?: load(sourceUuid, name).also { loaded[sourceUuid] = it }
        val found = when (val response = find(targetStream, file.hash, file.name)) {
            is ApiResult.Success -> response.value.filter {
                isCanonicalForwardUuid(it.uuid) && it.streamUuid == targetStream &&
                    it.userUuid == currentUserUuid && it.hash == file.hash && it.name == file.name &&
                    it.contentType.equals(file.contentType, ignoreCase = true)
            }
            is ApiResult.Error -> throw ForwardPreparationFailure(R.string.forward_upload_check_failed, key in startedUploads)
        }
        found.firstOrNull()?.let { return it.uuid.also { uuid -> copies[key] = uuid } }
        if (key in startedUploads) {
            throw ForwardPreparationFailure(R.string.forward_upload_uncertain, uncertain = true)
        }
        startedUploads += key
        val response = try { upload(file, targetStream) } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw ForwardPreparationFailure(R.string.forward_upload_uncertain, uncertain = true)
        }
        return when (response) {
            is ApiResult.Success -> {
                if (!isCanonicalForwardUuid(response.value)) throw ForwardPreparationFailure(R.string.forward_upload_unconfirmed, true)
                // Read metadata before using the uploaded UUID in the outgoing message.
                val confirmed = (find(targetStream, file.hash, file.name) as? ApiResult.Success)?.value?.firstOrNull {
                    it.uuid == response.value && it.streamUuid == targetStream && it.userUuid == currentUserUuid &&
                        it.hash == file.hash && it.name == file.name && it.contentType.equals(file.contentType, ignoreCase = true)
                } ?: throw ForwardPreparationFailure(R.string.forward_upload_uncertain, true)
                confirmed.uuid.also { copies[key] = it }
            }
            is ApiResult.Error -> {
                val code = response.error.code.toIntOrNull()
                if (code != null && code in 400..499 && code !in setOf(408, 409, 425)) {
                    startedUploads -= key
                    throw ForwardPreparationFailure(when (code) {
                        403 -> R.string.forward_upload_forbidden
                        413 -> R.string.forward_attachment_too_large
                        415 -> R.string.forward_attachment_unsupported
                        else -> R.string.forward_upload_failed
                    })
                }
                throw ForwardPreparationFailure(R.string.forward_upload_uncertain, true)
            }
        }
    }
}

internal data class PreparedForwardFile(val file: File, val name: String, val hash: String, val contentType: String = "application/octet-stream")

@Serializable
internal data class ForwardFileRecord(
    val uuid: String,
    val name: String,
    val hash: String,
    @SerialName("stream_uuid") val streamUuid: String?,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("content_type") val contentType: String = "application/octet-stream",
)

@Serializable
internal data class ForwardSourceFile(val uuid: String, val name: String, val hash: String,
    @SerialName("content_type") val contentType: String)

internal class ForwardSourceFileRequest(uuid: String) : ApiRequest<EmptyRequestData, ForwardSourceFile, ApiError> {
    override val method = HTTPMethod.GET
    override val url = "/api/workspace/v1/messenger/files/$uuid"
    override val data = EmptyRequestData()
}

internal class ForwardFilesRequest(streamUuid: String, hash: String, name: String) : ApiRequest<ForwardFilesQuery, List<ForwardFileRecord>, ApiError> {
    override val method = HTTPMethod.GET
    override val url = "/api/workspace/v1/messenger/files/"
    override val data = ForwardFilesQuery(streamUuid, hash, name)
}

@Serializable
internal data class ForwardFilesQuery(@SerialName("stream_uuid") val streamUuid: String, val hash: String, val name: String)

internal fun createForwardPreparation(
    context: Context,
    sources: List<MessageResponse>,
    client: WorkspaceAPIClient,
    currentUserUuid: String,
): ForwardContentPreparation {
    val copies = DestinationFileCopies(
        find = { stream, hash, name -> client.performRequest(ForwardFilesRequest(stream, hash, name)) },
        load = { uuid, _ ->
            withContext(Dispatchers.IO) {
                val metadata = (client.performRequest(ForwardSourceFileRequest(uuid)) as? ApiResult.Success)?.value
                    ?.takeIf { it.uuid == uuid } ?: throw ForwardPreparationFailure(R.string.forward_source_metadata_failed)
                val contentType = runCatching { ContentType.parse(metadata.contentType).toString() }.getOrNull()
                    ?: throw ForwardPreparationFailure(R.string.forward_source_format_failed)
                val safeName = metadata.name.substringAfterLast('/').substringAfterLast('\\')
                    .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim('.').take(160).ifBlank { "attachment" }
                val directory = File(context.cacheDir, "message-shares/${UUID.randomUUID()}").apply { check(mkdirs()) }
                val file = File(directory, safeName)
                val result = client.downloadFile("/api/workspace/v1/messenger/files/$uuid/actions/download", file)
                if (result !is ApiResult.Success) throw ForwardPreparationFailure(R.string.forward_source_download_failed)
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!hash.equals(metadata.hash, ignoreCase = true)) throw ForwardPreparationFailure(R.string.forward_source_changed)
                PreparedForwardFile(file, safeName, hash, contentType)
            }
        },
        upload = { file, stream -> uploadForwardFile(client, file, stream) },
        currentUserUuid = currentUserUuid,
    )
    return ForwardContentPreparation(sources,
        resolve = { id -> (client.performRequest(MessagesByIdsRequest(listOf(id))) as? ApiResult.Success)?.value?.singleOrNull { it.uuid == id } },
        copyFile = copies::copy,
    )
}


private fun escapeForwardFileLabel(value: String): String = value
    .replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")


/** Preserve status codes so an explicit rejection remains editable, while a lost upload ACK is not retried. */
private suspend fun uploadForwardFile(client: WorkspaceAPIClient, file: PreparedForwardFile, stream: String): ApiResult<String, ApiError> {
    return try {
        val baseUrl = client.userViewModel.repo.baseUrlFlow.first().orEmpty()
        suspend fun post(): HttpResponse {
            val token = client.baseAccessToken ?: client.userViewModel.repo.accessTokenFlow.first().orEmpty()
            return client.client.post("$baseUrl/api/workspace/v1/messenger/files/") {
            header("Authorization", "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", InputProvider(size = file.file.length()) { file.file.inputStream().asSource().buffered() }, Headers.build {
                    append(HttpHeaders.ContentType, file.contentType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
                append("stream_uuid", stream)
            }))
            }
        }
        var response = post()
        if (response.status.value == 401) { client.refreshToken(); response = post() }
        if (response.status.isSuccess()) {
            val uploaded = Json { ignoreUnknownKeys = true }.decodeFromString<UploadFileResponseData>(response.body<String>())
            ApiResult.Success(uploaded.uuid)
        } else ApiResult.Error(ApiError("Upload rejected", response.status.value.toString()))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { ApiResult.Error(ApiError("Upload result unknown", "REQUEST_FAILED")) }
}
