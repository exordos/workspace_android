package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.parseCanonicalMessageUuid

internal const val MAX_SELECTED_MESSAGES = 50

internal fun canSelectMessage(
    message: MessageResponse,
    streamUuid: String,
    topicUuid: String,
    pendingMessageUuid: String? = null,
): Boolean = parseCanonicalMessageUuid(message.uuid) != null &&
    message.streamUuid == streamUuid &&
    message.topicUuid == topicUuid &&
    message.uuid != pendingMessageUuid

internal class MessageSelection(
    private val canSelect: (MessageResponse) -> Boolean,
    private val canDelete: (MessageResponse) -> Boolean,
    private val deletion: MessageDeletion,
) {
    private var selected = linkedMapOf<String, MessageResponse>()
    private val _selectedMessageUuids = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessageUuids = _selectedMessageUuids.asStateFlow()
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()
    private val _canDeleteSelectedMessages = MutableStateFlow(false)
    val canDeleteSelectedMessages = _canDeleteSelectedMessages.asStateFlow()
    private val _deletingSelectedMessages = MutableStateFlow(false)
    val deletingSelectedMessages = _deletingSelectedMessages.asStateFlow()

    fun selectedMessages(): List<MessageResponse> = selected.values.map {
        it.copy(payload = it.payload.copy())
    }

    fun startMessageSelection(message: MessageResponse) {
        if (_deletingSelectedMessages.value || !canSelect(message)) return
        if (message.uuid !in selected) toggleMessageSelection(message)
    }

    fun toggleMessageSelection(message: MessageResponse) {
        if (_deletingSelectedMessages.value || !canSelect(message)) return
        if (message.uuid in selected) {
            selected.remove(message.uuid)
        } else if (selected.size < MAX_SELECTED_MESSAGES) {
            selected[message.uuid] = message.copy(payload = message.payload.copy())
        } else {
            deletion.showActionError(R.string.messages_selection_limit, MAX_SELECTED_MESSAGES)
        }
        publishSelection()
    }

    fun clearMessageSelection() {
        if (_deletingSelectedMessages.value) return
        selected.clear()
        publishSelection()
    }

    fun removeMessage(messageUuid: String) {
        selected.remove(messageUuid)
        publishSelection()
    }

    fun refreshMessages(messages: List<MessageResponse>) {
        val messagesByUuid = messages.associateBy { it.uuid }
        selected = selected.mapNotNull { (uuid, _) ->
            messagesByUuid[uuid]?.takeIf(canSelect)?.let {
                uuid to it.copy(payload = it.payload.copy())
            }
        }.toMap(linkedMapOf())
        publishSelection()
    }

    suspend fun deleteSelectedMessages() {
        if (!_deletingSelectedMessages.compareAndSet(false, true)) return
        val snapshot = selectedMessages()
        publishSelection()
        try {
            if (snapshot.isEmpty() || snapshot.any { !canDelete(it) }) {
                deletion.showActionError(R.string.messages_selection_own_only)
                return
            }
            var succeeded = 0
            var failed = 0
            for (message in snapshot) {
                if (deletion.delete(message)) {
                    succeeded++
                    selected.remove(message.uuid)
                    publishSelection()
                } else {
                    failed++
                }
            }
            if (failed > 0) {
                deletion.showActionError(
                    R.string.messages_delete_partial_failure,
                    succeeded,
                    snapshot.size,
                    failed,
                )
            }
        } finally {
            _deletingSelectedMessages.value = false
            publishSelection()
        }
    }

    private fun publishSelection() {
        _selectedMessageUuids.value = selected.keys.toSet()
        _isSelectionMode.value = selected.isNotEmpty()
        _canDeleteSelectedMessages.value = selected.isNotEmpty() &&
            !_deletingSelectedMessages.value && selected.values.all(canDelete)
    }
}
