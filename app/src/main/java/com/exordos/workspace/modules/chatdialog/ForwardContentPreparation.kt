package com.exordos.workspace.modules.chatdialog

import android.content.Context
import io.ktor.http.ContentType
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.R
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.ServerConfig
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.ForwardFileUploadRequest
import com.exordos.workspace.data.remote.dto.MessageElement
import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.data.remote.dto.MessageResponsePayload
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.modules.encryptedchat.EncryptedChatPresentation
import com.exordos.workspace.modules.encryptedchat.EncryptedChatRuntime

/** A recipient receives the captured text and target-scoped file copies, never a source ACL dependency. */
internal class ForwardContentPreparation(
    private val sources: List<MessageResponse>,
    private val resolve: suspend (String) -> MessageResponse?,
    private val copyFile: suspend (String, String, String) -> String,
    private val presentMessage: suspend (MessageResponse) -> MessageResponse = { requireForwardPlaintext(it) },
) {
    private val resolved = mutableMapOf<String, MessageResponse>()
    private val prepared = mutableMapOf<String, String>()

    suspend fun prepare(target: ForwardDestination): String {
        val projectedSources = try {
            sources.map { presentMessage(it) }.also {
                // A cached snapshot still requires access to every encrypted source generation.
                resolved.values.forEach { source -> presentMessage(source) }
            }
        } catch (failure: Throwable) { prepared.clear(); throw failure }
        prepared[target.streamUuid]?.let { return it }
        var nodes = 0
        suspend fun render(content: String, visited: Set<String>, depth: Int): String {
            if (depth > 8) throw ForwardPreparationFailure(R.string.forward_quotes_too_deep)
            val result = mutableListOf<String>()
            for (element in MarkdownPayloadParser.parse(content)) {
                if (++nodes > 256) throw ForwardPreparationFailure(R.string.forward_too_much_data)
                result += when (element) {
                    is MessageElement.PlainText -> element.text
                    is MessageElement.Image -> "![${escapeAttachmentLabel(element.fileName)}](urn:image:${copyFile(element.uuid, element.fileName, target.streamUuid)})"
                    is MessageElement.File -> "[${escapeAttachmentLabel(element.fileName)}](urn:file:${copyFile(element.uuid, element.fileName, target.streamUuid)})"
                    is MessageElement.SnapshotQuote -> buildForwardSnapshotBlock(element.displayName, render(element.text, visited, depth + 1))
                    is MessageElement.Quote -> {
                        if (element.text.isNotEmpty()) {
                            buildForwardSnapshotBlock(element.displayName, render(element.text, visited + element.uuid, depth + 1))
                        } else {
                            if (element.uuid in visited) throw ForwardPreparationFailure(R.string.forward_nested_quote_failed)
                            val rawSource = resolved[element.uuid] ?: resolve(element.uuid)?.also { resolved[element.uuid] = it }
                                ?: throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable)
                            val source = presentMessage(rawSource)
                            buildForwardSnapshotBlock(element.displayName, render(source.payload.content, visited + element.uuid, depth + 1))
                        }
                    }
                }
            }
            return result.joinToString("\n\n").also {
                if (it.length > 100_000) throw ForwardPreparationFailure(R.string.forward_too_much_text)
            }
        }
        val snapshots = projectedSources.map { source ->
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
                val code = response.error.httpStatusCode
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
    repo: EventsRepository,
    encryptedAccess: ForwardEncryptedAccess = ForwardEncryptedAccess(context, client, repo, currentUserUuid),
): ForwardContentPreparation {
    val copies = DestinationFileCopies(
        find = { stream, hash, name ->
            encryptedAccess.requireCurrent()
            client.performRequest(ForwardFilesRequest(stream, hash, name), encryptedAccess.serverId, encryptedAccess.config)
                .also { encryptedAccess.requireCurrent() }
        },
        load = { uuid, _ ->
            withContext(Dispatchers.IO) {
                encryptedAccess.requireCurrent()
                val metadata = (client.performRequest(ForwardSourceFileRequest(uuid),
                    encryptedAccess.serverId, encryptedAccess.config) as? ApiResult.Success)?.value
                    ?.takeIf { it.uuid == uuid } ?: throw ForwardPreparationFailure(R.string.forward_source_metadata_failed)
                val contentType = runCatching { ContentType.parse(metadata.contentType).toString() }.getOrNull()
                    ?: throw ForwardPreparationFailure(R.string.forward_source_format_failed)
                val safeName = localAttachmentFileName(metadata.name)
                val directory = File(context.cacheDir, "message-shares/${UUID.randomUUID()}").apply { check(mkdirs()) }
                val file = File(directory, safeName)
                encryptedAccess.requireCurrent()
                val result = client.downloadFile("/api/workspace/v1/messenger/files/$uuid/actions/download", file,
                    encryptedAccess.serverId, encryptedAccess.config)
                encryptedAccess.requireCurrent()
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
        upload = { file, stream ->
            encryptedAccess.requireCurrent()
            uploadForwardFile(client, file, stream, encryptedAccess.serverId, encryptedAccess.config)
                .also { encryptedAccess.requireCurrent() }
        },
        currentUserUuid = currentUserUuid,
    )
    return ForwardContentPreparation(sources,
        resolve = { id ->
            encryptedAccess.requireCurrent()
            (client.performRequest(MessagesByIdsRequest(listOf(id)), encryptedAccess.serverId,
                encryptedAccess.config) as? ApiResult.Success)?.value?.singleOrNull { it.uuid == id }
                .also { encryptedAccess.requireCurrent() }
        },
        copyFile = copies::copy,
        presentMessage = encryptedAccess::present,
    )
}


internal fun escapeAttachmentLabel(value: String): String = value
    .replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")


/** Preserve status codes so an explicit rejection remains editable, while a lost upload ACK is not retried. */
private suspend fun uploadForwardFile(client: WorkspaceAPIClient, file: PreparedForwardFile, stream: String,
                                      serverId: String, config: ServerConfig): ApiResult<String, ApiError> {
    return when (val response = client.performRequest(ForwardFileUploadRequest(file, stream), serverId, config)) {
        is ApiResult.Success -> ApiResult.Success(response.value.uuid)
        is ApiResult.Error -> response
    }
}

/** Wire ciphertext is never meaningful text for a snapshot or an external share. */
internal fun requireForwardPlaintext(message: MessageResponse): MessageResponse {
    if (message.payload.kind != "markdown" || message.originalEncryptedPayload != null)
        throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable)
    return message
}

/** Re-authorize even an existing UI projection: deleting its key must invalidate captured previews. */
internal suspend fun presentForwardMessage(message: MessageResponse,
                                           decrypt: suspend (MessageResponse) -> String?): MessageResponse {
    val encrypted = message.originalEncryptedPayload
    if (encrypted == null && message.payload.kind == "markdown") return message
    val payload = encrypted ?: message.payload
    if (payload.kind != "e2ee") throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable)
    val wire = message.copy(payload = payload.copy(), originalEncryptedPayload = null)
    val text = decrypt(wire) ?: throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable)
    return wire.copy(payload = MessageResponsePayload("markdown", text), originalEncryptedPayload = payload.copy())
}

