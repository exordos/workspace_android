package ru.genesiscorporation.workspace.beta.modules.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.BuildConfig
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.PersistedAttachment
import ru.genesiscorporation.workspace.beta.data.PersistedComposerDraft
import ru.genesiscorporation.workspace.beta.data.PersistedConversationRoute
import ru.genesiscorporation.workspace.beta.data.PersistedConversationState
import ru.genesiscorporation.workspace.beta.data.accountAttachmentCacheDirectory
import ru.genesiscorporation.workspace.beta.data.accountAttachmentCacheSizeBytes
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.modules.chatchannels.isDirectProviderChat
import ru.genesiscorporation.workspace.beta.modules.chatdialog.safeLocalFileName
import ru.genesiscorporation.workspace.beta.modules.chatdialog.sanitizePersistedConversationState
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class IncomingShareRequest(
    val requestId: String,
    val text: String,
    val attachmentUris: List<Uri>,
    val declaredMimeType: String?,
    val validationError: String? = null,
) {
    val hasContent: Boolean
        get() = text.isNotBlank() || attachmentUris.isNotEmpty()
}

internal data class IncomingShareDraftTarget(
    val streamUuid: String,
    val topicUuid: String,
    val chatTitle: String,
    val topicName: String?,
    val isDirectMessages: Boolean,
)

internal fun incomingShareStreams(
    streams: List<Stream>,
): List<Stream> =
    streams
        .asSequence()
        .filterNot(Stream::isArchived)
        .distinctBy(Stream::uuid)
        .sortedWith(
            compareByDescending<Stream> { it.isDirectProviderChat() }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
        .toList()

internal fun incomingShareTopics(
    stream: Stream,
    topics: List<TopicsResponseData>,
): List<TopicsResponseData> =
    topics
        .asSequence()
        .filter { it.streamUuid == stream.uuid }
        .distinctBy(TopicsResponseData::uuid)
        .sortedWith(
            compareByDescending<TopicsResponseData> { it.isDefault }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
        .toList()

internal fun resolveIncomingShareTarget(
    stream: Stream?,
    topic: TopicsResponseData?,
): IncomingShareDraftTarget? {
    stream ?: return null
    if (stream.isArchived) return null
    if (stream.isDirectProviderChat()) {
        val topicUuid = stream.defaultTopicUuid
            ?.takeIf(String::isNotBlank)
            ?: topic
                ?.takeIf { it.streamUuid == stream.uuid && it.isDefault }
                ?.uuid
            ?: return null
        return IncomingShareDraftTarget(
            streamUuid = stream.uuid,
            topicUuid = topicUuid,
            chatTitle = stream.name,
            topicName = null,
            isDirectMessages = true,
        )
    }
    val selectedTopic = topic
        ?.takeIf { it.streamUuid == stream.uuid }
        ?: return null
    return IncomingShareDraftTarget(
        streamUuid = stream.uuid,
        topicUuid = selectedTopic.uuid,
        chatTitle = stream.name,
        topicName = selectedTopic.name,
        isDirectMessages = false,
    )
}

internal sealed interface IncomingShareCommitResult {
    data class Accepted(
        val state: PersistedConversationState,
    ) : IncomingShareCommitResult

    data class Rejected(
        val message: String,
    ) : IncomingShareCommitResult
}

private data class StagedIncomingShare(
    val attachments: List<PersistedAttachment>,
    val createdFiles: List<File>,
)

fun Intent.toIncomingShareRequestOrNull(
    savedRequestId: String? = null,
): IncomingShareRequest? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
        return null
    }
    val requestId = canonicalIncomingShareRequestId(savedRequestId)
        ?: UUID.randomUUID().toString()
    return try {
        val subject = safeCharSequenceExtra(Intent.EXTRA_SUBJECT)
        val plainText = safeCharSequenceExtra(Intent.EXTRA_TEXT)
        val htmlText = safeStringExtra(Intent.EXTRA_HTML_TEXT)
            ?.let {
                HtmlCompat.fromHtml(
                    it.take(MAX_INCOMING_HTML_CHARS),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                ).toString()
            }
        val combinedText = combineIncomingShareText(
            subject = subject,
            text = plainText ?: htmlText,
        )
        val uris = incomingStreamUris()
            .distinctBy(Uri::toString)
            .take(MAX_INCOMING_URI_SCAN)
        val validationError = when {
            combinedText.length > MAX_INCOMING_TEXT_CHARS ->
                "Общий текст длиннее $MAX_INCOMING_TEXT_CHARS символов"

            uris.size > MAX_INCOMING_ATTACHMENTS ->
                "Можно поделиться не более чем $MAX_INCOMING_ATTACHMENTS файлами"

            combinedText.isBlank() && uris.isEmpty() ->
                "Android не передал текст или доступные файлы"

            else -> null
        }
        IncomingShareRequest(
            requestId = requestId,
            text = combinedText.take(MAX_INCOMING_TEXT_CHARS),
            attachmentUris = uris.take(MAX_INCOMING_ATTACHMENTS),
            declaredMimeType = type
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf(::isConcreteMimeType),
            validationError = validationError,
        )
    } catch (_: RuntimeException) {
        IncomingShareRequest(
            requestId = requestId,
            text = "",
            attachmentUris = emptyList(),
            declaredMimeType = null,
            validationError = "Не удалось безопасно прочитать данные из другого приложения",
        )
    }
}

