package ru.genesiscorporation.workspace.beta

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
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

object ChatFlow {
    @Serializable
    object ChatList

    @Serializable
    data class ChatDialog(val title: String, val chatId: String, val topicId: String?, val isDirectMessages: Boolean)
    @Serializable
    data class ChatTopic(val channelName: String, val channelId: String)
}


object LoginFlow {
    @Serializable
    object ChooseServer

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
    override val icon = R.drawable.group
    override val title = "Profile"
}