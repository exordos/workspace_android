package ru.genesiscorporation.workspace.beta.data.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PushTokenUpdates {
    private val mutableTokens = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tokens = mutableTokens.asSharedFlow()

    fun publish(token: String) {
        if (token.isNotBlank()) mutableTokens.tryEmit(token)
    }
}