internal fun combineIncomingShareText(
    subject: String?,
    text: String?,
): String {
    val cleanSubject = normalizeIncomingText(subject.orEmpty())
        .trim()
        .take(MAX_INCOMING_TEXT_CHARS + 1)
    val cleanText = normalizeIncomingText(text.orEmpty()).trim()
    return when {
        cleanSubject.isEmpty() -> cleanText
        cleanText.isEmpty() -> cleanSubject
        cleanText.startsWith(cleanSubject) -> cleanText
        else -> "$cleanSubject\n\n$cleanText"
    }
}

internal fun mergeIncomingShareDraft(
    existingState: PersistedConversationState?,
    target: IncomingShareDraftTarget,
    incomingText: String,
    incomingAttachments: List<PersistedAttachment>,
    updatedAt: String,
    incomingRequestId: String? = null,
): IncomingShareCommitResult {
    val sanitized = sanitizePersistedConversationState(
        state = existingState ?: PersistedConversationState(),
        expectedStreamUuid = target.streamUuid,
        expectedTopicUuid = target.topicUuid,
    )
    val route = PersistedConversationRoute(
        streamUuid = target.streamUuid,
        topicUuid = target.topicUuid,
        chatTitle = target.chatTitle.trim().take(MAX_ROUTE_LABEL_CHARS),
        topicName = target.topicName
            ?.trim()
            ?.take(MAX_ROUTE_LABEL_CHARS)
            ?.takeIf(String::isNotBlank),
        isDirectMessages = target.isDirectMessages,
    )
    if (
        route.streamUuid.isBlank() ||
        route.topicUuid.isBlank() ||
        route.chatTitle.isBlank() ||
        (!route.isDirectMessages && route.topicName == null)
    ) {
        return IncomingShareCommitResult.Rejected(
            "Выбранный чат больше недоступен",
        )
    }
    val validIncomingAttachments = incomingAttachments.filter {
        it.uri.startsWith("content://") &&
            it.fileName.isNotBlank() &&
            isConcreteMimeType(it.contentType) &&
            it.sizeBytes?.let { size ->
                size in 1..MAX_INCOMING_ATTACHMENT_BYTES
            } != false
    }
    if (validIncomingAttachments.size != incomingAttachments.size) {
        return IncomingShareCommitResult.Rejected(
            "Один из переданных файлов имеет небезопасный формат",
        )
    }
    if (incomingText.isBlank() && validIncomingAttachments.isEmpty()) {
        return IncomingShareCommitResult.Rejected(
            "В передаче нет текста или файлов",
        )
    }
    val baseText: String
    val baseAttachments: List<PersistedAttachment>
    val mergeIntoSuspendedDraft = sanitized.editingMessageUuid != null
    if (mergeIntoSuspendedDraft) {
        baseText = sanitized.suspendedDraft?.text.orEmpty()
        baseAttachments = sanitized.suspendedDraft?.attachments.orEmpty()
    } else {
        baseText = sanitized.draftText
        baseAttachments = sanitized.attachments
    }
    val mergedText = appendIncomingText(baseText, incomingText)
    if (mergedText.length > MAX_INCOMING_TEXT_CHARS) {
        return IncomingShareCommitResult.Rejected(
            "В выбранном чате уже есть черновик; вместе тексты превышают " +
                "$MAX_INCOMING_TEXT_CHARS символов",
        )
    }
    val mergedAttachments = (
        baseAttachments + validIncomingAttachments
    ).distinctBy(PersistedAttachment::uri)
    if (mergedAttachments.size > MAX_INCOMING_ATTACHMENTS) {
        return IncomingShareCommitResult.Rejected(
            "В выбранном черновике уже есть файлы; общий предел — " +
                "$MAX_INCOMING_ATTACHMENTS",
        )
    }
    val mergedState = if (mergeIntoSuspendedDraft) {
        sanitized.copy(
            route = route,
            suspendedDraft = PersistedComposerDraft(
                text = mergedText,
                quotedMessageUuid =
                    sanitized.suspendedDraft?.quotedMessageUuid,
                attachments = mergedAttachments,
            ),
            draftUpdatedAt = updatedAt,
            lastIncomingShareRequestId =
                incomingRequestId ?: sanitized.lastIncomingShareRequestId,
        )
    } else {
        sanitized.copy(
            route = route,
            draftText = mergedText,
            attachments = mergedAttachments,
            draftUpdatedAt = updatedAt,
            lastIncomingShareRequestId =
                incomingRequestId ?: sanitized.lastIncomingShareRequestId,
        )
    }
    return IncomingShareCommitResult.Accepted(mergedState)
}

