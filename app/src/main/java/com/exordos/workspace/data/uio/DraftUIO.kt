package com.exordos.workspace.data.uio

data class DraftUIO(
    val uuid: String,
    val streamUuid: String,
    val topicUuid: String,
    val updatedAt: String,
    var payload: MessagePayloadUIO,
    val revision: Int
)
