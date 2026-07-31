package ru.genesiscorporation.workspace.beta.modules.chatdialog

import android.net.Uri
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme

@RunWith(AndroidJUnit4::class)
class SelectedAttachmentPreviewInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun usesFigmaDimensionsAndExposesPerAttachmentUploadState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = SelectedLocalAttachment(
            uri = Uri.parse("content://workspace/photo.jpg"),
            fileName = "photo.jpg",
            contentType = "image/jpeg",
            sizeBytes = 1024,
        )
        val uploadLabel = context.getString(
            R.string.message_composer_attachment_uploading,
            attachment.fileName,
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = true) {
                SelectedAttachmentPreview(
                    attachment = attachment,
                    uploading = true,
                    enabled = true,
                    onRemove = {},
                )
            }
        }

        composeRule.onNodeWithTag(SELECTED_ATTACHMENT_PREVIEW_TAG)
            .assertWidthIsEqualTo(144.dp)
            .assertHeightIsEqualTo(101.dp)
        composeRule.onNodeWithContentDescription(uploadLabel).assertExists()
        composeRule.onNodeWithTag(SELECTED_ATTACHMENT_REMOVE_TAG)
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .assertIsNotEnabled()
    }

    @Test
    fun keepsRemovalActionEnabledWhenUploadIsIdle() {
        var removed = false
        val attachment = SelectedLocalAttachment(
            uri = Uri.parse("content://workspace/document.pdf"),
            fileName = "document.pdf",
            contentType = "application/pdf",
            sizeBytes = 2048,
        )

        composeRule.setContent {
            WokspaceTheme(darkTheme = false) {
                SelectedAttachmentPreview(
                    attachment = attachment,
                    uploading = false,
                    enabled = true,
                    onRemove = { removed = true },
                )
            }
        }

        composeRule.onNodeWithTag(SELECTED_ATTACHMENT_REMOVE_TAG)
            .assertIsEnabled()
            .performClick()
        assertTrue(removed)
    }
}
