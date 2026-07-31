package ru.genesiscorporation.workspace.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

class WorkspaceMarkdownInlineMetadataTest {
    @Test
    fun `mention catalog resolves UUID display name and username uniquely`() {
        val catalog = WorkspaceMentionCatalog.from(
            listOf(
                candidate(
                    uuid = ALICE_UUID,
                    displayText = "Alice Reed",
                    username = "alice",
                ),
            ),
        )

        assertEquals(ALICE_UUID, catalog.resolve(ALICE_UUID.uppercase())?.userUuid)
        assertEquals(ALICE_UUID, catalog.resolve(" @Alice   Reed ")?.userUuid)
        assertEquals(ALICE_UUID, catalog.resolve("ALICE")?.userUuid)
        assertEquals("Alice Reed", catalog.resolveUuid(ALICE_UUID)?.displayText)
    }

    @Test
    fun `mention catalog rejects ambiguous labels and conflicting UUID rows`() {
        val duplicateNameCatalog = WorkspaceMentionCatalog.from(
            listOf(
                candidate(ALICE_UUID, "Shared Name", "alice"),
                candidate(BOB_UUID, "Shared Name", "bob"),
            ),
        )
        val duplicateUuidCatalog = WorkspaceMentionCatalog.from(
            listOf(
                candidate(ALICE_UUID, "Alice Reed", "alice"),
                candidate(ALICE_UUID, "Different Person", "other"),
            ),
        )
        val repeatedUuidCatalog = WorkspaceMentionCatalog.from(
            listOf(
                candidate(ALICE_UUID, "Alice Reed", "alice"),
                candidate(ALICE_UUID, "Alice Reed", "alice"),
            ),
        )

        assertNull(duplicateNameCatalog.resolve("Shared Name"))
        assertEquals(ALICE_UUID, duplicateNameCatalog.resolve("alice")?.userUuid)
        assertNull(duplicateUuidCatalog.resolveUuid(ALICE_UUID))
        assertNull(duplicateUuidCatalog.resolve("alice"))
        assertNull(repeatedUuidCatalog.resolveUuid(ALICE_UUID))
    }

    @Test
    fun `resolved display plain canonical and link mentions use only user UUID URNs`() {
        val rendered = renderWorkspaceMarkdownInlineMetadata(
            markdown = "Hi @**Alice Reed**, @alice, " +
                "<@$ALICE_UUID> and [Alice](urn:user:$ALICE_UUID)",
            mentionCatalog = aliceCatalog(),
        )

        val mention = "[@Alice Reed](urn:user:$ALICE_UUID)"
        assertEquals(
            "Hi $mention, $mention, $mention and $mention",
            rendered,
        )
    }

    @Test
    fun `unresolved display mention is readable while canonical UUID remains actionable`() {
        val rendered = renderWorkspaceMarkdownInlineMetadata(
            markdown = "Hi @**Unknown User** <@$BOB_UUID>",
        )

        assertEquals(
            "Hi @Unknown User [@$BOB_UUID](urn:user:$BOB_UUID)",
            rendered,
        )
    }

    @Test
    fun `overlong or markdown-shaped labels cannot redirect or reshape mention links`() {
        val longPrefix = "A".repeat(200)
        val catalog = WorkspaceMentionCatalog.from(
            listOf(candidate(ALICE_UUID, longPrefix, "alice")),
        )
        val unsafeLabelCatalog = WorkspaceMentionCatalog.from(
            listOf(candidate(BOB_UUID, "A[lice]*Ops", "bob")),
        )

        assertEquals(
            "@${longPrefix}B",
            renderWorkspaceMarkdownInlineMetadata(
                markdown = "@**${longPrefix}B**",
                mentionCatalog = catalog,
            ),
        )
        assertEquals(
            "[@A\\[lice\\]\\*Ops](urn:user:$BOB_UUID)",
            renderWorkspaceMarkdownInlineMetadata(
                markdown = "<@$BOB_UUID>",
                mentionCatalog = unsafeLabelCatalog,
            ),
        )
        assertEquals(
            "@A\\_B",
            renderWorkspaceMarkdownInlineMetadata("@**A_B**"),
        )
    }

