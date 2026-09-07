package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MessageActionErrorUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun repeatedSelectionLimitFailureReplacesVisibleSnackbarAndAcknowledgesLatestEvent() {
        val deletion = MessageDeletion(canDelete = { true }, request = { ApiResult.Success("") }, onDeleted = {})
        val selection = MessageSelection(canSelect = { true }, canDelete = { true }, deletion = deletion)
        repeat(MAX_SELECTED_MESSAGES) { selection.toggleMessageSelection(message(it)) }
        val host = SnackbarHostState()
        compose.setContent {
            WokspaceTheme(darkTheme = true, dynamicColor = false) {
                val error by deletion.actionError.collectAsState()
                MessageActionErrorEffect(error, host, deletion::clearActionError)
                Column {
                    Button(onClick = { selection.toggleMessageSelection(message(MAX_SELECTED_MESSAGES)) }) {
                        Text("Select one more")
                    }
                    SnackbarHost(host)
                }
            }
        }

        compose.onNodeWithText("Select one more").performClick()
        val first = compose.runOnIdle { requireNotNull(host.currentSnackbarData) }
        compose.onNodeWithText("Select one more").performClick()
        compose.runOnIdle {
            assertNotSame(first, host.currentSnackbarData)
            assertEquals(R.string.messages_selection_limit, deletion.actionError.value?.resourceId)
            assertEquals(listOf(MAX_SELECTED_MESSAGES), deletion.actionError.value?.formatArgs)
            assertEquals(MAX_SELECTED_MESSAGES, selection.selectedMessageUuids.value.size)
            requireNotNull(host.currentSnackbarData).dismiss()
        }
        compose.runOnIdle { assertNull(deletion.actionError.value) }
    }

    @Test
    fun actionErrorsRenderInEnglishIncludingNumericArguments() {
        renderErrors("en")
        compose.onNodeWithText("This message cannot be deleted").assertIsDisplayed()
        compose.onNodeWithText("Could not delete the message. Please try again").assertIsDisplayed()
        compose.onNodeWithText("You can select no more than 50 messages").assertIsDisplayed()
        compose.onNodeWithText("Deleted: 1 of 2. Could not delete: 1. You can retry deleting the remaining messages").assertIsDisplayed()
    }

    @Test
    fun actionErrorsRenderInRussianIncludingNumericArguments() {
        renderErrors("ru")
        compose.onNodeWithText("Это сообщение нельзя удалить").assertIsDisplayed()
        compose.onNodeWithText("Не удалось удалить сообщение. Попробуйте ещё раз").assertIsDisplayed()
        compose.onNodeWithText("Можно выбрать не больше 50 сообщений").assertIsDisplayed()
        compose.onNodeWithText("Удалено: 1 из 2. Не удалось удалить: 1. Оставшиеся сообщения можно удалить повторно").assertIsDisplayed()
    }

    private fun renderErrors(language: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localizedContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
        val events = listOf(
            MessageActionError(R.string.message_delete_not_allowed, emptyList(), 1),
            MessageActionError(R.string.message_delete_failed, emptyList(), 2),
            MessageActionError(R.string.messages_selection_limit, listOf(50), 3),
            MessageActionError(R.string.messages_delete_partial_failure, listOf(1, 2, 1), 4),
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
            ) {
                WokspaceTheme(darkTheme = true, dynamicColor = false) {
                    Column {
                        events.forEach { error ->
                            val host = remember { SnackbarHostState() }
                            MessageActionErrorEffect(error, host, onErrorShown = {})
                            SnackbarHost(host)
                        }
                    }
                }
            }
        }
    }

    private fun message(index: Int) = MessageResponse(
        uuid = "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
        updatedAt = "2026-09-07T12:00:00Z",
        createdAt = "2026-09-07T12:00:00Z",
        streamUuid = "stream",
        topicUuid = "topic",
        userUuid = "current-user",
        authorUuid = "current-user",
        payload = MessageResponsePayload("markdown", "Selection limit fixture"),
        isOwn = true,
        reactions = emptyMap(),
        read = true,
    )
}
