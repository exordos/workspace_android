package ru.genesiscorporation.workspace.beta.modules.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class MyActivityModelTest {
    @Test
    fun followsTheFigmaDestinationOrder() {
        assertEquals(
            listOf(
                MyActivityDestination.INBOX,
                MyActivityDestination.STARRED,
                MyActivityDestination.PINNED,
                MyActivityDestination.MENTIONS,
                MyActivityDestination.REACTIONS,
                MyActivityDestination.DRAFTS,
                MyActivityDestination.FEED,
            ),
            supportedMyActivityDestinations(""),
        )
    }

    @Test
    fun searchIsTrimmedAndCaseInsensitive() {
        assertEquals(
            listOf(MyActivityDestination.DRAFTS),
            supportedMyActivityDestinations("  ЧЕРН  "),
        )
        assertEquals(
            emptyList<MyActivityDestination>(),
            supportedMyActivityDestinations("нет такого"),
        )
    }
}
