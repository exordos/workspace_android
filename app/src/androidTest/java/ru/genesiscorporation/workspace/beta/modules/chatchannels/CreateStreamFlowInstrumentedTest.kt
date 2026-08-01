package ru.genesiscorporation.workspace.beta.modules.chatchannels

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class CreateStreamFlowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validNameAndSelectedMemberSubmitTheFigmaForm() {
        var submission: CreateChannelInput? = null
        val users = listOf(
            user("current", "cassi", "Кассандра"),
            user("member", "member", "Участник"),
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                CreateStreamFlowScreen(
                    users = users,
                    catalogState = QueryState.Success,
                    createState = QueryState.Idle,
                    currentUserUuid = "current",
                    baseUrl = "",
                    onBack = {},
                    onClose = {},
                    onRetry = {},
                    onSubmit = { submission = it },
                    memberAvatar = { Spacer(Modifier.size(38.dp)) },
                )
            }
        }

        composeRule.onNodeWithTag(STREAM_CREATION_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Создать стрим").assertIsDisplayed()
        composeRule.onNodeWithTag("${STREAM_MEMBER_ROW_TAG_PREFIX}current")
            .assertDoesNotExist()
        composeRule.onNodeWithTag(STREAM_CREATE_SUBMIT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(STREAM_NAME_FIELD_TAG)
            .performTextInput("  Команда CASSI  ")
        composeRule.onNodeWithTag("${STREAM_MEMBER_ROW_TAG_PREFIX}member")
            .performClick()
        composeRule.onNodeWithTag(STREAM_CREATE_SUBMIT_TAG)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Команда CASSI", submission?.name)
            assertEquals(setOf("member"), submission?.memberUserUuids)
            assertEquals("", submission?.description)
            assertEquals(false, submission?.inviteOnly)
            assertEquals(false, submission?.announce)
        }
    }

    private fun user(
        uuid: String,
        username: String,
        firstName: String,
    ) = UserResponseData(
        uuid = uuid,
        username = username,
        firstName = firstName,
        email = "$username@example.test",
        status = "active",
        avatar = "",
    )
}
