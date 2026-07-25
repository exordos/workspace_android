package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UrnParserTest {
    @Test
    fun imageMetadataDoesNotBecomePartOfDownloadPath() {
        assertEquals(
            "/api/workspace/v1/messenger/files/1234/actions/download",
            UrnParser.parseUrl(
                "urn:image:1234?name=image.png&content_type=image%2Fpng",
                "https://workspace.example",
            ),
        )
    }
}