internal suspend fun commitIncomingShareToDraft(
    context: Context,
    request: IncomingShareRequest,
    ownerKey: String,
    target: IncomingShareDraftTarget,
    repository: ApiKeyRepository,
    conversationStateStore: ConversationStateStore,
): IncomingShareCommitResult {
    request.validationError?.let {
        return IncomingShareCommitResult.Rejected(it)
    }
    if (!request.hasContent) {
        return IncomingShareCommitResult.Rejected(
            "Android не передал текст или доступные файлы",
        )
    }
    if (!repository.isActiveCredentialOwner(ownerKey)) {
        return IncomingShareCommitResult.Rejected(
            "Аккаунт изменился; выберите чат заново",
        )
    }
    val existingBeforeStaging = try {
        repository.withActiveCredentialOwner(ownerKey) {
            conversationStateStore.read(
                ownerKey = ownerKey,
                streamUuid = target.streamUuid,
                topicUuid = target.topicUuid,
            )
        }
    } catch (_: Exception) {
        return IncomingShareCommitResult.Rejected(
            "Не удалось проверить защищённый черновик",
        )
    }
    if (
        existingBeforeStaging?.lastIncomingShareRequestId == request.requestId
    ) {
        return IncomingShareCommitResult.Accepted(
            sanitizePersistedConversationState(
                state = existingBeforeStaging,
                expectedStreamUuid = target.streamUuid,
                expectedTopicUuid = target.topicUuid,
            ),
        )
    }
    val staged = try {
        stageIncomingShare(context.applicationContext, request, ownerKey)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: IncomingShareException) {
        return IncomingShareCommitResult.Rejected(exception.userMessage)
    } catch (_: Exception) {
        return IncomingShareCommitResult.Rejected(
            "Не удалось подготовить переданные файлы",
        )
    }
    var storageWriteStarted = false
    var reusedExistingCommit = false
    return try {
        var mergeResult: IncomingShareCommitResult =
            IncomingShareCommitResult.Rejected(
                "Аккаунт изменился; выберите чат заново",
            )
        val committed = repository.withActiveCredentialOwner(ownerKey) {
            val existing = conversationStateStore.read(
                ownerKey = ownerKey,
                streamUuid = target.streamUuid,
                topicUuid = target.topicUuid,
            )
            if (existing?.lastIncomingShareRequestId == request.requestId) {
                mergeResult = IncomingShareCommitResult.Accepted(
                    sanitizePersistedConversationState(
                        state = existing,
                        expectedStreamUuid = target.streamUuid,
                        expectedTopicUuid = target.topicUuid,
                    ),
                )
                reusedExistingCommit = true
                return@withActiveCredentialOwner true
            }
            mergeResult = mergeIncomingShareDraft(
                existingState = existing,
                target = target,
                incomingText = request.text,
                incomingAttachments = staged.attachments,
                updatedAt = OffsetDateTime.now()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                incomingRequestId = request.requestId,
            )
            val accepted = mergeResult as? IncomingShareCommitResult.Accepted
                ?: return@withActiveCredentialOwner false
            storageWriteStarted = true
            conversationStateStore.write(
                ownerKey = ownerKey,
                streamUuid = target.streamUuid,
                topicUuid = target.topicUuid,
                state = accepted.state,
            )
            true
        } == true
        if (!committed) {
            staged.createdFiles.forEach(File::delete)
            mergeResult
        } else {
            if (reusedExistingCommit) {
                staged.createdFiles.forEach(File::delete)
            }
            mergeResult
        }
    } catch (cancellation: CancellationException) {
        if (!storageWriteStarted) {
            staged.createdFiles.forEach(File::delete)
        }
        throw cancellation
    } catch (_: Exception) {
        if (!storageWriteStarted) {
            staged.createdFiles.forEach(File::delete)
        }
        IncomingShareCommitResult.Rejected(
            "Не удалось сохранить содержимое в защищённый черновик",
        )
    }
}

