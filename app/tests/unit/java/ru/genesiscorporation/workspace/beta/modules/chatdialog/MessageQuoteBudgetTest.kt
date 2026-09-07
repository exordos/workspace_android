package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement

class MessageQuoteBudgetTest {
    private val quote = MessageElement.Quote("Author", "00000000-0000-0000-0000-000000000001", "")

    @Test fun branchingQuoteTreeNeverExceedsItsTotalBudget() {
        fun expanded(budget: Int, depth: Int): Int {
            if (depth == 0) return 1
            val children = budgetMessageQuotes(List(32) { quote }, (budget - 1).coerceAtLeast(0))
            return 1 + children.filter { it.element is MessageElement.Quote }.sumOf { expanded(it.quoteBudget, depth - 1) }
        }
        assertEquals(64, expanded(64, 8))
    }

    @Test fun tooManyReferencesAreCappedWithoutDroppingSurroundingText() {
        val elements = listOf(MessageElement.PlainText("Before")) + List(200) { quote } + MessageElement.PlainText("After")
        val result = budgetMessageQuotes(elements, 64)
        assertEquals(64, result.count { it.element is MessageElement.Quote })
        assertEquals(64, result.sumOf { it.quoteBudget })
        assertEquals("Before", (result.first().element as MessageElement.PlainText).text)
        assertEquals("After", (result.last().element as MessageElement.PlainText).text)
        assertEquals(67, result.size)
    }

    @Test fun zeroBudgetProducesOnePlaceholderForAllChildren() {
        val result = budgetMessageQuotes(List(32) { quote }, 0)
        assertEquals(1, result.size)
        assertTrue(result.single().element is MessageElement.PlainText)
    }
}
