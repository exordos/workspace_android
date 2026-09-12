package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.annotation.StringRes
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.SendMessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid

internal const val MAX_FORWARD_SOURCE_MESSAGES = 32

internal fun isCanonicalForwardUuid(value: String): Boolean = parseCanonicalMessageUuid(value) != null

internal fun canForwardMessage(message: MessageResponse): Boolean =
    isCanonicalForwardUuid(message.uuid) && message.authorUuid.isNotBlank()

/** Keep the supplied (conversation) order, never merge or silently discard sources. */
internal fun buildWorkspaceForwardMarkdown(
    messages: List<MessageResponse>,
    resolveSourceLabel: (MessageResponse) -> String = { it.streamUuid },
    resolveSourceIsDirect: (MessageResponse) -> Boolean = { false },
): String? {
    if (messages.isEmpty() || messages.size > MAX_FORWARD_SOURCE_MESSAGES ||
        messages.any { !canForwardMessage(it) } ||
        messages.map { parseCanonicalMessageUuid(it.uuid) }.distinct().size != messages.size
    ) return null
    return messages.joinToString("\n\n") { message ->
        val label = (message.user?.displayableName()?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.authorUuid.trim()).replace(Regex("[\\r\\n\\t]+"), " ").take(512)
        buildForwardSnapshotReference(
            label,
            message.uuid,
            message.payload.content,
            resolveSourceLabel(message).trim().ifEmpty { message.streamUuid },
            message.createdAt,
            sourceIsDirect = resolveSourceIsDirect(message),
        )
    }
}

internal fun buildForwardSnapshotReference(
    author: String,
    sourceUuid: String,
    content: String,
    sourceLabel: String? = null,
    sourceCreatedAt: String? = null,
    plainText: Boolean = false,
    sourceIsDirect: Boolean = false,
): String {
    require(isCanonicalForwardUuid(sourceUuid))
    val snapshot = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(content.toByteArray(StandardCharsets.UTF_8))
    val source = sourceLabel?.trim()?.takeIf { it.isNotEmpty() }?.take(512)
    val createdAt = sourceCreatedAt?.trim()?.takeIf { it.isNotEmpty() }
    require((source == null) == (createdAt == null))
    require(!sourceIsDirect || source != null)
    val format = if (plainText) "&format=plain" else ""
    val metadata = if (source == null) "" else {
        val encodedSource = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(source.toByteArray(StandardCharsets.UTF_8))
        val encodedCreatedAt = URLEncoder.encode(requireNotNull(createdAt), "UTF-8")
            .replace("+", "%20")
        val kind = if (sourceIsDirect) "&source_kind=direct" else ""
        "&source=$encodedSource$kind&created_at=$encodedCreatedAt"
    }
    return "[${escapeWorkspaceMarkdownInline(author)}](urn:forward:$sourceUuid?snapshot=$snapshot$format$metadata)"
}

internal fun decodeForwardSnapshot(encoded: String): String? {
    if (!Regex("[A-Za-z0-9_-]*").matches(encoded) || encoded.length % 4 == 1) return null
    return runCatching {
        val bytes = Base64.getUrlDecoder().decode(encoded)
        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        decoded.takeIf {
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(it.toByteArray(StandardCharsets.UTF_8)) == encoded
        }
    }.getOrNull()
}

internal fun buildForwardSnapshotBlock(author: String, content: String): String =
    "> **${escapeWorkspaceMarkdownInline(author)}**:\n" +
        content.lineSequence().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" } + "\n\n"

internal fun buildWorkspaceForwardMarkdown(message: MessageResponse): String? =
    buildWorkspaceForwardMarkdown(listOf(message))

internal fun escapeWorkspaceMarkdownInline(value: String): String = buildString {
    value.forEach { character ->
        if (character in "\\`*_{}()[]#+.!|>~-") append('\\')
        append(character)
    }
}

internal fun forwardableStreams(streams: List<Stream>): List<Stream> = streams
    .filter { !it.isArchived && isCanonicalForwardUuid(it.uuid) }
    .distinctBy(Stream::uuid)
    .sortedByDescending(Stream::updatedAt)

internal data class ForwardDestination(val streamUuid: String, val topicUuid: String)