private suspend fun stageIncomingShare(
    context: Context,
    request: IncomingShareRequest,
    ownerKey: String,
): StagedIncomingShare = withContext(Dispatchers.IO) {
    val directory = accountAttachmentCacheDirectory(context.cacheDir, ownerKey)
    if (!directory.exists() && !directory.mkdirs()) {
        throw IncomingShareException("Хранилище вложений недоступно")
    }
    val nowMillis = System.currentTimeMillis()
    directory.listFiles()
        .orEmpty()
        .filter { file ->
            file.isFile &&
                file.name.startsWith(".incoming-") &&
                file.name.endsWith(".part") &&
                nowMillis - file.lastModified() > INCOMING_PARTIAL_MAX_AGE_MS
        }
        .forEach(File::delete)
    val startingCacheBytes =
        accountAttachmentCacheSizeBytes(context.cacheDir, ownerKey)
    val createdFiles = mutableListOf<File>()
    val stagedAttachments = mutableListOf<PersistedAttachment>()
    var stagedBytes = 0L
    try {
        request.attachmentUris.forEach { uri ->
            if (uri.scheme != "content") {
                throw IncomingShareException(
                    "Другое приложение передало небезопасную ссылку на файл",
                )
            }
            val metadata = describeIncomingUri(
                context = context,
                uri = uri,
                fallbackMimeType = request.declaredMimeType,
            )
            metadata.sizeBytes?.let { reportedSize ->
                if (reportedSize <= 0L) {
                    throw IncomingShareException("Нельзя прикрепить пустой файл")
                }
                if (reportedSize > MAX_INCOMING_ATTACHMENT_BYTES) {
                    throw IncomingShareException("Один из файлов больше 25 MiB")
                }
                if (
                    startingCacheBytes + stagedBytes + reportedSize >
                    MAX_ACCOUNT_ATTACHMENT_CACHE_BYTES
                ) {
                    throw IncomingShareException(
                        "Для переданных файлов недостаточно места в кэше вложений",
                    )
                }
            }
            val finalFile = File(
                directory,
                "incoming-${UUID.randomUUID()}-${metadata.fileName}",
            )
            val targetFile = File(
                directory,
                ".incoming-${UUID.randomUUID()}.part",
            )
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (_: SecurityException) {
                null
            } ?: throw IncomingShareException(
                "Другое приложение не предоставило доступ к файлу",
            )
            var copied = 0L
            try {
                input.use { source ->
                    targetFile.outputStream().buffered().use { destination ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_INCOMING_ATTACHMENT_BYTES) {
                                throw IncomingShareException(
                                    "Один из файлов больше 25 MiB",
                                )
                            }
                            if (
                                startingCacheBytes + stagedBytes + copied >
                                MAX_ACCOUNT_ATTACHMENT_CACHE_BYTES
                            ) {
                                throw IncomingShareException(
                                    "Для переданных файлов недостаточно места " +
                                        "в кэше вложений",
                                )
                            }
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                if (copied == 0L) {
                    throw IncomingShareException("Нельзя прикрепить пустой файл")
                }
                if (!targetFile.renameTo(finalFile)) {
                    throw IncomingShareException(
                        "Не удалось завершить копирование файла",
                    )
                }
                createdFiles += finalFile
                stagedBytes += copied
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    finalFile,
                )
                stagedAttachments += PersistedAttachment(
                    uri = contentUri.toString(),
                    fileName = metadata.fileName,
                    contentType = metadata.contentType,
                    sizeBytes = copied,
                )
            } catch (exception: Exception) {
                targetFile.delete()
                finalFile.delete()
                throw exception
            }
        }
        StagedIncomingShare(stagedAttachments, createdFiles)
    } catch (cancellation: CancellationException) {
        createdFiles.forEach(File::delete)
        throw cancellation
    } catch (exception: Exception) {
        createdFiles.forEach(File::delete)
        throw exception
    }
}