    @Test
    fun `known emoji renders while custom and unknown shortcodes stay readable`() {
        val rendered = renderWorkspaceMarkdownInlineMetadata(
            markdown = "Hi :smile: :thumbs-up: :party_parrot: :unknown:",
            resolveEmoji = emojiResolver(),
        )

        assertEquals(
            "Hi 😄 👍 :party_parrot: :unknown:",
            rendered,
        )
    }

    @Test
    fun `code destinations autolinks images and escapes remain metadata inert`() {
        val markdown = listOf(
            "Inline `:smile: @alice <@$ALICE_UUID>`",
            "",
            "```md",
            ":smile: @alice <@$ALICE_UUID>",
            "```",
            "",
            "> ~~~",
            "> :smile: @alice",
            "> ~~~",
            "",
            "    :smile: @alice",
            ">     :smile: @alice",
            "",
            "[label :smile:](https://example.test/:smile:/@alice)",
            "<https://example.test/:smile:/@alice>",
            "![alt :smile:](https://example.test/:smile:)",
            "\\:smile: \\@alice",
            "Unclosed < marker :smile: @alice",
            "Visible :smile: @alice",
        ).joinToString("\n")

        val rendered = renderWorkspaceMarkdownInlineMetadata(
            markdown = markdown,
            mentionCatalog = aliceCatalog(),
            resolveEmoji = emojiResolver(),
        )

        assertEquals(
            markdown.replace(
                "[label :smile:]",
                "[label 😄]",
            ).replace(
                "Unclosed < marker :smile: @alice",
                "Unclosed < marker 😄 [@Alice Reed](urn:user:$ALICE_UUID)",
            ).replace(
                "Visible :smile: @alice",
                "Visible 😄 [@Alice Reed](urn:user:$ALICE_UUID)",
            ),
            rendered,
        )
    }

    @Test
    fun `metadata inside revealed spoiler text renders without changing control URN`() {
        val spoiler = parseWorkspaceInlineSpoilers(
            "Before ||:smile: @**Alice Reed**||",
        ).render(
            revealedIds = setOf(0),
            showLabel = "Show",
            hideLabel = "Hide",
        )

        assertEquals(
            "Before [Hide](urn:workspace-mobile-inline-spoiler:0) " +
                "😄 [@Alice Reed](urn:user:$ALICE_UUID)",
            renderWorkspaceMarkdownInlineMetadata(
                markdown = spoiler,
                mentionCatalog = aliceCatalog(),
                resolveEmoji = emojiResolver(),
            ),
        )
    }

    @Test
    fun `catalog parser ignores comments and malformed rows and normalizes aliases`() {
        val parsed = parseWorkspaceEmojiCatalog(
            StringReader(
                listOf(
                    "# generated",
                    "smile\t😄",
                    "THUMBS-UP\t👍",
                    "malformed",
                    "\tempty",
                    "unsafe\tvalue\textra",
                ).joinToString("\n"),
            ).buffered(),
        )

        assertEquals(
            mapOf(
                "smile" to "😄",
                "thumbs_up" to "👍",
            ),
            parsed,
        )
    }

    private fun aliceCatalog(): WorkspaceMentionCatalog =
        WorkspaceMentionCatalog.from(
            listOf(candidate(ALICE_UUID, "Alice Reed", "alice")),
        )

    private fun emojiResolver(): (String) -> String? {
        val values = mapOf(
            "smile" to "😄",
            "thumbs_up" to "👍",
        )
        return { raw ->
            values[
                raw
                    .lowercase()
                    .replace('-', '_')
            ]
        }
    }

    private fun candidate(
        uuid: String,
        displayText: String,
        username: String,
    ) = WorkspaceMentionCandidate(
        userUuid = uuid,
        displayText = displayText,
        username = username,
    )

    private companion object {
        const val ALICE_UUID = "11111111-1111-4111-8111-111111111111"
        const val BOB_UUID = "22222222-2222-4222-8222-222222222222"
    }
}
