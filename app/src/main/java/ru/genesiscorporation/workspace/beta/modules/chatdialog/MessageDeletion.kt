package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid
import ru.genesiscorporation.workspace.beta.R
import java.util.concurrent.atomic.AtomicLong

/** A new token keeps identical failures observable while an earlier Snackbar is visible. */
data class MessageActionError(
    @param:StringRes val resourceId: Int,
    val formatArgs: List<Int>,
    val token: Long,
)

internal fun canDeleteMessage(
    message: MessageResponse,
    streamUuid: String,
    topicUuid: String,
    pendingMessageUuid: String? = null,
): Boolean = message.isOwn &&
    parseCanonicalMessageUuid(message.uuid) != null &&
    message.streamUuid == streamUuid &&
    message.topicUuid == topicUuid &&
    message.uuid != pendingMessageUuid

internal class MessageDeletion(
    private val canDelete: (MessageResponse) -> Boolean,
    private val request: suspend (String) -> ApiResult<String, ApiError>,
    private val onDeleted: (String) -> Unit,
) {
    private val mutex = Mutex()
    private val _deletingMessageUuids = MutableStateFlow<Set<String>>(emptySet())
    val deletingMessageUuids = _deletingMessageUuids.asStateFlow()
    private val errorTokens = AtomicLong()
    private val _actionError = MutableStateFlow<MessageActionError?>(null)
    val actionError = _actionError.asStateFlow()

    fun clearActionError(error: MessageActionError) {
        _actionError.compareAndSet(error, null)
    }

    fun showActionError(@StringRes resourceId: Int, vararg formatArgs: Int) {
        _actionError.value = MessageActionError(resourceId, formatArgs.toList(), errorTokens.incrementAndGet())
    }

    suspend fun delete(message: MessageResponse): Boolean {
        if (!canDelete(message)) {
            showActionError(R.string.message_delete_not_allowed)
            return false
        }
        val messageUuid = message.uuid
        val started = mutex.withLock {
            if (messageUuid in _deletingMessageUuids.value) {
                false
            } else {
                _deletingMessageUuids.update { it + messageUuid }
                true
            }
        }
        if (!started) return false
        _actionError.value = null
        try {
            val result = request(messageUuid)
            currentCoroutineContext().ensureActive()
            return when (result) {
                is ApiResult.Success -> {
                    onDeleted(messageUuid)
                    true
                }
                is ApiResult.Error -> {
                    showActionError(R.string.message_delete_failed)
                    false
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            showActionError(R.string.message_delete_failed)
            return false
        } finally {
            _deletingMessageUuids.update { it - messageUuid }
        }
    }
}
