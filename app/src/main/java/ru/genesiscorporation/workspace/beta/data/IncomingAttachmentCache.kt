package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.BuildConfig

internal fun isOwnedIncomingAttachment(uri: Uri): Boolean =
    uri.scheme == "content" &&
        uri.authority == "${BuildConfig.APPLICATION_ID}.fileprovider" &&
        uri.pathSegments.lastOrNull()?.startsWith("incoming-") == true

internal suspend fun deleteOwnedIncomingAttachment(
    context: Context,
    uri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    if (!isOwnedIncomingAttachment(uri)) {
        return@withContext false
    }
    runCatching {
        if (context.contentResolver.delete(uri, null, null) > 0) {
            true
        } else {
            runCatching {
                context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Unit }
                    ?: error("Attachment descriptor is unavailable")
            }.isFailure
        }
    }.getOrDefault(false)
}

internal suspend fun deleteRemovedOwnedIncomingAttachments(
    context: Context,
    previous: PersistedConversationState?,
    current: PersistedConversationState?,
): Boolean {
    val retainedUris = current.attachmentUris()
    var allDeleted = true
    previous
        .attachmentUris()
        .asSequence()
        .filterNot(retainedUris::contains)
        .map(Uri::parse)
        .filter(::isOwnedIncomingAttachment)
        .forEach { uri ->
            if (!deleteOwnedIncomingAttachment(context, uri)) {
                allDeleted = false
            }
        }
    return allDeleted
}

private fun PersistedConversationState?.attachmentUris(): Set<String> =
    this
        ?.let { state ->
            buildSet {
                state.attachments.mapTo(this, PersistedAttachment::uri)
                state.suspendedDraft
                    ?.attachments
                    ?.mapTo(this, PersistedAttachment::uri)
            }
        }
        .orEmpty()
