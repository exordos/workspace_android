package ru.genesiscorporation.workspace.beta.modules.chatdialog

import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement

internal const val MAX_RENDERED_QUOTE_NODES = 64

internal data class BudgetedMessageElement(val element: MessageElement, val quoteBudget: Int = 0)

/** Divide a fixed tree budget among siblings before composition or prefetch. */
internal fun budgetMessageQuotes(elements: List<MessageElement>, budget: Int): List<BudgetedMessageElement> {
    val quoteCount = elements.count {
        it is MessageElement.Quote || it is MessageElement.SnapshotQuote || it is MessageElement.ForwardSnapshot
    }.coerceAtMost(budget.coerceAtLeast(0))
    var index = 0
    var omitted = false
    return buildList {
        elements.forEach { element ->
            if (element !is MessageElement.Quote && element !is MessageElement.SnapshotQuote &&
                element !is MessageElement.ForwardSnapshot
            ) {
                add(BudgetedMessageElement(element))
            } else if (index < quoteCount) {
                val share = budget / quoteCount + if (index < budget % quoteCount) 1 else 0
                add(BudgetedMessageElement(element, share))
                index++
            } else if (!omitted) {
                omitted = true
                add(BudgetedMessageElement(MessageElement.PlainText("Остальные вложенные цитаты скрыты")))
            }
        }
    }
}
