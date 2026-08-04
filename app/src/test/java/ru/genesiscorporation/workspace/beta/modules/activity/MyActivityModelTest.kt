package ru.genesiscorporation.workspace.beta.modules.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class MyActivityModelTest {
    @Test
    fun `empty query keeps the Figma activity order`() {
        assertEquals(
            MyActivityDestination.entries,
            filteredActivityDestinations(""),
        )
    }

    @Test
    fun `search is trimmed and case insensitive`() {
        assertEquals(
            listOf(MyActivityDestination.MENTIONS),
            filteredActivityDestinations("  УПОМИН  "),
        )
    }
}
