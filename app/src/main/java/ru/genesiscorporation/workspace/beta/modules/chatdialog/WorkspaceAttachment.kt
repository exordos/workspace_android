package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.net.Uri
import ru.genesiscorporation.workspace.beta.data.remote.dto.UploadFileResponseData
import java.net.URLDecoder
import java.net.URLEncoder
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class WorkspaceAttachmentKind(val urnName: String) {
    IMAGE("image"),
    VIDEO("video"),
    FILE("file"),
}

data class WorkspaceAttachment(
    val kind: WorkspaceAttachmentKind,
    val uuid: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long?,
    val urn: String,
)

data class SelectedLocalAttachment(
    val uri: Uri,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long?,
)

data class UserUploadMarkdownParts(
    val caption: String,
    val attachments: List<WorkspaceAttachment>,
)

fun buildWorkspaceAttachmentMarkdown(upload: UploadFileResponseData): String {
    val contentType = upload.contentType
        .substringBefore(';')
        .trim()
        .lowercase()
        .ifBlank { "application/octet-stream" }
    val kind = when {
        contentType.startsWith("image/") -> WorkspaceAttachmentKind.IMAGE
        contentType.startsWith("video/") -> WorkspaceAttachmentKind.VIDEO
        else -> WorkspaceAttachmentKind.FILE
    }
    val safeName = upload.name
        .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
        .replace(Regex("""\.{2,}"""), ".")
        .trim()
        .ifBlank { "file" }
    val query = buildList {
        add("name=${safeName.urlEncode()}")
        add("content_type=${contentType.urlEncode()}")
        upload.sizeBytes?.takeIf { it >= 0 }?.let { add("size=$it") }
    }.joinToString("&")
    val urn = "urn:${kind.urnName}:${upload.uuid}?$query"
    val label = safeName
        .replace("\\", "\\\\")
        .replace("]", "\\]")
    return if (kind == WorkspaceAttachmentKind.IMAGE) {
        "![$label]($urn)"
    } else {
        "[$label]($urn)"
    }
}

fun String.parseWorkspaceAttachmentsOrNull(): UserUploadMarkdownParts? {
    val matches = WORKSPACE_ATTACHMENT_MARKDOWN.findAll(this).toList()
    if (matches.isNotEmpty()) {
        return UserUploadMarkdownParts(
            caption = replace(WORKSPACE_ATTACHMENT_MARKDOWN, "")
                .lineSequence()
                .joinToString("\n") { it.trimEnd() }
                .trim(),
            attachments = matches.mapNotNull(::parseWorkspaceAttachment),
        ).takeIf { it.attachments.isNotEmpty() }
    }

    val legacyMatches = LEGACY_IMAGE_UPLOAD.findAll(this).toList()
    if (legacyMatches.isEmpty()) return null
    return UserUploadMarkdownParts(
        caption = replace(LEGACY_IMAGE_UPLOAD, "")
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trim(),
        attachments = legacyMatches.mapNotNull { match ->
            parseAttachment(
                kindName = "image",
                uuidValue = match.groupValues[2].removePrefix("urn:image:"),
                urn = match.groupValues[2],
                label = match.groupValues[1],
                query = "",
            )
        },
    ).takeIf { it.attachments.isNotEmpty() }
}

private fun parseWorkspaceAttachment(match: MatchResult): WorkspaceAttachment? =
    parseAttachment(
        kindName = match.groupValues[4],
        uuidValue = match.groupValues[5],
        urn = match.groupValues[3],
        label = match.groupValues[2]
            .replace("""\]""", "]")
            .replace("""\\""", "\\"),
        query = match.groupValues[3].substringAfter('?', ""),
    )

private fun parseAttachment(
    kindName: String,
    uuidValue: String,
    urn: String,
    label: String,
    query: String,
): WorkspaceAttachment? {
    val uuid = runCatching { UUID.fromString(uuidValue).toString() }.getOrNull() ?: return null
    val kind = WorkspaceAttachmentKind.entries.firstOrNull { it.urnName == kindName } ?: return null
    val params = query
        .split('&')
        .mapNotNull { part ->
            val name = part.substringBefore('=', "").takeIf(String::isNotBlank) ?: return@mapNotNull null
            name.urlDecode() to part.substringAfter('=', "").urlDecode()
        }
        .toMap()
    val fileName = params["name"]?.takeIf(String::isNotBlank)
        ?: label.takeIf(String::isNotBlank)
        ?: "file"
    val contentType = params["content_type"]
        ?.takeIf { it.matches(Regex("""[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+""")) }
        ?: when (kind) {
            WorkspaceAttachmentKind.IMAGE -> "image/*"
            WorkspaceAttachmentKind.VIDEO -> "video/*"
            WorkspaceAttachmentKind.FILE -> "application/octet-stream"
        }
    return WorkspaceAttachment(
        kind = kind,
        uuid = uuid,
        fileName = fileName.take(255),
        contentType = contentType,
        sizeBytes = params["size"]?.toLongOrNull()?.takeIf { it >= 0 },
        urn = urn,
    )
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun String.urlDecode(): String =
    runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)

internal fun safeLocalFileName(value: String): String =
    value
        .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
        .replace(Regex("""\.{2,}"""), ".")
        .trim()
        .take(180)
        .ifBlank { "file" }

internal fun pruneAttachmentCache(
    directory: File,
    incomingBytes: Long,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val files = directory.listFiles()
        .orEmpty()
        .filter(File::isFile)
        .sortedBy(File::lastModified)
        .toMutableList()
    files
        .filter { nowMillis - it.lastModified() > ATTACHMENT_CACHE_MAX_AGE_MS }
        .forEach { expired ->
            expired.delete()
            files.remove(expired)
        }
    var totalBytes = files.sumOf(File::length)
    while (
        files.isNotEmpty() &&
        (files.size >= ATTACHMENT_CACHE_MAX_FILES ||
            totalBytes + incomingBytes > ATTACHMENT_CACHE_MAX_BYTES)
    ) {
        val oldest = files.removeAt(0)
        totalBytes -= oldest.length()
        oldest.delete()
    }
}

private const val ATTACHMENT_CACHE_MAX_FILES = 8
private const val ATTACHMENT_CACHE_MAX_BYTES = 100L * 1024L * 1024L
private const val ATTACHMENT_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L

private val WORKSPACE_ATTACHMENT_MARKDOWN = Regex(
    """(!?)\[((?:\\.|[^\]])*)]\((urn:(image|video|file):([0-9a-fA-F-]{36})(?:\?[^)\s]*)?)\)""",
)
private val LEGACY_IMAGE_UPLOAD = Regex(
    """\(([^)]+)\)\s*\[(urn:image:[0-9a-fA-F-]{36})]""",
)
