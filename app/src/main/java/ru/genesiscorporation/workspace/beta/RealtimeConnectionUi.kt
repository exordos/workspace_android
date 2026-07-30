package ru.genesiscorporation.workspace.beta

import ru.genesiscorporation.workspace.beta.data.RealtimeConnectionState

enum class RealtimeConnectionBannerKind {
    CONNECTING,
    RECOVERING,
}

data class RealtimeConnectionBannerState(
    val kind: RealtimeConnectionBannerKind,
    val canRetryNow: Boolean,
)

internal fun realtimeConnectionBannerState(
    state: RealtimeConnectionState,
): RealtimeConnectionBannerState? =
    when (state) {
        RealtimeConnectionState.CONNECTING ->
            RealtimeConnectionBannerState(
                kind = RealtimeConnectionBannerKind.CONNECTING,
                canRetryNow = false,
            )

        RealtimeConnectionState.BACKING_OFF ->
            RealtimeConnectionBannerState(
                kind = RealtimeConnectionBannerKind.RECOVERING,
                canRetryNow = true,
            )

        RealtimeConnectionState.CONNECTED,
        RealtimeConnectionState.PAUSED,
        -> null
    }
