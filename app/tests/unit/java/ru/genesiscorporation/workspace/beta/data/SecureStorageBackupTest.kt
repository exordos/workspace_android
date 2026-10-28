package ru.genesiscorporation.workspace.beta.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SecureStorageBackupTest {
    @Test fun deviceBoundTokenKeysetsAreExcludedFromEveryBackupTransport() {
        for (filename in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/xml/$filename"))
            val root = document.documentElement
            val transports = if (root.tagName == "full-backup-content") listOf(root) else
                listOf("cloud-backup", "device-transfer").map {
                    root.getElementsByTagName(it).item(0) as Element
                }
            for (transport in transports) {
                val excludes = transport.getElementsByTagName("exclude")
                assertTrue("$filename/${transport.tagName} must not restore a token keyset without its Keystore key",
                    (0 until excludes.length).any {
                        val exclude = excludes.item(it) as Element
                        exclude.getAttribute("domain") == "sharedpref" &&
                            exclude.getAttribute("path") == "secure_tokens.xml"
                    })
            }
        }
    }
}
