package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UrnParserTest {
    @Test
    fun imageMetadataDoesNotBecomePartOfDownloadPath() {
        val fileUuid = "369741bd-270f-4ab1-bb30-940a41753754"
        assertEquals(
            "/api/workspace/v1/messenger/files/$fileUuid/actions/download",
            UrnParser.parseUrl(
                "urn:image:$fileUuid?name=image.png&content_type=image%2Fpng",
                "https://workspace.example",
            ),
        )
    }

    @Test
    fun invalidImageIdentifierIsRejected() {
        assertEquals(
            null,
            UrnParser.parseUrl(
                "urn:image:../../users/me",
                "https://workspace.example",
            ),
        )
    }

    @Test
    fun gravatarUsesTheTlsOnlyEndpoint() {
        assertEquals(
            "https://secure.gravatar.com/avatar/avatar-hash",
            UrnParser.parseUrl(
                "urn:gravatar:avatar-hash",
                "https://workspace.example",
            ),
        )
    }
}
