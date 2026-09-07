package ru.genesiscorporation.workspace.beta.modules.chatdialog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponsePayload

class ForwardedQuoteParsingTest {
    private val first = "11111111-1111-4111-8111-111111111111"
    private val second = "22222222-2222-4222-8222-222222222222"

    @Test
    fun `reference-only forwarding preserves every source and has a nonempty preview`() {
        val markdown = "[Alice](urn:quote:$first)\n\n[Bob](urn:quote:$second)"
        val elements = MarkdownPayloadParser.parse(markdown)
        assertEquals(listOf(first, second), elements.filterIsInstance<MessageElement.Quote>().map { it.uuid })
        assertTrue(elements.all { it is MessageElement.Quote })
        assertEquals(listOf(first, second), message(markdown).asQuotedMessages().map { it.uuid })
        assertEquals("Forwarded message", message(markdown).description())
        assertEquals("Пересланное сообщение", message(markdown).description(forwardedLabel = "Пересланное сообщение"))
    }

    @Test
    fun `escaped author name remains one reference with readable label`() {
        val markdown = "[Alice \\[team\\]](urn:quote:$first)"
        val quote = MarkdownPayloadParser.parse(markdown).single() as MessageElement.Quote
        assertEquals("Alice [team]", quote.displayName)
        assertEquals(first, quote.uuid)
        assertEquals(first, message(markdown).asQuotedMessages().single().uuid)
    }

    @Test
    fun `existing reply bodies and ordinary messages keep their preview`() {
        val markdown = "[Alice](urn:quote:$first)\n\nReply body\n\n[Bob](urn:quote:$second)\n\nAnother reply"
        assertEquals(listOf("Reply body", "Another reply"), message(markdown).asQuotedMessages().map { it.text })
        assertEquals("Another reply", message(markdown).description())
        assertEquals("Ordinary message", message("Ordinary message").description())
    }

    @Test
    fun `attachment and quote references retain their original order`() {
        val elements = MarkdownPayloadParser.parse("[Alice](urn:quote:$first)\n\n![photo.png](urn:image:$second)\n\nComment")
        assertTrue(elements[0] is MessageElement.Quote)
        assertTrue(elements[1] is MessageElement.Image)
        assertEquals(MessageElement.PlainText("Comment"), elements[2])
    }

    @Test
    fun `materialized forward preserves author body and nested snapshots without source access`() {
        val elements = MarkdownPayloadParser.parse("> **Alice \\[team\\]**:\n> First line\n>\n> > **Bob**:\n> > Nested text\n\n> **Carol**:\n> Second message")
        val snapshots = elements.filterIsInstance<MessageElement.SnapshotQuote>()
        assertEquals(2, snapshots.size)
        assertEquals("Alice [team]", snapshots[0].displayName)
        assertEquals("Carol", snapshots[1].displayName)
        val nested = MarkdownPayloadParser.parse(snapshots[0].text)
        assertEquals(MessageElement.PlainText("First line"), nested[0])
        assertEquals(MessageElement.SnapshotQuote("Bob", "Nested text"), nested[1])
        assertEquals("Second message", snapshots[1].text)
    }

    @Test
    fun `fenced code does not load references or interpret quote lines`() {
        val code = "```text\n[Alice](urn:quote:$first)\n> literal\nprint(\\n)\n```"
        assertEquals(listOf(MessageElement.PlainText(code)), MarkdownPayloadParser.parse(code))
    }

    @Test
    fun `snapshot attachments are parsed inside their own quote`() {
        val source = "> **Alice**:\n> ![photo.png](urn:image:$second)"
        val snapshot = MarkdownPayloadParser.parse(source).single() as MessageElement.SnapshotQuote
        assertEquals(listOf(MessageElement.Image("photo.png", second)), MarkdownPayloadParser.parse(snapshot.text))
    }

    @Test
    fun `ordinary and nested blockquotes remain unchanged markdown`() {
        val markdown = "> Ordinary **emphasis**\n>\n> > Nested text\n> **Alice**:\n> Still ordinary quoted text"
        assertEquals(listOf(MessageElement.PlainText(markdown)), MarkdownPayloadParser.parse(markdown))
    }

    @Test
    fun `ordinary bold blockquote keeps its text preview while authored snapshot uses localized label`() {
        val ordinary = "> **important** details\n> More quoted text"
        assertEquals(ordinary, message(ordinary).description())
        assertEquals(ordinary, message(ordinary).description(forwardedLabel = "Пересланное сообщение"))
        assertEquals("Forwarded message", message("> **Alice**:\n> Forwarded body").description())
        assertEquals(
            "Пересланное сообщение",
            message("> **Alice**:\n> Forwarded body").description(forwardedLabel = "Пересланное сообщение"),
        )
    }

