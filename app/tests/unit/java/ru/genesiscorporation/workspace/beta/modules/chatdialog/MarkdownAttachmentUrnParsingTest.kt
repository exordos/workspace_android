package ru.genesiscorporation.workspace.beta.modules.chatdialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageElement

class MarkdownAttachmentUrnParsingTest {
    @Test fun `web photo metadata is excluded from the download UUID and caption is retained`() {
        val content = "Посмотри макет\n\n![Макет.png](urn:image:$IMAGE?name=remote-name.png&content_type=image%2Fpng&size=1234)"
        assertEquals(listOf(MessageElement.PlainText("Посмотри макет"), MessageElement.Image("Макет.png", IMAGE)),
            MarkdownPayloadParser.parse(content))
    }

    @Test fun `web file metadata cannot replace the displayed escaped label`() {
        val content = "[План \\[версия 2\\].pdf](urn:file:$FILE?name=another.pdf&content_type=application%2Fpdf&size=789)"
        assertEquals(listOf(MessageElement.File("План [версия 2].pdf", FILE)), MarkdownPayloadParser.parse(content))
    }

    @Test fun `attachment UUID without metadata remains compatible with Android messages`() {
        assertEquals(listOf(MessageElement.Image("photo.jpg", IMAGE), MessageElement.File("notes.txt", FILE)),
            MarkdownPayloadParser.parse("[photo.jpg](urn:image:$IMAGE)\n\n[notes.txt](urn:file:$FILE)"))
    }

    @Test fun `malformed and path traversal references remain text and never become downloads`() {
        val invalid = listOf(
            "../../private", "$IMAGE/../../private", "$IMAGE%2F..%2Fprivate", "$IMAGE#fragment",
            "1-1-1-1-1", "11111111-1111-1111-1111-11111111111g", " $IMAGE", "$IMAGE/",
            "https://example.invalid/$IMAGE", "$IMAGE%3Fname=image.png",
        )
        for (kind in listOf("image", "file")) for (reference in invalid) {
            val content = "[attachment](urn:$kind:$reference?name=ignored)"
            val elements = MarkdownPayloadParser.parse(content)
            assertEquals("Invalid $kind reference was interpreted as a download", listOf(MessageElement.PlainText(content)), elements)
        }
    }

    @Test fun `invalid first attachment does not hide a later valid attachment`() {
        val invalid = "![wrong](urn:image:../../private)"
        assertEquals(listOf(MessageElement.PlainText(invalid), MessageElement.Image("valid.png", IMAGE)),
            MarkdownPayloadParser.parse("$invalid\n![valid.png](urn:image:$IMAGE?size=42)"))
    }

    @Test fun `untrusted metadata never becomes part of an attachment path`() {
        val element = MarkdownPayloadParser.parse(
            "[Local label](urn:file:$FILE?name=../../private&redirect=https%3A%2F%2Fexample.invalid%2F&size=1)").single() as MessageElement.File
        assertEquals(FILE, element.uuid)
        assertEquals("Local label", element.fileName)
        assertFalse(element.uuid.any { it == '?' || it == '/' || it == '%' || it == '&' })
    }

    @Test fun `metadata links in inline code and fenced code remain literal`() {
        val attachment = "![photo](urn:image:$IMAGE?name=photo.png&size=1)"
        for (content in listOf("`$attachment`", "```text\n$attachment\n```"))
            assertEquals(listOf(MessageElement.PlainText(content)), MarkdownPayloadParser.parse(content))
    }

    @Test fun `canonical UUID reuse keeps attachment case and rejects surrounding whitespace`() {
        val uuid = "ABCDEF01-ABCD-4ABC-8DEF-ABCDEF012345"
        assertEquals(listOf(MessageElement.File("label", uuid)), MarkdownPayloadParser.parse("[label](urn:file:$uuid?size=42)"))
        for (reference in listOf(" $uuid", "$uuid ")) {
            val content = "[label](urn:file:$reference?size=42)"
            assertEquals(listOf(MessageElement.PlainText(content)), MarkdownPayloadParser.parse(content))
        }
        val label = "Report [v2]\\draft.pdf"
        val markdown = "[${escapeAttachmentLabel(label)}](urn:file:$uuid)"
        assertEquals(listOf(MessageElement.File(label, uuid)), MarkdownPayloadParser.parse(markdown))
    }

    companion object {
        private const val IMAGE = "11111111-1111-4111-8111-111111111111"
        private const val FILE = "22222222-2222-4222-8222-222222222222"
    }
}
