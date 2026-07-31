package ru.genesiscorporation.workspace.beta.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class WorkspaceMarkdownSemanticsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun richBlocksExposeLocalizedStructureWithoutReplacingMessageText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedDescription = listOf(
            context.getString(R.string.markdown_structure_quote),
            context.getString(R.string.markdown_structure_ordered_list),
            context.getString(R.string.markdown_structure_code_block) + ": kotlin",
        ).joinToString(". ")
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                EnhancedMarkdown(
                    markdown = """
                        > quoted

                        1. first
                        2. second

                        ```kotlin
                        val answer = 42
                        ```
                    """.trimIndent(),
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    navController = null,
                    viewModel = null,
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                expectedDescription,
            ),
            useUnmergedTree = true,
        ).assertExists()
        onView(withText(containsString("quoted")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun plainInlineMarkdownDoesNotInventStructuralState() {
        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                EnhancedMarkdown(
                    markdown = "Plain **bold** and `inline code`",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    navController = null,
                    viewModel = null,
                )
            }
        }

        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }
}
