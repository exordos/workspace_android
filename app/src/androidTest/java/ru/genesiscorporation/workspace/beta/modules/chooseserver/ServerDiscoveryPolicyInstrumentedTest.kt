package ru.genesiscorporation.workspace.beta.modules.chooseserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsResponseData

@RunWith(AndroidJUnit4::class)
class ServerDiscoveryPolicyInstrumentedTest {
    @Test
    fun safeWorkspaceSettingsPassOnAndroidRuntime() {
        assertTrue(
            isUsableWorkspaceServerSettings(
                ServerSettingsResponseData(
                    email_auth_enabled = true,
                    realmName = "Example Workspace",
                    meetUrl = "https://meet.example.com/rooms/",
                    realmUrl = "https://workspace.example.com",
                    realmIcon = "urn:url:https://workspace.example.com/logo.png",
                ),
            ),
        )
    }

    @Test
    fun insecureCanonicalRealmFailsClosedOnAndroidRuntime() {
        assertFalse(
            isUsableWorkspaceServerSettings(
                ServerSettingsResponseData(
                    email_auth_enabled = true,
                    realmName = "Example Workspace",
                    meetUrl = "https://meet.example.com",
                    realmUrl = "http://workspace.example.com",
                    realmIcon = null,
                ),
            ),
        )
    }
}
