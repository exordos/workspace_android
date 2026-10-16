package ru.genesiscorporation.workspace.beta.modules.login

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.MainActivity

@RunWith(AndroidJUnit4::class)
class PublicationWindowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun organizationInputSurvivesRotationAndSubmitRemainsReachableWithKeyboard() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
        val fixture = "https://example.org"
        compose.onNodeWithContentDescription("Адрес организации")
            .performClick().performTextReplacement(fixture)
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            compose.onNodeWithContentDescription("Адрес организации").assertTextContains(fixture)
            assertSubmitReachable()
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            compose.onNodeWithContentDescription("Адрес организации").assertTextContains(fixture)
            assertSubmitReachable()
        } finally {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun assertSubmitReachable() {
        val button = compose.onNodeWithText("Войти")
        // Configuration changes precede the final window and IME layout.
        compose.waitUntil(10_000) { button.performScrollTo().isDisplayed() }
        button.assertIsDisplayed()
    }
}
