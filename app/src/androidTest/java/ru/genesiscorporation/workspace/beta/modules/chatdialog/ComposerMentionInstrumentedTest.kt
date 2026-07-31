package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class ComposerMentionInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun suggestionsExposeLocalizedFunctionalRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suggestion = ComposerMentionSuggestion(
            userUuid = "11111111-1111-4111-8111-111111111111",
            displayName = "Alice Reed",
            username = "alice",
            email = "alice@example.com",
            status = "active",
        )
        val itemDescription = context.getString(
            R.string.message_composer_mention_item_description,
            suggestion.displayName,
            suggestion.username,
        )
        val selected = mutableListOf<ComposerMentionSuggestion>()
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                ComposerMentionSuggestions(
                    suggestions = listOf(suggestion),
                    onSelect = selected::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription(itemDescription)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(suggestion), selected)
        }
    }
}