data class ForwardSourceDestination(
    val streamName: String,
    val streamUuid: String,
    val topicName: String,
    val topicUuid: String,
    val isDirectMessages: Boolean,
    val messageUuid: String,
)

/** Resolve route data only after an ACL-checked source read has succeeded. */
internal suspend fun resolveForwardSourceDestinationWithAcl(
    messageUuid: String,
    readSource: suspend (String) -> MessageResponse?,
    cachedStreams: () -> List<Stream>,
    readStreams: suspend () -> List<Stream>,
    cachedTopics: (String) -> List<TopicsResponseData>,
    readTopics: suspend (String) -> List<TopicsResponseData>,
    cacheSource: (MessageResponse) -> Unit = {},
    cacheStream: (Stream) -> Unit = {},
    cacheTopic: (TopicsResponseData) -> Unit = {},
): ForwardSourceDestination? {
    val uuid = parseCanonicalMessageUuid(messageUuid) ?: return null
    val source = readSource(uuid)?.takeIf { parseCanonicalMessageUuid(it.uuid) == uuid } ?: return null
    val stream = cachedStreams().firstOrNull { it.uuid == source.streamUuid }
        ?: readStreams().singleOrNull { it.uuid == source.streamUuid }?.also(cacheStream)
        ?: return null
    val topic = cachedTopics(source.streamUuid).firstOrNull { it.uuid == source.topicUuid }
        ?: readTopics(source.topicUuid).singleOrNull {
            it.uuid == source.topicUuid && it.streamUuid == source.streamUuid
        }?.also(cacheTopic)
        ?: return null
    cacheSource(source)
    return ForwardSourceDestination(
        streamName = stream.name,
        streamUuid = stream.uuid,
        topicName = topic.name,
        topicUuid = topic.uuid,
        isDirectMessages = stream.directUserUuid != null,
        messageUuid = source.uuid,
    )
}

internal enum class ForwardDeliveryStatus { EDITING, SENDING, VERIFYING, UNCERTAIN, COMPLETED }

internal data class ForwardDeliveryState(
    val status: ForwardDeliveryStatus = ForwardDeliveryStatus.EDITING,
    @param:StringRes val error: Int? = null,
) {
    val busy: Boolean get() = status == ForwardDeliveryStatus.SENDING || status == ForwardDeliveryStatus.VERIFYING
    val canEdit: Boolean get() = status == ForwardDeliveryStatus.EDITING
}

