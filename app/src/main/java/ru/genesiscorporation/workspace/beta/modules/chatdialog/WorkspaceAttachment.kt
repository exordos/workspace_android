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
    val uploaded: UploadedAttachmentCheckpoint? = null,
)

data class UploadedAttachmentCheckpoint(
    val uuid: String,
    val name: String,
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

internal fun createUploadedAttachmentCheckpoint(
    upload: UploadFileResponseData,
    fallbackName: String,
    fallbackContentType: String,
    fallbackSizeBytes: Long?,
): UploadedAttachmentCheckpoint? {
    val uuid = canonicalAttachmentUuid(upload.uuid) ?: return null
    val name = safeLocalFileName(upload.name.ifBlank { fallbackName })
    val contentType = normalizedAttachmentContentType(
        upload.contentType.ifBlank { fallbackContentType },
    ) ?: normalizedAttachmentContentType(fallbackContentType)
        ?: "application/octet-stream"
    val sizeBytes = upload.sizeBytes
        ?.takeIf(::isValidAttachmentSize)
        ?: fallbackSizeBytes?.takeIf(::isValidAttachmentSize)
    return UploadedAttachmentCheckpoint(
        uuid = uuid,
        name = name,
        contentType = contentType,
        sizeBytes = sizeBytes,
    )
}

internal fun UploadedAttachmentCheckpoint.toUploadResponseOrNull():
    UploadFileResponseData? {
    val canonicalUuid = canonicalAttachmentUuid(uuid) ?: return null
    val canonicalName = name
        .takeIf {
            it.isNotBlank() &&
                it.length <= MAX_ATTACHMENT_FILE_NAME_CHARS &&
                safeLocalFileName(it) == it
        }
        ?: return null
    val canonicalContentType = normalizedAttachmentContentType(contentType)
        ?: return null
    if (sizeBytes != null && !isValidAttachmentSize(sizeBytes)) return null
    return UploadFileResponseData(
        uuid = canonicalUuid,
        name = canonicalName,
        contentType = canonicalContentType,
        sizeBytes = sizeBytes,
    )
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

internal fun canonicalAttachmentUuid(value: String): String? =
    runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { canonical -> canonical.equals(value, ignoreCase = true) }

private fun normalizedAttachmentContentType(value: String): String? =
    value
        .substringBefore(';')
        .trim()
        .lowercase()
        .takeIf { it.length <= MAX_ATTACHMENT_CONTENT_TYPE_CHARS }
        ?.takeIf { it.matches(ATTACHMENT_CONTENT_TYPE) }

private fun isValidAttachmentSize(value: Long): Boolean =
    value in 1..MAX_ATTACHMENT_UPLOAD_BYTES

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
private const val MAX_ATTACHMENT_FILE_NAME_CHARS = 255
private const val MAX_ATTACHMENT_CONTENT_TYPE_CHARS = 127
private const val MAX_ATTACHMENT_UPLOAD_BYTES = 25L * 1024L * 1024L

private val ATTACHMENT_CONTENT_TYPE = Regex(
    """[a-z0-9.+-]+/[a-z0-9.+-]+""",
)

private val WORKSPACE_ATTACHMENT_MARKDOWN = Regex(
    """(!?)\[((?:\\.|[^\]])*)]\((urn:(image|video|file):([0-9a-fA-F-]{36})(?:\?[^)\s]*)?)\)""",
)
private val LEGACY_IMAGE_UPLOAD = Regex(
    """\(([^)]+)\)\s*\[(urn:image:[0-9a-fA-F-]{36})]""",
)
