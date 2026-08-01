package ru.genesiscorporation.workspace.beta

import kotlinx.serialization.Serializable

interface Destinations {
    val route: String
    val icon: Int
    val title: String
}

object Chat: Destinations {
    override val route = "Chat"
    override val icon = R.drawable.chat_bubble
    override val title = "Chat"
}

object Feed: Destinations {
    override val route = "Feed"
    override val icon = R.drawable.ic_nav_feed
    override val title = "Feed"
}

object Calendar: Destinations {
    override val route = "Calendar"
    override val icon = R.drawable.ic_nav_calendar
    override val title = "Calendar"
}

object Mail: Destinations {
    override val route = "Mail"
    override val icon = R.drawable.ic_mail
    override val title = "Mail"
}

object ChatFlow {
    @Serializable
    object ChatList

    @Serializable
    object Inbox

    @Serializable
    object Feed

    @Serializable
    object Starred

    @Serializable
    data class StreamFeed(val streamName: String, val streamUuid: String)

    @Serializable
    object Drafts

    @Serializable
    object FolderDisplay

    @Serializable
    data class ChatDialog(
        val title: String,
        val chatId: String,
        val topicName: String?,
        val topicUuid: String,
        val isDirectMessages: Boolean,
        val userId: Int?,
        val focusProviderMessageId: String? = null,
        val focusMessageUuid: String? = null,
        val beginForwardMessageUuid: String? = null,
        val draftStorageSlot: String? = null,
    )
    @Serializable
    data class ChatTopic(val channelName: String, val channelId: String)

    @Serializable
    data class ChatUserInfo(val userName: String, val userId: String, val avatarUrl: String, val email: String)

    @Serializable
    data class ChannelInfo(val channelId: String)
}


object LoginFlow {
    @Serializable
    object ChooseServer

    @Serializable
    object Login
}

object ProfileFlow {
    @Serializable
    object Main

    @Serializable
    object About

    @Serializable
    object ExternalIntegrations

    @Serializable
    object FolderDisplay

    @Serializable
    object Login
}

object Calls: Destinations {
    override val route = "Calls"
    override val icon = R.drawable.call
    override val title = "Calls"
}

object Profile: Destinations {
    override val route = "Profile"
    override val icon = R.drawable.ic_profile
    override val title = "Settings"
}
