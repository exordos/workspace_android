package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ru.genesiscorporation.workspace.beta.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessagesByIdsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid
import java.io.File
import java.security.MessageDigest
import io.ktor.http.ContentType
import java.util.UUID

internal data class ExternalShareFile(val uuid: String, val name: String)
internal data class ExternalShareContent(val text: String, val files: List<ExternalShareFile>)
internal data class ExternalShareLabels(val attachment: String = "Attachment", val workspaceLink: String = "[Workspace link]")

/** Materialize Workspace references before handing content to another app. */
internal suspend fun materializeExternalMessages(
    sources: List<MessageResponse>,
    labels: ExternalShareLabels = ExternalShareLabels(),
    resolveQuote: suspend (String) -> MessageResponse?,
): ExternalShareContent {
    require(sources.isNotEmpty() && sources.size <= MAX_FORWARD_SOURCE_MESSAGES)
    val files = linkedMapOf<String, ExternalShareFile>()
    val resolved = mutableMapOf<String, MessageResponse?>()
    var characters = 0

    suspend fun render(message: MessageResponse, visited: Set<String>, depth: Int, authorLabel: String? = null): String {
        require(depth <= 8) { "В сообщении слишком много вложенных цитат" }
        val author = authorLabel ?: message.user?.displayableName()?.trim()?.takeIf { it.isNotEmpty() } ?: message.authorUuid
        val pieces = mutableListOf<String>()
        for (element in MarkdownPayloadParser.parse(message.payload.content)) {
            when (element) {
                is MessageElement.PlainText -> pieces += externalPlainText(element.text, labels.workspaceLink)
                is MessageElement.Image -> {
                    val uuid = requireNotNull(parseCanonicalMessageUuid(element.uuid)) { "Неверная ссылка на изображение" }
                    val name = element.fileName.ifBlank { "image" }
                    files.putIfAbsent(uuid, ExternalShareFile(uuid, name))
                    pieces += name
                }
                is MessageElement.File -> {
                    val uuid = requireNotNull(parseCanonicalMessageUuid(element.uuid)) { "Неверная ссылка на файл" }
                    files.putIfAbsent(uuid, ExternalShareFile(uuid, element.fileName.ifBlank { "attachment" }))
                    pieces += element.fileName.ifBlank { labels.attachment }
                }
                is MessageElement.SnapshotQuote -> {
                    val nested = message.copy(payload = message.payload.copy(content = element.text))
                    pieces += render(nested, visited, depth + 1, element.displayName)
                        .lineSequence().joinToString("\n") { "> $it" }
                }
                is MessageElement.Quote -> {
                    val uuid = requireNotNull(parseCanonicalMessageUuid(element.uuid))
                    val quote = if (element.text.isNotEmpty()) {
                        message.copy(payload = message.payload.copy(content = element.text))
                    } else {
                        require(uuid !in visited) { "Не удалось подготовить вложенную цитату" }
                        val source = if (resolved.containsKey(uuid)) resolved[uuid] else resolveQuote(uuid).also { resolved[uuid] = it }
                        requireNotNull(source) { "Цитируемое сообщение недоступно. Попробуйте переслать его в Workspace" }
                    }
                    pieces += render(quote, visited + uuid, depth + 1, element.displayName).lineSequence().joinToString("\n") { "> $it" }
                }
            }
        }
        val result = "$author:\n${pieces.joinToString("\n\n")}"
        characters += result.length
        require(characters <= 100_000 && files.size <= 32) { "Для одной пересылки выбрано слишком много данных" }
        return result
    }
    val text = sources.joinToStringSuspend("\n\n") { render(it, setOf(it.uuid), 0) }
    return ExternalShareContent(text, files.values.toList())
}

/** Preserve visible text and web links; internal link targets have no meaning outside Workspace. */
internal fun externalPlainText(markdown: String, workspaceLink: String = "[Workspace link]"): String = markdown
    .replace(Regex("""!?\[((?:\\.|[^\]\\])*)]\(urn:[^)]*\)""")) { it.groupValues[1] }
    .replace(Regex("""urn:[^\s)]+"""), workspaceLink)

private suspend fun <T> List<T>.joinToStringSuspend(separator: String, transform: suspend (T) -> String): String {
    val parts = ArrayList<String>(size)
    for (element in this) parts += transform(element)
    return parts.joinToString(separator)
}

internal suspend fun prepareExternalShareIntent(
    context: Context,
    sources: List<MessageResponse>,
    client: WorkspaceAPIClient,
): Intent = withContext(Dispatchers.IO) {
    val content = materializeExternalMessages(sources, ExternalShareLabels(
        context.getString(R.string.external_share_attachment), context.getString(R.string.external_share_workspace_link),
    )) { id ->
        (client.performRequest(MessagesByIdsRequest(listOf(id))) as? ApiResult.Success)?.value?.singleOrNull { it.uuid == id }
    }
    val root = File(context.cacheDir, "message-shares").apply { mkdirs() }
    // Keep granted files long enough for the selected app to consume them.
    root.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1_000L }
        ?.forEach { it.deleteRecursively() }
    val sessionDir = File(root, UUID.randomUUID().toString()).apply { check(mkdirs()) }
    try {
        val uris = ArrayList<Uri>()
        val mimeTypes = mutableListOf<String>()
        content.files.forEachIndexed { index, attachment ->
            val metadata = (client.performRequest(ForwardSourceFileRequest(attachment.uuid)) as? ApiResult.Success)?.value
                ?.takeIf { it.uuid == attachment.uuid } ?: error("Cannot read original attachment metadata")
            val declaredMime = ContentType.parse(metadata.contentType).toString()
            val safeName = metadata.name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim('.').take(160).ifBlank { "attachment" }
            val directory = File(sessionDir, index.toString()).apply { check(mkdirs()) }
            val file = File(directory, safeName)
            val result = client.downloadFile("/api/workspace/v1/messenger/files/${attachment.uuid}/actions/download", file)
            require(result is ApiResult.Success && file.isFile) { "Не удалось подготовить вложение: $safeName" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) }.equals(metadata.hash, ignoreCase = true))
            uris += FileProvider.getUriForFile(context, "${context.packageName}.message-share-files", file)
            mimeTypes += declaredMime
        }
        buildExternalShareIntent(content.text, uris, commonShareMimeType(mimeTypes), context.getString(R.string.external_share_attachments))
    } catch (failure: Throwable) {
        sessionDir.deleteRecursively()
        throw failure
    }
}

internal fun commonShareMimeType(types: List<String>): String = when {
    types.isEmpty() -> "text/plain"
    types.distinct().size == 1 -> types.first()
    types.map { it.substringBefore('/') }.distinct().size == 1 -> "${types.first().substringBefore('/')}/*"
    else -> "*/*"
}

internal fun buildExternalShareIntent(text: String, uris: ArrayList<Uri>, mimeType: String, attachmentLabel: String = "Attachments"): Intent =
    Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_TEXT, text)
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
        else if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        if (uris.isNotEmpty()) {
            clipData = ClipData(attachmentLabel, arrayOf(mimeType), ClipData.Item(uris.first())).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
