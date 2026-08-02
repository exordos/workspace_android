package ru.genesiscorporation.workspace.beta.modules.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.data.remote.dto.TopicsResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class StreamMessagePresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamMessageMatchesTheFigmaInformationHierarchyAndKeepsActions() {
        var openCount = 0
        var forwardCount = 0
        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                StreamMessageRow(
                    message = message(),
                    topic = topic(),
                    author = author(),
                    avatarBaseUrl = "",
                    showAvatar = false,
                    busy = false,
                    onOpen = { openCount += 1 },
                    onForward = { forwardCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Cassandra Volkova").assertIsDisplayed()
        composeRule.onNodeWithText("# Android app #10").assertIsDisplayed()
        composeRule.onNodeWithText("Сообщение из общего потока").assertIsDisplayed()
        val messageNode = composeRule.onNodeWithContentDescription(
            "Сообщение от Cassandra Volkova в теме Android app #10. " +
                "Нажмите, чтобы открыть в чате",
        )
        messageNode.assertIsDisplayed().performClick()
        messageNode.performTouchInput { longClick() }

        composeRule.runOnIdle {
            assertEquals(1, openCount)
            assertEquals(1, forwardCount)
        }
    }

    private fun message() = MessageResponse(
        uuid = "11111111-1111-4111-8111-111111111111",
        updatedAt = "2026-08-02T12:53:00Z",
        createdAt = "2026-08-02T12:53:00Z",
        streamUuid = "22222222-2222-4222-8222-222222222222",
        topicUuid = "33333333-3333-4333-8333-333333333333",
        userUuid = "44444444-4444-4444-8444-444444444444",
        authorUuid = "44444444-4444-4444-8444-444444444444",
        payload = MessageResponsePayload(
            kind = "markdown",
            content = "Сообщение из общего потока",
        ),
        isOwn = false,
        reactions = emptyMap(),
    )

    private fun topic() = TopicsResponseData(
        uuid = "33333333-3333-4333-8333-333333333333",
        name = "Android app #10",
        streamUuid = "22222222-2222-4222-8222-222222222222",
        updatedAt = "2026-08-02T12:53:00Z",
        unreadCount = 0,
        isDone = false,
        isDefault = false,
    )

    private fun author() = UserResponseData(
        firstName = "Cassandra",
        lastName = "Volkova",
        username = "cassi",
        uuid = "44444444-4444-4444-8444-444444444444",
        status = "online",
        avatar = "",
    )
}
