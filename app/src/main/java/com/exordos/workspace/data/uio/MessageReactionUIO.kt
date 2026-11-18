package com.exordos.workspace.data.uio

data class MessageReactionUIO(
    val uuid: String,
    val userUuid: String,
    val emojiName: String,
    val messageUuid: String,
)
