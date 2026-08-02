package ru.genesiscorporation.workspace.beta.modules.chatchannels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicDividerVisibilityTest {
    @Test
    fun `selected first topic hides its top and bottom dividers`() {
        assertFalse(shouldShowTopicDividerAfter(itemIndex = -1, selectedTopicIndex = 0))
        assertFalse(shouldShowTopicDividerAfter(itemIndex = 0, selectedTopicIndex = 0))
        assertTrue(shouldShowTopicDividerAfter(itemIndex = 1, selectedTopicIndex = 0))
    }

    @Test
    fun `selected middle topic hides only its adjacent dividers`() {
        assertTrue(shouldShowTopicDividerAfter(itemIndex = -1, selectedTopicIndex = 1))
        assertFalse(shouldShowTopicDividerAfter(itemIndex = 0, selectedTopicIndex = 1))
        assertFalse(shouldShowTopicDividerAfter(itemIndex = 1, selectedTopicIndex = 1))
        assertTrue(shouldShowTopicDividerAfter(itemIndex = 2, selectedTopicIndex = 1))
    }

    @Test
    fun `selected last topic hides its top and bottom dividers`() {
        assertTrue(shouldShowTopicDividerAfter(itemIndex = 0, selectedTopicIndex = 2))
        assertFalse(shouldShowTopicDividerAfter(itemIndex = 1, selectedTopicIndex = 2))
        assertFalse(shouldShowTopicDividerAfter(itemIndex = 2, selectedTopicIndex = 2))
    }

    @Test
    fun `all dividers remain visible without a selected topic`() {
        assertTrue(shouldShowTopicDividerAfter(itemIndex = -1, selectedTopicIndex = null))
        assertTrue(shouldShowTopicDividerAfter(itemIndex = 0, selectedTopicIndex = null))
        assertTrue(shouldShowTopicDividerAfter(itemIndex = 1, selectedTopicIndex = null))
    }
}