/** One account boundary shared by target sends, read-back and source quote expansion. */
internal class ForwardEncryptedAccess(
    private val context: Context,
    private val client: WorkspaceAPIClient,
    private val repo: EventsRepository,
    private val currentUserUuid: String,
) {
    val serverId = repo.serverId
    private val capturedConfig = client.requireUserViewModel().selectedServer.value?.copy()
    val config: ServerConfig get() = capturedConfig ?: throw ForwardPreparationFailure(R.string.forward_recipient_unknown)

    fun requireCurrent(uncertain: Boolean = false) {
        val selected = client.requireUserViewModel().selectedServer.value
        val captured = capturedConfig
        if (captured == null || selected == null || selected.id != serverId || captured.id != serverId ||
            selected.baseUrl != captured.baseUrl || selected.projectUuid != captured.projectUuid ||
            selected.needsToRelogin || currentUserUuid.isBlank() ||
            repo.currentUser.value?.uuid != currentUserUuid ||
            client.requireUserViewModel().getTokens(serverId)?.accessToken.isNullOrBlank())
            throw ForwardPreparationFailure(R.string.forward_recipient_unknown, uncertain)
    }

    suspend fun present(message: MessageResponse): MessageResponse {
        requireCurrent()
        if (!config.projectUuid.isNullOrBlank() && message.projectId != null && message.projectId != config.projectUuid)
            throw ForwardPreparationFailure(R.string.forward_quoted_message_unavailable)
        val result = presentForwardMessage(message) { wire ->
            withRuntime(wire.streamUuid, wire.topicUuid) { runtime ->
                (runtime.presentation(wire) as? EncryptedChatPresentation.Text)?.text
            }
        }
        requireCurrent()
        return result
    }

    suspend fun send(target: ForwardDestination, canonicalUuid: String, markdown: String): MessageResponse {
        requireCurrent()
        if (repo.streams.value.none { it.uuid == target.streamUuid && it.encryption && !it.isArchived })
            throw ForwardPreparationFailure(R.string.forward_destination_unavailable)
        return withRuntime(target.streamUuid, target.topicUuid, mutating = true) {
            it.sendText(markdown, target.topicUuid, canonicalUuid)
        }
    }

    private suspend fun <T> withRuntime(streamUuid: String, topicUuid: String,
                                        mutating: Boolean = false,
                                        action: suspend (EncryptedChatRuntime) -> T): T = coroutineScope {
        requireCurrent()
        // Ordinary legacy sessions can have no selected project UUID. Encryption
        // must still have the complete durable key scope before mounting a runtime.
        if (config.projectUuid.isNullOrBlank())
            throw ForwardPreparationFailure(R.string.forward_destination_unavailable)
        if (repo.streams.value.none { it.uuid == streamUuid && it.encryption })
            throw ForwardPreparationFailure(R.string.forward_destination_unavailable)
        val runtime = EncryptedChatRuntime(context, client, repo, this, streamUuid, topicUuid)
        try { action(runtime).also { requireCurrent(uncertain = mutating) } } finally { runtime.close() }
    }
}
