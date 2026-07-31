package ru.genesiscorporation.workspace.beta.modules.chatdialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class ComposerPreviewInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modeTabsSwitchToARealMarkdownPreviewAndBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writeLabel = context.getString(R.string.message_composer_write)
        val previewLabel = context.getString(R.string.message_composer_preview)
        val previewRegionLabel = context.getString(
            R.string.message_composer_preview_region,
        )
        var mode by mutableStateOf(ComposerMode.WRITE)
        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    ComposerModeTabs(
                        mode = mode,
                        onModeChange = { mode = it },
                    )
                    if (mode == ComposerMode.PREVIEW) {
                        ComposerMarkdownPreview(
                            markdown = "**Rendered preview**",
                            hasAttachments = false,
                            viewModel = null,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(writeLabel).assertIsSelected()
        composeRule.onNodeWithText(previewLabel).assertIsNotSelected()
        composeRule.onNodeWithContentDescription(previewRegionLabel)
            .assertDoesNotExist()
        composeRule.onNodeWithText(previewLabel).performClick()
        composeRule.onNodeWithText(previewLabel).assertIsSelected()
        composeRule.onNodeWithContentDescription(previewRegionLabel)
            .assertExists()
        composeRule.onNodeWithText(writeLabel).performClick()
        composeRule.onNodeWithText(writeLabel).assertIsSelected()
        composeRule.onNodeWithContentDescription(previewRegionLabel)
            .assertDoesNotExist()
    }
}
