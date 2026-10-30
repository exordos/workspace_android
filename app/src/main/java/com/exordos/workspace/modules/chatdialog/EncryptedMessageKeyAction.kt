package com.exordos.workspace.modules.chatdialog

import com.exordos.workspace.data.remote.dto.MessageResponse
import com.exordos.workspace.modules.encryptedchat.EncryptedChatFailure
import com.exordos.workspace.modules.encryptedchat.EncryptedChatRequestStatus
import com.exordos.workspace.modules.encryptedchat.EncryptedChatSessionState

internal data class EncryptedMessageKeyTarget(val keyUuid: String, val rawMessage: MessageResponse)

/** A message action always targets that message's generation, including historical generations. */
internal fun encryptedMessageKeyTarget(
    message: MessageResponse,
    streamUuid: String,
    topicUuid: String,
    projectUuid: String?,
): EncryptedMessageKeyTarget? {
    if (message.streamUuid != streamUuid || message.topicUuid != topicUuid ||
        (projectUuid != null && message.projectId != null && message.projectId != projectUuid)) return null
    val payload = message.originalEncryptedPayload ?: message.payload
    if (payload.kind != "e2ee" || payload.keyUuid.isNullOrBlank()) return null
    return EncryptedMessageKeyTarget(payload.keyUuid,
        message.copy(payload = payload.copy(), originalEncryptedPayload = null))
}

internal fun messageKeyRequestPending(keyUuid: String?, state: EncryptedChatSessionState): Boolean =
    keyUuid.isNullOrBlank() || state.pending || state.error == EncryptedChatFailure.STALE ||
        state.requestStatus[keyUuid] == EncryptedChatRequestStatus.REQUESTED
