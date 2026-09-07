package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.annotation.StringRes
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
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid

internal const val MAX_FORWARD_SOURCE_MESSAGES = 32

internal fun isCanonicalForwardUuid(value: String): Boolean = parseCanonicalMessageUuid(value) != null

internal fun canForwardMessage(message: MessageResponse): Boolean =
    isCanonicalForwardUuid(message.uuid) && message.authorUuid.isNotBlank()

/** Keep the supplied (conversation) order, never merge or silently discard sources. */
internal fun buildWorkspaceForwardMarkdown(messages: List<MessageResponse>): String? {
    if (messages.isEmpty() || messages.size > MAX_FORWARD_SOURCE_MESSAGES ||
        messages.any { !canForwardMessage(it) } ||
        messages.map { parseCanonicalMessageUuid(it.uuid) }.distinct().size != messages.size
    ) return null
    return messages.joinToString("\n\n") { message ->
        val label = (message.user?.displayableName()?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.authorUuid.trim()).replace(Regex("[\\r\\n\\t]+"), " ").take(512)
        buildForwardSnapshotBlock(label, message.payload.content)
    }
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
