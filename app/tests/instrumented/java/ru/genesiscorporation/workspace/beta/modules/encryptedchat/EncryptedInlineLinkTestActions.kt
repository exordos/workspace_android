package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/** Press the real annotated text range; a paragraph-level semantic click cannot test inline links. */
internal fun ComposeContentTestRule.clickEncryptedInlineLink(tag: String, label: String) {
    val node = onNodeWithTag(tag, useUnmergedTree = true)
    val layout = encryptedTextLayout(tag)
    val start = layout.layoutInput.text.text.indexOf(label)
    assertTrue("Expected inline link label is present", start >= 0 && label.isNotEmpty())
    val bounds = layout.getBoundingBox(start + label.length / 2)
    node.performTouchInput { click(bounds.center) }
}

internal fun ComposeContentTestRule.encryptedTextLayout(tag: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
        assertTrue("Text exposes its production layout", get(layouts))
    }
    return layouts.single()
}

@OptIn(ExperimentalTextApi::class)
internal fun ComposeContentTestRule.encryptedInlineLinkCount(tag: String): Int = encryptedTextLayout(tag).layoutInput.text.let {
    it.getLinkAnnotations(0, it.length).size
}