    @Test
    fun `authored snapshot after an ordinary blockquote is still recognized`() {
        val ordinary = "> Ordinary quote\n> > Nested quote"
        assertEquals(
            listOf(MessageElement.PlainText(ordinary), MessageElement.SnapshotQuote("Alice", "Forwarded body")),
            MarkdownPayloadParser.parse("$ordinary\n\n> **Alice**:\n> Forwarded body"),
        )
    }

    @Test
    fun `ordinary blockquote keeps existing inline file handling without promoting later lines`() {
        val markdown = "> Read [report.pdf](urn:file:$first)\n> **Alice**:\n> Ordinary followup"
        assertEquals(
            listOf(
                MessageElement.PlainText("> Read"),
                MessageElement.File("report.pdf", first),
                MessageElement.PlainText("> **Alice**:\n> Ordinary followup"),
            ),
            MarkdownPayloadParser.parse(markdown),
        )
    }

    @Test
    fun `ordinary quotes nested inside snapshots retain their markdown`() {
        val snapshot = MarkdownPayloadParser.parse("> **Alice**:\n> > Ordinary nested quote\n> > Continued").single() as MessageElement.SnapshotQuote
        assertEquals(
            listOf(MessageElement.PlainText("> Ordinary nested quote\n> Continued")),
            MarkdownPayloadParser.parse(snapshot.text),
        )
    }

    @Test
    fun `JSON newlines decode once while literal backslash n remains user text`() {
        val payload = Json.decodeFromString<MessageResponsePayload>(
            """{"kind":"markdown","content":"First\nSecond\\nLiteral"}""",
        )
        assertEquals("First\nSecond\\nLiteral", payload.content)
        assertEquals(listOf(MessageElement.PlainText(payload.content)), MarkdownPayloadParser.parse(payload.content))
    }

    @Test
    fun `inline code stays in its surrounding paragraph and never resolves references`() {
        val text = "Before `[Alice](urn:quote:$first)` after `> literal` and tail"
        assertEquals(listOf(MessageElement.PlainText(text)), MarkdownPayloadParser.parse(text))
    }

    @Test
    fun `canonical selected quote text stays captured instead of resolving the full source`() {
        val quote = MarkdownPayloadParser.parse("[Alice](urn:quote:$first?text=Selected%20text%0Asecond%20line%20%26%20%23)").single() as MessageElement.Quote
        assertEquals(first, quote.uuid)
        assertEquals("Selected text\nsecond line & #", quote.text)
        assertTrue(MarkdownPayloadParser.parse("[Alice](urn:quote:$first?text=%ZZ)").single() is MessageElement.PlainText)
    }

    @Test
    fun `selected quote preview decodes the captured snippet without exposing its reference`() {
        val markdown = "[Alice \\[team\\]](urn:quote:$first?text=Selected+text%0Asecond%20line%20%26%20%23%20%E2%9C%93)"
        val selectedText = (MarkdownPayloadParser.parse(markdown).single() as MessageElement.Quote).text
        assertEquals("Selected text\nsecond line & # ✓", selectedText)
        assertEquals(selectedText, message(markdown).description())
        assertEquals(first, message(markdown).asQuotedMessages().single().uuid)
    }

    @Test
    fun `reply body keeps preview precedence over a captured quote snippet`() {
        val markdown = "[Alice](urn:quote:$first?text=Selected%20snippet)\n\nReply body"
        assertEquals("Reply body", message(markdown).description())
        assertEquals("Reply body", message(markdown).asQuotedMessages().single().text)
    }

    @Test
    fun `mixed whole and selected quote previews preserve source order`() {
        val markdown = "[Alice](urn:quote:$first)\n\n[Bob](urn:quote:$second?text=Last%20snippet)"
        assertEquals(listOf(first, second), message(markdown).asQuotedMessages().map { it.uuid })
        assertEquals(listOf("", "Last snippet"), message(markdown).asQuotedMessages().map { it.text })
        assertEquals("Last snippet", message(markdown).description())
    }

    @Test
    fun `malformed selected quote encoding keeps the literal preview fallback`() {
        for (encoded in listOf("%ZZ", "%", "%2")) {
            val markdown = "[Alice](urn:quote:$first?text=$encoded)\n\nReply body"
            assertEquals(listOf(MessageElement.PlainText(markdown)), MarkdownPayloadParser.parse(markdown))
            assertEquals(markdown, message(markdown).description())
            assertEquals(null, message(markdown).asQuotedMessages().single().uuid)
        }
    }

    private fun message(content: String) = MessageResponse(
        uuid = "33333333-3333-4333-8333-333333333333",
        updatedAt = "2026-09-07T12:00:00Z", createdAt = "2026-09-07T12:00:00Z",
        streamUuid = "stream", topicUuid = "topic", userUuid = "reader", authorUuid = "sender",
        payload = MessageResponsePayload("markdown", content), isOwn = false,
        reactions = emptyMap(), read = true,
    )
}
