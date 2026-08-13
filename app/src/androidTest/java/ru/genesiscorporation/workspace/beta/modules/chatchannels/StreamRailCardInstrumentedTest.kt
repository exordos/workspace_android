package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.DisplayedUnreadCount
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class StreamRailCardInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandedRailUsesFigmaDimensionsAndKeepsTextOutOfThePanelEdge() {
        var clickCount = 0
        var longClickCount = 0
        val stream = Stream(
            uuid = "11111111-1111-4111-8111-111111111111",
            unreadCount = 23,
            updatedAt = "2026-08-02T08:00:00Z",
            name = "Разработка Workspace",
            isPrivate = false,
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                StreamRailCard(
                    item = stream,
                    hasUnreadMention = false,
                    fullyMuted = false,
                    displayedUnread = DisplayedUnreadCount(stream.unreadCount, false),
                    hasSplitCounters = false,
                    baseUrl = "",
                    avatarUrn = null,
                    selected = true,
                    onClick = { clickCount += 1 },
                    onLongClick = { longClickCount += 1 },
                    avatarContent = {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(Color.Blue),
                        )
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag("stream-rail-${stream.uuid}")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(56.dp)
            .assertHeightIsEqualTo(64.dp)
            .performClick()
        composeRule
            .onNodeWithTag(
                testTag = "stream-rail-unread-${stream.uuid}",
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(stream.name, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(
                "Открыть канал ${stream.name}, непрочитанных: ${stream.unreadCount}",
            )
            .assertIsDisplayed()
            .performTouchInput { longClick() }

        composeRule.runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

    @Test
    fun mentionsOnlyUnreadShowsAtSignWhenCurrentUserWasMentioned() {
        val stream = Stream(
            uuid = "22222222-2222-4222-8222-222222222222",
            unreadCount = 23,
            updatedAt = "2026-08-02T13:00:00Z",
            name = "Упоминания",
            isPrivate = false,
            notificationMode = "mentions_only",
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                StreamRailCard(
                    item = stream,
                    hasUnreadMention = true,
                    fullyMuted = false,
                    displayedUnread = DisplayedUnreadCount(stream.unreadCount, false),
                    hasSplitCounters = false,
                    baseUrl = "",
                    avatarUrn = null,
                    selected = false,
                    onClick = {},
                    onLongClick = null,
                    avatarContent = { Box(Modifier.size(40.dp)) },
                )
            }
        }

        composeRule.onNodeWithText("@", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("23", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun mentionsOnlyOrdinaryUnreadKeepsNumericBadge() {
        val stream = Stream(
            uuid = "33333333-3333-4333-8333-333333333333",
            unreadCount = 23,
            updatedAt = "2026-08-02T13:00:00Z",
            name = "Обычные сообщения",
            isPrivate = false,
            notificationMode = "mentions_only",
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                StreamRailCard(
                    item = stream,
                    hasUnreadMention = false,
                    fullyMuted = false,
                    displayedUnread = DisplayedUnreadCount(stream.unreadCount, false),
                    hasSplitCounters = false,
                    baseUrl = "",
                    avatarUrn = null,
                    selected = false,
                    onClick = {},
                    onLongClick = null,
                    avatarContent = { Box(Modifier.size(40.dp)) },
                )
            }
        }

        composeRule.onNodeWithText("23", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("@", useUnmergedTree = true).assertDoesNotExist()
    }
}
