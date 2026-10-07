package ru.genesiscorporation.workspace.beta.modules.chatdialog

import java.security.MessageDigest

/** Leave room for the cache UUID prefix within Android's 255-byte filename limit. */
internal fun localAttachmentFileName(name: String): String {
    val safe = name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .trim('.').ifBlank { "attachment" }
    val limit = 180
    if (safe.toByteArray(Charsets.UTF_8).size <= limit) return safe

    val extension = safe.substringAfterLast('.', "").let {
        if (it.isNotEmpty() && it.toByteArray(Charsets.UTF_8).size <= 32) ".$it" else ""
    }
    val hash = MessageDigest.getInstance("SHA-256").digest(safe.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it) }
    val suffix = "-$hash$extension"
    val prefix = StringBuilder()
    var bytes = 0
    var offset = 0
    while (offset < safe.length) {
        val codePoint = safe.codePointAt(offset)
        val character = String(Character.toChars(codePoint))
        val size = character.toByteArray(Charsets.UTF_8).size
        if (bytes + size > limit - suffix.toByteArray(Charsets.UTF_8).size) break
        prefix.append(character)
        bytes += size
        offset += Character.charCount(codePoint)
    }
    return prefix.toString().trimEnd('.', ' ') + suffix
}
