package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class AllTopicsRowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allTopicsRowIsLabelledAndOpensTheStreamTimeline() {
        var clickCount = 0
        val stream = Stream(
            uuid = "11111111-1111-4111-8111-111111111111",
            unreadCount = 0,
            updatedAt = "2026-08-01T18:00:00Z",
            name = "Разработка",
            isPrivate = false,
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                AllTopicsRow(
                    stream = stream,
                    onClick = { clickCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Все темы").assertIsDisplayed()
        composeRule.onNodeWithText("Общий поток канала").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Все темы канала Разработка")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }
}
