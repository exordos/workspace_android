package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.WorkspaceAuthIdleTimeout
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class AuthIdleTimeoutPreferenceInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opensEveryDesktopParityPresetAndUpdatesTheVisibleValue() {
        var selected by mutableStateOf(WorkspaceAuthIdleTimeout.THREE_DAYS)
        var open by mutableStateOf(false)
        val selections = mutableListOf<WorkspaceAuthIdleTimeout>()

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                Column {
                    AuthIdleTimeoutPreference(
                        selected = selected,
                        enabled = true,
                        onOpen = { open = true },
                    )
                    if (open) {
                        AuthIdleTimeoutDialog(
                            selected = selected,
                            enabled = true,
                            onDismiss = { open = false },
                            onSelected = { timeout ->
                                selections += timeout
                                selected = timeout
                                open = false
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(AUTH_IDLE_TIMEOUT_ROW_TAG)
            .assertIsEnabled()
            .assertTextContains("Автовыход")
            .assertTextContains("После неактивности")
            .assertTextContains("3 дня")
            .performClick()
        WorkspaceAuthIdleTimeout.entries
            .zip(listOf("6 часов", "12 часов", "24 часа", "3 дня", "7 дней", "Никогда"))
            .forEach { (timeout, label) ->
                composeRule.onNodeWithTag(
                    "$AUTH_IDLE_TIMEOUT_OPTION_TAG_PREFIX${timeout.name}",
                ).assertTextContains(label)
            }

        composeRule.onNodeWithTag(
            "$AUTH_IDLE_TIMEOUT_OPTION_TAG_PREFIX${WorkspaceAuthIdleTimeout.NEVER.name}",
        ).performScrollTo().performClick()

        composeRule.onNodeWithTag(AUTH_IDLE_TIMEOUT_DIALOG_TAG)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(AUTH_IDLE_TIMEOUT_ROW_TAG)
            .assertTextContains("Никогда")
        composeRule.runOnIdle {
            assertEquals(listOf(WorkspaceAuthIdleTimeout.NEVER), selections)
        }
    }

    @Test
    fun unavailableAccountCannotOpenThePicker() {
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                AuthIdleTimeoutPreference(
                    selected = WorkspaceAuthIdleTimeout.THREE_DAYS,
                    enabled = false,
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithTag(AUTH_IDLE_TIMEOUT_ROW_TAG)
            .assertIsNotEnabled()
    }
}