private data class IncomingUriMetadata(
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long?,
)

private fun describeIncomingUri(
    context: Context,
    uri: Uri,
    fallbackMimeType: String?,
): IncomingUriMetadata {
    var displayName: String? = null
    var sizeBytes: Long? = null
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { displayName = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { sizeBytes = cursor.getLong(it) }
            }
        }
    } catch (_: SecurityException) {
        throw IncomingShareException(
            "Другое приложение не предоставило доступ к файлу",
        )
    } catch (_: RuntimeException) {
        // Some providers expose the stream but not metadata. The bounded copy
        // below remains the source of truth for size.
    }
    val contentType = runCatching {
        context.contentResolver.getType(uri)
    }.getOrNull()
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(::isConcreteMimeType)
        ?: fallbackMimeType?.takeIf(::isConcreteMimeType)
        ?: "application/octet-stream"
    return IncomingUriMetadata(
        fileName = safeLocalFileName(
            displayName ?: uri.lastPathSegment ?: "file",
        ),
        contentType = contentType,
        sizeBytes = sizeBytes?.takeIf { it >= 0L },
    )
}

private fun Intent.safeCharSequenceExtra(name: String): String? =
    getCharSequenceExtra(name)?.toString()

private fun Intent.safeStringExtra(name: String): String? =
    getStringExtra(name)

@Suppress("DEPRECATION")
private fun Intent.incomingStreamUris(): List<Uri> {
    val extrasValue = extras?.get(Intent.EXTRA_STREAM)
    val extraUris = when (extrasValue) {
        is Uri -> listOf(extrasValue)
        is ArrayList<*> -> extrasValue.filterIsInstance<Uri>()
        is Array<*> -> extrasValue.filterIsInstance<Uri>()
        else -> emptyList()
    }
    val clipUris = buildList {
        val clip = clipData ?: return@buildList
        repeat(clip.itemCount.coerceAtMost(MAX_INCOMING_URI_SCAN)) { index ->
            clip.getItemAt(index).uri?.let(::add)
        }
    }
    return extraUris + clipUris
}

private fun normalizeIncomingText(value: String): String =
    value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\u0000", "")

private fun canonicalIncomingShareRequestId(value: String?): String? =
    value
        ?.let { candidate ->
            runCatching { UUID.fromString(candidate).toString() }.getOrNull()
        }

private fun appendIncomingText(existing: String, incoming: String): String =
    when {
        incoming.isBlank() -> existing
        existing.isBlank() -> incoming
        existing.endsWith("\n\n") -> existing + incoming
        existing.endsWith('\n') -> "$existing\n$incoming"
        else -> "$existing\n\n$incoming"
    }

private fun isConcreteMimeType(value: String): Boolean =
    value.matches(Regex("""[a-z0-9.+-]+/[a-z0-9.+-]+"""))

private class IncomingShareException(
    val userMessage: String,
) : IOException(userMessage)

internal const val MAX_INCOMING_ATTACHMENTS = 10
internal const val MAX_INCOMING_TEXT_CHARS = 40_000
private const val MAX_INCOMING_HTML_CHARS = 100_000
private const val MAX_INCOMING_URI_SCAN = MAX_INCOMING_ATTACHMENTS + 1
private const val MAX_INCOMING_ATTACHMENT_BYTES = 25L * 1024L * 1024L
private const val MAX_ACCOUNT_ATTACHMENT_CACHE_BYTES = 100L * 1024L * 1024L
private const val INCOMING_PARTIAL_MAX_AGE_MS = 24L * 60L * 60L * 1000L
private const val MAX_ROUTE_LABEL_CHARS = 512
