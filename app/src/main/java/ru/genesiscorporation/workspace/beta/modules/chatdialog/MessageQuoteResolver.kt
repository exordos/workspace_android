package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid

internal sealed interface MessageQuoteState {
    data object Loading : MessageQuoteState
    data class Ready(val message: MessageResponse) : MessageQuoteState
    data object Unavailable : MessageQuoteState
    data object Error : MessageQuoteState
}

internal class MessageQuoteResolver(
    private val scope: CoroutineScope,
    private val request: suspend (List<String>) -> ApiResult<List<MessageResponse>, ApiError>,
) {
    private val lock = Any()
    private var generation = 0
    private val pending = linkedSetOf<String>()
    private val deleted = mutableSetOf<String>()
    private val messageVersions = mutableMapOf<String, Int>()
    private val _states = MutableStateFlow<Map<String, MessageQuoteState>>(emptyMap())
    val states = _states.asStateFlow()

    fun load(messageUuids: Collection<String>) {
        val scheduled = synchronized(lock) {
            val next = LinkedHashMap(_states.value)
            var added = false
            messageUuids.forEach { value ->
                val uuid = parseCanonicalMessageUuid(value)
                when {
                    uuid == null -> next[value] = MessageQuoteState.Unavailable
                    uuid in deleted -> next[uuid] = MessageQuoteState.Unavailable
                    uuid !in next -> {
                        next[uuid] = MessageQuoteState.Loading
                        pending += uuid
                        added = true
                    }
                }
            }
            _states.value = next
            added
        }
        if (!scheduled) return
        scope.launch {
            // Coalesce references discovered by adjacent composed messages.
            yield()
            val (requestGeneration, ids) = synchronized(lock) {
                generation to pending.toList().also { pending.clear() }
            }
            ids.chunked(50).forEach { batch -> resolve(requestGeneration, batch) }
        }
    }

    fun retry(messageUuid: String) {
        val uuid = parseCanonicalMessageUuid(messageUuid) ?: return
        val retry = synchronized(lock) {
            if (_states.value[uuid] != MessageQuoteState.Error || uuid in deleted) {
                false
            } else {
                _states.value = _states.value - uuid
                true
            }
        }
        if (retry) load(listOf(uuid))
    }

    fun invalidate(messageUuid: String) {
        val uuid = parseCanonicalMessageUuid(messageUuid) ?: return
        synchronized(lock) {
            if (uuid !in _states.value && uuid !in pending) return
            deleted += uuid
            pending -= uuid
            _states.value = _states.value + (uuid to MessageQuoteState.Unavailable)
        }
    }

    fun update(message: MessageResponse) {
        val uuid = parseCanonicalMessageUuid(message.uuid) ?: return
        synchronized(lock) {
            if (uuid !in _states.value || uuid in deleted) return
            messageVersions[uuid] = (messageVersions[uuid] ?: 0) + 1
            pending -= uuid
            _states.value = _states.value + (
                uuid to MessageQuoteState.Ready(message.copy(payload = message.payload.copy()))
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            generation++
            pending.clear()
            deleted.clear()
            messageVersions.clear()
            _states.value = emptyMap()
        }
    }

    private suspend fun resolve(requestGeneration: Int, ids: List<String>) {
        val requestVersions = synchronized(lock) {
            if (generation != requestGeneration) return
            ids.filter { it !in deleted && _states.value[it] == MessageQuoteState.Loading }
                .associateWith { messageVersions[it] ?: 0 }
        }
        val activeIds = requestVersions.keys.toList()
        if (activeIds.isEmpty()) return
        val result = try {
            request(activeIds).also { currentCoroutineContext().ensureActive() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            ApiResult.Error(ApiError("Reference request failed", "REQUEST_FAILED"))
        }
        synchronized(lock) {
            if (generation != requestGeneration) return
            val next = LinkedHashMap(_states.value)
            val messages = when (result) {
                is ApiResult.Success -> result.value.associateBy { it.uuid.lowercase() }
                is ApiResult.Error -> emptyMap()
            }
            activeIds.forEach { uuid ->
                if ((messageVersions[uuid] ?: 0) != requestVersions[uuid]) return@forEach
                next[uuid] = when {
                    uuid in deleted -> MessageQuoteState.Unavailable
                    result is ApiResult.Success -> {
                        messages[uuid]?.let { message ->
                            MessageQuoteState.Ready(message.copy(payload = message.payload.copy()))
                        } ?: MessageQuoteState.Unavailable
                    }
                    result is ApiResult.Error && result.error.code in setOf("403", "404") ->
                        MessageQuoteState.Unavailable
                    else -> MessageQuoteState.Error
                }
            }
            _states.value = next
        }
    }
}
