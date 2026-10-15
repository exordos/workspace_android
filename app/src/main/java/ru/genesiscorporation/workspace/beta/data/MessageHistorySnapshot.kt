package ru.genesiscorporation.workspace.beta.data

import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse

/** Apply an authoritative history response without losing changes made while it was loading. */
internal fun mergeMessageHistorySnapshot(
    beforeRequest: List<MessageResponse>,
    current: List<MessageResponse>,
    snapshot: List<MessageResponse>,
    pendingUuids: Set<String>,
): List<MessageResponse> {
    val beforeById = beforeRequest.associateBy { it.uuid }
    val currentIds = current.mapTo(mutableSetOf()) { it.uuid }
    val removedDuringRequest = beforeById.keys - currentIds
    val merged = snapshot.filterNot { it.uuid in removedDuringRequest }
        .associateByTo(linkedMapOf()) { it.uuid }
    current.forEach { message ->
        if (message.uuid in pendingUuids || message != beforeById[message.uuid]) {
            merged[message.uuid] = message
        }
    }
    return merged.values.toList()
}
