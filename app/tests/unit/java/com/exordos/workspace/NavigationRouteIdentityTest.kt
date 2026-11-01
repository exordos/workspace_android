package com.exordos.workspace

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every typed destination belongs to the current Exordos namespace. */
@OptIn(ExperimentalSerializationApi::class)
class NavigationRouteIdentityTest {
    @Test fun `every typed destination uses the current application namespace`() {
        val routes: Map<String, KSerializer<*>> = mapOf(
            "HomeFlow.HomeBase" to HomeFlow.HomeBase.serializer(),
            "HomeFlow.HomeInbounds" to HomeFlow.HomeInbounds.serializer(),
            "HomeFlow.HomeFavorites" to HomeFlow.HomeFavorites.serializer(),
            "HomeFlow.HomeMentions" to HomeFlow.HomeMentions.serializer(),
            "HomeFlow.HomeReactions" to HomeFlow.HomeReactions.serializer(),
            "HomeFlow.HomeDrafts" to HomeFlow.HomeDrafts.serializer(),
            "HomeFlow.HomeFeed" to HomeFlow.HomeFeed.serializer(),
            "HomeFlow.ChatDialog" to HomeFlow.ChatDialog.serializer(),
            "ChatFlow.ChatList" to ChatFlow.ChatList.serializer(),
            "ChatFlow.ChatDialog" to ChatFlow.ChatDialog.serializer(),
            "ChatFlow.ChatTopic" to ChatFlow.ChatTopic.serializer(),
            "ChatFlow.ChatUserInfo" to ChatFlow.ChatUserInfo.serializer(),
            "ChatFlow.StreamInfo" to ChatFlow.StreamInfo.serializer(),
            "ChatFlow.CreateBase" to ChatFlow.CreateBase.serializer(),
            "ChatFlow.CreateStream" to ChatFlow.CreateStream.serializer(),
            "ChatFlow.CreateDirectStream" to ChatFlow.CreateDirectStream.serializer(),
            "ChatFlow.AddUsersToStream" to ChatFlow.AddUsersToStream.serializer(),
            "LoginFlow.Login" to LoginFlow.Login.serializer(),
            "LoginFlow.Otp" to LoginFlow.Otp.serializer(),
            "LoginFlow.Projects" to LoginFlow.Projects.serializer(),
            "StreamCreationFlow.CreateBase" to StreamCreationFlow.CreateBase.serializer(),
            "StreamCreationFlow.CreateStream" to StreamCreationFlow.CreateStream.serializer(),
            "StreamCreationFlow.CreateDirectStream" to StreamCreationFlow.CreateDirectStream.serializer(),
            "SettingsFlow.Settings" to SettingsFlow.Settings.serializer(),
            "SettingsFlow.FolderList" to SettingsFlow.FolderList.serializer(),
            "SettingsFlow.AddFolder" to SettingsFlow.AddFolder.serializer(),
            "ProfileFlow.Main" to ProfileFlow.Main.serializer(),
            "ProfileFlow.OwnUserSettings" to ProfileFlow.OwnUserSettings.serializer(),
            "ProfileFlow.VisualSettings" to ProfileFlow.VisualSettings.serializer(),
            "ProfileFlow.FolderSettings" to ProfileFlow.FolderSettings.serializer(),
            "ProfileFlow.AddFolder" to ProfileFlow.AddFolder.serializer(),
            "ProfileFlow.Login" to ProfileFlow.Login.serializer(),
            "ProfileFlow.Otp" to ProfileFlow.Otp.serializer(),
        )
        routes.forEach { (route, serializer) ->
            assertEquals(route, "com.exordos.workspace.$route", serializer.descriptor.serialName)
        }
    }
}