/** No optimistic success and no second POST after an ambiguous first attempt. */
internal class MessageForwarding(
    val sources: List<MessageResponse>,
    private val currentUserUuid: String,
    private val post: suspend (String, ForwardDestination, String) -> ApiResult<SendMessageResponse, ApiError>,
    private val read: suspend (String) -> ApiResult<List<MessageResponse>, ApiError>,
    private val onConfirmed: (MessageResponse) -> Unit,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val prepare: suspend (ForwardDestination) -> String = { requireNotNull(buildWorkspaceForwardMarkdown(sources)) },
) {
    private var content = requireNotNull(buildWorkspaceForwardMarkdown(sources))
    private val mutex = Mutex()
    private var destination: ForwardDestination? = null
    val pendingDestination: ForwardDestination? get() = destination
    private var requestUuid: String? = null
    private var acknowledgedUuid: String? = null
    private val _state = MutableStateFlow(ForwardDeliveryState())
    val state = _state.asStateFlow()

    suspend fun send(target: ForwardDestination) {
        if (!mutex.tryLock()) return
        try {
            if (!_state.value.canEdit) return
            if (!isCanonicalForwardUuid(target.streamUuid) || !isCanonicalForwardUuid(target.topicUuid) ||
                currentUserUuid.isBlank()
            ) {
                _state.value = ForwardDeliveryState(error = R.string.forward_recipient_unknown)
                return
            }
            destination = target
            requestUuid = null
            acknowledgedUuid = null
            prepareAndPost(target)
            currentCoroutineContext().ensureActive()
        } catch (failure: ForwardPreparationFailure) {
            _state.value = ForwardDeliveryState(if (failure.uncertain) ForwardDeliveryStatus.UNCERTAIN else ForwardDeliveryStatus.EDITING, failure.resourceId)
        } catch (cancelled: CancellationException) {
            markUncertain()
            throw cancelled
        } catch (_: Exception) {
            markUncertain()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun prepareAndPost(target: ForwardDestination) {
            _state.value = ForwardDeliveryState(ForwardDeliveryStatus.SENDING)
            content = prepare(target)
            currentCoroutineContext().ensureActive()
            requestUuid = newUuid()
            when (val result = post(requireNotNull(requestUuid), target, content)) {
                is ApiResult.Success -> {
                    // Deployed servers may return a different UUID; read the returned identity.
                    acknowledgedUuid = parseCanonicalMessageUuid(result.value.uuid)
                    if (result.value.topicUuid != target.topicUuid || acknowledgedUuid == null) {
                        markUncertain()
                    } else {
                        verifyLocked()
                    }
                }
                is ApiResult.Error -> {
                    val code = result.error.code.toIntOrNull()
                    if (code != null && code in 400..499 && code !in setOf(408, 409, 425)) {
                        _state.value = ForwardDeliveryState(error = when (code) {
                            403 -> R.string.forward_chat_forbidden
                            404 -> R.string.forward_destination_unavailable
                            else -> R.string.forward_send_failed
                        })
                    } else {
                        // The server can have accepted the POST before the connection failed.
                        verifyLocked()
                    }
                }
            }
    }

    suspend fun verify() {
        if (!mutex.tryLock()) return
        try {
            if (_state.value.status == ForwardDeliveryStatus.UNCERTAIN) verifyLocked()
        } catch (failure: ForwardPreparationFailure) {
            _state.value = ForwardDeliveryState(if (failure.uncertain) ForwardDeliveryStatus.UNCERTAIN else ForwardDeliveryStatus.EDITING, failure.resourceId)
        } catch (cancelled: CancellationException) {
            markUncertain()
            throw cancelled
        } catch (_: Exception) {
            markUncertain()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun verifyLocked() {
        val target = destination ?: return
        val id = acknowledgedUuid ?: requestUuid ?: run { prepareAndPost(target); return }
        _state.value = ForwardDeliveryState(ForwardDeliveryStatus.VERIFYING)
        val result = read(id)
        currentCoroutineContext().ensureActive()
        val confirmed = (result as? ApiResult.Success)?.value?.singleOrNull { message ->
            parseCanonicalMessageUuid(message.uuid) == id &&
                message.isOwn && message.authorUuid == currentUserUuid &&
                message.streamUuid == target.streamUuid && message.topicUuid == target.topicUuid &&
                message.payload.kind == "markdown" && message.payload.content == content
        }
        if (confirmed != null) {
            _state.value = ForwardDeliveryState(ForwardDeliveryStatus.COMPLETED)
            onConfirmed(confirmed)
        } else {
            markUncertain()
        }
    }

    private fun markUncertain() {
        _state.value = ForwardDeliveryState(
            ForwardDeliveryStatus.UNCERTAIN,
            R.string.forward_delivery_uncertain,
        )
    }
}


internal class ForwardPreparationFailure(
    @param:StringRes val resourceId: Int,
    val uncertain: Boolean = false,
) : Exception("Forward preparation failed (resource $resourceId)")

/** Lives in the chat ViewModel so cancellation/reopening cannot discard an unresolved POST. */
internal class ForwardSessionStore {
    private var ownerKey: String? = null
    private var active: MessageForwarding? = null
    fun unresolved(owner: String): MessageForwarding? = active?.takeIf {
        ownerKey == owner && (it.state.value.busy || it.state.value.status == ForwardDeliveryStatus.UNCERTAIN)
    }
    fun getOrCreate(owner: String, create: () -> MessageForwarding): MessageForwarding =
        unresolved(owner) ?: create().also { ownerKey = owner; active = it }

    /** Forget only an idle unresolved attempt after the user accepts that it may have arrived. */
    fun abandon(owner: String, expected: MessageForwarding): Boolean {
        if (ownerKey != owner || active !== expected ||
            expected.state.value.status != ForwardDeliveryStatus.UNCERTAIN
        ) return false
        active = null
        ownerKey = null
        return true
    }
}
