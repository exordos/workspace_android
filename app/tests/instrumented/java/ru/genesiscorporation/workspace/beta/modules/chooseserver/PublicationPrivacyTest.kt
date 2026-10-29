package ru.genesiscorporation.workspace.beta.modules.chooseserver

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class PublicationPrivacyTest {
    @get:Rule val compose = createComposeRule()

    private val title: String
        get() = if (InstrumentationRegistry.getInstrumentation().targetContext.resources
                .configuration.locales[0].language == "ru") {
            "Политика конфиденциальности"
        } else {
            "Privacy policy"
        }

    private fun showEntry(handler: UriHandler) {
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                WokspaceTheme(darkTheme = false, dynamicColor = false) {
                    ChooseServerContent("", QueryState.Idle, {}, {})
                }
            }
        }
    }

    @Test fun privacyPolicyIsAvailableBeforeSignInAndOpensWorkspaceDocument() {
        var opened: String? = null
        showEntry(object : UriHandler {
            override fun openUri(uri: String) { opened = uri }
        })
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("https://exordos.com/legal/workspace-privacy", opened)
        }
    }

    @Test fun unavailableBrowserDoesNotCrashTheSignInScreen() {
        showEntry(object : UriHandler {
            override fun openUri(uri: String) {
                throw IllegalArgumentException("Synthetic browser unavailable")
            }
        })
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    @Test fun embeddedCallSdkDoesNotRequestDeviceCalendarAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()
        assertFalse("Device calendar is not used by Workspace", Manifest.permission.READ_CALENDAR in permissions)
        assertFalse("Device calendar is not used by Workspace", Manifest.permission.WRITE_CALENDAR in permissions)
    }
}
