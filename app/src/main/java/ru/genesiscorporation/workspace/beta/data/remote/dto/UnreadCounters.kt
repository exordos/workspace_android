package ru.genesiscorporation.workspace.beta.data.remote.dto

data class DisplayedUnreadCount(
    val count: Int,
    val passive: Boolean,
)

internal fun resolvedActiveUnreadCount(
    unreadCount: Int,
    activeUnreadCount: Int?,
): Int = (activeUnreadCount ?: unreadCount).coerceAtLeast(0)

internal fun resolvedPassiveUnreadCount(passiveUnreadCount: Int?): Int =
    (passiveUnreadCount ?: 0).coerceAtLeast(0)

internal fun resolveDisplayedUnreadCount(
    unreadCount: Int,
    activeUnreadCount: Int?,
    passiveUnreadCount: Int?,
): DisplayedUnreadCount? {
    val active = resolvedActiveUnreadCount(unreadCount, activeUnreadCount)
    if (active > 0) return DisplayedUnreadCount(count = active, passive = false)

    val passive = resolvedPassiveUnreadCount(passiveUnreadCount)
    return passive
        .takeIf { it > 0 }
        ?.let { DisplayedUnreadCount(count = it, passive = true) }
}

fun Stream.resolvedActiveUnreadCount(): Int =
    resolvedActiveUnreadCount(unreadCount, activeUnreadCount)

fun Stream.resolvedPassiveUnreadCount(): Int =
    resolvedPassiveUnreadCount(passiveUnreadCount)

fun Stream.displayedUnreadCount(): DisplayedUnreadCount? =
    resolveDisplayedUnreadCount(unreadCount, activeUnreadCount, passiveUnreadCount)

fun TopicsResponseData.resolvedActiveUnreadCount(): Int =
    resolvedActiveUnreadCount(unreadCount, activeUnreadCount)

fun TopicsResponseData.resolvedPassiveUnreadCount(): Int =
    resolvedPassiveUnreadCount(passiveUnreadCount)

fun TopicsResponseData.displayedUnreadCount(): DisplayedUnreadCount? =
    resolveDisplayedUnreadCount(unreadCount, activeUnreadCount, passiveUnreadCount)

fun FolderItem.resolvedActiveUnreadCount(): Int =
    resolvedActiveUnreadCount(unreadCount, activeUnreadCount)

fun FolderItem.resolvedPassiveUnreadCount(): Int =
    resolvedPassiveUnreadCount(passiveUnreadCount)

fun FolderItem.displayedUnreadCount(): DisplayedUnreadCount? =
    resolveDisplayedUnreadCount(unreadCount, activeUnreadCount, passiveUnreadCount)

fun isTopicNotificationExplicitlyActive(notificationMode: String): Boolean =
    notificationMode.equals("unmute", ignoreCase = true) ||
        notificationMode.equals("follow", ignoreCase = true)

fun TopicsResponseData.isEffectivelyMuted(streamNotificationMode: String): Boolean =
    notificationMode.equals("mute", ignoreCase = true) ||
        (
            notificationMode.equals("default", ignoreCase = true) &&
                streamNotificationMode.equals("muted", ignoreCase = true)
            )

fun Stream.isFullyMuted(topics: List<TopicsResponseData>): Boolean =
    notificationMode.equals("muted", ignoreCase = true) &&
        topics.none { isTopicNotificationExplicitlyActive(it.notificationMode) }
